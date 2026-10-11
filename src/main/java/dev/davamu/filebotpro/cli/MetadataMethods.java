package dev.davamu.filebotpro.cli;

import static java.util.Collections.*;
import static java.util.Comparator.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.WebServices.*;

import java.util.List;
import java.util.Locale;

import dev.davamu.filebotpro.web.Artwork;
import dev.davamu.filebotpro.web.Movie;
import dev.davamu.filebotpro.web.MovieInfo;
import dev.davamu.filebotpro.web.Person;
import dev.davamu.filebotpro.web.SeriesInfo;

/**
 * Groovy extension methods for metadata objects (e.g. {@code movie.info}, {@code series.details} or
 * {@code series.crew}), as used by fn:artwork, fn:nfo and user scripts.
 */
public class MetadataMethods {

	/**
	 * Full movie information from TheMovieDB
	 */
	public static MovieInfo getInfo(Movie self) throws Exception {
		return TheMovieDB.getMovieInfo(self, getLocale(self.getLanguage()), true);
	}

	public static List<Artwork> getArtwork(Movie self, String category, Object language) throws Exception {
		int id = self.getTmdbId() > 0 ? self.getTmdbId() : getInfo(self).getId();
		return preferLanguage(TheMovieDB.getArtwork(id, category, getLocale(language)), language);
	}

	/**
	 * Keywords are not part of the movie information of this version
	 */
	public static List<String> getKeywords(MovieInfo self) {
		return emptyList();
	}

	/**
	 * Full series information (e.g. overview and actors for TheTVDB)
	 */
	public static SeriesInfo getDetails(SeriesInfo self) throws Exception {
		if (isTheTVDB(self)) {
			return TheTVDB.getSeriesInfo(self.getId(), getLocale(self.getLanguage()));
		}
		return self;
	}

	public static String getOverview(SeriesInfo self) throws Exception {
		if (isTheMovieDB(self)) {
			return TheMovieDB_TV.getOverview(self.getId(), getLocale(self.getLanguage()));
		}
		return null;
	}

	public static List<Person> getCrew(SeriesInfo self) throws Exception {
		if (isTheTVDB(self)) {
			return TheTVDB.getActors(self.getId(), getLocale(self.getLanguage()));
		}
		if (isTheMovieDB(self)) {
			return TheMovieDB_TV.getCrew(self.getId(), getLocale(self.getLanguage()));
		}
		return emptyList();
	}

	public static List<Artwork> getArtwork(SeriesInfo self, String category, Object language) throws Exception {
		if (isTheTVDB(self)) {
			return TheTVDB.getArtwork(self.getId(), category, getLocale(language));
		}
		if (isTheMovieDB(self)) {
			return preferLanguage(TheMovieDB_TV.getArtwork(self.getId(), category, getLocale(language)), language);
		}
		return emptyList();
	}

	/**
	 * Prefer artwork in the given language, then artwork without text, then everything else (stable order, so rating
	 * order is kept within each group). Without a language, artwork without text comes first (e.g. backdrops).
	 */
	private static List<Artwork> preferLanguage(List<Artwork> artwork, Object language) {
		String code = language == null ? null : getLocale(language).getLanguage();
		return artwork.stream().sorted(comparingInt(a -> {
			Locale l = a.getLanguage();
			if (l == null || l.getLanguage().isEmpty()) {
				return code == null ? 0 : 1;
			}
			return l.getLanguage().equals(code) ? 0 : 2;
		})).collect(toList());
	}

	private static boolean isTheMovieDB(SeriesInfo info) {
		return TheMovieDB_TV.getIdentifier().equalsIgnoreCase(info.getDatabase()) && info.getId() != null;
	}

	private static boolean isTheTVDB(SeriesInfo info) {
		return TheTVDB.getIdentifier().equalsIgnoreCase(info.getDatabase()) && info.getId() != null;
	}

	private static Locale getLocale(Object language) {
		if (language instanceof Locale) {
			return (Locale) language;
		}
		if (language != null && language.toString().length() > 0) {
			return Locale.forLanguageTag(language.toString());
		}
		return Locale.ENGLISH;
	}

}
