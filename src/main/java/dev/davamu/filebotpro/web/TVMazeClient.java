package dev.davamu.filebotpro.web;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.util.JsonUtilities.*;
import static dev.davamu.filebotpro.web.WebRequest.*;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.Icon;

import dev.davamu.filebotpro.Cache;
import dev.davamu.filebotpro.CacheType;
import dev.davamu.filebotpro.ResourceManager;

public class TVMazeClient extends AbstractEpisodeListProvider {

	@Override
	public String getIdentifier() {
		return "TVmaze";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("search.tvmaze");
	}

	@Override
	public boolean hasSeasonSupport() {
		return true;
	}

	@Override
	public SortOrder vetoRequestParameter(SortOrder order) {
		return SortOrder.Airdate;
	}

	@Override
	public Locale vetoRequestParameter(Locale language) {
		return Locale.ENGLISH;
	}

	@Override
	public List<SearchResult> fetchSearchResult(String query, Locale locale) throws Exception {
		// e.g. http://api.tvmaze.com/search/shows?q=girls
		Object response = request("search/shows?q=" + encode(query, true));

		// TVmaze does not find series by name and year, e.g. Doctor Who 2005 or Doctor Who (2005)
		Matcher year = QUERY_YEAR.matcher(query);
		if (streamJsonObjects(response).count() == 0 && year.find()) {
			response = request("search/shows?q=" + encode(query.substring(0, year.start()).trim(), true));
		}

		// FUTURE WORK: consider adding TVmaze aka titles for each result, e.g. http://api.tvmaze.com/shows/1/akas
		return streamJsonObjects(response).map(it -> {
			Object show = it.get("show");
			Integer id = getInteger(show, "id");
			String name = getString(show, "name");

			// add Name (Year) alias, so that series with the same name can be told apart by year
			SimpleDate premiered = getStringValue(show, "premiered", SimpleDate::parse);
			String nameWithYear = premiered == null ? null : String.format("%s (%d)", name, premiered.getYear());

			return nameWithYear == null || name.endsWith(")") ? new SearchResult(id, name) : new SearchResult(id, name, singletonList(nameWithYear));
		}).collect(toList());
	}

	// e.g. Doctor Who (2005) or Doctor Who 2005
	private static final Pattern QUERY_YEAR = Pattern.compile("\\(?\\b(?:19|20)\\d{2}\\b\\)?\\s*$");

	protected SeriesInfo fetchSeriesInfo(SearchResult show, SortOrder sortOrder, Locale locale) throws Exception {
		// e.g. http://api.tvmaze.com/shows/1
		Object response = request("shows/" + show.getId());

		String status = getStringValue(response, "status", String::new);
		SimpleDate premiered = getStringValue(response, "premiered", SimpleDate::parse);
		Integer runtime = getStringValue(response, "runtime", Integer::parseInt);
		if (runtime == null) {
			// runtime is null for shows with varying episode runtimes
			runtime = getStringValue(response, "averageRuntime", Integer::parseInt);
		}
		Object[] genres = getArray(response, "genres");
		Double rating = getStringValue(getMap(response, "rating"), "average", Double::parseDouble);

		SeriesInfo seriesInfo = new SeriesInfo(this, sortOrder, locale, show.getId(), SeriesInfo.TYPE_SERIES);
		seriesInfo.setName(show.getName() != null ? show.getName() : getString(response, "name")); // lookup by id has no name
		seriesInfo.setAliasNames(stream(show.getAliasNames()).filter(a -> !a.startsWith(seriesInfo.getName() + " (")).toArray(String[]::new)); // exclude Name (Year) search alias
		seriesInfo.setStatus(status);
		seriesInfo.setRuntime(runtime);
		seriesInfo.setStartDate(premiered);
		seriesInfo.setRating(rating);
		seriesInfo.setGenres(stream(genres).map(Objects::toString).collect(toList()));

		return seriesInfo;
	}

	@Override
	protected SeriesData fetchSeriesData(SearchResult searchResult, SortOrder sortOrder, Locale locale) throws Exception {
		SeriesInfo seriesInfo = fetchSeriesInfo(searchResult, sortOrder, locale);

		// e.g. http://api.tvmaze.com/shows/1/episodes
		Object response = request("shows/" + seriesInfo.getId() + "/episodes");

		List<Episode> episodes = streamJsonObjects(response).map(episode -> {
			Integer id = getInteger(episode, "id");
			Integer seasonNumber = getInteger(episode, "season");
			Integer episodeNumber = getInteger(episode, "number");
			String episodeTitle = getString(episode, "name");
			SimpleDate airdate = getStringValue(episode, "airdate", SimpleDate::parse);

			return new Episode(seriesInfo.getName(), seasonNumber, episodeNumber, episodeTitle, null, null, airdate, id, seriesInfo, getInteger(episode, "runtime"));
		}).collect(toList());

		return new SeriesData(seriesInfo, episodes);
	}

	public SearchResult lookupByTheTVDBID(int id) throws Exception {
		if (id <= 0) {
			throw new IllegalArgumentException("Illegal TheTVDB ID: " + id);
		}
		return lookup("thetvdb=" + id);
	}

	public SearchResult lookupByIMDbID(int imdbid) throws Exception {
		if (imdbid <= 0) {
			throw new IllegalArgumentException("Illegal IMDbID ID: " + imdbid);
		}
		return lookup("imdb=" + String.format("tt%07d", imdbid));
	}

	protected SearchResult lookup(String query) throws Exception {
		// e.g. https://api.tvmaze.com/lookup/shows?thetvdb=81189
		Object response = request("lookup/shows?" + query);
		Integer id = getInteger(response, "id");
		return id == null ? null : new SearchResult(id, getString(response, "name"));
	}

	protected Object request(String resource) throws Exception {
		Cache cache = Cache.getCache(getName(), CacheType.Monthly);
		return cache.json(resource, s -> getResource(resource)).get();
	}

	protected URL getResource(String resource) throws Exception {
		return new URL("https://api.tvmaze.com/" + resource);
	}

	@Override
	public URI getEpisodeListLink(SearchResult searchResult) {
		return URI.create("https://www.tvmaze.com/shows/" + searchResult.getId());
	}

}
