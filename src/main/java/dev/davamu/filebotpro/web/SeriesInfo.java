package dev.davamu.filebotpro.web;

import static java.util.Arrays.*;
import static java.util.Collections.*;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class SeriesInfo implements Serializable {

	// keep compatible with cached data of earlier versions (new fields are simply null)
	private static final long serialVersionUID = 5143049022897641236L;

	public static final String TYPE_SERIES = "TV Series";
	public static final String TYPE_ANIME = "Anime";

	// request parameters
	protected String database;
	protected String order;
	protected String language;

	// original language of the series (e.g. ja for anime)
	protected String originalLanguage;

	// series classification
	protected String type;

	// series parameters
	protected Integer id;
	protected String name;
	protected String[] aliasNames;
	protected String certification;
	protected SimpleDate startDate;
	protected String[] genres;
	protected String network;
	protected Double rating;
	protected Integer ratingCount;
	protected Integer runtime;
	protected String status;

	// production countries (ISO 3166-1 alpha-2), age ratings per country and season names
	protected String[] countries;
	protected LinkedHashMap<String, String> certifications;
	protected TreeMap<Integer, String> seasonNames;

	public SeriesInfo() {
		// used by deserializer
	}

	public SeriesInfo(SeriesInfo other) {
		this.database = other.database;
		this.order = other.order;
		this.language = other.language;
		this.originalLanguage = other.originalLanguage;
		this.type = other.type;
		this.id = other.id;
		this.name = other.name;
		this.aliasNames = other.aliasNames == null ? null : other.aliasNames.clone();
		this.certification = other.certification;
		this.startDate = other.startDate == null ? null : other.startDate.clone();
		this.genres = other.genres == null ? null : other.genres.clone();
		this.network = other.network;
		this.rating = other.rating;
		this.ratingCount = other.ratingCount;
		this.runtime = other.runtime;
		this.status = other.status;
		this.countries = other.countries == null ? null : other.countries.clone();
		this.certifications = other.certifications == null ? null : new LinkedHashMap<String, String>(other.certifications);
		this.seasonNames = other.seasonNames == null ? null : new TreeMap<Integer, String>(other.seasonNames);
	}

	public SeriesInfo(Datasource database, SortOrder order, Locale language, Integer id, String type) {
		this.database = database == null ? null : database.getIdentifier();
		this.order = order == null ? null : order.name();
		this.language = language == null ? null : language.getLanguage();

		this.id = id;
		this.type = type;
	}

	public void setDatabase(String database) {
		this.database = database;
	}

	public String getDatabase() {
		return database;
	}

	public void setOrder(String order) {
		this.order = order;
	}

	public String getOrder() {
		return order;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getOriginalLanguage() {
		return originalLanguage;
	}

	public void setOriginalLanguage(String originalLanguage) {
		this.originalLanguage = originalLanguage;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<String> getAliasNames() {
		return aliasNames == null ? emptyList() : asList(aliasNames.clone());
	}

	public void setAliasNames(String... aliasNames) {
		this.aliasNames = aliasNames.clone();
	}

	public String getCertification() {
		return certification;
	}

	public void setCertification(String certification) {
		this.certification = certification;
	}

	public SimpleDate getStartDate() {
		return startDate;
	}

	public void setStartDate(SimpleDate startDate) {
		this.startDate = startDate;
	}

	public List<String> getGenres() {
		return genres == null ? emptyList() : asList(genres.clone());
	}

	public void setGenres(List<String> genres) {
		this.genres = genres.toArray(new String[0]);
	}

	public String getNetwork() {
		return network;
	}

	public void setNetwork(String network) {
		this.network = network;
	}

	public Double getRating() {
		return rating;
	}

	public void setRating(Double rating) {
		this.rating = rating;
	}

	public Integer getRatingCount() {
		return ratingCount;
	}

	public void setRatingCount(Integer ratingCount) {
		this.ratingCount = ratingCount;
	}

	public Integer getRuntime() {
		return runtime;
	}

	public void setRuntime(Integer runtime) {
		this.runtime = runtime;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public List<String> getCountries() {
		return countries == null ? emptyList() : asList(countries.clone());
	}

	public void setCountries(List<String> countries) {
		this.countries = countries.toArray(new String[0]);
	}

	public Map<String, String> getCertifications() {
		return certifications == null ? emptyMap() : unmodifiableMap(certifications); // e.g. ['US': TV-14]
	}

	public void setCertifications(Map<String, String> certifications) {
		this.certifications = new LinkedHashMap<String, String>(certifications);
	}

	public Map<Integer, String> getSeasonNames() {
		return seasonNames == null ? emptyMap() : unmodifiableMap(seasonNames); // e.g. [1: The Beginning]
	}

	public void setSeasonNames(Map<Integer, String> seasonNames) {
		this.seasonNames = new TreeMap<Integer, String>(seasonNames);
	}

	@Override
	public int hashCode() {
		return id == null ? 0 : id;
	}

	@Override
	public boolean equals(Object object) {
		if (object instanceof SeriesInfo) {
			SeriesInfo other = (SeriesInfo) object;
			return Objects.equals(id, other.id) && Objects.equals(database, other.database);
		}
		return false;
	}

	@Override
	public SeriesInfo clone() {
		return new SeriesInfo(this);
	}

	@Override
	public String toString() {
		return database + "::" + id;
	}

}
