package me.aap.fermata.ui.fragment;

import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_EXO;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_MP;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_VLC;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_SCANNER_DEFAULT;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_SCANNER_SYSTEM;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_SCANNER_VLC;
import static me.aap.utils.ui.UiUtils.ID_NULL;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.PlaybackControlPrefs;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.Consumer;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PrefCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.pref.PreferenceView;
import me.aap.utils.pref.PreferenceViewAdapter;

/**
 * @author Andrey Pavlenko
 */
public class SettingsFragment extends MainActivityFragment implements MainActivityListener,
		PreferenceStore.Listener {
	private PreferenceViewAdapter adapter;

	@Override
	public int getFragmentId() {
		return R.id.settings_fragment;
	}

	@Override
	public CharSequence getTitle() {
		PreferenceSet set = adapter.getPreferenceSet();
		if (set.getParent() != null) return getResources().getString(set.get().title);
		else return getResources().getString(R.string.settings);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.pref_list_view, container, false);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		if (adapter != null) outState.putInt("id", adapter.getPreferenceSet().getId());
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
		super.onViewCreated(view, state);
		adapter = createAdapter();
		MainActivityDelegate a = getActivityDelegate();
		a.addBroadcastListener(this);
		a.getPrefs().addBroadcastListener(this);

		RecyclerView listView = view.findViewById(R.id.prefs_list_view);
		listView.setHasFixedSize(true);
		listView.setLayoutManager(new LinearLayoutManager(getContext()));
		listView.setAdapter(adapter);

		if (state != null) {
			PreferenceSet p = adapter.getPreferenceSet().find(state.getInt("id", ID_NULL));
			if (p != null) adapter.setPreferenceSet(p);
		}
	}

	@Override
	public void onDestroyView() {
		cleanUp(getActivityDelegate());
		super.onDestroyView();
	}

	private void cleanUp(MainActivityDelegate a) {
		a.removeBroadcastListener(this);
		a.getPrefs().removeBroadcastListener(this);
		FermataApplication.get().getAddonManager()
				.removeBroadcastListeners(l -> l instanceof AddonPrefsBuilder);
		if (adapter != null) adapter.onDestroy();
		adapter = null;
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (e == ACTIVITY_DESTROY) cleanUp(a);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs) {
		if (adapter == null) return;
		MainActivityDelegate a = getMainActivity();
		if (MainActivityPrefs.hasTextIconSizePref(a, prefs)) {
			adapter.setSize(a.getTextIconSize());
		}
	}

	@Override
	public boolean isRootPage() {
		return adapter.getPreferenceSet().getParent() == null;
	}

	@Override
	public boolean onBackPressed() {
		PreferenceSet p = adapter.getPreferenceSet().getParent();
		if (p == null) return false;
		adapter.setPreferenceSet(p);
		return true;
	}

	public static void addDelayPrefs(PreferenceSet set, PreferenceStore store,
																	 Pref<IntSupplier> pref, @StringRes int title,
																	 ChangeableCondition visibility) {
		set.addIntPref(o -> {
			o.store = store;
			o.pref = pref;
			o.title = title;
			o.seekMin = -5000;
			o.seekMax = 5000;
			o.seekScale = 50;
			o.ems = 3;
			o.visibility = visibility;
		});
	}

	public static void addAudioPrefs(PreferenceSet set, PreferenceStore store, boolean isCar) {
		addDelayPrefs(set, store, MediaLibPrefs.AUDIO_DELAY, R.string.audio_delay, null);

		if (!isCar) {
			set.addStringPref(o -> {
				Locale locale = Locale.getDefault();
				o.store = store;
				o.pref = MediaLibPrefs.AUDIO_LANG;
				o.title = R.string.preferred_audio_lang;
				o.stringHint = locale.getLanguage() + ' ' + locale.getISO3Language();
			});
			set.addStringPref(o -> {
				o.store = store;
				o.pref = MediaLibPrefs.AUDIO_KEY;
				o.title = R.string.preferred_audio_key;
				o.stringHint = "studio1 studio2 default";
			});
		}
	}

	public static void addSubtitlePrefs(PreferenceSet set, PreferenceStore store, boolean isCar) {
		set.addBooleanPref(o -> {
			o.store = store;
			o.pref = MediaLibPrefs.SUB_ENABLED;
			o.title = R.string.display_subtitles;
		});

		addDelayPrefs(set, store, MediaLibPrefs.SUB_DELAY, R.string.subtitle_delay,
				PrefCondition.create(store, MediaLibPrefs.SUB_ENABLED));

		if (!isCar) {
			set.addStringPref(o -> {
				Locale locale = Locale.getDefault();
				o.store = store;
				o.pref = MediaLibPrefs.SUB_LANG;
				o.title = R.string.preferred_sub_lang;
				o.stringHint = locale.getLanguage() + ' ' + locale.getISO3Language();
				o.visibility = PrefCondition.create(store, MediaLibPrefs.SUB_ENABLED);
			});
			set.addStringPref(o -> {
				o.store = store;
				o.pref = MediaLibPrefs.SUB_KEY;
				o.title = R.string.preferred_sub_key;
				o.stringHint = "full forced";
				o.visibility = PrefCondition.create(store, MediaLibPrefs.SUB_ENABLED);
			});
		}
	}

	@NonNull
	private MainActivityDelegate getMainActivity() {
		return MainActivityDelegate.get(getContext());
	}

	private PreferenceViewAdapter createAdapter() {
		MainActivityDelegate a = getMainActivity();
		MediaLibPrefs mediaPrefs = a.getMediaServiceBinder().getLib().getPrefs();
		int[] timeUnits = new int[]{R.string.time_unit_second, R.string.time_unit_minute,
				R.string.time_unit_percent};
		boolean isCar = a.isCarActivity();
		PreferenceSet set = new PreferenceSet();
		PreferenceSet sub1;
		PreferenceSet sub2;

		sub1 = set.subSet(o -> o.title = R.string.interface_prefs);

		if (BuildConfig.AUTO && a.isCarActivity()) {
			addAAInterface(a, sub1);
		} else {
			if (BuildConfig.AUTO) {
				addAAInterface(a, sub1.subSet(o -> o.title = R.string.interface_prefs_aa));
			}
			addInterface(a, sub1,
					MainActivityPrefs.THEME_MAIN,
					MainActivityPrefs.HIDE_BARS,
					MainActivityPrefs.FULLSCREEN,
					MainActivityPrefs.SHOW_PG_UP_DOWN,
					MainActivityPrefs.NAV_BAR_POS,
					MainActivityPrefs.NAV_BAR_SIZE,
					MainActivityPrefs.TOOL_BAR_SIZE,
					MainActivityPrefs.CONTROL_PANEL_SIZE,
					MainActivityPrefs.TEXT_ICON_SIZE);
		}

		sub1.addBooleanPref(o -> {
			o.store = mediaPrefs;
			o.pref = BrowsableItemPrefs.SHOW_TRACK_ICONS;
			o.title = R.string.show_track_icons;
		});

		sub1 = set.subSet(o -> o.title = R.string.playback_settings);
		sub1.addBooleanPref(o -> {
			o.store = mediaPrefs;
			o.pref = BrowsableItemPrefs.PLAY_NEXT;
			o.title = R.string.play_next_on_completion;
		});

		sub1 = set.subSet(o -> o.title = R.string.playback_control);

		sub2 = sub1.subSet(o -> o.title = R.string.rw_ff_click);
		sub2.addIntPref(o -> {
			o.store = a.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.RW_FF_TIME;
			o.title = R.string.time;
			o.seekMax = 60;
		});
		sub2.addListPref(o -> {
			o.store = a.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.RW_FF_TIME_UNIT;
			o.title = R.string.time_unit;
			o.subtitle = R.string.time_unit_sub;
			o.formatSubtitle = true;
			o.values = timeUnits;
		});

		sub2 = sub1.subSet(o -> o.title = R.string.rw_ff_long_click);
		sub2.addIntPref(o -> {
			o.store = a.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.RW_FF_LONG_TIME;
			o.title = R.string.time;
			o.seekMax = 60;
		});
		sub2.addListPref(o -> {
			o.store = a.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.RW_FF_LONG_TIME_UNIT;
			o.title = R.string.time_unit;
			o.subtitle = R.string.time_unit_sub;
			o.formatSubtitle = true;
			o.values = timeUnits;
		});

		sub2 = sub1.subSet(o -> o.title = R.string.prev_next_long_click);
		sub2.addIntPref(o -> {
			o.store = a.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.PREV_NEXT_LONG_TIME;
			o.title = R.string.time;
			o.seekMax = 60;
		});
		sub2.addListPref(o -> {
			o.store = a.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.PREV_NEXT_LONG_TIME_UNIT;
			o.title = R.string.time_unit;
			o.subtitle = R.string.time_unit_sub;
			o.formatSubtitle = true;
			o.values = timeUnits;
		});

		sub1.addBooleanPref(o -> {
			o.store = a.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.PLAY_PAUSE_STOP;
			o.title = R.string.play_pause_stop;
		});

		sub2 = sub1.subSet(o -> o.title = R.string.video_control);
		sub2.addIntPref(o -> {
			o.store = a.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.VIDEO_CONTROL_START_DELAY;
			o.title = R.string.video_control_start_delay;
			o.seekMax = 60;
		});
		sub2.addIntPref(o -> {
			o.store = a.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.VIDEO_CONTROL_TOUCH_DELAY;
			o.title = R.string.video_control_touch_delay;
			o.seekMax = 60;
		});
		sub2.addIntPref(o -> {
			o.store = a.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.VIDEO_CONTROL_SEEK_DELAY;
			o.title = R.string.video_control_seek_delay;
			o.seekMax = 60;
		});

		if (BuildConfig.AUTO) {
			sub2.addBooleanPref(o -> {
				o.store = a.getPlaybackControlPrefs();
				o.pref = PlaybackControlPrefs.VIDEO_AA_SHOW_STATUS;
				o.title = R.string.video_aa_show_status;
			});
		}

		PrefCondition<BooleanSupplier> exoCond = PrefCondition.create(mediaPrefs, MediaLibPrefs.EXO_ENABLED);
		PrefCondition<BooleanSupplier> vlcCond = PrefCondition.create(mediaPrefs, MediaLibPrefs.VLC_ENABLED);
		Consumer<PreferenceView.ListOpts> initList = o -> {
			if (o.visibility == null) o.visibility = exoCond.or(vlcCond);

			o.values = new int[]{R.string.engine_mp_name, R.string.engine_exo_name, R.string.engine_vlc_name};
			o.valuesMap = new int[]{MEDIA_ENG_MP, MEDIA_ENG_EXO, MEDIA_ENG_VLC};
			o.valuesFilter = i -> {
				if (i == 1) return exoCond.get();
				if (i == 2) return vlcCond.get();
				return true;
			};
		};
		sub1 = set.subSet(o -> o.title = R.string.engine_prefs);
		sub1.addBooleanPref(o -> {
			o.store = mediaPrefs;
			o.removeDefault = false;
			o.pref = MediaLibPrefs.EXO_ENABLED;
			o.title = R.string.enable_exoplayer;
		});
		sub1.addBooleanPref(o -> {
			o.store = mediaPrefs;
			o.removeDefault = false;
			o.pref = MediaLibPrefs.VLC_ENABLED;
			o.title = R.string.enable_vlcplayer;
		});
		sub1.addListPref(o -> {
			o.store = mediaPrefs;
			o.removeDefault = false;
			o.pref = MediaLibPrefs.AUDIO_ENGINE;
			o.title = R.string.preferred_audio_engine;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.initList = initList;
		});
		sub1.addListPref(o -> {
			o.store = mediaPrefs;
			o.removeDefault = false;
			o.pref = MediaLibPrefs.VIDEO_ENGINE;
			o.title = R.string.preferred_video_engine;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.initList = initList;
		});
		sub1.addListPref(o -> {
			o.store = mediaPrefs;
			o.removeDefault = false;
			o.pref = MediaLibPrefs.MEDIA_SCANNER;
			o.title = R.string.preferred_media_scanner;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.visibility = vlcCond;
			o.values = new int[]{R.string.preferred_media_scanner_default, R.string.preferred_media_scanner_system, R.string.engine_vlc_name};
			o.valuesMap = new int[]{MEDIA_SCANNER_DEFAULT, MEDIA_SCANNER_SYSTEM, MEDIA_SCANNER_VLC};
		});

		sub1 = set.subSet(o -> o.title = R.string.video_settings);
		sub1.addListPref(o -> {
			o.store = mediaPrefs;
			o.pref = MediaLibPrefs.VIDEO_SCALE;
			o.title = R.string.video_scaling;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.video_scaling_best, R.string.video_scaling_fill,
					R.string.video_scaling_orig, R.string.video_scaling_4, R.string.video_scaling_16};
		});
		sub1.addBooleanPref(o -> {
			o.store = a.getPrefs();
			o.pref = MainActivityPrefs.LANDSCAPE_VIDEO;
			o.title = R.string.play_video_landscape;
		});
		sub1.addBooleanPref(o -> {
			o.store = a.getPrefs();
			o.pref = MainActivityPrefs.CHANGE_BRIGHTNESS;
			o.title = R.string.change_brightness;
		});
		sub1.addIntPref(o -> {
			o.store = a.getPrefs();
			o.pref = MainActivityPrefs.BRIGHTNESS;
			o.title = R.string.video_brightness;
			o.subtitle = R.string.change_brightness_sub;
			o.seekMin = 0;
			o.seekMax = 255;
			o.visibility = PrefCondition.create(a.getPrefs(), MainActivityPrefs.CHANGE_BRIGHTNESS);
		});

		sub2 = sub1.subSet(o -> {
			o.title = R.string.audio;
			o.visibility = PrefCondition.create(mediaPrefs, MediaLibPrefs.VLC_ENABLED);
		});
		addAudioPrefs(sub2, mediaPrefs, isCar);

		sub2 = sub1.subSet(o -> {
			o.title = R.string.subtitles;
			o.visibility = PrefCondition.create(mediaPrefs, MediaLibPrefs.VLC_ENABLED);
		});
		addSubtitlePrefs(sub2, mediaPrefs, isCar);

		addAddons(set);

		sub1 = set.subSet(o -> o.title = R.string.other);
		sub1.addBooleanPref(o -> {
			o.store = a.getPrefs();
			o.pref = MainActivityPrefs.CHECK_UPDATES;
			o.title = R.string.check_updates;
		});

		return new PreferenceViewAdapter(set) {
			@Override
			public void setPreferenceSet(PreferenceSet set) {
				super.setPreferenceSet(set);
				a.fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
			}
		};
	}

	private void addAAInterface(MainActivityDelegate a, PreferenceSet ps) {
		if (BuildConfig.AUTO) {
			addInterface(a, ps,
					MainActivityPrefs.THEME_AA,
					MainActivityPrefs.HIDE_BARS_AA,
					MainActivityPrefs.FULLSCREEN_AA,
					MainActivityPrefs.SHOW_PG_UP_DOWN_AA,
					MainActivityPrefs.NAV_BAR_POS_AA,
					MainActivityPrefs.NAV_BAR_SIZE_AA,
					MainActivityPrefs.TOOL_BAR_SIZE_AA,
					MainActivityPrefs.CONTROL_PANEL_SIZE_AA,
					MainActivityPrefs.TEXT_ICON_SIZE_AA);
		}
	}

	private void addInterface(
			MainActivityDelegate a, PreferenceSet ps,
			Pref<IntSupplier> theme,
			Pref<BooleanSupplier> hideBars,
			Pref<BooleanSupplier> fullScreen,
			Pref<BooleanSupplier> pgUpDown,
			Pref<IntSupplier> nbPos,
			Pref<DoubleSupplier> nbSize,
			Pref<DoubleSupplier> tbSize,
			Pref<DoubleSupplier> cpSize,
			Pref<DoubleSupplier> textIconSize) {
		ps.addListPref(o -> {
			o.store = a.getPrefs();
			o.pref = theme;
			o.title = R.string.theme;
			o.subtitle = R.string.theme_sub;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.theme_dark, R.string.theme_light, R.string.theme_day_night, R.string.theme_black};
		});
		ps.addBooleanPref(o -> {
			o.store = a.getPrefs();
			o.pref = hideBars;
			o.title = R.string.hide_bars;
			o.subtitle = R.string.hide_bars_sub;
		});
		ps.addBooleanPref(o -> {
			o.store = a.getPrefs();
			o.pref = fullScreen;
			o.title = R.string.fullscreen_mode;
		});
		ps.addBooleanPref(o -> {
			o.store = a.getPrefs();
			o.pref = pgUpDown;
			o.title = R.string.show_pg_up_down;
		});
		ps.addListPref(o -> {
			o.store = a.getPrefs();
			o.pref = nbPos;
			o.title = R.string.nav_bar_pos;
			o.subtitle = R.string.nav_bar_pos_sub;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.nav_bar_pos_bottom, R.string.nav_bar_pos_left, R.string.nav_bar_pos_right};
		});
		ps.addFloatPref(o -> {
			o.store = a.getPrefs();
			o.pref = nbSize;
			o.title = R.string.nav_bar_size;
			o.scale = 0.05f;
			o.seekMin = 10;
			o.seekMax = 40;
		});
		ps.addFloatPref(o -> {
			o.store = a.getPrefs();
			o.pref = tbSize;
			o.title = R.string.tool_bar_size;
			o.scale = 0.05f;
			o.seekMin = 10;
			o.seekMax = 40;
		});
		ps.addFloatPref(o -> {
			o.store = a.getPrefs();
			o.pref = cpSize;
			o.title = R.string.control_panel_size;
			o.scale = 0.05f;
			o.seekMin = 10;
			o.seekMax = 40;
		});
		ps.addFloatPref(o -> {
			o.store = a.getPrefs();
			o.pref = textIconSize;
			o.title = R.string.text_icon_size;
			o.scale = 0.05f;
			o.seekMin = 10;
			o.seekMax = 40;
		});
	}

	private void addAddons(PreferenceSet set) {
		AddonManager amgr = FermataApplication.get().getAddonManager();
		PreferenceSet sub = set.subSet(o -> o.title = R.string.addons);
		PreferenceStore store = FermataApplication.get().getPreferenceStore();

		for (AddonInfo addon : BuildConfig.ADDONS) {
			AddonPrefsBuilder b = new AddonPrefsBuilder(amgr, addon, store);
			PreferenceSet sub1 = sub.subSet(b);
			sub1.configure(b::configure);
		}
	}

	private static final class AddonPrefsBuilder implements Consumer<PreferenceView.Opts>, AddonManager.Listener {
		private final AddonManager amgr;
		private final AddonInfo info;
		private final PreferenceStore store;
		private PreferenceSet set;

		public AddonPrefsBuilder(AddonManager amgr, AddonInfo info, PreferenceStore store) {
			this.amgr = amgr;
			this.info = info;
			this.store = store;
			amgr.addBroadcastListener(this);
		}

		void configure(PreferenceSet set) {
			this.set = set;

			set.addBooleanPref(o -> {
				o.title = R.string.enable;
				o.pref = info.enabledPref;
				o.store = store;
			});

			FermataAddon a = amgr.getAddon(info.className);

			if (a != null) {
				ChangeableCondition cond = PrefCondition.create(store, info.enabledPref);
				a.contributeSettings(store, set, cond);
			}
		}

		@Override
		public void accept(PreferenceView.Opts o) {
			o.title = info.addonName;
			o.icon = info.icon;
		}

		@Override
		public void onAddonChanged(AddonManager mgr, AddonInfo info, boolean installed) {
			if (set != null) set.configure(this::configure);
		}
	}
}
