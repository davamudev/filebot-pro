package dev.davamu.filebotpro.util;

import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Normalize country codes and names of the different data sources to ISO 3166-1 alpha-2 codes (e.g. usa, USA, United States => US).
 */
public final class CountryCodes {

	private static final Map<String, String> ALPHA3 = stream(Locale.getISOCountries()).collect(toMap(c -> Locale.of("", c).getISO3Country().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));
	private static final Map<String, String> NAMES = stream(Locale.getISOCountries()).collect(toMap(c -> Locale.of("", c).getDisplayCountry(Locale.ENGLISH).toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));

	// common alternative names used by OMDb / IMDb
	private static final Map<String, String> ALIASES = Map.of("usa", "US", "uk", "GB", "west germany", "DE", "east germany", "DE", "soviet union", "RU", "south korea", "KR", "north korea", "KP", "russia", "RU", "czech republic", "CZ", "vietnam", "VN");

	public static String toAlpha2(String country) {
		if (country == null || country.isBlank()) {
			return null;
		}

		String s = country.strip();
		if (s.length() == 2) {
			return s.toUpperCase(Locale.ROOT);
		}
		if (s.length() == 3 && ALPHA3.containsKey(s.toUpperCase(Locale.ROOT))) {
			return ALPHA3.get(s.toUpperCase(Locale.ROOT));
		}

		String name = s.toLowerCase(Locale.ROOT);
		return ALIASES.getOrDefault(name, NAMES.get(name));
	}

	private CountryCodes() {
		throw new UnsupportedOperationException();
	}

}
