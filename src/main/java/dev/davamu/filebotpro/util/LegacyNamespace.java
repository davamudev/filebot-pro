package dev.davamu.filebotpro.util;

import java.util.regex.Pattern;

/**
 * FileBot Pro lives in the {@code dev.davamu.filebotpro} namespace. Scripts, format expressions and file tags written for older versions may still reference the legacy {@code net.filebot} namespace, so these references are rewritten.
 */
public final class LegacyNamespace {

	public static final String LEGACY_PACKAGE = "net.filebot";
	public static final String PACKAGE = "dev.davamu.filebotpro";

	private static final Pattern LEGACY_REFERENCE = Pattern.compile("\\bnet\\.filebot\\.");

	public static String migrate(String source) {
		if (source == null || !source.contains(LEGACY_PACKAGE)) {
			return source;
		}
		return LEGACY_REFERENCE.matcher(source).replaceAll(PACKAGE + ".");
	}

	private LegacyNamespace() {
		throw new UnsupportedOperationException();
	}

}
