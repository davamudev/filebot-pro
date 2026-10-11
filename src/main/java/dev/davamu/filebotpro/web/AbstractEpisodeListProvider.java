package dev.davamu.filebotpro.web;

import static java.util.Arrays.*;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import dev.davamu.filebotpro.Cache;
import dev.davamu.filebotpro.Cache.TypedCache;
import dev.davamu.filebotpro.CacheType;

public abstract class AbstractEpisodeListProvider implements EpisodeListProvider {

	protected abstract List<SearchResult> fetchSearchResult(String query, Locale locale) throws Exception;

	protected abstract SeriesData fetchSeriesData(SearchResult searchResult, SortOrder sortOrder, Locale locale) throws Exception;

	@Override
	public List<SearchResult> search(String query, Locale language) throws Exception {
		return getSearchCache(language).computeIfAbsent(query, it -> {
			return fetchSearchResult(query, language);
		});
	}

	@Override
	public SortOrder vetoRequestParameter(SortOrder order) {
		return order == null ? SortOrder.Airdate : order;
	}

	@Override
	public Locale vetoRequestParameter(Locale language) {
		return language == null || language.getLanguage().isEmpty() ? Locale.ENGLISH : language;
	}

	@Override
	public List<Episode> getEpisodeList(SearchResult searchResult, SortOrder sortOrder, Locale language) throws Exception {
		return getSeriesData(searchResult, sortOrder, language).getEpisodeList();
	}

	@Override
	public List<Episode> getEpisodeList(int id, SortOrder order, Locale language) throws Exception {
		return getEpisodeList(new SearchResult(id), order, language);
	}

	@Override
	public SeriesInfo getSeriesInfo(SearchResult searchResult, Locale language) throws Exception {
		return getSeriesData(searchResult, null, language).getSeriesInfo();
	}

	@Override
	public SeriesInfo getSeriesInfo(int id, Locale language) throws Exception {
		return getSeriesInfo(new SearchResult(id), language);
	}

	protected SeriesData getSeriesData(SearchResult searchResult, SortOrder order, Locale language) throws Exception {
		// override preferences if requested parameters are not supported
		SortOrder requestOrder = vetoRequestParameter(order);
		Locale requestLanguage = vetoRequestParameter(language);

		TypedCache<SeriesData> cache = getDataCache(requestOrder, requestLanguage);
		SeriesData data = cache.get(searchResult.getId());
		if (data == null) {
			data = fetchSeriesData(searchResult, requestOrder, requestLanguage);
			cache.put(searchResult.getId(), data, getTimeToLive(data));
		}
		return data;
	}

	/**
	 * Adaptive cache update: episode lists of series that have ended rarely change, so they are kept for a week instead
	 * of a day
	 */
	protected Duration getTimeToLive(SeriesData data) {
		String status = data.seriesInfo == null ? null : data.seriesInfo.getStatus();
		if (status != null && ENDED_STATUS.matcher(status).find()) {
			return ENDED_SERIES_TIME_TO_LIVE;
		}
		return null;
	}

	private static final Pattern ENDED_STATUS = Pattern.compile("^(Ended|Canceled|Cancelled)$", Pattern.CASE_INSENSITIVE);
	private static final Duration ENDED_SERIES_TIME_TO_LIVE = Duration.ofDays(7);

	protected Cache getCache(String section) {
		return Cache.getCache(getName() + "_" + section, CacheType.Daily);
	}

	protected TypedCache<List<SearchResult>> getSearchCache(Locale language) {
		return getCache("search_" + language).castList(SearchResult.class);
	}

	protected TypedCache<SeriesData> getDataCache(SortOrder order, Locale language) {
		return getCache("data_" + order.ordinal() + "_" + language).cast(SeriesData.class);
	}

	protected static class SeriesData implements Serializable {

		public SeriesInfo seriesInfo;
		public Episode[] episodeList;

		public SeriesData() {
			// used by serializer
		}

		public SeriesData(SeriesInfo seriesInfo, List<Episode> episodeList) {
			this.seriesInfo = seriesInfo;
			this.episodeList = episodeList.toArray(new Episode[episodeList.size()]);
		}

		public SeriesInfo getSeriesInfo() {
			return seriesInfo.clone();
		}

		public List<Episode> getEpisodeList() {
			return asList(episodeList.clone());
		}

	}

}
