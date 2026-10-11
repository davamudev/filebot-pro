package dev.davamu.filebotpro.web;

import static dev.davamu.filebotpro.util.JsonUtilities.*;

import java.io.File;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;

import dev.davamu.filebotpro.WebServices;
import dev.davamu.filebotpro.util.FileUtilities;

/**
 * Describes one subtitle file on OpenSubtitles.com (a subtitle with multiple CDs yields one descriptor per file).
 *
 * @see OpenSubtitlesClient
 */
public class OpenSubtitlesSubtitleDescriptor implements SubtitleDescriptor, Serializable {

	private String subtitleId;
	private int fileId;
	private String fileName;
	private String release;
	private String language;
	private String languageName;

	private double fps;
	private int cdNumber;
	private int cdCount;
	private boolean hearingImpaired;
	private boolean hashMatch;
	private int downloadCount;

	private String featureType;
	private String featureTitle;
	private int featureYear;
	private int imdbId;
	private int tmdbId;
	private int season;
	private int episode;

	public OpenSubtitlesSubtitleDescriptor() {
		// used by deserializer
	}

	/**
	 * @param attributes
	 *            attributes of a subtitle search result
	 * @param file
	 *            one of the files of the subtitle
	 */
	public OpenSubtitlesSubtitleDescriptor(Map<?, ?> attributes, Map<?, ?> file, String languageName) {
		this.subtitleId = getString(attributes, "subtitle_id");
		this.fileId = intValue(file, "file_id", 0);
		this.fileName = getString(file, "file_name");
		this.release = getString(attributes, "release");
		this.language = getString(attributes, "language");
		this.languageName = languageName;

		this.fps = getNumber(attributes, "fps");
		this.cdNumber = intValue(file, "cd_number", 1);
		this.cdCount = intValue(attributes, "nb_cd", 1);
		this.hearingImpaired = Boolean.TRUE.equals(attributes.get("hearing_impaired"));
		this.hashMatch = Boolean.TRUE.equals(attributes.get("moviehash_match"));
		this.downloadCount = intValue(attributes, "download_count", 0);

		Map<?, ?> feature = getMap(attributes, "feature_details");
		this.featureType = getString(feature, "feature_type");
		this.featureTitle = getString(feature, "title");
		this.featureYear = intValue(feature, "year", 0);
		this.imdbId = intValue(feature, "imdb_id", 0);
		this.tmdbId = intValue(feature, "tmdb_id", 0);
		this.season = intValue(feature, "season_number", 0);
		this.episode = intValue(feature, "episode_number", 0);
	}

	private static int intValue(Object node, String key, int defaultValue) {
		Integer i = getInteger(node, key);
		return i == null ? defaultValue : i;
	}

	private static double getNumber(Object node, String key) {
		Object value = asMap(node).get(key);
		return value instanceof Number ? ((Number) value).doubleValue() : 0;
	}

	public String getSubtitleId() {
		return subtitleId;
	}

	public int getFileId() {
		return fileId;
	}

	public String getLanguage() {
		return language;
	}

	public boolean isHashMatch() {
		return hashMatch;
	}

	public boolean isHearingImpaired() {
		return hearingImpaired;
	}

	public int getDownloadCount() {
		return downloadCount;
	}

	public String getFeatureType() {
		return featureType;
	}

	public String getFeatureTitle() {
		return featureTitle;
	}

	public int getFeatureYear() {
		return featureYear;
	}

	public int getImdbId() {
		return imdbId;
	}

	public int getTmdbId() {
		return tmdbId;
	}

	public int getSeason() {
		return season;
	}

	public int getEpisode() {
		return episode;
	}

	public int getSubActualCD() {
		return cdNumber;
	}

	public int getSubSumCD() {
		return cdCount;
	}

	public float getMovieFPS() {
		return (float) fps;
	}

	public long getMovieTimeMS() {
		return 0; // not part of the REST API search results
	}

	public String getMovieReleaseName() {
		return release;
	}

	@Override
	public String getPath() {
		return getName() + "." + getType();
	}

	@Override
	public String getName() {
		// file names usually lack the extension, release names usually have it
		String name = fileName != null && !fileName.isEmpty() ? fileName : release;
		return name == null ? String.valueOf(fileId) : FileUtilities.hasExtension(name, "srt", "sub", "ass", "ssa", "smi", "txt") ? FileUtilities.getNameWithoutExtension(name) : name;
	}

	@Override
	public String getLanguageName() {
		return languageName != null ? languageName : language;
	}

	@Override
	public String getType() {
		return "srt"; // subtitles are always downloaded as UTF-8 encoded SRT files
	}

	@Override
	public long getLength() {
		return -1;
	}

	@Override
	public ByteBuffer fetch() throws Exception {
		return WebServices.OpenSubtitles.download(fileId);
	}

	@Override
	public int hashCode() {
		return fileId;
	}

	@Override
	public boolean equals(Object object) {
		if (object instanceof OpenSubtitlesSubtitleDescriptor) {
			OpenSubtitlesSubtitleDescriptor other = (OpenSubtitlesSubtitleDescriptor) object;
			return fileId == other.fileId && Objects.equals(subtitleId, other.subtitleId);
		}
		return false;
	}

	@Override
	public String toString() {
		return getPath();
	}

	@Override
	public File toFile() {
		return new File(getPath());
	}

}
