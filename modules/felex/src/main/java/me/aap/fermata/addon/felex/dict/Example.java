package me.aap.fermata.addon.felex.dict;

/**
 * @author Andrey Pavlenko
 */
public class Example {
	static final Example DUMMY = new Example(null);
	private final String sentence;
	private String translation;

	Example(String sentence) {
		this.sentence = sentence;
	}

	Example(String sentence, String translation) {
		this.sentence = sentence;
		this.translation = translation;
	}

	public String getSentence() {
		return sentence;
	}

	public String getTranslation() {
		return translation;
	}

	void setTranslation(String translation) {
		this.translation = translation;
	}
}
