package dev.davamu.filebotpro.web;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.Comparator.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.util.JsonUtilities.*;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import javax.swing.Icon;

public class TMDbTVClient extends AbstractEpisodeListProvider implements ArtworkProvider {

	// series details with age ratings and translations (same request for series data and overview so the response is cached only once)
	private static final Map<String, Object> SERIES_DETAILS = singletonMap("append_to_response", "content_ratings,translations");

	private final TMDbClient tmdb;

	public TMDbTVClient(TMDbClient tmdb) {
		this.tmdb = tmdb;
	}

	@Override
	public String getIdentifier() {
		return "TheMovieDB::TV";
	}

	@Override
	public String getName() {
		return tmdb.getName();
	}

	@Override
	public Icon getIcon() {
		return tmdb.getIcon();
	}

	@Override
	public boolean hasSeasonSupport() {
		return true;
	}

	@Override
	public SortOrder vetoRequestParameter(SortOrder order) {
		// alternative orders are episode groups
		return getEpisodeGroupType(order) != null ? order : SortOrder.Airdate;
	}

	/**
	 * TheMovieDB episode group type of the given order, see https://developer.themoviedb.org/reference/tv-series-episode-groups
	 */
	protected static Integer getEpisodeGroupType(SortOrder order) {
		if (order == null) {
			return null;
		}

		switch (order) {
		case Absolute:
			return 2;
		case DVD:
			return 3;
		case Digital:
			return 4;
		case StoryArc:
			return 5;
		case Production:
			return 6;
		default:
			return null;
		}
	}

	/**
	 * Id of the episode group with the most episodes of the given type, or null if the series has no such episode group
	 */
	protected String getEpisodeGroup(int seriesId, int type, Locale locale) throws Exception {
		// http://api.themoviedb.org/3/tv/id/episode_groups
		Object response = tmdb.request("tv/" + seriesId + "/episode_groups", emptyMap(), locale);

		return streamJsonObjects(response, "results").filter(it -> Objects.equals(getInteger(it, "type"), type)).max(comparing(it -> {
			Integer count = getInteger(it, "episode_count");
			return count == null ? 0 : count;
		})).map(it -> getString(it, "id")).orElse(null);
	}

	/**
	 * Episodes in the order of the given episode group, with groups as seasons (group 0 is Specials) or as one absolute list
	 */
	protected List<Episode> getEpisodeGroupEpisodes(String groupId, boolean absolute, String name, SeriesInfo info, Map<Integer, String> seasonNames, Locale locale) throws Exception {
		// http://api.themoviedb.org/3/tv/episode_group/id
		Object response = tmdb.request("tv/episode_group/" + groupId, emptyMap(), locale);

		List<Map<?, ?>> groups = streamJsonObjects(response, "groups").sorted(comparing(it -> {
			Integer order = getInteger(it, "order");
			return order == null ? 0 : order;
		})).collect(toList());

		List<Episode> episodes = new ArrayList<Episode>();
		List<Episode> specials = new ArrayList<Episode>();

		for (Map<?, ?> group : groups) {
			Integer season = getInteger(group, "order");
			String groupName = getString(group, "name");
			boolean special = season == null || season == 0;

			if (!special && !absolute && groupName != null && !groupName.isBlank()) {
				seasonNames.put(season, groupName);
			}

			List<Map<?, ?>> groupEpisodes = streamJsonObjects(group, "episodes").sorted(comparing(it -> {
				Integer order = getInteger(it, "order");
				return order == null ? 0 : order;
			})).collect(toList());

			for (Map<?, ?> episode : groupEpisodes) {
				Integer id = getInteger(episode, "id");
				String episodeTitle = getString(episode, "name");
				SimpleDate airdate = getStringValue(episode, "air_date", SimpleDate::parse);

				if (special) {
					specials.add(new Episode(name, null, null, episodeTitle, null, specials.size() + 1, airdate, id, info, getInteger(episode, "runtime")));
				} else if (absolute) {
					episodes.add(new Episode(name, null, episodes.size() + 1, episodeTitle, episodes.size() + 1, null, airdate, id, info, getInteger(episode, "runtime")));
				} else {
					int number = (int) episodes.stream().filter(e -> season.equals(e.getSeason())).count() + 1;
					episodes.add(new Episode(name, season, number, episodeTitle, episodes.size() + 1, null, airdate, id, info, getInteger(episode, "runtime")));
				}
			}
		}

		// add specials at the end
		episodes.addAll(specials);

		if (!isEnglish(locale)) {
			Object english = tmdb.request("tv/episode_group/" + groupId, emptyMap(), Locale.US);
			setEnglishTitles(episodes, streamJsonObjects(english, "groups").flatMap(group -> streamJsonObjects(group, "episodes")));
		}

		return episodes;
	}

	protected static boolean isEnglish(Locale locale) {
		return Locale.ENGLISH.getLanguage().equals(locale.getLanguage());
	}

	/**
	 * Add the English titles of localized episodes, so files named after them (e.g. Show - 1x07 - The Breaking Point) still match by title
	 */
	protected void setEnglishTitles(List<Episode> episodes, Stream<Map<?, ?>> englishEpisodes) {
		Map<Integer, String> titles = new LinkedHashMap<Integer, String>();
		englishEpisodes.forEach(it -> {
			Integer id = getInteger(it, "id");
			String title = getString(it, "name");
			if (id != null && title != null && !title.isBlank()) {
				titles.putIfAbsent(id, title);
			}
		});

		for (Episode e : episodes) {
			String title = titles.get(e.getId());
			if (title != null && !title.equals(e.getTitle())) {
				e.setEnglishTitle(title);
			}
		}
	}

	@Override
	public URI getEpisodeListLink(SearchResult searchResult) {
		return URI.create("https://www.themoviedb.org/tv/" + searchResult.getId());
	}

	@Override
	protected List<SearchResult> fetchSearchResult(String query, Locale locale) throws Exception {
		// query by name with year filter if possible
		Matcher nameYear = tmdb.getNameYearMatcher(query);
		if (nameYear.matches()) {
			return searchTV(nameYear.group(1).trim(), Integer.parseInt(nameYear.group(2)), locale, true);
		} else {
			return searchTV(query.trim(), -1, locale, true);
		}
	}

	public List<SearchResult> searchTV(String seriesName, int startYear, Locale locale, boolean extendedInfo) throws Exception {
		Map<String, Object> query = new LinkedHashMap<String, Object>(2);
		query.put("query", seriesName);
		if (startYear > 0) {
			query.put("first_air_date_year", startYear);
		}
		Object response = tmdb.request("search/tv", query, locale);

		return streamJsonObjects(response, "results").map(it -> {
			Integer id = getInteger(it, "id");
			String name = getString(it, "name");
			String originalName = getString(it, "original_name");

			if (name == null) {
				name = originalName;
			}

			if (id == null || name == null) {
				return null;
			}

			tmdb.putPoster("tv/" + id, getString(it, "poster_path"));
			String[] alternativeTitles = tmdb.getAlternativeTitles("tv/" + id, "results", name, originalName, extendedInfo);

			return new SearchResult(id, name, alternativeTitles);
		}).filter(Objects::nonNull).collect(toList());
	}

	@Override
	protected SeriesData fetchSeriesData(SearchResult series, SortOrder sortOrder, Locale locale) throws Exception {
		// http://api.themoviedb.org/3/tv/id
		Object tv = tmdb.request("tv/" + series.getId(), SERIES_DETAILS, locale);

		// retrieve localized series name from response
		String originalName = getString(tv, "original_name");
		String originalLanguage = getString(tv, "original_language");
		String name = getLocalizedName(tv, originalName, originalLanguage, locale);

		// alternative orders via episode groups, or default to airdate order if the series has no such episode group
		Integer groupType = getEpisodeGroupType(sortOrder);
		String groupId = groupType == null ? null : getEpisodeGroup(series.getId(), groupType, locale);
		if (groupId == null && sortOrder != SortOrder.Absolute) {
			sortOrder = SortOrder.Airdate;
		}

		SeriesInfo info = new SeriesInfo(this, sortOrder, locale, series.getId(), SeriesInfo.TYPE_SERIES);
		info.setName(name);
		info.setAliasNames(Stream.concat(Stream.of(series.getName(), originalName), Stream.of(series.getAliasNames())).filter(Objects::nonNull).filter(s -> !s.equals(name)).distinct().toArray(String[]::new));
		info.setStatus(getString(tv, "status"));
		info.setOriginalLanguage(originalLanguage);
		info.setStartDate(getStringValue(tv, "first_air_date", SimpleDate::parse));
		info.setRating(getStringValue(tv, "vote_average", Double::parseDouble));
		info.setRatingCount(getStringValue(tv, "vote_count", Integer::parseInt));
		info.setRuntime(stream(getArray(tv, "episode_run_time")).map(Object::toString).map(Integer::parseInt).findFirst().orElse(null));
		info.setGenres(streamJsonObjects(tv, "genres").map(it -> getString(it, "name")).collect(toList()));
		info.setNetwork(streamJsonObjects(tv, "networks").map(it -> getString(it, "name")).findFirst().orElse(null));
		info.setCertification(getCertification(tv, locale));
		info.setCertifications(getCertifications(tv));
		info.setCountries(Stream.concat(stream(getArray(tv, "origin_country")).map(Object::toString), streamJsonObjects(tv, "production_countries").map(it -> getString(it, "iso_3166_1"))).filter(Objects::nonNull).distinct().collect(toList()));

		Map<Integer, String> seasonNames = new TreeMap<Integer, String>();
		streamJsonObjects(tv, "seasons").forEach(it -> {
			Integer number = getInteger(it, "season_number");
			String seasonName = getString(it, "name");
			if (number != null && seasonName != null && !seasonName.isBlank()) {
				seasonNames.put(number, seasonName);
			}
		});
		if (groupId != null) {
			Map<Integer, String> groupNames = new TreeMap<Integer, String>();
			List<Episode> episodes = getEpisodeGroupEpisodes(groupId, sortOrder == SortOrder.Absolute, name, info, groupNames, locale);
			info.setSeasonNames(groupNames);
			return new SeriesData(info, episodes);
		}

		info.setSeasonNames(seasonNames);

		int[] seasons = streamJsonObjects(tv, "seasons").mapToInt(it -> getInteger(it, "season_number")).toArray();
		List<Episode> episodes = new ArrayList<Episode>();
		List<Episode> specials = new ArrayList<Episode>();
		boolean absolute = sortOrder == SortOrder.Absolute;

		for (int s : seasons) {
			// http://api.themoviedb.org/3/tv/id/season/season_number
			Object season = tmdb.request("tv/" + series.getId() + "/season/" + s, emptyMap(), locale);

			streamJsonObjects(season, "episodes").forEach(episode -> {
				Integer id = getInteger(episode, "id");
				Integer episodeNumber = getInteger(episode, "episode_number");
				Integer seasonNumber = getInteger(episode, "season_number");
				String episodeTitle = getString(episode, "name");
				SimpleDate airdate = getStringValue(episode, "air_date", SimpleDate::parse);

				Integer absoluteNumber = episodes.size() + 1;

				if (s > 0 && absolute) {
					// absolute order without absolute episode group
					episodes.add(new Episode(name, null, absoluteNumber, episodeTitle, absoluteNumber, null, airdate, id, info, getInteger(episode, "runtime")));
				} else if (s > 0) {
					episodes.add(new Episode(name, seasonNumber, episodeNumber, episodeTitle, absoluteNumber, null, airdate, id, info, getInteger(episode, "runtime")));
				} else {
					specials.add(new Episode(name, null, null, episodeTitle, null, episodeNumber, airdate, id, info, getInteger(episode, "runtime")));
				}
			});
		}

		// add specials at the end
		episodes.addAll(specials);

		if (!isEnglish(locale)) {
			List<Map<?, ?>> english = new ArrayList<Map<?, ?>>();
			for (int s : seasons) {
				streamJsonObjects(tmdb.request("tv/" + series.getId() + "/season/" + s, emptyMap(), Locale.US), "episodes").forEach(english::add);
			}
			setEnglishTitles(episodes, english.stream());
		}

		return new SeriesData(info, episodes);
	}

	/**
	 * TheMovieDB returns the original name (e.g. Japanese) as name if there is no translation for the requested language, so fall back to the English
	 * translation in that case
	 */
	protected String getLocalizedName(Object tv, String originalName, String originalLanguage, Locale locale) {
		String name = getString(tv, "name");
		if (name != null && !name.equals(originalName)) {
			return name;
		}

		// original name is the requested name
		if (originalLanguage == null || originalLanguage.equals(locale.getLanguage())) {
			return name != null ? name : originalName;
		}

		List<Map<?, ?>> translations = streamJsonObjects(getMap(tv, "translations"), "translations").collect(toList());
		return Stream.of(locale, Locale.US, Locale.ENGLISH).filter(l -> !l.getLanguage().isEmpty()).flatMap(l -> {
			// prefer exact country match
			return translations.stream().filter(it -> l.getLanguage().equals(getString(it, "iso_639_1"))).sorted(comparing(it -> !l.getCountry().equals(getString(it, "iso_3166_1"))));
		}).map(it -> getString(getMap(it, "data"), "name")).filter(s -> s != null && !s.isBlank()).findFirst().orElse(name != null ? name : originalName);
	}

	/**
	 * Age rating for the country of the given locale, or for the US by default
	 */
	protected String getCertification(Object tv, Locale locale) {
		List<Map<?, ?>> ratings = streamJsonObjects(getMap(tv, "content_ratings"), "results").collect(toList());
		return Stream.of(locale.getCountry(), "US").filter(c -> c != null && c.length() > 0).map(c -> {
			return ratings.stream().filter(it -> c.equalsIgnoreCase(getString(it, "iso_3166_1"))).map(it -> getString(it, "rating")).filter(Objects::nonNull).findFirst().orElse(null);
		}).filter(Objects::nonNull).findFirst().orElse(null);
	}

	/**
	 * Age ratings of all countries, e.g. US: TV-14
	 */
	protected Map<String, String> getCertifications(Object tv) {
		Map<String, String> certifications = new LinkedHashMap<String, String>();
		streamJsonObjects(getMap(tv, "content_ratings"), "results").forEach(it -> {
			String country = getString(it, "iso_3166_1");
			String rating = getString(it, "rating");
			if (country != null && rating != null && !rating.isBlank()) {
				certifications.putIfAbsent(country, rating);
			}
		});
		return certifications;
	}

	public String getOverview(int id, Locale locale) throws Exception {
		return getString(tmdb.request("tv/" + id, SERIES_DETAILS, locale), "overview");
	}

	/**
	 * TheTVDB series id of the given TheMovieDB series (or null if TheMovieDB does not know it)
	 */
	public Integer getTheTVDBId(int id) throws Exception {
		return getInteger(tmdb.request("tv/" + id + "/external_ids", emptyMap(), Locale.ROOT), "tvdb_id");
	}

	/**
	 * TheTVDB episode id of the given TheMovieDB episode (default season and episode numbers, season 0 for specials)
	 */
	public Integer getTheTVDBEpisodeId(int id, int season, int episode) throws Exception {
		return getInteger(tmdb.request("tv/" + id + "/season/" + season + "/episode/" + episode + "/external_ids", emptyMap(), Locale.ROOT), "tvdb_id");
	}

	/**
	 * TheMovieDB series id of the given TheTVDB series (or null if TheMovieDB does not know it)
	 */
	public Integer findByTheTVDBId(int id) throws Exception {
		return streamJsonObjects(findByExternalId(id, "tvdb_id"), "tv_results").map(it -> getInteger(it, "id")).filter(Objects::nonNull).findFirst().orElse(null);
	}

	/**
	 * TheMovieDB series id, season number and episode number of the given TheTVDB episode (or null if TheMovieDB does not know it)
	 */
	public int[] findEpisodeByTheTVDBId(int id) throws Exception {
		return streamJsonObjects(findByExternalId(id, "tvdb_id"), "tv_episode_results").map(it -> {
			Integer series = getInteger(it, "show_id");
			Integer season = getInteger(it, "season_number");
			Integer episode = getInteger(it, "episode_number");
			return series == null || season == null || episode == null ? null : new int[] { series, season, episode };
		}).filter(Objects::nonNull).findFirst().orElse(null);
	}

	protected Object findByExternalId(int id, String source) throws Exception {
		// e.g. http://api.themoviedb.org/3/find/349232?external_source=tvdb_id
		return tmdb.request("find/" + id, singletonMap("external_source", (Object) source), Locale.ROOT);
	}

	/**
	 * Main cast and crew of the series
	 */
	public List<Person> getCrew(int id, Locale locale) throws Exception {
		return getPeople(tmdb.request("tv/" + id + "/credits", emptyMap(), locale), "cast", "crew");
	}

	/**
	 * Series artwork (posters, backdrops, logos) or season posters (season/1)
	 */
	@Override
	public List<Artwork> getArtwork(int id, String category, Locale locale) throws Exception {
		String resource = "tv/" + id + "/images";
		String section = category;

		if (category.startsWith("season/")) {
			resource = "tv/" + id + "/season/" + category.substring(7) + "/images";
			section = "posters";
		}

		return streamJsonObjects(tmdb.request(resource, emptyMap(), Locale.ROOT), section).map(it -> {
			URL image = getStringValue(it, "file_path", tmdb::resolveImage);
			String width = getString(it, "width");
			String height = getString(it, "height");
			Locale language = getStringValue(it, "iso_639_1", Locale::new);
			Double rating = getStringValue(it, "vote_average", Double::parseDouble);

			return new Artwork(Stream.of(category, String.join("x", width, height)), image, language, rating);
		}).sorted(Artwork.RATING_ORDER).collect(toList());
	}

	/**
	 * Episode details (title, overview, air date, still image, crew and guest stars)
	 */
	public EpisodeInfo getEpisodeInfo(int seriesId, Episode episode, Locale locale) throws Exception {
		int season = episode.getSpecial() != null ? 0 : episode.getSeason();
		int number = episode.getSpecial() != null ? episode.getSpecial() : episode.getEpisode();

		// http://api.themoviedb.org/3/tv/id/season/season_number/episode/episode_number
		Object data = tmdb.request("tv/" + seriesId + "/season/" + season + "/episode/" + number, emptyMap(), locale);

		Integer id = getInteger(data, "id");
		String title = getString(data, "name");
		String overview = getString(data, "overview");
		SimpleDate airdate = getStringValue(data, "air_date", SimpleDate::parse);
		URL image = getStringValue(data, "still_path", tmdb::resolveImage);

		List<Person> people = getPeople(data, "crew", "guest_stars");

		return new EpisodeInfo(this, locale, seriesId, id, people, overview, title, getInteger(data, "season_number"), getInteger(data, "episode_number"), airdate, image);
	}

	protected List<Person> getPeople(Object data, String... sections) {
		return Stream.of(sections).flatMap(section -> streamJsonObjects(data, section)).map(it -> {
			String name = getString(it, "name");
			String character = getString(it, "character");
			String job = character != null ? Person.ACTOR : getString(it, "job");
			URL image = getStringValue(it, "profile_path", tmdb::resolveImage);
			return name == null ? null : new Person(name, character, job, getString(it, "department"), getInteger(it, "order"), image);
		}).filter(Objects::nonNull).sorted(Person.CREDIT_ORDER).collect(toList());
	}

}
