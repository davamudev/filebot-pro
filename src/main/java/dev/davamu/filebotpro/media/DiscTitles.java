package dev.davamu.filebotpro.media;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.MediaTypes.*;
import static dev.davamu.filebotpro.WebServices.*;
import static dev.davamu.filebotpro.media.MediaDetection.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.davamu.filebotpro.mediainfo.CachedMediaInfo;
import dev.davamu.filebotpro.similarity.Match;
import dev.davamu.filebotpro.web.AnimeLists;
import dev.davamu.filebotpro.web.Episode;
import dev.davamu.filebotpro.web.Movie;
import dev.davamu.filebotpro.web.SeriesInfo;
import dev.davamu.filebotpro.web.SortOrder;

/**
 * Identify disc rips (e.g. Disc_1_t01.mkv or title_t03.mkv by MakeMKV) with the titles of Blu-ray and DVD discs from
 * TheDiscDB: episodes, main movies and extras. Title indexes depend on the minimum title length that MakeMKV was
 * configured with, so titles are identified by their duration first, and by disc number, title index and size if
 * several titles have the same duration.
 */
public class DiscTitles {

	public static final String TV = "tv";
	public static final String MOVIE = "movie";

	// e.g. Disc_1_t01, 00001_t01, title_t03
	private static final Pattern DISC_RIP = Pattern.compile("(?i)_t(\\d{2,3})$");

	// e.g. Disc 1, Disc_2, Disk3, D4, BD1
	private static final Pattern DISC_NUMBER = Pattern.compile("(?i)(?<![a-z0-9])(?:disc|disk|dvd|bd|d)[ ._-]?(\\d{1,2})(?![0-9])");

	// duration of the file compared to the duration of the title (which has second precision)
	private static final int MAX_SECONDS_DIFFERENCE = 2;

	// size of the file compared to the size of the title, if titles of the same duration need to be told apart
	private static final double MAX_SIZE_DIFFERENCE = 0.05;

	// size of the file compared to the size of the title, to identify a movie without knowing its name
	private static final double MAX_SIZE_DIFFERENCE_UNKNOWN = 0.02;

	// titles that have been identified, for the {disc} binding and the extras folders of the {plex} binding
	private static final Map<File, Title> identified = synchronizedMap(new LinkedHashMap<File, Title>(64, 0.75f, true) {

		@Override
		protected boolean removeEldestEntry(Map.Entry<File, Title> eldest) {
			return size() > 10000;
		}
	});

	public static boolean isDiscRip(File file) {
		return VIDEO_FILES.accept(file) && DISC_RIP.matcher(getNameWithoutExtension(file.getName())).find();
	}

	/**
	 * @return the disc title of a file that has been identified, or null
	 */
	public static Title getTitle(File file) {
		return file == null ? null : identified.get(file);
	}

	private static List<Title> getTitles(String kind, int tmdb) {
		try {
			return releaseInfo.getDiscTitles().getOrDefault(kind + "/" + tmdb, emptyList());
		} catch (Exception e) {
			debug.warning(cause("Failed to load disc titles", e));
			return emptyList();
		}
	}

	/**
	 * Match disc rips with the episodes (and the extras) of the discs of the series. TheDiscDB numbers episodes like
	 * TheMovieDB, so episodes are matched via episode ids, and via the anime lists for AniDB.
	 *
	 * @return matches for the disc rips that have been identified (extras are matched to the episode or season they
	 *         belong to)
	 */
	public static List<Match<File, ?>> matchEpisodes(List<File> files, Collection<Episode> episodes, Locale locale) {
		List<File> discRips = filter(files, DiscTitles::isDiscRip);
		if (discRips.isEmpty()) {
			return emptyList();
		}

		List<Match<File, ?>> matches = new ArrayList<Match<File, ?>>();

		// episodes may belong to multiple series
		for (SeriesInfo info : episodes.stream().map(Episode::getSeriesInfo).filter(i -> i != null && i.getId() != null).distinct().collect(toList())) {
			try {
				Integer tvdb = getTheTVDBSeriesId(info);
				Integer tmdb = TheMovieDB_TV.getIdentifier().equals(info.getDatabase()) ? info.getId() : tvdb == null ? null : TheMovieDB_TV.findByTheTVDBId(tvdb);
				if (tmdb == null || getTitles(TV, tmdb).isEmpty()) {
					continue;
				}

				Set<File> matched = matches.stream().map(Match::getValue).collect(toSet());
				Map<File, Title> titles = match(getTitles(TV, tmdb), filter(discRips, f -> !matched.contains(f)));

				List<Episode> series = episodes.stream().filter(e -> info.equals(e.getSeriesInfo())).collect(toList());
				for (Map.Entry<File, Title> it : titles.entrySet()) {
					Title t = it.getValue();
					Episode episode = null;

					if (t.episode > 0) {
						episode = getEpisode(series, info, tmdb, tvdb, t.season, t.episode, locale);
					} else if (t.isExtra() && t.season > 0) {
						episode = getEpisode(series, info, tmdb, tvdb, t.season, 1, locale); // extra of a season
					} else if (t.isExtra()) {
						episode = series.stream().filter(Episode::isRegular).findFirst().orElse(null); // extra of the series
					}

					if (episode != null) {
						debug.finest(format("Disc title: %s => %s [%s]", it.getKey().getName(), t.getDescription(), episode));
						identified.put(it.getKey(), t);
						matches.add(new Match<File, Episode>(it.getKey(), episode.clone()));
					}
				}
			} catch (Exception e) {
				debug.log(Level.WARNING, e, cause("Failed to match disc titles", e));
			}
		}

		return matches;
	}

	private static Integer getTheTVDBSeriesId(SeriesInfo info) throws Exception {
		if (TheTVDB.getIdentifier().equals(info.getDatabase())) {
			return info.getId();
		}
		if (TheMovieDB_TV.getIdentifier().equals(info.getDatabase())) {
			return TheMovieDB_TV.getTheTVDBId(info.getId());
		}
		if (AniDB.getIdentifier().equals(info.getDatabase())) {
			return AnimeList.map(info.getId(), 1, AnimeLists.DB.AniDB, AnimeLists.DB.TheTVDB).orElse(null);
		}
		return null;
	}

	/**
	 * Episode of the given series for the given TheMovieDB season and episode numbers (matched by episode id, since the
	 * episode list may be in another order or from another database)
	 */
	private static Episode getEpisode(List<Episode> series, SeriesInfo info, int tmdb, Integer tvdb, int season, int number, Locale locale) throws Exception {
		if (TheMovieDB_TV.getIdentifier().equals(info.getDatabase())) {
			Integer id = TheMovieDB_TV.getEpisodeList(tmdb, SortOrder.Airdate, locale).stream().filter(e -> isEpisodeNumber(e, season, number)).map(Episode::getId).findFirst().orElse(null);
			return findEpisode(series, id, season, number);
		}

		// TheTVDB episode for the TheMovieDB episode
		Integer id = null;
		try {
			id = TheMovieDB_TV.getTheTVDBEpisodeId(tmdb, season, number);
		} catch (Exception e) {
			debug.finest(cause("Failed to retrieve TheTVDB episode id", e));
		}

		if (TheTVDB.getIdentifier().equals(info.getDatabase())) {
			return findEpisode(series, id, season, number);
		}

		// AniDB episode that the anime lists map to the TheTVDB episode
		if (AniDB.getIdentifier().equals(info.getDatabase()) && tvdb != null) {
			Integer episodeId = id;
			Episode e = TheTVDB.getEpisodeList(tvdb, SortOrder.Airdate, locale).stream().filter(it -> episodeId != null ? episodeId.equals(it.getId()) : isEpisodeNumber(it, season, number)).findFirst().orElse(null);
			if (e == null) {
				return null;
			}

			int s = e.getSpecial() != null ? 0 : e.getSeason(), n = e.getSpecial() != null ? e.getSpecial() : e.getEpisode();
			for (Episode it : series) {
				Optional<Episode> mapping = AnimeList.map(it, AnimeLists.DB.AniDB, AnimeLists.DB.TheTVDB);
				if (mapping.isPresent() && isEpisodeNumber(mapping.get(), s, n)) {
					return it;
				}
			}
		}

		return null;
	}

	private static Episode findEpisode(List<Episode> series, Integer id, int season, int number) {
		return series.stream().filter(e -> id != null && id.equals(e.getId())).findFirst().orElseGet(() -> {
			// same season and episode numbers
			return series.stream().filter(e -> isEpisodeNumber(e, season, number)).findFirst().orElse(null);
		});
	}

	private static boolean isEpisodeNumber(Episode e, int season, int number) {
		return season == 0 ? Objects.equals(e.getSpecial(), number) : Objects.equals(e.getSeason(), season) && Objects.equals(e.getEpisode(), number);
	}

	/**
	 * Identify the main movie and the extras among disc rips: disc rips of a movie that is already known are classified
	 * with the titles of that movie, and disc rips of an unknown movie are identified by the duration and size of all disc
	 * rips in the same folder.
	 *
	 * @param known
	 *            movies that have already been detected for some of the files (e.g. via nfo files or file names)
	 * @return the movie of each disc rip that has been identified (extras are matched to their movie, never as a movie
	 *         part)
	 */
	public static Map<File, Movie> matchMovies(Collection<File> files, Map<File, Movie> known, Locale locale) {
		Map<File, Movie> matches = new LinkedHashMap<File, Movie>();

		for (List<File> folder : mapByFolder(filter(files, DiscTitles::isDiscRip)).values()) {
			try {
				// movies that have been detected for the disc rips of this folder
				Map<Integer, Movie> movies = new LinkedHashMap<Integer, Movie>();
				folder.stream().map(known::get).filter(m -> m != null && m.getTmdbId() > 0).forEach(m -> movies.putIfAbsent(m.getTmdbId(), m));

				// identify the movie by the disc rips alone
				if (movies.isEmpty()) {
					Integer tmdb = identifyMovie(folder);
					if (tmdb != null) {
						Movie movie = TheMovieDB.getMovieDescriptor(new Movie(null, new String[0], 0, -1, tmdb, locale), locale);
						if (movie != null) {
							debug.fine(format("Disc rips: %s => %s", folder.get(0).getParentFile().getName(), movie));
							movies.put(tmdb, movie);
						}
					}
				}

				for (Movie movie : movies.values()) {
					List<File> remaining = filter(folder, f -> !matches.containsKey(f) && (known.get(f) == null || movie.equals(known.get(f)) || movies.size() == 1));
					match(getTitles(MOVIE, movie.getTmdbId()), remaining).forEach((f, t) -> {
						debug.finest(format("Disc title: %s => %s [%s]", f.getName(), t.getDescription(), movie));
						identified.put(f, t);
						matches.put(f, movie);
					});
				}
			} catch (Exception e) {
				debug.log(Level.WARNING, e, cause("Failed to match disc titles", e));
			}
		}

		return matches;
	}

	/**
	 * TheMovieDB id of the movie whose disc titles match the duration and size of the disc rips (more disc rips than any
	 * other movie, and at least two if there are multiple disc rips), or null
	 */
	private static Integer identifyMovie(List<File> files) throws Exception {
		Map<File, Integer> durations = getDurations(files);
		Map<Integer, Long> score = new LinkedHashMap<Integer, Long>();

		for (Map.Entry<String, List<Title>> it : releaseInfo.getDiscTitles().entrySet()) {
			if (!it.getKey().startsWith(MOVIE + "/")) {
				continue;
			}
			long n = durations.entrySet().stream().filter(d -> it.getValue().stream().anyMatch(t -> isSameDuration(t, d.getValue()) && isSameSize(t, d.getKey().length(), MAX_SIZE_DIFFERENCE_UNKNOWN))).count();
			if (n > 0) {
				score.put(it.getValue().get(0).tmdb, n);
			}
		}

		List<Map.Entry<Integer, Long>> ranking = score.entrySet().stream().sorted(Map.Entry.<Integer, Long> comparingByValue().reversed()).collect(toList());
		if (ranking.isEmpty() || (ranking.size() > 1 && ranking.get(0).getValue().equals(ranking.get(1).getValue())) || (durations.size() > 1 && ranking.get(0).getValue() < 2)) {
			return null;
		}
		return ranking.get(0).getKey();
	}

	private static Map<File, Integer> getDurations(Collection<File> files) {
		Map<File, Integer> durations = new LinkedHashMap<File, Integer>();
		for (File f : files) {
			if (isDiscRip(f)) {
				try {
					durations.put(f, (int) CachedMediaInfo.read(f).getDuration().getSeconds());
				} catch (Exception e) {
					debug.finest(cause("Failed to read duration", e));
				}
			}
		}
		return durations;
	}

	private static boolean isSameDuration(Title t, int seconds) {
		return Math.abs(t.seconds - seconds) <= MAX_SECONDS_DIFFERENCE;
	}

	private static boolean isSameSize(Title t, long bytes, double tolerance) {
		return t.bytes > 0 && Math.abs(t.bytes - bytes) <= t.bytes * tolerance;
	}

	/**
	 * @return the titles of one release (e.g. DVD or Blu-ray box set) that match the most disc rips
	 */
	private static Map<File, Title> match(List<Title> titles, Collection<File> files) {
		if (titles.isEmpty()) {
			return emptyMap();
		}

		Map<File, Integer> durations = getDurations(files);

		Map<File, Title> best = emptyMap();
		for (List<Title> release : titles.stream().collect(groupingBy(Title::getRelease, LinkedHashMap::new, toList())).values()) {
			Map<File, Title> matches = matchRelease(release, durations);
			if (matches.size() > best.size()) {
				best = matches;
			}
		}
		return best;
	}

	private static Map<File, Title> matchRelease(List<Title> release, Map<File, Integer> durations) {
		Map<File, Title> matches = new LinkedHashMap<File, Title>();

		durations.forEach((file, seconds) -> {
			List<Title> options = select(release, t -> isSameDuration(t, seconds));

			// titles of the same disc (if the disc number is known)
			Integer disc = getDiscNumber(file);
			if (disc != null && options.stream().anyMatch(t -> t.disc == disc)) {
				options = select(options, t -> t.disc == disc);
			}

			// titles with the same index (if MakeMKV listed all titles)
			Integer index = getTitleIndex(file);
			if (isAmbiguous(options) && index != null && options.stream().anyMatch(t -> t.index == index)) {
				options = select(options, t -> t.index == index);
			}

			// title with the closest size
			if (isAmbiguous(options)) {
				long bytes = file.length();
				options = options.stream().filter(t -> isSameSize(t, bytes, MAX_SIZE_DIFFERENCE)).sorted(Comparator.comparingLong(t -> Math.abs(t.bytes - bytes))).limit(1).collect(toList());
			}

			if (options.size() > 0 && !isAmbiguous(options)) {
				matches.put(file, options.get(0));
			}
		});

		return matches;
	}

	private static boolean isAmbiguous(List<Title> options) {
		return options.stream().map(Title::getContent).distinct().count() > 1;
	}

	private static List<Title> select(List<Title> titles, Predicate<Title> condition) {
		return titles.stream().filter(condition).collect(toList());
	}

	private static Integer getTitleIndex(File file) {
		Matcher m = DISC_RIP.matcher(getNameWithoutExtension(file.getName()));
		return m.find() ? Integer.valueOf(m.group(1)) : null;
	}

	/**
	 * Disc number from the file name or the names of the parent and grandparent folders
	 */
	private static Integer getDiscNumber(File file) {
		List<String> names = new ArrayList<String>();
		names.add(DISC_RIP.matcher(getNameWithoutExtension(file.getName())).replaceFirst(""));
		for (File f = file.getParentFile(); f != null && names.size() < 3; f = f.getParentFile()) {
			names.add(f.getName());
		}

		for (String name : names) {
			Matcher m = DISC_NUMBER.matcher(name);
			if (m.find()) {
				return Integer.valueOf(m.group(1));
			}
		}
		return null;
	}

	/**
	 * Title of a disc, e.g. an episode, the main movie or an extra (Extra, Featurette, DeletedScene, Trailer, Interview,
	 * Scene, Short, Music, Other)
	 */
	public static class Title {

		public static final String EPISODE = "Episode";
		public static final String MAIN_MOVIE = "MainMovie";

		private final String kind;
		private final int tmdb;
		private final String release;
		private final int disc;
		private final int index;
		private final int seconds;
		private final long bytes;
		private final String type;
		private final int season;
		private final int episode;
		private final String name;

		public Title(String kind, int tmdb, String release, int disc, int index, int seconds, long bytes, String type, int season, int episode, String name) {
			this.kind = kind;
			this.tmdb = tmdb;
			this.release = release;
			this.disc = disc;
			this.index = index;
			this.seconds = seconds;
			this.bytes = bytes;
			this.type = type;
			this.season = season;
			this.episode = episode;
			this.name = name;
		}

		/**
		 * e.g. tv/1396 or movie/603
		 */
		public String getKey() {
			return kind + "/" + tmdb;
		}

		public String getRelease() {
			return release;
		}

		public int getDisc() {
			return disc;
		}

		public int getIndex() {
			return index;
		}

		public int getSeconds() {
			return seconds;
		}

		public String getType() {
			return type;
		}

		public String getName() {
			return name;
		}

		/**
		 * e.g. Behind The Story: River
		 */
		public String getTitle() {
			return name;
		}

		/**
		 * Extras folder of the Plex naming standard (e.g. Featurettes), or null for episodes and main movies
		 */
		public String getFolder() {
			return isExtra() ? NamingStandard.PLEX.getExtrasFolder(type) : null;
		}

		/**
		 * @return season number (0 for specials) or null
		 */
		public Integer getSeason() {
			return season >= 0 ? season : null;
		}

		public Integer getEpisode() {
			return episode > 0 ? episode : null;
		}

		/**
		 * Neither an episode of a series nor the main movie of a movie disc
		 */
		public boolean isExtra() {
			return !(MOVIE.equals(kind) ? MAIN_MOVIE : EPISODE).equals(type);
		}

		private String getContent() {
			return String.join("/", type, String.valueOf(season), String.valueOf(episode), name);
		}

		/**
		 * kind, tmdb, release, disc, title index, seconds, bytes, type, season, episode, title
		 */
		public static Title parse(String[] v) {
			try {
				return new Title(v[0], Integer.parseInt(v[1]), v[2], Integer.parseInt(v[3]), Integer.parseInt(v[4]), Integer.parseInt(v[5]), Long.parseLong(v[6]), v[7], Integer.parseInt(v[8]), Integer.parseInt(v[9]), v.length > 10 ? v[10] : "");
			} catch (Exception e) {
				return null; // ignore invalid rows
			}
		}

		@Override
		public String toString() {
			return String.format("%s: %s", type, name);
		}

		public String getDescription() {
			return String.format("%s/disc%02d/t%02d [%ds] %s: %s", release, disc, index, seconds, type, name);
		}

		@Override
		public int hashCode() {
			return Objects.hash(kind, tmdb, release, disc, index);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof Title t && t.kind.equals(kind) && t.tmdb == tmdb && t.release.equals(release) && t.disc == disc && t.index == index;
		}
	}

}
