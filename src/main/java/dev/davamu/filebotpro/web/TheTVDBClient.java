package dev.davamu.filebotpro.web;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.CachedResource.fetchIfModified;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.util.JsonUtilities.*;
import static dev.davamu.filebotpro.web.EpisodeUtilities.*;
import static dev.davamu.filebotpro.web.WebRequest.*;

import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import javax.swing.Icon;

import dev.davamu.filebotpro.Cache;
import dev.davamu.filebotpro.CacheType;
import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.util.CountryCodes;

/**
 * TheTVDB API v4 client, see https://thetvdb.github.io/v4-api/
 */
public class TheTVDBClient extends AbstractEpisodeListProvider implements ArtworkProvider {

	private static URL getEndpoint() {
		try {
			return new URL(System.getProperty("dev.davamu.filebotpro.TheTVDB.url", "https://api4.thetvdb.com/v4/"));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static URL getBannerMirror() {
		try {
			return new URL(System.getProperty("dev.davamu.filebotpro.TheTVDB.banner.url", "https://artworks.thetvdb.com/"));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	// API v4 identifies languages by ISO 639-2 codes
	private static final String DEFAULT_LANGUAGE = "eng";

	// TheTVDB artwork types (GET /artwork/types) by category name
	private static final Map<String, Integer> ARTWORK_TYPES = Map.ofEntries(Map.entry("series", 1), Map.entry("banner", 1), Map.entry("poster", 2), Map.entry("fanart", 3), Map.entry("background", 3), Map.entry("icon", 5), Map.entry("seasonwide", 6), Map.entry("season", 7), Map.entry("seasonfanart", 8), Map.entry("seasonicon", 10), Map.entry("clearart", 22), Map.entry("clearlogo", 23));

	private final URL api;
	private final String apikey;

	public TheTVDBClient(URL api, String apikey) {
		this.api = api;
		this.apikey = apikey;
	}

	public TheTVDBClient(String apikey) {
		this(getEndpoint(), apikey);
	}

	@Override
	public String getIdentifier() {
		return "TheTVDB";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("search.thetvdb");
	}

	@Override
	public boolean hasSeasonSupport() {
		return true;
	}

	@Override
	public SortOrder vetoRequestParameter(SortOrder order) {
		// every order maps to a TheTVDB season type (see getSeasonType)
		return order == null ? SortOrder.Airdate : order;
	}

	/**
	 * TheTVDB season type of the given order, see https://thetvdb.github.io/v4-api/#/Series/getSeriesEpisodes
	 */
	protected static String getSeasonType(SortOrder order) {
		switch (order) {
		case Official:
			return "official"; // i.e. Aired Order
		case DVD:
			return "dvd";
		case Digital:
			return "alternate"; // i.e. Alternate Order
		case StoryArc:
			return "altdvd"; // i.e. Alternate DVD Order
		case Production:
			return "regional"; // i.e. Regional Order
		default:
			return "default"; // i.e. the default order of the series (usually Aired Order) with absolute numbers
		}
	}

	protected Object postJson(String path, Object object) throws Exception {
		// curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json' 'https://api4.thetvdb.com/v4/login' --data '{"apikey":"XXXXX"}'
		ByteBuffer response = post(getEndpoint(path), json(object, false).getBytes(UTF_8), "application/json", null);
		return readJson(UTF_8.decode(response));
	}

	protected Object requestJson(String path, Duration expirationTime) throws Exception {
		// responses are language independent (translations are requested by path), and the v4 cache must not mix with v2 responses
		Cache cache = Cache.getCache(getName() + "_v4", CacheType.Monthly);
		return cache.json(path, this::getEndpoint).fetch(fetchIfModified(this::getRequestHeader)).expire(expirationTime).get();
	}

	protected URL getEndpoint(String path) throws Exception {
		return new URL(api, path);
	}

	private Map<String, String> getRequestHeader() {
		Map<String, String> header = new LinkedHashMap<String, String>(2);
		header.put("Accept", "application/json");
		header.put("Authorization", "Bearer " + getAuthorizationToken());
		return header;
	}

	protected static Optional<String> getLanguageCode(Locale locale) {
		if (locale == null || locale.getLanguage().isEmpty()) {
			return Optional.empty();
		}

		// regional variants have their own language code
		if (locale.getLanguage().equals("pt") && locale.getCountry().equals("BR")) {
			return Optional.of("pt");
		}
		if (locale.getLanguage().equals("zh") && (locale.getCountry().equals("TW") || locale.getScript().equals("Hant"))) {
			return Optional.of("zhtw");
		}

		try {
			return Optional.of(locale.getISO3Language());
		} catch (MissingResourceException e) {
			return Optional.empty();
		}
	}

	private final Object tokenLock = new Object();
	private final Duration tokenExpireDuration = Duration.ofDays(25); // token expires after one month

	private String token = null;
	private Instant tokenExpireInstant = null;

	private String getAuthorizationToken() {
		synchronized (tokenLock) {
			if (token == null || (tokenExpireInstant != null && Instant.now().isAfter(tokenExpireInstant))) {
				try {
					Object json = postJson("login", singletonMap("apikey", ApiKeys.require(getName(), "thetvdb", apikey)));
					token = getString(getMap(json, "data"), "token");
					tokenExpireInstant = Instant.now().plus(tokenExpireDuration);
				} catch (Exception e) {
					throw new IllegalStateException("Failed to retrieve authorization token: " + e.getMessage(), e);
				}
			}
			return token;
		}
	}

	/**
	 * Translated value from a record requested with {@code meta=translations}, defaulting to English and then to the untranslated value.
	 */
	private static String getTranslation(Object record, String translations, String field, Locale locale) {
		return Stream.of(getLanguageCode(locale).orElse(DEFAULT_LANGUAGE), DEFAULT_LANGUAGE).distinct().map(language -> {
			return streamJsonObjects(getMap(record, "translations"), translations).filter(it -> language.equals(getString(it, "language"))).map(it -> getString(it, field)).filter(Objects::nonNull).findFirst();
		}).flatMap(Optional::stream).findFirst().orElse(getString(record, field));
	}

	private static String getTranslation(Map<?, ?> translations, Locale locale) {
		return Stream.of(getLanguageCode(locale).orElse(DEFAULT_LANGUAGE), DEFAULT_LANGUAGE).map(language -> getString(translations, language)).filter(Objects::nonNull).findFirst().orElse(null);
	}

	@Override
	public List<SearchResult> fetchSearchResult(String query, Locale locale) throws Exception {
		Map<String, Object> parameters = new LinkedHashMap<String, Object>(2);
		parameters.put("query", query);
		parameters.put("type", "series");

		Object json = requestJson("search?" + encodeParameters(parameters, true), Cache.ONE_DAY);

		return streamJsonObjects(json, "data").map(it -> {
			// e.g. tvdb_id, name, slug, aliases, translations, overviews, first_air_time, network, status, image_url, thumbnail
			Integer id = getInteger(it, "tvdb_id");
			String originalName = getString(it, "name");
			String seriesName = Optional.ofNullable(getTranslation(getMap(it, "translations"), locale)).orElse(originalName);

			if (id == null || seriesName == null || seriesName.startsWith("**") || seriesName.endsWith("**")) {
				debug.warning(format("Ignore invalid series: %s [%s]", seriesName, id));
				return null;
			}

			String[] aliasNames = Stream.concat(Stream.of(originalName, getString(getMap(it, "translations"), DEFAULT_LANGUAGE)), stream(getArray(it, "aliases"))).filter(Objects::nonNull).map(Object::toString).filter(name -> !name.equals(seriesName)).distinct().toArray(String[]::new);

			String poster = Optional.ofNullable(getString(it, "thumbnail")).orElse(getString(it, "image_url"));
			if (poster != null) {
				posters.put(id, poster);
			}

			String slug = getString(it, "slug");
			String network = getString(it, "network");
			String status = getString(it, "status");
			SimpleDate firstAired = getStringValue(it, "first_air_time", SimpleDate::parse);
			String overview = Optional.ofNullable(getTranslation(getMap(it, "overviews"), locale)).orElse(getString(it, "overview"));

			return (SearchResult) new TheTVDBSearchResult(id, seriesName, aliasNames, slug, firstAired, overview, network, status);
		}).filter(Objects::nonNull).collect(toList());
	}

	// poster of each search result so that the selection dialog can show thumbnails without additional requests
	private final Map<Integer, String> posters = new ConcurrentHashMap<Integer, String>();

	public URI getPosterThumbnail(int id) {
		try {
			URL url = resolveImage(posters.get(id));
			return url == null ? null : url.toURI();
		} catch (Exception e) {
			debug.warning(cause("Failed to resolve poster", e));
			return null;
		}
	}

	/**
	 * Extended series record with translations, artworks, characters and seasons.
	 */
	protected Map<?, ?> getSeriesRecord(int id) throws Exception {
		return getMap(requestJson("series/" + id + "/extended?meta=translations", Cache.ONE_WEEK), "data");
	}

	@Override
	public TheTVDBSeriesInfo getSeriesInfo(int id, Locale language) throws Exception {
		return getSeriesInfo(new SearchResult(id), language);
	}

	@Override
	public TheTVDBSeriesInfo getSeriesInfo(SearchResult series, Locale locale) throws Exception {
		Map<?, ?> data = getSeriesRecord(series.getId());

		TheTVDBSeriesInfo info = new TheTVDBSeriesInfo(this, locale, series.getId());
		info.setSlug(getString(data, "slug"));
		info.setName(getTranslation(data, "nameTranslations", "name", locale));

		Stream<?> aliases = Stream.of(stream(series.getAliasNames()), Stream.of(getString(data, "name")), streamJsonObjects(data, "aliases").map(it -> getString(it, "name"))).flatMap(it -> it);
		info.setAliasNames(aliases.filter(Objects::nonNull).map(Object::toString).filter(name -> !name.equals(info.getName())).distinct().toArray(String[]::new));

		info.setCertification(streamJsonObjects(data, "contentRatings").sorted(Comparator.comparing(it -> !"usa".equals(getString(it, "country")))).map(it -> getString(it, "name")).filter(Objects::nonNull).findFirst().orElse(null));
		Map<String, String> certifications = new LinkedHashMap<String, String>();
		streamJsonObjects(data, "contentRatings").forEach(it -> {
			String country = CountryCodes.toAlpha2(getString(it, "country"));
			String rating = getString(it, "name");
			if (country != null && rating != null) {
				certifications.putIfAbsent(country, rating);
			}
		});
		info.setCertifications(certifications);
		info.setCountries(Stream.of(CountryCodes.toAlpha2(getString(data, "originalCountry"))).filter(Objects::nonNull).collect(toList()));

		// season names of the official order (most seasons have no name)
		Map<Integer, String> seasonNames = new TreeMap<Integer, String>();
		streamJsonObjects(data, "seasons").filter(it -> "official".equals(getString(getMap(it, "type"), "type"))).forEach(it -> {
			Integer number = getInteger(it, "number");
			String seasonName = getString(it, "name");
			if (number != null && seasonName != null && !seasonName.isBlank()) {
				seasonNames.put(number, seasonName);
			}
		});
		info.setSeasonNames(seasonNames);

		info.setNetwork(Stream.of("latestNetwork", "originalNetwork").map(key -> getString(getMap(data, key), "name")).filter(Objects::nonNull).findFirst().orElse(null));
		info.setStatus(getString(getMap(data, "status"), "name"));

		info.setRuntime(getInteger(data, "averageRuntime"));
		info.setGenres(streamJsonObjects(data, "genres").map(it -> getString(it, "name")).filter(Objects::nonNull).collect(toList()));
		info.setStartDate(getStringValue(data, "firstAired", SimpleDate::parse));

		// TheTVDB SeriesInfo extras
		info.setImdbId(streamJsonObjects(data, "remoteIds").filter(it -> "IMDB".equalsIgnoreCase(getString(it, "sourceName"))).map(it -> getString(it, "id")).findFirst().orElse(null));
		info.setOverview(getTranslation(data, "overviewTranslations", "overview", locale));
		info.setAirsDayOfWeek(getAirsDayOfWeek(getMap(data, "airsDays")));
		info.setAirsTime(getString(data, "airsTime"));
		info.setBannerUrl(getStringValue(data, "image", this::resolveImage));
		info.setLastUpdated(getStringValue(data, "lastUpdated", s -> LocalDateTime.parse(s.replace(' ', 'T')).toEpochSecond(ZoneOffset.UTC)));

		return info;
	}

	private static String getAirsDayOfWeek(Map<?, ?> airsDays) {
		List<String> days = airsDays.entrySet().stream().filter(it -> Boolean.parseBoolean(String.valueOf(it.getValue()))).map(it -> it.getKey().toString()).collect(toList());
		if (days.isEmpty()) {
			return null;
		}
		if (days.size() == 7) {
			return "Daily";
		}
		return days.stream().map(day -> day.substring(0, 1).toUpperCase(Locale.ROOT) + day.substring(1)).collect(joining(", "));
	}

	/**
	 * All pages of the episodes of the given season type (official, dvd, absolute, ...), translated if a language is given.
	 */
	protected List<Map<?, ?>> getEpisodeRecords(int seriesId, String seasonType, String language) throws Exception {
		List<Map<?, ?>> episodes = new ArrayList<Map<?, ?>>();

		for (int page = 0; page < 100; page++) {
			Object json = requestJson("series/" + seriesId + "/episodes/" + seasonType + (language == null ? "" : "/" + language) + "?page=" + page, Cache.ONE_DAY);
			streamJsonObjects(getMap(json, "data"), "episodes").forEach(episodes::add);

			if (getString(getMap(json, "links"), "next") == null) {
				break;
			}
		}

		return episodes;
	}

	private Map<Integer, String> getEpisodeTitles(int seriesId, String seasonType, String language) {
		try {
			Map<Integer, String> titles = new HashMap<Integer, String>();
			for (Map<?, ?> it : getEpisodeRecords(seriesId, seasonType, language)) {
				Integer id = getInteger(it, "id");
				String name = getString(it, "name");
				if (id != null && name != null) {
					titles.put(id, name);
				}
			}
			return titles;
		} catch (Exception e) {
			debug.warning(cause("Failed to retrieve episode titles [" + language + "]", e));
			return emptyMap();
		}
	}

	@Override
	protected SeriesData fetchSeriesData(SearchResult series, SortOrder sortOrder, Locale locale) throws Exception {
		// fetch series info
		SeriesInfo info = getSeriesInfo(series, locale);
		info.setOrder(sortOrder.name());

		// if series name isn't even available in English then just use whatever value we've got
		if (info.getName() == null) {
			info.setName(series.getName());
		}

		// fetch episode data (not every series has a DVD or alternate order, so fall back to the default and then to the official order)
		String seasonType = getSeasonType(sortOrder);
		List<Map<?, ?>> records = emptyList();
		try {
			records = getEpisodeRecords(series.getId(), seasonType, null);
		} catch (Exception e) {
			debug.warning(cause("Failed to retrieve " + seasonType + " order", e));
		}
		for (String fallback : List.of("default", "official")) {
			if (records.isEmpty() && !fallback.equals(seasonType)) {
				debug.fine(format("[%s] No %s order for %s => %s", getName(), seasonType, series, fallback));
				seasonType = fallback;
				records = getEpisodeRecords(series.getId(), seasonType, null);
			}
		}

		// episode titles in the preferred language, defaulting to English and then to the original language
		List<Map<Integer, String>> titles = new ArrayList<Map<Integer, String>>(2);
		for (String language : Stream.of(getLanguageCode(locale).orElse(DEFAULT_LANGUAGE), DEFAULT_LANGUAGE).distinct().collect(toList())) {
			titles.add(getEpisodeTitles(series.getId(), seasonType, language));
		}

		List<Episode> episodes = new ArrayList<Episode>();
		List<Episode> specials = new ArrayList<Episode>();

		for (Map<?, ?> it : records) {
			Integer id = getInteger(it, "id");
			String episodeName = titles.stream().map(t -> t.get(id)).filter(Objects::nonNull).findFirst().orElse(getString(it, "name"));

			Integer absoluteNumber = getInteger(it, "absoluteNumber");
			SimpleDate airdate = getStringValue(it, "aired", SimpleDate::parse);

			// default numbering
			Integer episodeNumber = getInteger(it, "number");
			Integer seasonNumber = getInteger(it, "seasonNumber");

			// adjust for forced absolute numbering (if possible)
			if (sortOrder == SortOrder.Absolute && absoluteNumber != null && absoluteNumber > 0) {
				seasonNumber = null;
				episodeNumber = absoluteNumber;
			} else if (sortOrder == SortOrder.AbsoluteAirdate && airdate != null) {
				// use airdate as absolute episode number
				seasonNumber = null;
				episodeNumber = airdate.getYear() * 1_00_00 + airdate.getMonth() * 1_00 + airdate.getDay();
			}

			if (seasonNumber == null || seasonNumber > 0) {
				// handle as normal episode
				episodes.add(new Episode(info.getName(), seasonNumber, episodeNumber, episodeName, absoluteNumber, null, airdate, id, new SeriesInfo(info), getInteger(it, "runtime")));
			} else {
				// handle as special episode
				specials.add(new Episode(info.getName(), null, null, episodeName, absoluteNumber, episodeNumber, airdate, id, new SeriesInfo(info), getInteger(it, "runtime")));
			}
		}

		// episodes my not be ordered by DVD episode number
		episodes.sort(episodeComparator());

		// add specials at the end
		episodes.addAll(specials);

		return new SeriesData(info, episodes);
	}

	public SearchResult lookupByID(int id, Locale locale) throws Exception {
		if (id <= 0) {
			throw new IllegalArgumentException("Illegal TheTVDB ID: " + id);
		}

		SeriesInfo info = getSeriesInfo(new SearchResult(id), locale);
		return new SearchResult(id, info.getName(), info.getAliasNames());
	}

	public SearchResult lookupByIMDbID(int imdbid, Locale locale) throws Exception {
		if (imdbid <= 0) {
			throw new IllegalArgumentException("Illegal IMDbID ID: " + imdbid);
		}

		Object json = requestJson("search/remoteid/" + String.format("tt%07d", imdbid), Cache.ONE_MONTH);
		Optional<Integer> id = streamJsonObjects(json, "data").map(it -> getInteger(getMap(it, "series"), "id")).filter(Objects::nonNull).findFirst();
		return id.isPresent() ? lookupByID(id.get(), locale) : null;
	}

	@Override
	public URI getEpisodeListLink(SearchResult searchResult) {
		return URI.create("https://thetvdb.com/dereferrer/series/" + searchResult.getId());
	}

	@Override
	public List<Artwork> getArtwork(int id, String category, Locale locale) throws Exception {
		Integer type = ARTWORK_TYPES.get(category.toLowerCase(Locale.ROOT));
		if (type == null) {
			throw new IllegalArgumentException("Illegal TheTVDB artwork category: " + category + " " + ARTWORK_TYPES.keySet());
		}

		Map<?, ?> data = getSeriesRecord(id);

		// season artwork refers to the season record
		Map<Integer, Integer> seasons = new HashMap<Integer, Integer>();
		streamJsonObjects(data, "seasons").forEach(it -> {
			Integer seasonId = getInteger(it, "id");
			Integer number = getInteger(it, "number");
			if (seasonId != null && number != null) {
				seasons.put(seasonId, number);
			}
		});

		// preferred language first, then artwork without text, then English
		String language = getLanguageCode(locale).orElse(DEFAULT_LANGUAGE);
		Comparator<Map<?, ?>> languageOrder = Comparator.comparingInt(it -> {
			String l = getString(it, "language");
			return language.equals(l) ? 0 : l == null ? 1 : DEFAULT_LANGUAGE.equals(l) ? 2 : 3;
		});
		Comparator<Map<?, ?>> scoreOrder = Comparator.comparing(it -> Optional.ofNullable(getDouble(it, "score")).orElse(0d), Comparator.reverseOrder());

		return streamJsonObjects(data, "artworks").filter(it -> type.equals(getInteger(it, "type"))).sorted(languageOrder.thenComparing(scoreOrder)).map(it -> {
			URL url = getStringValue(it, "image", this::resolveImage);
			Integer season = seasons.get(getInteger(it, "seasonId"));
			Integer width = getInteger(it, "width");
			Integer height = getInteger(it, "height");
			String resolution = width != null && height != null && width > 0 && height > 0 ? width + "x" + height : null;
			String text = type == 1 && Boolean.parseBoolean(getString(it, "includesText")) ? "graphical" : null;
			String l = getString(it, "language");

			return new Artwork(Stream.of(category, season, resolution, text), url, l == null ? null : language.equals(l) ? locale : new Locale(l), getDouble(it, "score"));
		}).filter(it -> it.getUrl() != null).collect(toList());
	}

	protected URL resolveImage(String path) {
		if (path == null || path.isEmpty()) {
			return null;
		}

		// API v4 returns absolute image URLs, except for some paths relative to the artwork host
		try {
			return new URL(getBannerMirror(), path);
		} catch (Exception e) {
			throw new IllegalArgumentException(path, e);
		}
	}

	public List<String> getLanguages() throws Exception {
		Object response = requestJson("languages", Cache.ONE_MONTH);
		return streamJsonObjects(response, "data").map(it -> getString(it, "id")).collect(toList());
	}

	public List<Person> getActors(int seriesId, Locale locale) throws Exception {
		// e.g. [name:Jayne Cobb, personName:Adam Baldwin, peopleType:Actor, sort:3, image:..., personImgURL:...]
		return streamJsonObjects(getSeriesRecord(seriesId), "characters").filter(it -> Person.ACTOR.equals(getString(it, "peopleType"))).map(it -> {
			String name = getString(it, "personName");
			String character = getString(it, "name");
			Integer order = getInteger(it, "sort");
			URL image = Optional.ofNullable(getString(it, "image")).or(() -> Optional.ofNullable(getString(it, "personImgURL"))).map(this::resolveImage).orElse(null);

			return new Person(name, character, Person.ACTOR, null, order, image);
		}).filter(it -> it.getName() != null).sorted(Person.CREDIT_ORDER).collect(toList());
	}

	public EpisodeInfo getEpisodeInfo(int id, Locale locale) throws Exception {
		Object response = requestJson("episodes/" + id + "/extended?meta=translations", Cache.ONE_MONTH);
		Map<?, ?> data = getMap(response, "data");

		Integer seriesId = getInteger(data, "seriesId");
		String overview = getTranslation(data, "overviewTranslations", "overview", locale);

		// e.g. Director, Writer, Guest Star, Actor, Producer
		List<Person> people = streamJsonObjects(data, "characters").map(it -> {
			String name = getString(it, "personName");
			String job = getString(it, "peopleType");
			return name == null || job == null ? null : new Person(name, getString(it, "name"), job, null, getInteger(it, "sort"), null);
		}).filter(Objects::nonNull).collect(toList());

		String title = getTranslation(data, "nameTranslations", "name", locale);
		Integer season = getInteger(data, "seasonNumber");
		Integer episode = getInteger(data, "number");
		SimpleDate airdate = getStringValue(data, "aired", SimpleDate::parse);
		URL image = getStringValue(data, "image", this::resolveImage);

		return new EpisodeInfo(this, locale, seriesId, id, people, overview, title, season, episode, airdate, image);
	}

}
