package dev.davamu.filebotpro.ui.theme;

/**
 * User selectable appearance.
 */
public enum Theme {

	System("Follow system"), Light("Light"), Dark("Dark");

	private final String label;

	Theme(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	/**
	 * Resolve theme names, including names of legacy themes (e.g. Darcula or Nimbus).
	 */
	public static Theme forName(String name) {
		if (name != null) {
			for (Theme t : values()) {
				if (t.name().equalsIgnoreCase(name.trim())) {
					return t;
				}
			}

			switch (name.trim().toLowerCase()) {
			case "darcula":
			case "darkmode":
				return Dark;
			case "nimbus":
			case "metal":
			case "crossplatform":
				return Light;
			}
		}
		return System;
	}

}
