package dev.davamu.filebotpro;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.media.MediaDetection.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.stream.Stream;

import javax.swing.Icon;

import dev.davamu.filebotpro.media.LocalDatasource;
import dev.davamu.filebotpro.similarity.MetricAvg;
import dev.davamu.filebotpro.web.AcoustIDClient;
import dev.davamu.filebotpro.web.Artwork;
import dev.davamu.filebotpro.web.Datasource;
import dev.davamu.filebotpro.web.EpisodeListProvider;
import dev.davamu.filebotpro.web.FanartTVClient;
import dev.davamu.filebotpro.web.ID3Lookup;
import dev.davamu.filebotpro.web.LocalSearch;
import dev.davamu.filebotpro.web.Movie;
import dev.davamu.filebotpro.web.MovieIdentificationService;
import dev.davamu.filebotpro.web.MusicIdentificationService;
import dev.davamu.filebotpro.web.OMDbClient;
import dev.davamu.filebotpro.web.SearchResult;
import dev.davamu.filebotpro.web.ShooterSubtitles;
import dev.davamu.filebotpro.web.SubtitleProvider;
import dev.davamu.filebotpro.web.SubtitleSearchResult;
import dev.davamu.filebotpro.web.TVMazeClient;
import dev.davamu.filebotpro.web.TheTVDBSearchResult;
import dev.davamu.filebotpro.web.ThumbnailProvider;
import dev.davamu.filebotpro.web.VideoHashSubtitleService;

/**
 * Reuse the same web service client so login, cache, etc. can be shared.
 */
public final class WebServices {

	// movie sources
	public static final OMDbClient OMDb = new OMDbClient(getApiKey("omdb"));
	public static final dev.davamu.filebotpro.web.TMDbClient TheMovieDB = new TMDbClient(getApiKey("themoviedb"));

	// episode sources
	public static final TVMazeClient TVmaze = new TVMazeClient();
	public static final dev.davamu.filebotpro.web.AnidbClient AniDB = new AnidbClient(getApiKey("anidb"), 1);

	// extended TheTVDB module with local search
	public static final dev.davamu.filebotpro.web.TheTVDBClient TheTVDB = new TheTVDBClient(getApiKey("thetvdb"));
	public static final dev.davamu.filebotpro.web.TMDbTVClient TheMovieDB_TV = new TMDbTVClient(TheMovieDB);

	// subtitle sources
	public static final dev.davamu.filebotpro.web.OpenSubtitlesClient OpenSubtitles = new OpenSubtitlesClient(getApiKey("opensubtitles"), getApplicationName() + " v" + getApplicationVersion());
	public static final ShooterSubtitles Shooter = new ShooterSubtitles();

	// other sources
	public static final dev.davamu.filebotpro.web.AnimeLists AnimeList = new AnimeLists();
	public static final FanartTVClient FanartTV = new FanartTVClient(getApiKey("fanart.tv"));
	public static final AcoustIDClient AcoustID = new AcoustIDClient(getApiKey("acoustid"));
	public static final ID3Lookup MediaInfoID3 = new ID3Lookup();

	public static Datasource[] getServices() {
		return new Datasource[] { TheMovieDB, OMDb, TheTVDB, AniDB, TheMovieDB_TV, TVmaze, AcoustID, MediaInfoID3, LocalDatasource.EXIF, LocalDatasource.XATTR, LocalDatasource.FILE, OpenSubtitles, Shooter, AnimeList, FanartTV };
	}

	public static MovieIdentificationService[] getMovieIdentificationServices() {
		return new MovieIdentificationService[] { TheMovieDB, OMDb };
	}

	public static EpisodeListProvider[] getEpisodeListProviders() {
		return new EpisodeListProvider[] { TheTVDB, AniDB, TheMovieDB_TV, TVmaze };
	}

	public static MusicIdentificationService[] getMusicIdentificationServices() {
		return new MusicIdentificationService[] { AcoustID, MediaInfoID3 };
	}

	public static LocalDatasource[] getLocalDatasources() {
		return new LocalDatasource[] { LocalDatasource.EXIF, LocalDatasource.XATTR, LocalDatasource.FILE };
	}

	public static SubtitleProvider[] getSubtitleProviders(Locale locale) {
		return new SubtitleProvider[] { OpenSubtitles };
	}

	public static VideoHashSubtitleService[] getVideoHashSubtitleServices(Locale locale) {
		switch (locale.getLanguage()) {
		case "zh":
			return new VideoHashSubtitleService[] { OpenSubtitles, Shooter }; // special support for 射手网 for Chinese language subtitles
		default:
			return new VideoHashSubtitleService[] { OpenSubtitles };
		}
	}

	public static Datasource getService(String name) {
		return getService(name, getServices());
	}

	public static EpisodeListProvider getEpisodeListProvider(String name) {
		// TheMovieDB means TheMovieDB::TV in an episode context (e.g. -list --db TheMovieDB)
		if (TheMovieDB.getIdentifier().equalsIgnoreCase(name)) {
			return TheMovieDB_TV;
		}
		return getService(name, getEpisodeListProviders());
	}

	public static MovieIdentificationService getMovieIdentificationService(String name) {
		return getService(name, getMovieIdentificationServices());
	}

	public static MusicIdentificationService getMusicIdentificationService(String name) {
		return getService(name, getMusicIdentificationServices());
	}

	public static <T extends Datasource> T getService(String name, T... services) {
		return stream(services).filter(it -> {
			return it.getIdentifier().equalsIgnoreCase(name);
		}).findFirst().orElse(null);
	}

	public static final ExecutorService requestThreadPool = Executors.newCachedThreadPool();

	private static class TMDbClient extends dev.davamu.filebotpro.web.TMDbClient implements ThumbnailProvider {

		public TMDbClient(String apikey) {
			super(apikey);
		}

		// local TheMovieDB search index
		private final Map<Integer, Resource<LocalSearch<Movie>>> localIndexPerYear = synchronizedMap(new HashMap<>());

		private LocalSearch<Movie> computeLocalIndex(int year) throws Exception {
			if (year > 0) {
				// limit search index to a given year (so we don't have to check all movies of all time all the time)
				Movie[] movies = stream(releaseInfo.getMovieList()).filter(m -> year == m.getYear()).toArray(Movie[]::new);

				// search by primary movie name and all known alias names
				return new LocalSearch<>(movies, Movie::getEffectiveNamesWithoutYear);
			} else {
				// check all movies of all time if release year is not known (but only compare to primary title for performance reasons)
				return new LocalSearch<>(releaseInfo.getMovieList(), m -> singleton(m.getName()));
			}
		}

		private LocalSearch<Movie> getLocalIndex(int year) throws Exception {
			Resource<LocalSearch<Movie>> index = localIndexPerYear.computeIfAbsent(year, y -> {
				return Resource.lazy(() -> computeLocalIndex(y));
			});

			// make sure that LocalSearch initialization happens outside of the synchronized map
			return index.get();
		}

		@Override
		public List<Movie> searchMovie(String movieName, int movieYear, Locale locale, boolean extendedInfo) throws Exception {
			// run local search and API search in parallel
			List<Callable<List<Movie>>> searches = new ArrayList<>();

			// online API search first
			searches.add(() -> TMDbClient.super.searchMovie(movieName, movieYear, locale, extendedInfo));

			if (movieYear > 0) {
				// the year might be off by 1 so we also check movies from the previous year and the next year
				searches.add(() -> getLocalIndex(movieYear).search(movieName));
				searches.add(() -> getLocalIndex(movieYear - 1).search(movieName));
				searches.add(() -> getLocalIndex(movieYear + 1).search(movieName));
			} else {
				// search all movies of all years if year is unknown
				searches.add(() -> getLocalIndex(0).search(movieName));
			}

			// combine alias names into a single search results, and keep API search name as primary name
			LinkedHashSet<Movie> movies = new LinkedHashSet<>();

			for (Future<List<Movie>> searchResult : requestThreadPool.invokeAll(searches)) {
				movies.addAll(searchResult.get());
			}

			return new ArrayList<>(movies);
		}

		@Override
		public Map<SearchResult, Icon> getThumbnails(List<SearchResult> keys, ResolutionVariant variant) throws Exception {
			return ThumbnailServices.getThumbnails(keys, variant, k -> {
				int tmdbId = k instanceof Movie m ? m.getTmdbId() : k.getId();
				if (tmdbId > 0) {
					return getPosterThumbnail("movie/" + tmdbId, variant);
				}
				// TMDb also resolves movie records by IMDb id
				return k instanceof Movie m && m.getImdbId() > 0 ? getPosterThumbnail(String.format("movie/tt%07d", m.getImdbId()), variant) : null;
			});
		}
	}

	private static class TMDbTVClient extends dev.davamu.filebotpro.web.TMDbTVClient implements ThumbnailProvider {

		private final dev.davamu.filebotpro.web.TMDbClient tmdb;

		public TMDbTVClient(dev.davamu.filebotpro.web.TMDbClient tmdb) {
			super(tmdb);
			this.tmdb = tmdb;
		}

		@Override
		public Map<SearchResult, Icon> getThumbnails(List<SearchResult> keys, ResolutionVariant variant) throws Exception {
			return ThumbnailServices.getThumbnails(keys, variant, k -> tmdb.getPosterThumbnail("tv/" + k.getId(), variant));
		}
	}

	private static class TheTVDBClient extends dev.davamu.filebotpro.web.TheTVDBClient implements ThumbnailProvider {

		public TheTVDBClient(String apikey) {
			super(apikey);
		}

		@Override
		public List<SearchResult> fetchSearchResult(String query, Locale locale) throws Exception {
			return sortBySimilarity(super.fetchSearchResult(query, locale), singleton(query), getSeriesMatchMetric());
		}

		@Override
		public Map<SearchResult, Icon> getThumbnails(List<SearchResult> keys, ResolutionVariant variant) throws Exception {
			return ThumbnailServices.getThumbnails(keys, variant, k -> getPosterThumbnail(k.getId()));
		}
	}

	private static class AnidbClient extends dev.davamu.filebotpro.web.AnidbClient {

		public AnidbClient(String client, int clientver) {
			super(client, clientver);
		}

		@Override
		public SearchResult[] getAnimeTitles() throws Exception {
			return releaseInfo.getAnidbIndex();
		}

		@Override
		public List<Artwork> getArtwork(int id, String category, Locale locale) throws Exception {
			// picture from the bundled anime list (anime-offline-database), so no AniDB request is needed
			String picture = releaseInfo.getAnidbPictures().get(id);
			return picture == null ? emptyList() : singletonList(new Artwork(Stream.of("picture"), new URL(picture), null, null));
		}
	}

	private static class AnimeLists extends dev.davamu.filebotpro.web.AnimeLists {

		@Override
		public dev.davamu.filebotpro.web.AnimeLists.Model getModel() throws Exception {
			return releaseInfo.getAnimeListModel();
		}
	}

	private static class OpenSubtitlesClient extends dev.davamu.filebotpro.web.OpenSubtitlesClient {

		public OpenSubtitlesClient(String apikey, String userAgent) {
			super(apikey, userAgent);
		}

		// local OpenSubtitles search index
		private final Resource<LocalSearch<SubtitleSearchResult>> localIndex = Resource.lazy(() -> new LocalSearch<SubtitleSearchResult>(releaseInfo.getOpenSubtitlesIndex(), SearchResult::getEffectiveNames));

		@Override
		public List<SubtitleSearchResult> search(final String query) throws Exception {
			return sortBySimilarity(localIndex.get().search(query), singleton(query), new MetricAvg(getSeriesMatchMetric(), getMovieMatchMetric()));
		}
	}

	/**
	 * Dummy constructor to prevent instantiation.
	 */
	private WebServices() {
		throw new UnsupportedOperationException();
	}

	public static final String LOGIN_SEPARATOR = ":";
	public static final String LOGIN_OPENSUBTITLES = "osdb.user";

	/**
	 * Initialize client settings from system properties
	 */
	static {
		String[] osdbLogin = getLogin(LOGIN_OPENSUBTITLES);
		OpenSubtitles.setUser(osdbLogin[0], osdbLogin[1]);
	}

	/**
	 * The REST API needs the actual password (the legacy XML-RPC API accepted its MD5 hash), so it is stored in the user
	 * preferences: encrypted with the Windows user account (DPAPI) on Windows, only base64 encoded elsewhere (use the
	 * file permissions of the user folder). Legacy MD5 entries can't be used anymore and require a new login.
	 */
	private static final String PASSWORD_PREFIX = "b64:";
	private static final String PASSWORD_PREFIX_DPAPI = "dpapi:";

	private static String encodePassword(String password) {
		byte[] bytes = password.getBytes(UTF_8);
		if (com.sun.jna.Platform.isWindows()) {
			try {
				return PASSWORD_PREFIX_DPAPI + Base64.getEncoder().encodeToString(com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(bytes));
			} catch (Throwable e) {
				debug.log(Level.WARNING, "Failed to encrypt password with DPAPI", e);
			}
		}
		return PASSWORD_PREFIX + Base64.getEncoder().encodeToString(bytes);
	}

	private static String decodePassword(String value) {
		if (value.startsWith(PASSWORD_PREFIX_DPAPI)) {
			byte[] data = Base64.getDecoder().decode(value.substring(PASSWORD_PREFIX_DPAPI.length()));
			return new String(com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(data), UTF_8);
		}
		return new String(Base64.getDecoder().decode(value.substring(PASSWORD_PREFIX.length())), UTF_8);
	}

	public static String[] getLogin(String key) {
		try {
			String[] values = Settings.forPackage(WebServices.class).get(key, LOGIN_SEPARATOR).split(LOGIN_SEPARATOR, 2); // empty username/password by default
			if (values != null && values.length == 2 && values[0] != null && values[1] != null) {
				if (values[1].startsWith(PASSWORD_PREFIX) || values[1].startsWith(PASSWORD_PREFIX_DPAPI)) {
					return new String[] { values[0], decodePassword(values[1]) };
				}
				if (values[1].length() > 0) {
					log.warning(String.format("OpenSubtitles: Please enter your login details again for the new OpenSubtitles.com API (%s)", values[0]));
				}
				return new String[] { values[0], "" };
			}
		} catch (Exception e) {
			debug.log(Level.SEVERE, e.getMessage(), e);
		}
		return new String[] { "", "" };
	}

	public static void setLogin(String id, String user, String password) {
		// delete login
		if ((user == null || user.isEmpty()) && (password == null || password.isEmpty())) {
			if (LOGIN_OPENSUBTITLES.equals(id)) {
				OpenSubtitles.setUser("", "");
				Settings.forPackage(WebServices.class).remove(id);
			} else {
				throw new IllegalArgumentException();
			}
		} else {
			// enter login
			if (user == null || password == null || user.contains(LOGIN_SEPARATOR) || (user.isEmpty() && !password.isEmpty()) || (!user.isEmpty() && password.isEmpty())) {
				throw new IllegalArgumentException(String.format("Illegal login: %s", user));
			}

			if (LOGIN_OPENSUBTITLES.equals(id)) {
				OpenSubtitles.setUser(user, password);
				Settings.forPackage(WebServices.class).put(id, user + LOGIN_SEPARATOR + encodePassword(password));
			} else {
				throw new IllegalArgumentException();
			}
		}
	}

}
