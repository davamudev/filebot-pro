package dev.davamu.filebotpro.web;

import dev.davamu.filebotpro.Settings;

/**
 * Fail early with a helpful message if a web service requires an API key that has not been configured.
 */
public final class ApiKeys {

	public static String require(String service, String name, String key) {
		if (key == null || key.isBlank()) {
			throw new IllegalStateException(String.format("%s API key is missing. Add %s = YOUR_KEY to %s", service, name, Settings.getUserApiKeysFile()));
		}
		return key;
	}

	private ApiKeys() {
		throw new UnsupportedOperationException();
	}

}
