package dev.davamu.filebotpro.web;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.util.JsonUtilities.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;
import static dev.davamu.filebotpro.web.OpenSubtitlesHasher.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Stream;

import javax.swing.Icon;

import dev.davamu.filebotpro.Cache;
import dev.davamu.filebotpro.Cache.TypedCache;
import dev.davamu.filebotpro.CacheType;
import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.WebServices;
import dev.davamu.filebotpro.web.OpenSubtitlesApi.OpenSubtitlesException;

/**
 * Subtitle client for OpenSubtitles.com (REST API).
 *
 * Searching works with the application API key alone. Downloads work anonymously as well (5 per day and IP), and use
 * the download quota of the user if login details have been set.
 */
public class OpenSubtitlesClient implements SubtitleProvider, VideoHashSubtitleService, MovieIdentificationService {

	public static final String WEBSITE = "https://www.opensubtitles.com";

	protected final OpenSubtitlesApi api;

	private String username = "";
	private String password = "";

	private long sessionExpires = 0;
	private boolean loginFailed = false;

	public OpenSubtitlesClient(String apikey, String userAgent) {
		this.api = new OpenSubtitlesApi(apikey, userAgent);
	}

	@Override
	public String getIdentifier() {
		return "OpenSubtitles";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("search.opensubtitles");
	}

	@Override
	public URI getLink() {
		return URI.create(WEBSITE);
	}

	public synchronized void setUser(String username, String password) {
		// cancel previous session
		logout();

		this.username = username == null ? "" : username;
		this.password = password == null ? "" : password;
		this.loginFailed = false;
	}

	public synchronized boolean isAnonymous() {
		return username.isEmpty() || password.isEmpty();
	}

	/**
	 * Log in with the user login details (if any) so downloads count against the quota of the user
	 */
	public synchronized void login() throws Exception {
		if (isAnonymous() || (api.hasSession() && System.currentTimeMillis() < sessionExpires)) {
			return;
		}

		// login is expensive and rate limited, so never retry with the same bad login details
		if (loginFailed) {
			throw new IllegalStateException(getName() + ": Login failed. Please check your login details.");
		}

		Map<String, Object> login = new LinkedHashMap<String, Object>(2);
		login.put("username", username);
		login.put("password", password);

		try {
			Object response = api.post("login", login);
			api.setSession(getString(response, "base_url"), getString(response, "token"));
			sessionExpires = System.currentTimeMillis() + Duration.ofHours(23).toMillis(); // tokens are valid for 24 hours
			debug.fine(() -> String.format("[%s] Logged in as %s", getName(), username));
		} catch (OpenSubtitlesException e) {
			if (e.getStatus() == 401) {
				loginFailed = true;
				throw new IllegalStateException(getName() + ": Login failed. Please check your login details.", e);
			}
			throw e;
		}
	}

	public synchronized void logout() {
		if (api.hasSession()) {
			try {
				api.delete("logout");
			} catch (Exception e) {
				debug.log(Level.FINE, "Failed to log out", e);
			}
		}
		api.setSession(null, null);
		sessionExpires = 0;
	}

	/**
	 * Account information: allowed_downloads, remaining_downloads, downloads_count, level and vip
	 */
	public synchronized Map<?, ?> getUserInfo() throws Exception {
		if (isAnonymous()) {
			throw new IllegalStateException(getName() + ": No login details");
		}
		login();
		return getMap(api.get("infos/user", emptyMap()), "data");
	}

	@Override
	public List<SubtitleSearchResult> search(String query) throws Exception {
		return searchFeatures(query, null);
	}

	@Override
	public List<Movie> searchMovie(String query, Locale locale) throws Exception {
		return searchFeatures(query, "movie").stream().map(Movie.class::cast).collect(toList());
	}

	protected List<SubtitleSearchResult> searchFeatures(String query, String type) throws Exception {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("query", query);
		parameters.put("type", type);

		return getSearchCache("features").computeIfAbsent(parameters.toString(), it -> {
			return streamJsonObjects(api.get("features", parameters), "data").map(it2 -> getSearchResult(getMap(it2, "attributes"), type)).filter(Objects::nonNull).collect(toList());
		});
	}

	protected SubtitleSearchResult getSearchResult(Map<?, ?> feature) {
		return getSearchResult(feature, null);
	}

	/**
	 * @param type
	 *            the requested feature type (results don't include the feature type if the search was filtered by type)
	 */
	protected SubtitleSearchResult getSearchResult(Map<?, ?> feature, String type) {
		String featureType = Objects.toString(feature.get("feature_type"), type);
		Integer imdb = getInteger(feature, "imdb_id");
		String title = getString(feature, "title");

		// episodes are not search results
		if (imdb == null || title == null || "Episode".equalsIgnoreCase(featureType)) {
			return null;
		}

		// search results have lower-case titles, so prefer the same title with proper case
		String name = Stream.concat(Stream.of(getString(feature, "original_title")), streamAliasNames(feature)).filter(Objects::nonNull).filter(title::equalsIgnoreCase).findFirst().orElse(title);

		Integer year = getStringValue(feature, "year", s -> s.matches("\\d{4}") ? Integer.parseInt(s) : null);
		Integer tmdb = getStringValue(feature, "tmdb_id", s -> s.matches("\\d+") ? Integer.parseInt(s) : null);
		String[] aliasNames = streamAliasNames(feature).filter(s -> !s.equalsIgnoreCase(name)).toArray(String[]::new);
		SubtitleSearchResult.Kind kind = "Tvshow".equalsIgnoreCase(featureType) ? SubtitleSearchResult.Kind.Series : "Movie".equalsIgnoreCase(featureType) ? SubtitleSearchResult.Kind.Movie : SubtitleSearchResult.Kind.Other;

		return new SubtitleSearchResult(name, aliasNames, year == null ? 0 : year, imdb, tmdb == null ? -1 : tmdb, Locale.ENGLISH, kind, 0);
	}

	private Stream<String> streamAliasNames(Map<?, ?> feature) {
		Object aka = feature.get("title_aka");
		Stream<Object> names = aka instanceof Collection ? ((Collection<?>) aka).stream().map(Object.class::cast) : aka instanceof Object[] ? stream((Object[]) aka) : Stream.empty();
		return Stream.concat(Stream.of(feature.get("original_title")), names).filter(Objects::nonNull).map(Object::toString).map(String::trim).filter(s -> s.length() > 0).distinct();
	}

	/**
	 * Guess movie or series from a file name
	 */
	@Override
	public List<SubtitleSearchResult> guess(String tag) throws Exception {
		return getSearchCache("guess").computeIfAbsent(tag, it -> {
			Object guess = api.get("utilities/guessit", singletonMap("filename", tag));
			String title = getString(guess, "title");
			if (title == null) {
				return emptyList();
			}

			Integer year = getInteger(guess, "year");
			String type = "episode".equalsIgnoreCase(getString(guess, "type")) ? "tvshow" : "movie";

			Map<String, Object> parameters = new HashMap<String, Object>();
			parameters.put("query", title);
			parameters.put("type", type);
			if (year != null && type.equals("movie")) {
				parameters.put("year", year);
			}

			return streamJsonObjects(api.get("features", parameters), "data").map(f -> getSearchResult(getMap(f, "attributes"), type)).filter(Objects::nonNull).limit(5).collect(toList());
		});
	}

	public List<SubtitleDescriptor> getSubtitleList(SubtitleSearchResult searchResult, Locale locale) throws Exception {
		return getSubtitleList(searchResult, -1, -1, locale);
	}

	@Override
	public List<SubtitleDescriptor> getSubtitleList(SubtitleSearchResult searchResult, int[][] episodeFilter, Locale locale) throws Exception {
		// no filter
		if (episodeFilter == null || episodeFilter.length == 0) {
			return getSubtitleList(searchResult, -1, -1, locale);
		}

		int[] seasons = stream(episodeFilter).mapToInt(ii -> ii[0]).filter(i -> i >= 0).sorted().distinct().toArray();
		int[] episodes = stream(episodeFilter).mapToInt(ii -> ii[1]).filter(i -> i >= 0).sorted().distinct().toArray();

		// no filter
		if (seasons.length == 0 && episodes.length == 0) {
			return getSubtitleList(searchResult, -1, -1, locale);
		}

		// episode filter
		if (seasons.length == 1 && episodes.length == 1) {
			return getSubtitleList(searchResult, seasons[0], episodes[0], locale);
		}

		// season filter
		if (seasons.length > 0 && episodes.length == 0) {
			return stream(seasons).boxed().flatMap(s -> {
				try {
					return getSubtitleList(searchResult, s, -1, locale).stream();
				} catch (Exception e) {
					throw new RuntimeException(String.format("Failed to retrieve subtitle list for season: %s S%02d [%s]", searchResult, s, locale), e);
				}
			}).distinct().collect(toList());
		}

		// multi-episode filter
		return stream(episodeFilter).flatMap(ii -> {
			try {
				return getSubtitleList(searchResult, ii[0], ii[1], locale).stream();
			} catch (Exception e) {
				throw new RuntimeException(String.format("Failed to retrieve subtitle list for episode: %s %s [%s]", searchResult, asList(ii), locale), e);
			}
		}).distinct().collect(toList());
	}

	public List<SubtitleDescriptor> getSubtitleList(SubtitleSearchResult searchResult, int season, int episode, Locale locale) throws Exception {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("languages", getLanguageFilter(locale));

		// series are searched by parent id, movies by id
		if (searchResult.isSeries() || season >= 0 || episode >= 0) {
			parameters.put("parent_imdb_id", searchResult.getImdbId());
			parameters.put("season_number", season >= 0 ? season : null);
			parameters.put("episode_number", episode >= 0 ? episode : null);
		} else {
			parameters.put("imdb_id", searchResult.getImdbId());
		}

		return searchSubtitles(parameters);
	}

	/**
	 * Search subtitles (all pages, up to 500 subtitles)
	 */
	public List<SubtitleDescriptor> searchSubtitles(Map<String, Object> parameters) throws Exception {
		return getSubtitlesCache().computeIfAbsent(parameters.toString(), it -> {
			Map<String, String> languages = getLanguageNames();
			List<SubtitleDescriptor> subtitles = new ArrayList<SubtitleDescriptor>();

			for (int page = 1; page <= 10; page++) {
				Map<String, Object> query = new HashMap<String, Object>(parameters);
				if (page > 1) {
					query.put("page", page);
				}

				Object response = api.get("subtitles", query);
				streamJsonObjects(response, "data").map(s -> getMap(s, "attributes")).forEach(a -> {
					for (Map<?, ?> file : asMapArray(a.get("files"))) {
						subtitles.add(new OpenSubtitlesSubtitleDescriptor(a, file, languages.get(getString(a, "language"))));
					}
				});

				Integer pages = getInteger(response, "total_pages");
				if (pages == null || page >= pages) {
					break;
				}
			}

			// regional Spanish variants ("ea" or "sp") before plain "es" if both have been searched for
			if (Objects.toString(parameters.get("languages"), "").contains(",")) {
				subtitles.sort(Comparator.comparing(s -> "es".equals(((OpenSubtitlesSubtitleDescriptor) s).getLanguage())));
			}

			return subtitles;
		});
	}

	@Override
	public Map<File, List<SubtitleDescriptor>> getSubtitleList(File[] files, Locale locale) throws Exception {
		return getSubtitleListByHash(files, locale);
	}

	protected Map<File, List<SubtitleDescriptor>> getSubtitleList(File[] files, Function<File, Map<String, Object>> queryMapper) throws Exception {
		Map<File, List<SubtitleDescriptor>> results = new LinkedHashMap<File, List<SubtitleDescriptor>>(files.length);

		for (File f : files) {
			Map<String, Object> query = queryMapper.apply(f);
			results.put(f, query == null ? emptyList() : searchSubtitles(query));
		}

		return results;
	}

	/**
	 * Subtitles that have been uploaded for the exact same video file
	 */
	public Map<File, List<SubtitleDescriptor>> getSubtitleListByHash(File[] files, Locale locale) throws Exception {
		return getSubtitleList(files, f -> {
			String hash = getMovieHash(f);
			if (hash == null) {
				return null;
			}

			Map<String, Object> parameters = new HashMap<String, Object>();
			parameters.put("moviehash", hash);
			parameters.put("moviehash_match", "only");
			parameters.put("languages", getLanguageFilter(locale));
			return parameters;
		});
	}

	public Map<File, List<SubtitleDescriptor>> getSubtitleListByTag(File[] files, Locale locale) throws Exception {
		return getSubtitleList(files, f -> {
			Map<String, Object> parameters = new HashMap<String, Object>();
			parameters.put("query", getNameWithoutExtension(f.getName()));
			parameters.put("languages", getLanguageFilter(locale));
			return parameters;
		});
	}

	protected String getMovieHash(File f) {
		if (f.length() > HASH_CHUNK_SIZE) {
			try {
				return computeHash(f);
			} catch (Exception e) {
				debug.log(Level.SEVERE, "Failed to compute hash", e);
			}
		} else {
			// debug dummy files, e.g. { "hash":"ca8395374fad4b83", "size":639511378 }
			try {
				Map<?, ?> json = asMap(readJson(readTextFile(f)));
				if (json != null && json.get("hash") != null) {
					return json.get("hash").toString();
				}
			} catch (Exception e) {
				debug.finest("Ignore sample file: " + f);
			}
		}
		return null;
	}

	/**
	 * Request a download link and fetch the subtitle file (counts against the download quota)
	 */
	public ByteBuffer download(int fileId) throws Exception {
		Object response;
		synchronized (this) {
			try {
				login();
			} catch (Exception e) {
				log.warning(e.getMessage()); // continue with anonymous download
			}

			try {
				response = api.post("download", singletonMap("file_id", fileId));
			} catch (OpenSubtitlesException e) {
				// e.g. 406 Not Acceptable (download quota exceeded)
				throw new IllegalStateException(String.format("%s: %s", getName(), e.getMessage()), e);
			}
		}

		debug.finest(() -> String.format("[%s] Downloads: %s requests, %s remaining", getName(), getInteger(response, "requests"), getInteger(response, "remaining")));

		String link = getString(response, "link");
		if (link == null) {
			throw new IllegalStateException(String.format("%s: %s", getName(), getString(response, "message")));
		}

		HttpRequest request = HttpRequest.newBuilder(URI.create(link)).header("User-Agent", api.getUserAgent()).timeout(Duration.ofSeconds(60)).build();
		byte[] data = HttpClients.get().send(request, BodyHandlers.ofByteArray()).body();
		return ByteBuffer.wrap(data);
	}

	@Override
	public Movie getMovieDescriptor(Movie id, Locale locale) throws Exception {
		if (id.getImdbId() <= 0) {
			throw new IllegalArgumentException("Illegal IMDbID ID: " + id.getImdbId());
		}

		return getLookupCache(locale).computeIfAbsent(id.getImdbId(), it -> {
			// localized movie data from TheMovieDB, or the OpenSubtitles feature as fallback
			try {
				Movie movie = WebServices.TheMovieDB.getMovieDescriptor(new Movie(id.getImdbId()), locale);
				if (movie != null) {
					return movie;
				}
			} catch (Exception e) {
				debug.finest(cause("Failed to lookup movie on TheMovieDB", e));
			}

			return streamJsonObjects(api.get("features", singletonMap("imdb_id", id.getImdbId())), "data").map(f -> getMap(f, "attributes")).map(this::getSearchResult).filter(Objects::nonNull).map(r -> new Movie(r.getName(), r.getAliasNames(), r.getYear(), r.getImdbId(), r.getTmdbId(), locale)).findFirst().orElse(null);
		});
	}

	public Movie getMovieDescriptor(File movieFile, Locale locale) throws Exception {
		return getMovieDescriptors(singleton(movieFile), locale).get(movieFile);
	}

	/**
	 * Identify movies by file hash: subtitles uploaded for the same file must agree on the same movie (at least 80% of
	 * them, and at least 2 by default)
	 */
	public Map<File, Movie> getMovieDescriptors(Collection<File> movieFiles, Locale locale) throws Exception {
		Map<File, Movie> results = new HashMap<File, Movie>();

		int minMatches = Integer.getInteger("dev.davamu.filebotpro.OpenSubtitles.minHashMatches", 2);

		for (File f : movieFiles) {
			String hash = getMovieHash(f);
			if (hash == null) {
				continue;
			}

			Map<String, Object> parameters = new HashMap<String, Object>();
			parameters.put("moviehash", hash);
			parameters.put("moviehash_match", "only");

			// count hash matches per movie
			Map<Integer, List<OpenSubtitlesSubtitleDescriptor>> matches = searchSubtitles(parameters).stream().map(OpenSubtitlesSubtitleDescriptor.class::cast).filter(OpenSubtitlesSubtitleDescriptor::isHashMatch).filter(s -> s.getImdbId() > 0).collect(groupingBy(OpenSubtitlesSubtitleDescriptor::getImdbId));
			int total = matches.values().stream().mapToInt(List::size).sum();

			matches.entrySet().stream().max((a, b) -> Integer.compare(a.getValue().size(), b.getValue().size())).ifPresent(best -> {
				int count = best.getValue().size();
				OpenSubtitlesSubtitleDescriptor s = best.getValue().get(0);

				if (count >= minMatches && count >= 0.8 * total && "Movie".equalsIgnoreCase(s.getFeatureType())) {
					results.put(f, new Movie(s.getFeatureTitle(), new String[0], s.getFeatureYear(), s.getImdbId(), s.getTmdbId() > 0 ? s.getTmdbId() : -1, locale));
				} else {
					debug.finest(() -> String.format("[%s] Ignore hash match for %s: %s", getName(), f.getName(), matches.values().stream().map(l -> l.get(0).getFeatureTitle() + " x" + l.size()).collect(toList())));
				}
			});
		}

		return results;
	}

	@Override
	public URI getSubtitleListLink(SubtitleSearchResult searchResult, Locale locale) {
		// movie or series page (e.g. https://www.opensubtitles.com/en/movies/1999-the-matrix)
		if (searchResult.getImdbId() > 0) {
			try {
				String url = (String) getCache("links").computeIfAbsent(searchResult.getImdbId(), it -> {
					return streamJsonObjects(api.get("features", singletonMap("imdb_id", searchResult.getImdbId())), "data").map(f -> getString(getMap(f, "attributes"), "url")).filter(Objects::nonNull).findFirst().orElse(null);
				});
				if (url != null) {
					return URI.create(url);
				}
			} catch (Exception e) {
				debug.finest(cause("Failed to retrieve page link", e));
			}
		}

		// search page
		String language = locale == null || locale.getLanguage().isEmpty() ? "en" : locale.getLanguage();
		String query = URLEncoder.encode(searchResult.getName(), StandardCharsets.UTF_8).replace("+", "%20");
		return URI.create(String.format("%s/%s/search-all/q-%s/hearing_impaired-include/machine_translated-/trusted_sources-", WEBSITE, language, query));
	}

	/**
	 * Language names by language code (e.g. en, es, pt-br)
	 */
	protected synchronized Map<String, String> getLanguageNames() throws Exception {
		Cache cache = Cache.getCache(getName() + "_languages", CacheType.Persistent);
		Map<?, ?> languages = (Map<?, ?>) cache.computeIfAbsent("languages", k -> {
			Map<String, String> m = new LinkedHashMap<String, String>();
			streamJsonObjects(api.get("infos/languages", emptyMap()), "data").forEach(it -> m.put(getString(it, "language_code"), getString(it, "language_name")));
			return m;
		});

		Map<String, String> names = new HashMap<String, String>();
		languages.forEach((k, v) -> names.put(String.valueOf(k), String.valueOf(v)));
		return names;
	}

	/**
	 * Language code for the given locale (e.g. en, pt-br, zh-cn) or null for all languages
	 */
	public String getLanguageCode(Locale locale) {
		if (locale == null || locale.getLanguage().isEmpty()) {
			return null;
		}

		String language = locale.getLanguage();
		String country = locale.getCountry().toLowerCase();

		// legacy ISO 639 codes
		switch (language) {
		case "iw":
			language = "he";
			break;
		case "in":
			language = "id";
			break;
		case "ji":
			language = "yi";
			break;
		}

		// regional variants have their own language code
		switch (language) {
		case "pt":
			return country.equals("br") ? "pt-br" : "pt-pt";
		case "zh":
			return country.equals("tw") || country.equals("hk") || "Hant".equals(locale.getScript()) ? "zh-tw" : "zh-cn";
		case "es":
			return LATIN_AMERICA.contains(country) ? "ea" : "es";
		}

		return language;
	}

	/**
	 * Countries that use Latin American Spanish (e.g. es-MX or es-419)
	 */
	private static final Set<String> LATIN_AMERICA = Set.of("419", "ar", "bo", "cl", "co", "cr", "cu", "do", "ec", "gt", "hn", "mx", "ni", "pa", "pe", "pr", "py", "sv", "us", "uy", "ve");

	/**
	 * Language codes to search for (comma separated and sorted as required by the API)
	 */
	protected String getLanguageFilter(Locale locale) {
		String language = getLanguageCode(locale);

		// Spanish subtitles are tagged "es" (most of them, from Spain and Latin America alike), "ea" (Latin America) or
		// "sp" (Spain), and searching one code never returns the others, so search the regional variant together with "es"
		if ("ea".equals(language)) {
			return "ea,es";
		}
		if ("es".equals(language)) {
			return "es,sp";
		}

		return language;
	}

	public Cache getCache(String section) {
		return Cache.getCache(getName() + "_v2_" + section, CacheType.Daily);
	}

	protected TypedCache<List<SubtitleSearchResult>> getSearchCache(String method) {
		return getCache("search_" + method).castList(SubtitleSearchResult.class);
	}

	protected TypedCache<List<SubtitleDescriptor>> getSubtitlesCache() {
		return getCache("subtitles").castList(SubtitleDescriptor.class);
	}

	protected TypedCache<Movie> getLookupCache(Locale locale) {
		return getCache("lookup_" + locale).cast(Movie.class);
	}

}
