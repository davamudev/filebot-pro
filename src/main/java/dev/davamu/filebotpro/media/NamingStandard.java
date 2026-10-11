package dev.davamu.filebotpro.media;

import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.similarity.Normalization.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.util.Objects;

import dev.davamu.filebotpro.web.AudioTrack;
import dev.davamu.filebotpro.web.Episode;
import dev.davamu.filebotpro.web.EpisodeFormat;
import dev.davamu.filebotpro.web.Movie;
import dev.davamu.filebotpro.web.MoviePart;
import dev.davamu.filebotpro.web.MultiEpisode;
import dev.davamu.filebotpro.web.SeriesInfo;
import dev.davamu.filebotpro.web.SimpleDate;

public class NamingStandard {

	/**
	 * Plex Naming Standard (i.e. Default Implementation)
	 * 
	 * https://support.plex.tv/articles/categories/media-preparation/
	 */
	public static final NamingStandard PLEX = new NamingStandard();

	/**
	 * Kodi Naming Standard (i.e. same as Plex, but with Series Name (Year) folders, {tmdb=603} style id tags and SxE and non-range
	 * based multi-episode formatting)
	 *
	 * https://kodi.wiki/view/Naming_video_files
	 */
	public static final NamingStandard KODI = new NamingStandard() {

		@Override
		public String formatIdTag(String database, String id) {
			// Kodi accepts {tmdb-603} as well, but documents {tmdb=603} as the filename identifier format
			return "{" + database + "=" + id + "}";
		}

		@Override
		public String getSeriesFolder(Episode e) {
			return formatSeriesNameWithYear(e);
		}

		@Override
		public String getExtrasFolder(String type) {
			return "Extras"; // Kodi keeps all extras in one folder
		}

		@Override
		public String formatEpisodeNumbers(Episode e) {
			if (e instanceof MultiEpisode) {
				return EpisodeFormat.SeasonEpisode.formatMultiSxE((MultiEpisode) e);
			}
			return EpisodeFormat.SeasonEpisode.formatSxE(e);
		}
	};

	/**
	 * Emby Naming Standard (i.e. same as Plex, but with [tmdbid-603] style id tags)
	 *
	 * https://emby.media/support/articles/Movie-Naming.html
	 */
	public static final NamingStandard EMBY = new NamingStandard() {

		@Override
		public String formatIdTag(String database, String id) {
			return "[" + database + "id-" + id + "]";
		}

		@Override
		protected String getOtherExtrasFolder() {
			return "Extras";
		}
	};

	/**
	 * Jellyfin Naming Standard (i.e. same as Emby, but with Series Name (Year) folders)
	 *
	 * https://jellyfin.org/docs/general/server/media/shows/
	 */
	public static final NamingStandard JELLYFIN = new NamingStandard() {

		@Override
		public String getSeriesFolder(Episode e) {
			return formatSeriesNameWithYear(e);
		}

		@Override
		public String formatIdTag(String database, String id) {
			return "[" + database + "id-" + id + "]";
		}
	};

	/**
	 * Folder for the extras of a movie or series in the movie, season or series folder
	 *
	 * @param type
	 *            type of the disc title (TheDiscDB), e.g. Featurette or DeletedScene
	 */
	public String getExtrasFolder(String type) {
		switch (type) {
		case "DeletedScene":
			return "Deleted Scenes";
		case "Featurette":
			return "Featurettes";
		case "Interview":
			return "Interviews";
		case "Scene":
			return "Scenes";
		case "Short":
			return "Shorts";
		case "Trailer":
			return "Trailers";
		default:
			return getOtherExtrasFolder();
		}
	}

	protected String getOtherExtrasFolder() {
		return "Other";
	}

	public String getPath(Object o) {
		return getPath(o, false);
	}

	/**
	 * @param ids
	 *            add database id tags (e.g. {tmdb-603}) to the movie or series folder, so the media server does not have to guess
	 */
	public String getPath(Object o, boolean ids) {
		return getPath(o, ids, false);
	}

	/**
	 * @param year
	 *            use Series Name (Year) for the series folder and episode file name (movies always include the year)
	 */
	public String getPath(Object o, boolean ids, boolean year) {
		if (o instanceof Episode)
			return getPath((Episode) o, ids, year);
		if (o instanceof Movie)
			return getPath((Movie) o, ids);
		if (o instanceof AudioTrack)
			return getPath((AudioTrack) o);

		return null;
	}

	public String getPath(Episode e) {
		return getPath(e, false);
	}

	public String getPath(Episode e, boolean ids) {
		return getPath(e, ids, false);
	}

	public String getPath(Episode e, boolean ids, boolean year) {
		String seriesFolder = year ? formatSeriesNameWithYear(e) : getSeriesFolder(e);
		if (ids) {
			seriesFolder = appendIdTag(seriesFolder, getSeriesIdTag(e));
		}

		String episodeName = year ? getEpisodeName(e, formatSeriesNameWithYear(e)) : getEpisodeName(e);

		if (e.isAnime()) {
			// Anime
			return path(getAnimeFolder(), seriesFolder, episodeName);
		} else {
			// TV Series
			return path(getSeriesFolder(), seriesFolder, getSeasonFolder(e), episodeName);
		}
	}

	public String getPath(Movie m) {
		return getPath(m, false);
	}

	public String getPath(Movie m, boolean ids) {
		// Movie
		String name = m.getNameWithYear();

		// Movie (multi-part)
		if (m instanceof MoviePart) {
			name = String.format("%s CD%d", name, ((MoviePart) m).getPartIndex());
		}

		String movieFolder = ids ? appendIdTag(m.getNameWithYear(), getMovieIdTag(m)) : m.getNameWithYear();

		return path(getMovieFolder(), movieFolder, name);
	}

	public String getMovieIdTag(Movie m) {
		if (m.getTmdbId() > 0)
			return formatIdTag("tmdb", String.valueOf(m.getTmdbId()));
		if (m.getImdbId() > 0)
			return formatIdTag("imdb", String.format("tt%07d", m.getImdbId()));

		return null;
	}

	public String getSeriesIdTag(Episode e) {
		SeriesInfo info = e.getSeriesInfo();
		if (info == null || info.getId() == null || info.getDatabase() == null)
			return null;

		switch (info.getDatabase()) {
		case "TheTVDB":
			return formatIdTag("tvdb", info.getId().toString());
		case "TheMovieDB::TV":
			return formatIdTag("tmdb", info.getId().toString());
		default:
			return null; // media servers only know TheTVDB and TheMovieDB ids
		}
	}

	public String formatIdTag(String database, String id) {
		return "{" + database + "-" + id + "}";
	}

	private static String appendIdTag(String name, String tag) {
		// e.g. The Matrix (1999) {tmdb-603}
		return tag == null ? name : name + " " + tag;
	}

	public String getPath(AudioTrack a) {
		// Music
		String name = first(a.getTrackTitle(), a.getTitle());

		// prepend track number
		if (a.getTrack() != null) {
			name = String.format("%02d - %s", a.getTrack(), name);
		}

		return path(getMusicFolder(), a.getArtist(), a.getAlbum(), name);
	}

	private static String path(String... name) {
		return stream(name).filter(Objects::nonNull).map(s -> {
			return replaceColon(s, ".", " - ");
		}).map(s -> {
			return replacePathSeparators(s, " ");
		}).map(s -> {
			return normalizeQuotationMarks(s);
		}).map(s -> {
			return trimTrailingPunctuation(s);
		}).map(s -> {
			return validateFileName(s);
		}).filter(s -> s.length() > 0).collect(joining("/"));
	}

	private static String first(String... options) {
		return stream(options).filter(Objects::nonNull).findFirst().get();
	}

	public String getMovieFolder() {
		return "Movies";
	}

	public String getSeriesFolder() {
		return "TV Shows";
	}

	public String getAnimeFolder() {
		return "Anime";
	}

	public String getMusicFolder() {
		return "Music";
	}

	public String getSeriesFolder(Episode e) {
		return formatSeriesName(e);
	}

	public String getSeasonFolder(Episode e) {
		if (e.getSpecial() != null) {
			return "Specials";
		}

		if (e.getSeason() != null) {
			return String.format("Season %02d", e.getSeason());
		}

		return null;
	}

	public String getEpisodeName(Episode e) {
		return getEpisodeName(e, formatSeriesName(e));
	}

	protected String getEpisodeName(Episode e, String seriesName) {
		return String.join(" - ", seriesName, formatEpisodeNumbers(e), formatEpisodeTitle(e));
	}

	public String formatSeriesName(Episode e) {
		if (e.isAnime()) {
			// Anime
			return e.getSeriesInfo().getName(); // series info name is the primary Anime name
		} else {
			// TV Series
			return e.getSeriesName();
		}
	}

	public String formatSeriesNameWithYear(Episode e) {
		String name = formatSeriesName(e);
		SimpleDate startDate = e.getSeriesInfo() == null ? null : e.getSeriesInfo().getStartDate();
		if (startDate == null) {
			return name;
		}

		// account for TV Shows that contain the year in the series name, e.g. Doctor Who (2005)
		String year = String.format(" (%d)", startDate.getYear());
		return name.endsWith(year) ? name : name + year;
	}

	public String formatEpisodeNumbers(Episode e) {
		if (e.isAnime()) {
			// Anime
			return EpisodeFormat.SeasonEpisode.formatSxE(e);
		} else {
			// TV Series
			return EpisodeFormat.SeasonEpisode.formatS00E00(e);
		}
	}

	public String formatEpisodeTitle(Episode e) {
		// enforce title length limit by default
		return truncateText(EpisodeFormat.SeasonEpisode.formatMultiTitle(e), TITLE_MAX_LENGTH);
	}

	public static final int TITLE_MAX_LENGTH = 150;

}
