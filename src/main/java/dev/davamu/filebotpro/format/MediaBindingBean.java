package dev.davamu.filebotpro.format;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.MediaTypes.*;
import static dev.davamu.filebotpro.WebServices.*;
import static dev.davamu.filebotpro.format.Define.*;
import static dev.davamu.filebotpro.format.ExpressionFormatMethods.*;
import static dev.davamu.filebotpro.hash.VerificationUtilities.*;
import static dev.davamu.filebotpro.media.MediaDetection.*;
import static dev.davamu.filebotpro.media.XattrMetaInfo.*;
import static dev.davamu.filebotpro.similarity.Normalization.*;
import static dev.davamu.filebotpro.subtitle.SubtitleUtilities.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;
import static dev.davamu.filebotpro.util.RegularExpressions.*;
import static dev.davamu.filebotpro.util.StringUtilities.*;
import static dev.davamu.filebotpro.web.EpisodeUtilities.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import dev.davamu.filebotpro.ApplicationFolder;
import dev.davamu.filebotpro.HistorySpooler;
import dev.davamu.filebotpro.Language;
import dev.davamu.filebotpro.MediaTypes;
import dev.davamu.filebotpro.archive.Archive;
import dev.davamu.filebotpro.vfs.FileInfo;
import dev.davamu.filebotpro.Resource;
import dev.davamu.filebotpro.Settings;
import dev.davamu.filebotpro.hash.HashType;
import dev.davamu.filebotpro.media.DiscTitles;
import dev.davamu.filebotpro.media.FFProbe;
import dev.davamu.filebotpro.media.ImageMetadata;
import dev.davamu.filebotpro.media.LocalDatasource.PhotoFile;
import dev.davamu.filebotpro.media.MetaAttributes;
import dev.davamu.filebotpro.media.NamingStandard;
import dev.davamu.filebotpro.media.VideoFormat;
import dev.davamu.filebotpro.media.XattrChecksum;
import dev.davamu.filebotpro.mediainfo.CachedMediaInfo;
import dev.davamu.filebotpro.mediainfo.MediaInfo;
import dev.davamu.filebotpro.mediainfo.MediaInfo.StreamKind;
import dev.davamu.filebotpro.mediainfo.MediaInfoException;
import dev.davamu.filebotpro.similarity.Normalization;
import dev.davamu.filebotpro.similarity.SimilarityComparator;
import dev.davamu.filebotpro.util.FileUtilities;
import dev.davamu.filebotpro.web.AnimeLists;
import dev.davamu.filebotpro.web.AudioTrack;
import dev.davamu.filebotpro.web.Episode;
import dev.davamu.filebotpro.web.EpisodeListProvider;
import dev.davamu.filebotpro.web.EpisodeFormat;
import dev.davamu.filebotpro.web.Movie;
import dev.davamu.filebotpro.web.MovieInfo;
import dev.davamu.filebotpro.web.MoviePart;
import dev.davamu.filebotpro.web.SearchResult;
import dev.davamu.filebotpro.web.SeriesInfo;
import dev.davamu.filebotpro.web.SimpleDate;
import dev.davamu.filebotpro.web.SortOrder;
import dev.davamu.filebotpro.web.TheTVDBSeriesInfo;
import dev.davamu.filebotpro.web.XEM;

public class MediaBindingBean {

	private final Object infoObject;
	private final File mediaFile;
	private final Map<File, ?> context;

	public MediaBindingBean(Object infoObject, File mediaFile) {
		this(infoObject, mediaFile, null);
	}

	public MediaBindingBean(Object infoObject, File mediaFile, Map<File, ?> context) {
		this.infoObject = infoObject;
		this.mediaFile = mediaFile;
		this.context = context;
	}

	@Define("object")
	public Object getInfoObject() {
		return infoObject;
	}

	@Define("file")
	public File getFileObject() {
		return mediaFile;
	}

	@Define(undefined)
	public <T> T undefined(String name) {
		// omit expressions that depend on undefined values
		throw new BindingException(name, EXCEPTION_UNDEFINED);
	}

	@Define("n")
	public String getName() {
		if (infoObject instanceof Episode)
			return getEpisode().getSeriesName();
		else if (infoObject instanceof Movie)
			return getMovie().getName();
		else if (infoObject instanceof AudioTrack)
			return getAlbumArtist() != null ? getAlbumArtist() : getArtist();
		else if (infoObject instanceof File)
			return FileUtilities.getName((File) infoObject);

		return null;
	}

	@Define("y")
	public Integer getYear() throws Exception {
		if (infoObject instanceof Episode)
			return getStartDate().getYear();
		if (infoObject instanceof Movie)
			return getMovie().getYear();

		return getReleaseDate().getYear();
	}

	@Define("decade")
	public Integer getDecade() throws Exception {
		Integer year = getYear();
		return year == null ? null : (year / 10) * 10;
	}

	@Define("ny")
	public String getNameWithYear() {
		String n = getName();
		try {
			// account for TV Shows that contain the year in the series name, e.g. Doctor Who (2005)
			String y = String.format(" (%d)", getYear());
			return n.endsWith(y) ? n : n + y;
		} catch (Exception e) {
			// default to {n} if {y} is undefined
		}
		return n;
	}

	@Define("s")
	public Integer getSeasonNumber() {
		// look up season numbers via TheTVDB for AniDB episode data
		return trySeasonEpisodeForAnime(getEpisode()).getSeason();
	}

	@Define("e")
	public Integer getEpisodeNumber() {
		return getEpisode().getEpisode();
	}

	@Define("es")
	public List<Integer> getEpisodeNumbers() {
		return getEpisodes().stream().map(it -> {
			return it.getEpisode() == null ? it.getSpecial() == null ? null : it.getSpecial() : it.getEpisode();
		}).filter(Objects::nonNull).collect(toList());
	}

	@Define("e00")
	public String getE00() {
		if (isRegularEpisode())
			return getEpisodeNumbers().stream().map(i -> String.format("%02d", i)).collect(joining("-"));
		else
			return "Special " + join(getEpisodeNumbers(), "-");
	}

	@Define("sxe")
	public String getSxE() {
		return EpisodeFormat.SeasonEpisode.formatSxE(trySeasonEpisodeForAnime(getEpisode())); // magically convert AniDB absolute numbers to TheTVDB SxE numbers

	}

	@Define("s00e00")
	public String getS00E00() {
		return EpisodeFormat.SeasonEpisode.formatS00E00(trySeasonEpisodeForAnime(getEpisode())); // magically convert AniDB absolute numbers to TheTVDB SxE numbers
	}

	@Define("t")
	public String getTitle() {
		String t = null;

		if (infoObject instanceof Episode) {
			t = getEpisode().getTitle();
		} else if (infoObject instanceof Movie) {
			t = getMovieInfo().getTagline();
		} else if (infoObject instanceof AudioTrack) {
			t = getMusic().getTrackTitle() != null ? getMusic().getTrackTitle() : getMusic().getTitle();
		}

		// enforce title length limit by default
		return truncateText(t, NamingStandard.TITLE_MAX_LENGTH);
	}

	@Define("d")
	public SimpleDate getReleaseDate() {
		if (infoObject instanceof Episode)
			return getEpisode().getAirdate();
		if (infoObject instanceof Movie)
			return getMovieInfo().getReleased();
		if (infoObject instanceof AudioTrack)
			return getMusic().getAlbumReleaseDate();
		if (infoObject instanceof File)
			return new SimpleDate(getTimeStamp());

		return null;
	}

	@Define("dt")
	public ZonedDateTime getTimeStamp() {
		File f = getMediaFile();

		// try EXIF Date-Taken for image files or File Last-Modified for generic files
		try {
			return getPhoto().getDateTaken().get();
		} catch (Exception e) {
			// ignore and default to file creation date
		}

		try {
			return Instant.ofEpochMilli(getCreationDate(f)).atZone(ZoneOffset.systemDefault());
		} catch (Exception e) {
			debug.warning(e::toString);
		}

		return null;
	}

	@Define("ct")
	public ZonedDateTime getCreationTime() throws Exception {
		return Instant.ofEpochMilli(getCreationDate(getMediaFile())).atZone(ZoneOffset.systemDefault());
	}

	@Define("mt")
	public ZonedDateTime getLastModifiedTime() throws Exception {
		return Files.getLastModifiedTime(getMediaFile().toPath()).toInstant().atZone(ZoneOffset.systemDefault());
	}

	@Define("airdate")
	public SimpleDate getAirdate() {
		return getEpisode().getAirdate();
	}

	@Define("age")
	public Long getAgeInDays() throws Exception {
		SimpleDate releaseDate = getReleaseDate();

		if (releaseDate != null) {
			// avoid time zone issues by interpreting all dates and times as UTC
			long days = ChronoUnit.DAYS.between(releaseDate.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant(), Instant.now());

			if (days >= 0) {
				return days;
			}
		}

		return null;
	}

	@Define("startdate")
	public SimpleDate getStartDate() throws Exception {
		SimpleDate startdate = getEpisode().getSeriesInfo().getStartDate();

		// use series metadata startdate if possible
		if (startdate != null) {
			return startdate;
		}

		// access episode list and find minimum airdate if necessary
		return getEpisodeList().stream().filter(Episode::isRegular).map(Episode::getAirdate).filter(Objects::nonNull).min(SimpleDate::compareTo).get();
	}

	@Define("absolute")
	public Integer getAbsoluteEpisodeNumber() {
		return getEpisode().getAbsolute();
	}

	@Define("special")
	public Integer getSpecialNumber() {
		return getEpisode().getSpecial();
	}

	@Define("series")
	public SeriesInfo getSeriesInfo() {
		return getEpisode().getSeriesInfo();
	}

	@Define("alias")
	public List<String> getAliasNames() {
		if (infoObject instanceof Movie)
			return asList(getMovie().getAliasNames());
		if (infoObject instanceof Episode)
			return getSeriesInfo().getAliasNames();

		return null;
	}

	@Define("primaryTitle")
	public String getPrimaryTitle() {
		if (infoObject instanceof Movie)
			return getPrimaryMovieInfo().getOriginalName();
		if (infoObject instanceof Episode) {
			// romanized Japanese (x-jat) AniDB title for anime on TheTVDB
			String anime = getAnimeTitle(getEpisode());
			if (anime != null) {
				return anime;
			}
			return getPrimarySeriesInfo().getName(); // force English series name for TheTVDB data or default to SeriesInfo name (for AniDB episode data this would be the primary title)
		}

		return null;
	}

	private String getAnimeTitle(Episode e) {
		if (!isInstance(TheTVDB, e) || e.getSeriesInfo().getId() == null) {
			return null;
		}

		try {
			int season = e.getSeason() != null ? e.getSeason() : e.getSpecial() != null ? 0 : 1;
			Optional<Integer> anidb = AnimeList.map(e.getSeriesInfo().getId(), season, AnimeLists.DB.TheTVDB, AnimeLists.DB.AniDB);
			if (anidb.isPresent()) {
				// the first name of each entry in the local AniDB index is the main title
				return stream(releaseInfo.getAnidbIndex()).filter(it -> it.getId() == anidb.get()).map(SearchResult::getName).findFirst().orElse(null);
			}
		} catch (Exception ex) {
			debug.warning(cause("Failed to map TheTVDB series to AniDB", ex));
		}
		return null;
	}

	@Define("id")
	public Object getId() throws Exception {
		if (infoObject instanceof Episode)
			return getEpisode().getSeriesInfo().getId();
		if (infoObject instanceof Movie)
			return getMovie().getId();
		if (infoObject instanceof AudioTrack)
			return getMusic().getMBID();

		return null;
	}

	@Define("tmdbid")
	public String getTmdbId() {
		if (getMovie().getTmdbId() > 0)
			return String.valueOf(getMovie().getTmdbId());
		if (getMovie().getImdbId() > 0)
			return getPrimaryMovieInfo().getId().toString(); // lookup IMDbID for TMDbID

		return null;
	}

	@Define("tvdbid")
	public Integer getTheTVDBId() throws Exception {
		SeriesInfo info = getEpisode().getSeriesInfo();
		if (info.getId() == null || info.getDatabase() == null) {
			return null;
		}

		switch (info.getDatabase()) {
		case "TheTVDB":
			return info.getId();
		case "TheMovieDB::TV":
			return TheMovieDB_TV.getTheTVDBId(info.getId()); // lookup TheTVDB id via TheMovieDB external ids
		case "AniDB":
			return AnimeList.map(info.getId(), 1, AnimeLists.DB.AniDB, AnimeLists.DB.TheTVDB).orElse(null);
		default:
			return null;
		}
	}

	@Define("imdbid")
	public String getImdbId() {
		if (getMovie().getImdbId() > 0)
			return String.format("tt%07d", getMovie().getImdbId());
		if (getMovie().getTmdbId() > 0)
			return String.format("tt%07d", getPrimaryMovieInfo().getImdbId()); // lookup IMDbID for TMDbID

		return null;
	}

	@Define("vc")
	public String getVideoCodec() {
		// e.g. XviD, x264, DivX 5, MPEG-4 Visual, AVC, etc.
		String codec = getMediaInfo(StreamKind.Video, 0, "Encoded_Library_Name", "Encoded_Library/Name", "CodecID/Hint", "Format");

		// get first token (e.g. DivX 5 => DivX)
		return tokenize(codec).findFirst().orElse(null);
	}

	@Define("vcf")
	public String getVideoCompressionFormat() {
		// e.g. AVC, HEVC, AV1, VP9, MPEG-4 Visual
		return getMediaInfo(StreamKind.Video, 0, "Format");
	}

	@Define("ac")
	public String getAudioCodec() {
		// e.g. AC-3, DTS, AAC, Vorbis, MP3, etc.
		String codec = getMediaInfo(StreamKind.Audio, 0, "CodecID/Hint", "Format");

		// remove punctuation (e.g. AC-3 => AC3)
		return normalizePunctuation(codec, "", "");
	}

	@Define("cf")
	public String getContainerFormat() {
		// container format extensions (e.g. avi, mkv mka mks, OGG, etc.)
		String extensions = getMediaInfo(StreamKind.General, 0, "Codec/Extensions", "Format");

		// get first extension
		return tokenize(extensions).map(String::toLowerCase).findFirst().get();
	}

	@Define("vf")
	public String getVideoFormat() {
		int w = Integer.parseInt(getMediaInfo(StreamKind.Video, 0, "Width"));
		int h = Integer.parseInt(getMediaInfo(StreamKind.Video, 0, "Height"));

		// e.g. 720p, nobody actually wants files to be tagged as interlaced, e.g. 720i
		return String.format("%dp", VideoFormat.DEFAULT_GROUPS.guessFormat(w, h));
	}

	@Define("hpi")
	public String getExactVideoFormat() {
		String height = getMediaInfo(StreamKind.Video, 0, "Height");
		String scanType = getMediaInfo(StreamKind.Video, 0, "ScanType");

		// progressive by default if ScanType is undefined
		String p = scanType.codePoints().map(Character::toLowerCase).mapToObj(Character::toChars).map(String::valueOf).findFirst().orElse("p");

		// e.g. 720p
		return height + p;
	}

	@Define("af")
	public String getAudioChannels() {
		int ch = getMediaInfo(StreamKind.Audio, "Channel(s)_Original", "Channel(s)").map(channels -> {
			// e.g. 15 objects / 6 channels
			return tokenize(channels, SLASH).map(s -> matchInteger(s)).filter(Objects::nonNull).min(Integer::compare).orElse(null);
		}).filter(Objects::nonNull).findFirst().get();

		// get first number, e.g. 6ch
		return ch + "ch";
	}

	@Define("channels")
	public String getAudioChannelPositions() {
		double d = getMediaInfo(StreamKind.Audio, CHANNEL_LAYOUT_KEYS).mapToDouble(MediaBindingBean::parseChannelLayout).filter(i -> i > 0).findFirst().getAsDouble();

		return formatChannelLayout(d);
	}

	@Define("acf")
	public String getAudioChannelFormat() {
		String format = getMediaInfo(StreamKind.Audio, 0, "Format");
		String commercial = getMediaInfo().get(StreamKind.Audio, 0, "Format_Commercial_IfAny") + " " + getMediaInfo().get(StreamKind.Audio, 0, "Format_Commercial") + " " + getMediaInfo().get(StreamKind.Audio, 0, "Format_AdditionalFeatures");

		String codec;
		if (format.equals("E-AC-3")) {
			codec = "DDP";
		} else if (format.equals("AC-3")) {
			codec = "DD";
		} else if (format.startsWith("MLP")) {
			codec = "TrueHD";
		} else if (format.startsWith("DTS")) {
			if (commercial.contains("Master Audio") || commercial.contains("XLL")) {
				codec = "DTS-HD MA";
			} else if (commercial.contains("High Resolution") || commercial.contains("XBR")) {
				codec = "DTS-HD HRA";
			} else {
				codec = "DTS";
			}
		} else if (format.equals("MPEG Audio")) {
			codec = "MP" + getMediaInfo().get(StreamKind.Audio, 0, "Format_Profile").replaceAll("\\D", "");
		} else if (format.equals("PCM")) {
			codec = "LPCM";
		} else {
			codec = normalizePunctuation(format, "", "");
		}

		// e.g. DD5.1, DDP5.1, AAC2.0, TrueHD7.1
		double channels = stream(CHANNEL_LAYOUT_KEYS).map(k -> getMediaInfo().get(StreamKind.Audio, 0, k)).mapToDouble(MediaBindingBean::parseChannelLayout).filter(i -> i > 0).findFirst().orElse(0);
		return channels > 0 ? codec + formatChannelLayout(channels) : codec;
	}

	private static final String[] CHANNEL_LAYOUT_KEYS = { "ChannelPositions/String2", "Channel(s)_Original", "Channel(s)" };

	private static double parseChannelLayout(String channels) {
		// e.g. ChannelPositions/String2: 3/2/2.1 / 3/2/0.1 (one audio stream may contain multiple multi-channel streams)
		return tokenize(channels).mapToDouble(s -> {
			try {
				if (s.contains("/")) {
					return tokenize(s, SLASH).mapToDouble(Double::parseDouble).reduce(0, (a, b) -> a + b);
				}

				// plain channel count, e.g. 6 => 5.1 and 8 => 7.1 (6 or more channels practically always include an LFE channel)
				int count = Integer.parseInt(s);
				return count >= 6 ? count - 0.9 : count;
			} catch (NumberFormatException e) {
				return 0;
			}
		}).max().orElse(0);
	}

	private static String formatChannelLayout(double channels) {
		return BigDecimal.valueOf(channels).setScale(1, RoundingMode.HALF_UP).toPlainString();
	}

	@Define("aco")
	public String getAudioChannelObjects() {
		return getMediaInfo(StreamKind.Audio, "Codec_Profile", "Format_Profile", "Format_Commercial").map(s -> {
			return SLASH.splitAsStream(s).findFirst().orElse(null);
		}).filter(Objects::nonNull).map(String::trim).filter(s -> s.length() > 0).findFirst().orElse(null);
	}

	@Define("resolution")
	public String getVideoResolution() {
		return join(getDimension(), "x"); // e.g. 1280x720
	}

	@Define("bitdepth")
	public int getVideoBitDepth() {
		String bitdepth = getMediaInfo(StreamKind.Video, 0, "BitDepth");

		return Integer.parseInt(bitdepth);
	}

	@Define("hdr")
	public String getHighDynamicRange() {
		// e.g. HDR_Format: Dolby Vision / SMPTE ST 2086 and HDR_Format_Compatibility: Blu-ray / HDR10
		String format = Stream.of("HDR_Format", "HDR_Format_Commercial", "HDR_Format_Compatibility").map(k -> getMediaInfo().get(StreamKind.Video, 0, k)).collect(joining(" / "));
		String transfer = getMediaInfo().get(StreamKind.Video, 0, "transfer_characteristics");

		String hdr = null;
		if (format.contains("HDR10+") || format.contains("2094")) {
			hdr = "HDR10+";
		} else if (format.contains("HDR10") || format.contains("2086") || transfer.contains("PQ") || transfer.contains("2084")) {
			hdr = "HDR10";
		} else if (transfer.contains("HLG")) {
			hdr = "HLG";
		}

		// Dolby Vision only (e.g. profile 5) or Dolby Vision with HDR10 / HLG fallback layer (e.g. profile 8)
		if (format.contains("Dolby Vision")) {
			return hdr == null ? "DV" : "DV " + hdr;
		}

		if (hdr != null) {
			return hdr;
		}

		// 10-bit BT.2020 video without HDR metadata
		if (getMediaInfo().get(StreamKind.Video, 0, "colour_primaries").matches("BT.2020") && getVideoBitDepth() >= 10) {
			return "HDR";
		}
		return null;
	}

	@Define("dovi")
	public String getDolbyVision() {
		// e.g. Dolby Vision, Version 1.0, dvhe.08.06, BL+RPU, HDR10 compatible / SMPTE ST 2086, HDR10 compatible
		String format = getMediaInfo().get(StreamKind.Video, 0, "HDR_Format") + " " + getMediaInfo().get(StreamKind.Video, 0, "HDR_Format_Commercial");
		return format.contains("Dolby Vision") ? "DV" : null;
	}

	@Define("ws")
	public String getWidescreen() {
		List<Integer> dim = getDimension();

		// width-to-height aspect ratio greater than 1.37:1
		return (float) dim.get(0) / dim.get(1) > 1.37f ? "WS" : null;
	}

	@Define("ar")
	public String getAspectRatio() {
		double ratio;
		try {
			ratio = Double.parseDouble(getMediaInfo(StreamKind.Video, 0, "DisplayAspectRatio"));
		} catch (Exception e) {
			List<Integer> dim = getDimension();
			ratio = (double) dim.get(0) / dim.get(1);
		}

		// use ratio (U+2236) instead of colon (U+003A) since colons are not allowed in file names
		if (Math.abs(ratio - 4.0 / 3) < 0.02)
			return "4∶3";
		if (Math.abs(ratio - 16.0 / 9) < 0.02)
			return "16∶9";

		// e.g. 2.39∶1
		return BigDecimal.valueOf(ratio).setScale(2, RoundingMode.HALF_UP).toPlainString() + "∶1";
	}

	@Define("hd")
	public String getVideoDefinitionCategory() {
		List<Integer> dim = getDimension();

		// UHD (2160p)
		if (dim.get(0) >= 3840 || dim.get(1) >= 2160)
			return "UHD";

		// QHD (1440p)
		if (dim.get(0) >= 2560 || dim.get(1) >= 1440)
			return "QHD";

		// FHD (1080p)
		if (dim.get(0) >= 1920 || dim.get(1) >= 1080)
			return "FHD";

		// HD (720p)
		if (dim.get(0) >= 1280 || dim.get(1) >= 720)
			return "HD";

		// SD
		return "SD";
	}

	@Define("vk")
	public String getVideoK() {
		List<Integer> dim = getDimension();

		// 8K (4320p) or 4K (2160p) tag, undefined for lower resolutions
		if (dim.get(0) >= 7680 || dim.get(1) >= 4320)
			return "8K";
		if (dim.get(0) >= 3840 || dim.get(1) >= 2160)
			return "4K";

		return null;
	}

	@Define("dim")
	public List<Integer> getDimension() {
		// collect value from Video Stream 0 or Image Stream 0
		return Stream.of(StreamKind.Video, StreamKind.Image).map(k -> {
			// collect Width and Height as Integer List
			return Stream.of("Width", "Height").map(p -> getMediaInfo().get(k, 0, p)).filter(s -> s.length() > 0).map(Integer::parseInt).collect(toList());
		}).filter(d -> d.size() == 2).findFirst().orElse(null);
	}

	@Define("width")
	public Integer getWidth() {
		return getDimension().get(0);
	}

	@Define("height")
	public Integer getHeight() {
		return getDimension().get(1);
	}

	@Define("original")
	public String getOriginalFileName() {
		String name = xattr.getOriginalName(getMediaFile());

		return name != null ? getNameWithoutExtension(name) : null;
	}

	@Define("history")
	public File getOriginalFilePath() throws Exception {
		// check in-memory history first
		Optional<File> path = HistorySpooler.getInstance().getSessionHistory().getOriginalPath(getMediaFile()).findFirst();
		if (path.isPresent()) {
			return path.get();
		}

		// check persistent history file as well
		return HistorySpooler.getInstance().getCompleteHistory().getOriginalPath(getMediaFile()).findFirst().orElse(null);
	}

	@Define("xattr")
	public Object getMetaAttributesObject() throws Exception {
		return xattr.getMetaInfo(getMediaFile());
	}

	@Define("crc32")
	public String getCRC32() throws Exception {
		// use inferred media file
		File inferredMediaFile = getInferredMediaFile();

		// try to get checksum from file name
		Optional<String> embeddedChecksum = stream(getFileNames(inferredMediaFile)).map(Normalization::getEmbeddedChecksum).filter(Objects::nonNull).findFirst();
		if (embeddedChecksum.isPresent()) {
			return embeddedChecksum.get();
		}

		// try to get checksum from sfv file
		String checksum = getHashFromVerificationFile(inferredMediaFile, HashType.SFV, 3);
		if (checksum != null) {
			return checksum;
		}

		// compute and store to xattr
		return XattrChecksum.CRC32.computeIfAbsent(inferredMediaFile);
	}

	@Define("fn")
	public String getFileName() {
		// name without file extension
		return FileUtilities.getName(getMediaFile());
	}

	@Define("mediaFileName")
	public String getMediaFileName() {
		// name of the video file without file extension (e.g. for subtitles, nfo or artwork files next to the video file)
		return FileUtilities.getName(getInferredMediaFile());
	}

	@Define("ext")
	public String getExtension() {
		// file extension
		return FileUtilities.getExtension(getMediaFile());
	}

	@Define("source")
	public String getVideoSource() throws Exception {
		// look for video source patterns in media file and it's parent folder (use inferred media file)
		return releaseInfo.getVideoSource(mediaTitles.get());
	}

	@Define("vs")
	public String getStandardVideoSource() throws Exception {
		// standard tag for the video source (e.g. BDRip ➔ BluRay)
		return releaseInfo.getStandardVideoSource(getVideoSource());
	}

	@Define("tags")
	public List<String> getVideoTags() throws Exception {
		// look for video source patterns in media file and it's parent folder (use inferred media file)
		List<String> matches = releaseInfo.getVideoTags(mediaTitles.get());
		if (matches.isEmpty()) {
			return null;
		}

		// heavy normalization for whatever text was captured with the tags pattern
		return matches.stream().map(s -> {
			return lowerTrail(upperInitial(normalizePunctuation(s)));
		}).sorted().distinct().collect(toList());
	}

	@Define("s3d")
	public String getStereoscopic3D() throws Exception {
		return releaseInfo.getStereoscopic3D(mediaTitles.get());
	}

	@Define("group")
	public String getReleaseGroup() throws Exception {
		// reduce false positives by removing the know titles from the name
		Pattern[] nonGroupPattern = { getKeywordExcludePattern(), releaseInfo.getVideoSourcePattern(), releaseInfo.getVideoFormatPattern(true), releaseInfo.getResolutionPattern(), releaseInfo.getStructureRootPattern() };

		// consider foldername, filename and original filename of inferred media file
		String[] filenames = stream(mediaTitles.get()).map(s -> releaseInfo.clean(s, nonGroupPattern)).filter(s -> s.length() > 0).toArray(String[]::new);

		// look for release group names in media file and it's parent folder
		return releaseInfo.getReleaseGroup(filenames);
	}

	@Define("subt")
	public String getSubtitleTags() throws Exception {
		if (!SUBTITLE_FILES.accept(getMediaFile())) {
			return null;
		}

		Language language = getLanguageTag();
		if (language != null) {
			String tag = '.' + language.getISO3B(); // Plex only supports ISO 639-2/B language codes
			String category = releaseInfo.getSubtitleCategoryTag(getFileNames(getMediaFile()));
			if (category != null) {
				return tag + '.' + category;
			}
			return tag;
		}

		return null;
	}

	@Define("lang")
	public Language getLanguageTag() throws Exception {
		// grep language from filename
		Locale languageTag = releaseInfo.getSubtitleLanguageTag(getFileNames(getMediaFile()));
		if (languageTag != null) {
			return Language.getLanguage(languageTag);
		}

		// detect language from subtitle text content
		if (SUBTITLE_FILES.accept(getMediaFile())) {
			try {
				return detectSubtitleLanguage(getMediaFile());
			} catch (Exception e) {
				throw new RuntimeException("Failed to detect subtitle language: " + e, e);
			}
		}

		return null;
	}

	@Define("languages")
	public List<Language> getSpokenLanguages() {
		if (infoObject instanceof Movie) {
			List<Locale> languages = getMovieInfo().getSpokenLanguages();
			return languages.stream().map(Language::getLanguage).filter(Objects::nonNull).collect(toList());
		}
		if (infoObject instanceof Episode) {
			SeriesInfo info = getSeriesInfo();
			String language = info.getOriginalLanguage() != null ? info.getOriginalLanguage() : info.getLanguage();
			return Stream.of(language).map(Language::findLanguage).filter(Objects::nonNull).collect(toList());
		}
		return null;
	}

	@Define("language")
	public Language getOriginalLanguage() {
		String language = null;
		if (infoObject instanceof Movie) {
			language = getMovieInfo().getOriginalLanguage();
		} else if (infoObject instanceof Episode) {
			language = getSeriesInfo().getOriginalLanguage();
		}
		return language == null ? null : Language.findLanguage(language);
	}

	@Define("runtime")
	public Integer getRuntime() {
		if (infoObject instanceof Movie)
			return getMovieInfo().getRuntime();
		if (infoObject instanceof Episode) {
			// episode-specific runtime if available, typical series runtime otherwise
			Integer runtime = getEpisode().getRuntime();
			return runtime != null && runtime > 0 ? runtime : getSeriesInfo().getRuntime();
		}

		return null;
	}

	@Define("actors")
	public List<String> getActors() throws Exception {
		if (infoObject instanceof Movie)
			return getMovieInfo().getActors();
		if (infoObject instanceof Episode)
			return ExpressionFormatMethods.getActors(getSeriesInfo()); // use TheTVDB API v4 to retrieve actors info

		return null;
	}

	@Define("genres")
	public List<String> getGenres() {
		if (infoObject instanceof Movie)
			return getMovieInfo().getGenres();
		if (infoObject instanceof Episode)
			return getSeriesInfo().getGenres();
		if (infoObject instanceof AudioTrack)
			return Stream.of(getMusic().getGenre()).filter(Objects::nonNull).flatMap(SEMICOLON::splitAsStream).map(String::trim).filter(s -> s.length() > 0).collect(toList());

		return null;
	}

	@Define("genre")
	public String getPrimaryGenre() {
		return getGenres().iterator().next();
	}

	@Define("director")
	public String getDirector() throws Exception {
		if (infoObject instanceof Movie)
			return getMovieInfo().getDirector();
		if (infoObject instanceof Episode)
			return ExpressionFormatMethods.getInfo(getEpisode()).getDirector(); // use TheTVDB API v4 to retrieve extended episode info

		return null;
	}

	@Define("certification")
	public String getCertification() {
		if (infoObject instanceof Movie)
			return getMovieInfo().getCertification();
		if (infoObject instanceof Episode)
			return getSeriesInfo().getCertification();

		return null;
	}

	@Define("country")
	public String getProductionCountry() {
		// ISO 3166-1 alpha-2 code, e.g. US
		if (infoObject instanceof Movie)
			return getMovieInfo().getProductionCountries().stream().findFirst().orElse(null);
		if (infoObject instanceof Episode)
			return getExtendedSeriesInfo().getCountries().stream().findFirst().orElse(null);

		return null;
	}

	@Define("sn")
	public String getSeasonName() {
		Episode e = getEpisode();
		Integer season = e.getSpecial() != null ? Integer.valueOf(0) : e.getSeason();
		return season == null ? null : getExtendedSeriesInfo().getSeasonNames().get(season);
	}

	@Define("rating")
	public Double getRating() {
		if (infoObject instanceof Movie)
			return getMovieInfo().getRating();
		if (infoObject instanceof Episode)
			return getSeriesInfo().getRating();

		return null;
	}

	@Define("votes")
	public Integer getVotes() {
		if (infoObject instanceof Movie)
			return getMovieInfo().getVotes();
		if (infoObject instanceof Episode)
			return getSeriesInfo().getRatingCount();

		return null;
	}

	@Define("collection")
	public String getCollection() {
		if (infoObject instanceof Movie)
			return getMovieInfo().getCollection();

		return null;
	}

	@Define("ci")
	public Integer getCollectionIndex() throws Exception {
		return ExpressionFormatMethods.getCollection(getMovie()).indexOf(getMovie()) + 1;
	}

	@Define("cy")
	public List<Integer> getCollectionYears() throws Exception {
		List<Integer> years = ExpressionFormatMethods.getCollection(getMovie()).stream().map(Movie::getYear).sorted().distinct().collect(toList());
		if (years.size() > 1) {
			return asList(years.get(0), years.get(years.size() - 1));
		} else {
			return years;
		}
	}

	@Define("info")
	public synchronized AssociativeScriptObject getMetaInfo() {
		if (infoObject instanceof Movie)
			return createPropertyBindings(getMovieInfo());
		if (infoObject instanceof Episode)
			return createPropertyBindings(getExtendedSeriesInfo());

		return null;
	}

	@Define("omdb")
	public synchronized AssociativeScriptObject getOmdbApiInfo() throws Exception {
		if (infoObject instanceof Movie) {
			if (getMovie().getImdbId() > 0) {
				return createPropertyBindings(OMDb.getMovieInfo(getMovie()));
			}
			if (getMovie().getTmdbId() > 0) {
				Integer imdbId = getPrimaryMovieInfo().getImdbId();
				return createPropertyBindings(OMDb.getMovieInfo(new Movie(imdbId)));
			}
		}

		if (infoObject instanceof Episode) {
			TheTVDBSeriesInfo info = (TheTVDBSeriesInfo) getPrimarySeriesInfo();
			int imdbId = matchInteger(info.getImdbId());
			return createPropertyBindings(OMDb.getMovieInfo(new Movie(imdbId)));
		}

		return null;
	}

	@Define("order")
	public DynamicBindings getSortOrderObject() {
		return new DynamicBindings(SortOrder::names, k -> {
			if (infoObject instanceof Episode) {
				SortOrder order = SortOrder.forName(k);
				Episode episode = fetchEpisode(getEpisode(), order, null);
				return createBindingObject(episode);
			}
			return undefined(k);
		});
	}

	@Define("localize")
	public DynamicBindings getLocalizedInfoObject() {
		return new DynamicBindings(Language::availableLanguages, k -> {
			Language language = Language.findLanguage(k);

			if (language != null && infoObject instanceof Movie) {
				Movie movie = TheMovieDB.getMovieDescriptor(getMovie(), language.getLocale());
				return createBindingObject(movie);
			}

			if (language != null && infoObject instanceof Episode) {
				Episode episode = fetchEpisode(getEpisode(), null, language.getLocale());
				return createBindingObject(episode);
			}

			return undefined(k);
		});
	}

	@Define("seasonize")
	public AssociativeScriptObject getSeasonizeObject() throws Exception {
		Episode e = getEpisode();

		if (e.getSeason() != null) {
			return createBindingObject(e); // do nothing
		}

		if (isInstance(AniDB, e)) {
			return AnimeList.map(e, AnimeLists.getDB(e), AnimeLists.DB.TheTVDB).map(this::createBindingObject).orElse(null); // map AniDB to TheTVDB bindings
		}

		return createBindingObject(fetchEpisode(e, SortOrder.Airdate, null));
	}

	@Define("az")
	public String getSortInitial() {
		try {
			return sortInitial(getCollection());
		} catch (Exception e) {
			return sortInitial(getName());
		}
	}

	@Define("anime")
	public boolean isAnime() {
		if (infoObject instanceof Episode) {
			Episode e = getEpisode();

			// AniDB episode data or TheTVDB series that is mapped to an AniDB entry
			if (e.isAnime() || isMappedToAniDB(e)) {
				return true;
			}

			SeriesInfo info = getEnglishSeriesInfo();
			return isAnime(info.getGenres(), info.getOriginalLanguage(), emptyList());
		}

		if (infoObject instanceof Movie) {
			MovieInfo info = getPrimaryMovieInfo();
			return info != null && isAnime(info.getGenres(), info.getOriginalLanguage(), info.getProductionCountries());
		}

		return false;
	}

	/**
	 * Best guess based on genres (English genre names), original language and country
	 */
	private static boolean isAnime(List<String> genres, String language, List<String> countries) {
		if (genres.stream().anyMatch("Anime"::equalsIgnoreCase)) {
			return true;
		}
		return genres.stream().anyMatch("Animation"::equalsIgnoreCase) && ("ja".equals(language) || countries.contains("JP"));
	}

	private boolean isMappedToAniDB(Episode e) {
		if (!isInstance(TheTVDB, e) || e.getSeriesInfo().getId() == null) {
			return false;
		}

		try {
			int season = e.getSeason() != null ? e.getSeason() : e.getSpecial() != null ? 0 : 1;
			return AnimeList.map(e.getSeriesInfo().getId(), season, AnimeLists.DB.TheTVDB, AnimeLists.DB.AniDB).isPresent();
		} catch (Exception ex) {
			debug.warning(cause("Failed to map TheTVDB series to AniDB", ex));
			return false;
		}
	}

	/**
	 * Series info with English genre names
	 */
	private SeriesInfo getEnglishSeriesInfo() {
		SeriesInfo info = getSeriesInfo();
		if (isInstance(TheMovieDB_TV, info) && !"en".equals(info.getLanguage())) {
			try {
				return TheMovieDB_TV.getSeriesInfo(info.getId(), Locale.US);
			} catch (Exception e) {
				debug.warning("Failed to retrieve English series info: " + e);
			}
		}
		return getPrimarySeriesInfo();
	}

	@Define("regular")
	public boolean isRegularEpisode() {
		return getEpisode().isRegular();
	}

	@Define("episodelist")
	public List<Episode> getEpisodeList() throws Exception {
		return fetchEpisodeList(getEpisode());
	}

	@Define("sy")
	public List<Integer> getSeasonYears() throws Exception {
		return getEpisodeList().stream().filter(e -> e.isRegular() && e.getSeason().equals(getSeasonNumber()) && e.getAirdate() != null).map(e -> e.getAirdate().getYear()).sorted().distinct().collect(toList());
	}

	@Define("sc")
	public Integer getSeasonCount() throws Exception {
		return getEpisodeList().stream().filter(e -> e.isRegular() && e.getSeason() != null).map(Episode::getSeason).max(Integer::compare).get();
	}

	@Define("mediaTitle")
	public String getMediaTitle() {
		return Stream.of(StreamKind.General, StreamKind.Video).flatMap(k -> getMediaInfo(k, "Title", "Movie")).findFirst().orElse(null);
	}

	@Define("mediaTags")
	public Map<String, String> getMediaTags() {
		// embedded tags like Title, Comment, Description, Genre, etc. (but not technical properties)
		Map<String, String> tags = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		getMediaInfo().snapshot(StreamKind.General, 0).forEach((k, v) -> {
			if (MEDIA_TAGS.contains(k) && v != null && v.length() > 0) {
				tags.put(k, v);
			}
		});
		return tags;
	}

	private static final Set<String> MEDIA_TAGS = Set.of("Title", "Movie", "Album", "Album/Performer", "Track", "Track/Position", "Part", "Part/Position", "Performer", "Composer", "Director", "Producer", "Publisher", "Genre", "Description", "Comment", "Copyright", "Collection", "Season", "ContentType", "Recorded_Date", "Released_Date", "Original/Released_Date", "LawRating", "Rating", "Keywords", "Synopsis", "Summary", "tvsh", "tven", "tves", "tvnn", "IMDB", "TMDB", "TVDB");

	@Define("audioLanguages")
	public List<Language> getAudioLanguageList() {
		return getMediaInfo(StreamKind.Audio, "Language").filter(Objects::nonNull).distinct().map(Language::findLanguage).filter(Objects::nonNull).collect(toList());
	}

	@Define("textLanguages")
	public List<Language> getTextLanguageList() {
		return getMediaInfo(StreamKind.Text, "Language").filter(Objects::nonNull).distinct().map(Language::findLanguage).filter(Objects::nonNull).collect(toList());
	}

	@Define("bitrate")
	public BitRate getOverallBitRate() {
		return new BitRate((long) Double.parseDouble(getMediaInfo(StreamKind.General, 0, "OverallBitRate")));
	}

	@Define("vbr")
	public BitRate getVideoBitRate() {
		Optional<String> bitrate = getMediaInfo(StreamKind.Video, "BitRate", "BitRate_Nominal").findFirst();
		if (bitrate.isPresent()) {
			return new BitRate((long) Double.parseDouble(bitrate.get()));
		}

		// MediaInfo often has no video bit rate for mkv files, so take the overall bit rate without the audio bit rates
		List<Double> audio = getMediaInfo(StreamKind.Audio, "BitRate", "BitRate_Nominal").map(Double::parseDouble).collect(toList());
		if (audio.size() == getMediaInfo().streamCount(StreamKind.Audio)) {
			long bps = getOverallBitRate().longValue() - (long) audio.stream().mapToDouble(Double::doubleValue).sum();
			if (bps > 0) {
				return new BitRate(bps);
			}
		}

		return undefined("vbr");
	}

	@Define("abr")
	public BitRate getAudioBitRate() {
		return new BitRate((long) Double.parseDouble(getMediaInfo(StreamKind.Audio, 0, "BitRate", "BitRate_Nominal", "BitRate_Maximum")));
	}

	@Define("kbps")
	public String getKiloBytesPerSecond() {
		return String.format("%.0f kbps", getOverallBitRate().longValue() / 1e3f);
	}

	@Define("mbps")
	public String getMegaBytesPerSecond() {
		return String.format("%.1f Mbps", getOverallBitRate().longValue() / 1e6f);
	}

	@Define("fps")
	public FrameRate getFrameRate() {
		return new FrameRate(getMediaInfo(StreamKind.Video, 0, "FrameRate"));
	}

	@Define("khz")
	public String getSamplingRate() {
		return getMediaInfo(StreamKind.Audio, 0, "SamplingRate/String");
	}

	@Define("duration")
	public Duration getDuration() {
		long d = (long) Double.parseDouble(getMediaInfo(StreamKind.General, 0, "Duration"));
		return Duration.ofMillis(d);
	}

	@Define("seconds")
	public long getSeconds() {
		return getDuration().getSeconds();
	}

	@Define("minutes")
	public long getMinutes() {
		return getDuration().toMinutes();
	}

	@Define("hours")
	public String getHours() {
		// use ratio (U+2236) instead of colon (U+003A) since colons are not allowed in file names
		return ExpressionFormatMethods.format(getDuration(), "H∶mm");
	}

	@Define("media")
	public AssociativeScriptObject getGeneralMediaInfo() {
		return createMediaInfoBindings(StreamKind.General).get(0);
	}

	@Define("menu")
	public AssociativeScriptObject getMenuInfo() {
		return createMediaInfoBindings(StreamKind.Menu).get(0);
	}

	@Define("image")
	public AssociativeScriptObject getImageInfo() {
		return createMediaInfoBindings(StreamKind.Image).get(0);
	}

	@Define("video")
	public List<AssociativeScriptObject> getVideoInfoList() {
		return createMediaInfoBindings(StreamKind.Video);
	}

	@Define("audio")
	public List<AssociativeScriptObject> getAudioInfoList() {
		return createMediaInfoBindings(StreamKind.Audio);
	}

	@Define("text")
	public List<AssociativeScriptObject> getTextInfoList() {
		return createMediaInfoBindings(StreamKind.Text);
	}

	@Define("chapters")
	public List<AssociativeScriptObject> getChaptersInfoList() {
		return createMediaInfoBindings(StreamKind.Chapters);
	}

	@Define("exif")
	public AssociativeScriptObject getImageMetadata() throws Exception {
		return new AssociativeScriptObject(getPhoto().snapshot());
	}

	@Define("camera")
	public AssociativeEnumObject getCamera() throws Exception {
		return getPhoto().getCameraModel().map(AssociativeEnumObject::new).orElse(null);
	}

	@Define("location")
	public AssociativeEnumObject getLocation() throws Exception {
		return getPhoto().getLocationTaken().map(AssociativeEnumObject::new).orElse(null);
	}

	@Define("artist")
	public String getArtist() {
		return getMusic().getArtist();
	}

	@Define("albumArtist")
	public String getAlbumArtist() {
		return getMusic().getAlbumArtist();
	}

	@Define("album")
	public String getAlbum() {
		return getMusic().getAlbum();
	}

	@Define("episode")
	public Episode getEpisode() {
		return (Episode) infoObject;
	}

	@Define("episodes")
	public List<Episode> getEpisodes() {
		return streamMultiEpisode(getEpisode()).collect(toList());
	}

	@Define("movie")
	public Movie getMovie() {
		return (Movie) infoObject;
	}

	@Define("music")
	public AudioTrack getMusic() {
		return (AudioTrack) infoObject;
	}

	@Define("photo")
	public ImageMetadata getPhoto() throws Exception {
		if (infoObject instanceof PhotoFile) {
			return ((PhotoFile) infoObject).getMetadata();
		}
		return new ImageMetadata((File) infoObject);
	}

	@Define("pi")
	public Integer getPart() {
		if (infoObject instanceof AudioTrack)
			return getMusic().getTrack();
		if (infoObject instanceof MoviePart)
			return ((MoviePart) infoObject).getPartIndex();

		return null;
	}

	@Define("medium")
	public Integer getMedium() {
		// e.g. disc number of a multi-disc album
		return getMusic().getMedium();
	}

	@Define("mediumCount")
	public Integer getMediumCount() {
		return getMusic().getMediumCount();
	}

	@Define("pn")
	public Integer getPartCount() {
		if (infoObject instanceof AudioTrack)
			return getMusic().getTrackCount();
		if (infoObject instanceof MoviePart)
			return ((MoviePart) infoObject).getPartCount();

		return null;
	}

	@Define("type")
	public String getInfoObjectType() {
		return infoObject.getClass().getSimpleName();
	}

	@Define("mime")
	public List<String> getMediaType() throws Exception {
		// format engine does not allow / in binding value
		return SLASH.splitAsStream(MediaTypes.getMediaType(getExtension())).collect(toList());
	}

	@Define("mediaPath")
	public File getMediaPath() throws Exception {
		return getStructurePathTail(getMediaFile());
	}

	@Define("relativeFile")
	public File getRelativeFile() throws Exception {
		// file path relative to the library root folder (e.g. Movies or TV Shows), same as {mediaPath}
		return getMediaPath();
	}

	@Define("f")
	public File getMediaFile() {
		// make sure file is not null, and that it is an existing file
		if (mediaFile == null) {
			throw new IllegalStateException(EXCEPTION_SAMPLE_FILE_NOT_SET);
		}
		return mediaFile;
	}

	@Define("folder")
	public File getMediaParentFolder() {
		return getMediaFile().getParentFile();
	}

	@Define("bytes")
	public ByteSize getFileSize() {
		File inferredMediaFile = getInferredMediaFile();

		// sum size of all files
		if (inferredMediaFile.isDirectory()) {
			return new ByteSize(listFiles(getMediaFile(), FILES).stream().mapToLong(File::length).sum());
		}

		// size of inferred media file (e.g. video file size for subtitle file)
		return new ByteSize(inferredMediaFile.length());
	}

	@Define("drive")
	public File getDrive() throws Exception {
		Path path = getMediaFile().toPath().toAbsolutePath();

		// drive letter or network share, e.g. D:\ or \\server\share\
		if (File.separatorChar == '\\') {
			return path.getRoot().toFile();
		}

		// mount point, e.g. /Volumes/Media or /mnt/media
		Path folder = Files.isDirectory(path) ? path : path.getParent();
		FileStore store = Files.getFileStore(folder);
		while (folder.getParent() != null && store.equals(Files.getFileStore(folder.getParent()))) {
			folder = folder.getParent();
		}
		return folder.toFile();
	}

	@Define("files")
	public List<File> getFiles() throws Exception {
		File file = getMediaFile();

		// all files in the folder (e.g. disk folders or folder mode)
		if (file.isDirectory()) {
			return listFiles(file, FILES, HUMAN_NAME_ORDER);
		}

		// archive contents as relative paths
		if (ARCHIVE_FILES.accept(file)) {
			try (Archive archive = Archive.open(file)) {
				return archive.listFiles().stream().map(FileInfo::toFile).collect(toList());
			}
		}

		return singletonList(file);
	}

	@Define("megabytes")
	public String getFileSizeInMegaBytes() {
		return String.format("%.0f", getFileSize().longValue() / 1e6);
	}

	@Define("gigabytes")
	public String getFileSizeInGigaBytes() {
		return String.format("%.1f", getFileSize().longValue() / 1e9);
	}

	@Define("encodedDate")
	public SimpleDate getEncodedDate() {
		return new SimpleDate(getMediaInfo().getCreationTime().toEpochMilli());
	}

	@Define("today")
	public SimpleDate getToday() {
		return new SimpleDate(LocalDateTime.now());
	}

	@Define("home")
	public File getUserHome() {
		return ApplicationFolder.UserHome.get();
	}

	@Define("output")
	public File getUserDefinedOutputFolder() throws IOException {
		return new File(Settings.getApplicationArguments().output).getCanonicalFile();
	}

	@Define("defines")
	public Map<String, String> getUserDefinedArguments() throws IOException {
		return unmodifiableMap(Settings.getApplicationArguments().defines);
	}

	@Define("label")
	public String getUserDefinedLabel() throws IOException {
		return getUserDefinedArguments().entrySet().stream().filter(it -> {
			return it.getKey().endsWith("label") && it.getValue() != null && it.getValue().length() > 0;
		}).map(it -> it.getValue()).findFirst().orElse(null);
	}

	@Define("i")
	public Integer getModelIndex() {
		return 1 + identityIndexOf(context.values(), getInfoObject());
	}

	@Define("di")
	public Integer getDuplicateIndex() {
		return 1 + identityIndexOf(context.values().stream().filter(getInfoObject()::equals).collect(toList()), getInfoObject());
	}

	@Define("dc")
	public Integer getDuplicateCount() {
		return context.values().stream().filter(getInfoObject()::equals).mapToInt(i -> 1).sum();
	}

	@Define("plex")
	public StandardPath getPlexStandardPath() throws Exception {
		return getStandardPath(NamingStandard.PLEX);
	}

	@Define("kodi")
	public StandardPath getKodiStandardPath() throws Exception {
		return getStandardPath(NamingStandard.KODI);
	}

	@Define("emby")
	public StandardPath getEmbyStandardPath() throws Exception {
		return getStandardPath(NamingStandard.EMBY);
	}

	@Define("jellyfin")
	public StandardPath getJellyfinStandardPath() throws Exception {
		return getStandardPath(NamingStandard.JELLYFIN);
	}

	private StandardPath getStandardPath(NamingStandard standard) {
		String path = standard.getPath(infoObject);
		if (path == null) {
			return undefined("path");
		}

		String suffix = null;
		try {
			suffix = getSubtitleTags(); // undefined for video files
		} catch (Exception e) {
			// ignore => no language tags
		}
		// disc extras (e.g. Featurettes/Behind The Story) in the movie, season or series folder
		DiscTitles.Title disc = getDiscTitleObject();
		if (disc != null && disc.isExtra()) {
			String extra = standard.getExtrasFolder(disc.getType()) + "/" + validateFileName(disc.getName().isBlank() ? disc.getType() + " " + disc.getIndex() : disc.getName());
			boolean seriesFolder = infoObject instanceof Episode && disc.getSeason() == null && !getEpisode().isAnime();
			return new StandardPath(path, standard, infoObject, suffix == null ? "" : suffix, p -> {
				File folder = seriesFolder ? new File(p).getParentFile().getParentFile() : new File(p).getParentFile();
				return new File(folder, extra).getPath();
			});
		}

		return new StandardPath(path, standard, infoObject, suffix == null ? "" : suffix);
	}

	@Define("disc")
	public DiscTitles.Title getDiscTitle() {
		DiscTitles.Title disc = getDiscTitleObject();
		return disc != null ? disc : undefined("disc");
	}

	/**
	 * Disc title of the media file (or of the video file of a subtitle file) if it has been identified as a disc rip
	 */
	private DiscTitles.Title getDiscTitleObject() {
		DiscTitles.Title disc = DiscTitles.getTitle(mediaFile);
		if (disc == null && mediaFile != null && !VIDEO_FILES.accept(mediaFile)) {
			try {
				disc = DiscTitles.getTitle(getInferredMediaFile());
			} catch (Exception e) {
				// no video file
			}
		}
		return disc;
	}

	@Define("self")
	public AssociativeScriptObject getSelf() {
		return createBindingObject(mediaFile, infoObject, context, property -> null);
	}

	@Define("model")
	public List<AssociativeScriptObject> getModel() {
		List<AssociativeScriptObject> result = new ArrayList<AssociativeScriptObject>();
		for (Entry<File, ?> it : context.entrySet()) {
			result.add(createBindingObject(it.getKey(), it.getValue(), context, property -> null));
		}
		return result;
	}

	@Define("json")
	public String getInfoObjectDump() {
		return MetaAttributes.toJson(infoObject);
	}

	@Define("ffprobe")
	public Object getFFProbeDump() throws Exception {
		return new FFProbe().open(getInferredMediaFile());
	}

	@Define("XEM")
	public DynamicBindings getXrossEntityMapper() {
		return new DynamicBindings(XEM::names, k -> {
			if (infoObject instanceof Episode) {
				Episode e = getEpisode();
				XEM origin = XEM.forName(e.getSeriesInfo().getDatabase());
				XEM destination = XEM.forName(k);
				if (origin == destination) {
					return e;
				}
				return origin.map(e, destination).orElse(e);
			}
			return undefined(k);
		});
	}

	@Define("AnimeList")
	public DynamicBindings getAnimeLists() {
		return new DynamicBindings(AnimeLists.DB::names, k -> {
			if (infoObject instanceof Episode) {
				Episode e = getEpisode();
				if (AnimeLists.getDB(e) == AnimeLists.getDB(k)) {
					return e;
				}
				return AnimeList.map(e, AnimeLists.getDB(e), AnimeLists.getDB(k)).orElse(e);
			}
			return undefined(k);
		});
	}

	@Define("db")
	public DynamicBindings getDatabaseMapper() {
		return new DynamicBindings(MediaBindingBean::getDatabaseMapperNames, k -> {
			if (infoObject instanceof Episode) {
				Optional<Episode> mapping = mapEpisode(getEpisode(), k);
				if (mapping.isPresent()) {
					return createBindingObject(mapping.get());
				}
			}
			return undefined(k);
		});
	}

	private static final String THE_MOVIE_DB = "TheMovieDB";

	private static Collection<?> getDatabaseMapperNames() {
		return Stream.of(AnimeLists.DB.names().stream(), XEM.names().stream(), Stream.of(THE_MOVIE_DB)).flatMap(s -> s).distinct().collect(toList());
	}

	private static boolean isTheMovieDB(String db) {
		return THE_MOVIE_DB.equalsIgnoreCase(db) || TheMovieDB_TV.getIdentifier().equalsIgnoreCase(db);
	}

	private Optional<Episode> mapEpisode(Episode e, String k) throws Exception {
		String origin = e.getSeriesInfo().getDatabase();
		if (origin.equalsIgnoreCase(k) || (isTheMovieDB(origin) && isTheMovieDB(k))) {
			return Optional.of(e);
		}

		// TheTVDB and TheMovieDB via TheMovieDB external ids (other databases via TheTVDB)
		if (isTheMovieDB(k)) {
			if (TheTVDB.getIdentifier().equalsIgnoreCase(origin)) {
				return mapTheTVDBToTheMovieDB(e, e.getSeriesInfo().getId(), getSeriesLocale(e));
			}

			// mapped episodes may only have season and episode numbers
			Optional<Episode> tvdb = mapEpisode(e, TheTVDB.getIdentifier());
			if (tvdb.isPresent()) {
				SeriesInfo info = tvdb.get().getSeriesInfo();
				Integer series = info != null && TheTVDB.getIdentifier().equals(info.getDatabase()) ? info.getId() : AniDB.getIdentifier().equals(origin) ? AnimeList.map(e.getSeriesInfo().getId(), 1, AnimeLists.DB.AniDB, AnimeLists.DB.TheTVDB).orElse(null) : null;
				return mapTheTVDBToTheMovieDB(tvdb.get(), series, getSeriesLocale(e));
			}
			return Optional.empty();
		}

		if (isTheMovieDB(origin)) {
			Optional<Episode> tvdb = mapTheMovieDBToTheTVDB(e);
			return tvdb.isEmpty() || TheTVDB.getIdentifier().equalsIgnoreCase(k) ? tvdb : mapEpisode(tvdb.get(), k);
		}

		// prefer AnimeList mappings and use XEM mappings as fallback
		if (AnimeLists.DB.names().stream().anyMatch(origin::equalsIgnoreCase) && AnimeLists.DB.names().stream().anyMatch(k::equalsIgnoreCase)) {
			Optional<Episode> mapping = AnimeList.map(e, AnimeLists.getDB(origin), AnimeLists.getDB(k));
			if (mapping.isPresent()) {
				return mapping;
			}
		}

		if (XEM.names().stream().anyMatch(origin::equalsIgnoreCase) && XEM.names().stream().anyMatch(k::equalsIgnoreCase)) {
			return XEM.forName(origin).map(e, XEM.forName(k));
		}

		return Optional.empty();
	}

	/**
	 * @param tvdbSeries
	 *            TheTVDB series id (or null)
	 */
	private Optional<Episode> mapTheTVDBToTheMovieDB(Episode e, Integer tvdbSeries, Locale locale) {
		try {
			Integer series = null, season = null, number = null;

			// TheMovieDB knows the TheTVDB episode id
			int[] episode = e.getId() == null ? null : TheMovieDB_TV.findEpisodeByTheTVDBId(e.getId());
			if (episode != null) {
				series = episode[0];
				season = episode[1];
				number = episode[2];
			} else if (tvdbSeries != null) {
				// same season and episode numbers in the TheMovieDB series of the TheTVDB series
				series = TheMovieDB_TV.findByTheTVDBId(tvdbSeries);
				season = e.getSpecial() != null ? Integer.valueOf(0) : e.getSeason();
				number = e.getSpecial() != null ? e.getSpecial() : e.getEpisode();
			}

			if (series == null || season == null || number == null) {
				return Optional.empty();
			}

			int s = season, n = number;
			return TheMovieDB_TV.getEpisodeList(series, SortOrder.Airdate, locale).stream().filter(it -> isEpisodeNumber(it, s, n)).findFirst();
		} catch (Exception ex) {
			debug.finest(cause("Failed to map episode to TheMovieDB", ex));
			return Optional.empty();
		}
	}

	private Optional<Episode> mapTheMovieDBToTheTVDB(Episode e) {
		try {
			int series = e.getSeriesInfo().getId();
			Locale locale = getSeriesLocale(e);

			Integer tvdbSeries = TheMovieDB_TV.getTheTVDBId(series);
			if (tvdbSeries == null) {
				return Optional.empty();
			}

			// default season and episode numbers (the episode may be numbered by an episode group)
			Episode original = TheMovieDB_TV.getEpisodeList(series, SortOrder.Airdate, locale).stream().filter(it -> Objects.equals(it.getId(), e.getId())).findFirst().orElse(e);
			Integer season = original.getSpecial() != null ? Integer.valueOf(0) : original.getSeason();
			Integer number = original.getSpecial() != null ? original.getSpecial() : original.getEpisode();
			if (season == null || number == null) {
				return Optional.empty();
			}

			List<Episode> episodes = TheTVDB.getEpisodeList(tvdbSeries, SortOrder.Airdate, locale);

			// TheMovieDB knows the TheTVDB episode id
			try {
				Integer id = TheMovieDB_TV.getTheTVDBEpisodeId(series, season, number);
				Optional<Episode> episode = episodes.stream().filter(it -> id != null && id.equals(it.getId())).findFirst();
				if (episode.isPresent()) {
					return episode;
				}
			} catch (Exception ex) {
				debug.finest(cause("Failed to retrieve TheTVDB episode id", ex));
			}

			// same season and episode numbers
			int s = season, n = number;
			return episodes.stream().filter(it -> isEpisodeNumber(it, s, n)).findFirst();
		} catch (Exception ex) {
			debug.finest(cause("Failed to map episode to TheTVDB", ex));
			return Optional.empty();
		}
	}

	private static boolean isEpisodeNumber(Episode e, int season, int number) {
		return season == 0 ? Objects.equals(e.getSpecial(), number) : Objects.equals(e.getSeason(), season) && Objects.equals(e.getEpisode(), number);
	}

	private static Locale getSeriesLocale(Episode e) {
		String language = e.getSeriesInfo().getLanguage();
		return language == null ? Locale.ENGLISH : Locale.forLanguageTag(language);
	}

	/**
	 * Series info with season names, countries and all certifications (season names and certifications are not stored
	 * in xattr metadata, so fetch them again if necessary)
	 */
	public SeriesInfo getExtendedSeriesInfo() {
		SeriesInfo info = getSeriesInfo();
		if (info.getSeasonNames().isEmpty() && info.getCertifications().isEmpty() && info.getId() != null) {
			try {
				EpisodeListProvider db = getEpisodeListProvider(info.getDatabase());
				if (db != null) {
					return db.getSeriesInfo(info.getId(), info.getLanguage() == null ? Locale.ENGLISH : Locale.forLanguageTag(info.getLanguage()));
				}
			} catch (Exception e) {
				debug.warning("Failed to retrieve extended series info: " + e); // default to seriesInfo property
			}
		}
		return info;
	}

	public SeriesInfo getPrimarySeriesInfo() {
		if (isInstance(TheTVDB, getSeriesInfo())) {
			try {
				return TheTVDB.getSeriesInfo(getSeriesInfo().getId(), Locale.ENGLISH);
			} catch (Exception e) {
				debug.warning("Failed to retrieve primary series info: " + e); // default to seriesInfo property
			}
		}
		return getSeriesInfo();
	}

	private final Resource<MovieInfo> primaryMovieInfo = Resource.lazy(() -> TheMovieDB.getMovieInfo(getMovie(), Locale.US, false));
	private final Resource<MovieInfo> extendedMovieInfo = Resource.lazy(() -> getMovieInfo(getMovie().getLanguage(), true));

	public MovieInfo getPrimaryMovieInfo() {
		try {
			return primaryMovieInfo.get();
		} catch (Exception e) {
			throw new BindingException("info", "Failed to retrieve primary movie info: " + e, e);
		}
	}

	public MovieInfo getMovieInfo() {
		try {
			return extendedMovieInfo.get();
		} catch (Exception e) {
			throw new BindingException("info", "Failed to retrieve extended movie info: " + e, e);
		}
	}

	public synchronized MovieInfo getMovieInfo(Locale locale, boolean extendedInfo) throws Exception {
		Movie m = getMovie();

		if (m.getTmdbId() > 0)
			return TheMovieDB.getMovieInfo(m, locale == null ? Locale.US : locale, extendedInfo);
		if (m.getImdbId() > 0)
			return OMDb.getMovieInfo(m);

		return null;
	}

	// lazy initialize and then keep in memory
	private MediaInfo mediaInfo;

	public synchronized MediaInfo getMediaInfo() {
		// use inferred media file (e.g. actual movie file instead of subtitle file)
		if (mediaInfo == null) {
			mediaInfo = mediaInfoCache.get(getInferredMediaFile(), f -> {
				try {
					return CachedMediaInfo.read(f);
				} catch (Exception e) {
					throw new MediaInfoException(e.getMessage());
				}
			});
		}
		return mediaInfo;
	}

	// lazy initialize and then keep in memory
	private File inferredMediaFile;

	public synchronized File getInferredMediaFile() {
		if (inferredMediaFile == null) {
			inferredMediaFile = getInferredMediaFile(getMediaFile());
		}
		return inferredMediaFile;
	}

	private File getInferredMediaFile(File file) {
		if (file.isDirectory()) {
			// just select the first video file in the folder as media sample
			List<File> videos = getChildren(file, VIDEO_FILES, HUMAN_NAME_ORDER);
			if (videos.isEmpty()) {
				// check for indirect descendant video files as well
				videos = listFiles(file, VIDEO_FILES, HUMAN_NAME_ORDER);
			}
			if (videos.size() > 0) {
				return videos.get(0);
			}
		} else if ((SUBTITLE_FILES.accept(file) || IMAGE_FILES.accept(file)) || ((infoObject instanceof Episode || infoObject instanceof Movie) && !VIDEO_FILES.accept(file))) {
			// prefer equal match from current context if possible
			if (context != null) {
				for (Entry<File, ?> it : context.entrySet()) {
					if (infoObject.equals(it.getValue()) && VIDEO_FILES.accept(it.getKey())) {
						return it.getKey();
					}
				}
			}

			// file is a subtitle, or nfo, etc
			String baseName = stripReleaseInfo(FileUtilities.getName(file)).toLowerCase();
			List<File> videos = getChildren(file.getParentFile(), VIDEO_FILES);

			// find corresponding movie file
			for (File movieFile : videos) {
				if (!baseName.isEmpty() && stripReleaseInfo(FileUtilities.getName(movieFile)).toLowerCase().startsWith(baseName)) {
					return movieFile;
				}
			}

			// still no good match found -> just take the most probable video from the same folder
			if (videos.size() > 0) {
				sort(videos, SimilarityComparator.compareTo(FileUtilities.getName(file), FileUtilities::getName));
				return videos.get(0);
			}
		}

		return file;
	}

	private Integer identityIndexOf(Iterable<?> c, Object o) {
		Iterator<?> itr = c.iterator();
		for (int i = 0; itr.hasNext(); i++) {
			Object next = itr.next();
			if (o == next) {
				return i;
			}
		}
		return null;
	}

	private String getMediaInfo(StreamKind streamKind, int streamNumber, String... keys) {
		for (String key : keys) {
			String value = getMediaInfo().get(streamKind, streamNumber, key);
			if (value.length() > 0) {
				return value;
			}
		}
		return undefined(String.format("%s[%d][%s]", streamKind, streamNumber, join(keys, ", ")));
	}

	private Stream<String> getMediaInfo(StreamKind streamKind, String... keys) {
		return IntStream.range(0, getMediaInfo().streamCount(streamKind)).mapToObj(streamNumber -> {
			return stream(keys).map(key -> {
				return getMediaInfo().get(streamKind, streamNumber, key);
			}).filter(s -> s.length() > 0).findFirst().orElse(null);
		}).filter(Objects::nonNull);
	}

	private AssociativeScriptObject createBindingObject(Object info) {
		return createBindingObject(null, info, null, this::undefined);
	}

	private AssociativeScriptObject createBindingObject(File file, Object info, Map<File, ?> context, Function<String, Object> defaultValue) {
		MediaBindingBean mediaBindingBean = new MediaBindingBean(info, file, context) {

			@Override
			@Define(undefined)
			public <T> T undefined(String name) {
				return null; // never throw exceptions for empty or null values
			}
		};

		return new AssociativeScriptObject(new ExpressionBindings(mediaBindingBean), defaultValue);
	}

	private AssociativeScriptObject createPropertyBindings(Object object) {
		return new AssociativeScriptObject(new PropertyBindings(object), this::undefined);
	}

	private List<AssociativeScriptObject> createMediaInfoBindings(StreamKind kind) {
		return getMediaInfo().snapshot().get(kind).stream().map(m -> new AssociativeScriptObject(m, this::undefined)).collect(toList());
	}

	private final Resource<String[]> mediaTitles = Resource.lazy(() -> {
		// try to place embedded media title first
		try {
			String mediaTitle = getMediaInfo().getTitle();
			if (mediaTitle.length() > 0) {
				return Stream.concat(Stream.of(mediaTitle), stream(getFileNames(getInferredMediaFile()))).toArray(String[]::new);
			}
		} catch (Exception e) {
			debug.warning("Failed to read media title: " + e.getMessage());
		}

		// default to file name and xattr
		return getFileNames(getInferredMediaFile());
	});

	private String[] getFileNames(File file) {
		List<String> names = new ArrayList<String>();

		// original file name via xattr
		String original = xattr.getOriginalName(file);
		if (original != null) {
			names.add(getNameWithoutExtension(original));
		}

		// current file name
		names.add(getNameWithoutExtension(file.getName()));

		// current folder name
		File parent = file.getParentFile();
		if (parent != null && parent.getParent() != null) {
			names.add(parent.getName());
		}

		return names.toArray(new String[0]);
	}

	private Pattern getKeywordExcludePattern() {
		// collect key information
		List<Object> keys = new ArrayList<Object>();

		if (infoObject instanceof Episode || infoObject instanceof Movie) {
			keys.add(getName());
			keys.addAll(getAliasNames());

			if (infoObject instanceof Episode) {
				for (Episode e : getEpisodes()) {
					keys.add(e.getTitle());
				}
			} else if (infoObject instanceof Movie) {
				keys.add(getMovie().getYear());
			}
		}

		// word list for exclude pattern
		String pattern = keys.stream().filter(Objects::nonNull).map(Objects::toString).map(s -> {
			return normalizePunctuation(s, " ", "\\P{Alnum}+");
		}).filter(s -> s.length() > 0).collect(joining("|", "\\b(", ")\\b"));

		return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
	}

	@Override
	public String toString() {
		return String.format("%s ⇔ %s", infoObject, mediaFile == null ? null : mediaFile.getName());
	}

	public static final String EXCEPTION_UNDEFINED = "undefined";
	public static final String EXCEPTION_SAMPLE_FILE_NOT_SET = "Sample file has not been set. Click \"Change Sample\" to select a sample file.";

	/**
	 * Reading MediaInfo can be a very expensive operation, especially if network drives are involved, so we do our best to cache it for a while, because memory is cheap, and time is not.
	 */
	private static final Cache<File, MediaInfo> mediaInfoCache = Caffeine.newBuilder().expireAfterAccess(20, TimeUnit.MINUTES).build();

}
