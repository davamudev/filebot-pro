package dev.davamu.filebotpro.media;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.Logging.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.JsonWriter;

import dev.davamu.filebotpro.MetaAttributeView;
import dev.davamu.filebotpro.vfs.SimpleFileInfo;
import dev.davamu.filebotpro.web.AudioTrack;
import dev.davamu.filebotpro.web.Episode;
import dev.davamu.filebotpro.web.MappedEpisode;
import dev.davamu.filebotpro.web.Movie;
import dev.davamu.filebotpro.web.MoviePart;
import dev.davamu.filebotpro.util.LegacyNamespace;
import dev.davamu.filebotpro.web.MultiEpisode;
import dev.davamu.filebotpro.web.SeriesInfo;
import dev.davamu.filebotpro.web.TheTVDBSeriesInfo;

public class MetaAttributes {

	public static final String FILENAME_KEY = "dev.davamu.filebotpro.filename";
	public static final String METADATA_KEY = "dev.davamu.filebotpro.metadata";

	// tags written by older versions are still read
	public static final String LEGACY_FILENAME_KEY = "net.filebot.filename";
	public static final String LEGACY_METADATA_KEY = "net.filebot.metadata";

	public static final Map<String, String> JSON_TYPE_MAP = unmodifiableMap(Stream.of(Episode.class, MultiEpisode.class, MappedEpisode.class, Movie.class, MoviePart.class, AudioTrack.class, SimpleFileInfo.class).collect(toMap(Class::getName, Class::getSimpleName)));

	private final BasicFileAttributeView fileAttributeView;
	private final MetaAttributeView metaAttributeView;

	public MetaAttributes(File file) throws IOException {
		this.metaAttributeView = new MetaAttributeView(file);
		this.fileAttributeView = Files.getFileAttributeView(file.toPath(), BasicFileAttributeView.class);
	}

	public void setCreationDate(long millis) throws IOException {
		fileAttributeView.setTimes(null, null, FileTime.fromMillis(millis));
	}

	public long getCreationDate(long time) {
		try {
			return fileAttributeView.readAttributes().creationTime().toMillis();
		} catch (IOException e) {
			return 0;
		}
	}

	public void setOriginalName(String name) {
		metaAttributeView.put(FILENAME_KEY, name);
	}

	public String getOriginalName() {
		String name = metaAttributeView.get(FILENAME_KEY);
		return name != null ? name : metaAttributeView.get(LEGACY_FILENAME_KEY);
	}

	public void setObject(Object object) {
		metaAttributeView.put(METADATA_KEY, toJson(object));
	}

	public Object getObject() {
		String json = metaAttributeView.get(METADATA_KEY);
		if (json == null || json.isEmpty()) {
			json = metaAttributeView.get(LEGACY_METADATA_KEY);
		}
		if (json != null && json.length() > 0) {
			return toObject(json);
		}
		return null;
	}

	public void clear() {
		metaAttributeView.put(FILENAME_KEY, null);
		metaAttributeView.put(METADATA_KEY, null);
		metaAttributeView.put(LEGACY_FILENAME_KEY, null);
		metaAttributeView.put(LEGACY_METADATA_KEY, null);
	}

	public static String toJson(Object object) {
		Map<String, Object> options = new HashMap<String, Object>();
		options.put(JsonWriter.TYPE_NAME_MAP, JSON_TYPE_MAP);
		options.put(JsonWriter.SKIP_NULL_FIELDS, true);

		// keep xattr values small (e.g. 4 KB limit on ext4), the data source still has these fields
		List<String> seriesInfoBlackList = asList("certifications", "seasonNames");
		options.put(JsonWriter.FIELD_NAME_BLACK_LIST, Map.of(SeriesInfo.class, seriesInfoBlackList, TheTVDBSeriesInfo.class, seriesInfoBlackList));

		return JsonWriter.objectToJson(object, options);
	}

	public static Object toObject(String json) {
		if (json == null || json.isEmpty()) {
			return null;
		}

		Map<String, Object> options = new HashMap<String, Object>();
		options.put(JsonReader.TYPE_NAME_MAP, JSON_TYPE_MAP);

		// options must be a modifiable map (type names of older versions are migrated to the current package)
		String migrated = LegacyNamespace.migrate(json);

		// the JSON comes from extended attributes / alternate data streams of media files, which anyone can write:
		// never let it instantiate arbitrary classes
		if (!hasOnlyTrustedTypes(migrated)) {
			return null;
		}

		return JsonReader.jsonToJava(migrated, options);
	}

	// json-io also accepts the short key "@t"
	private static final Pattern JSON_TYPE = Pattern.compile("\"@t(?:ype)?\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

	private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

	// json-io decodes the escapes of keys and values, so the check has to look at the decoded text
	private static String decodeUnicodeEscapes(String json) {
		return UNICODE_ESCAPE.matcher(json).replaceAll(m -> Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1), 16))));
	}

	private static final Pattern TRUSTED_TYPE = Pattern.compile("[a-z]+" // json-io aliases of primitives and basic types (int, long, string, date, ...)
			+ "|\\[*L?(java\\.lang\\.(Object|String|Integer|Long|Double|Float|Short|Byte|Boolean|Character)|dev\\.davamu\\.filebotpro\\.[\\w.$]+);?"
			+ "|java\\.lang\\.(String|Integer|Long|Double|Float|Short|Byte|Boolean|Character)"
			+ "|java\\.util\\.(ArrayList|LinkedList|HashMap|LinkedHashMap|TreeMap|HashSet|LinkedHashSet|TreeSet|Date|Locale|EnumMap|EnumSet)"
			+ "|java\\.util\\.(ImmutableCollections|Collections)\\$\\w+"
			+ "|dev\\.davamu\\.filebotpro\\.[\\w.$]+");

	static boolean hasOnlyTrustedTypes(String json) {
		Matcher matcher = JSON_TYPE.matcher(decodeUnicodeEscapes(json));
		while (matcher.find()) {
			String type = matcher.group(1);
			if (!TRUSTED_TYPE.matcher(type).matches() && !JSON_TYPE_MAP.containsValue(type)) {
				debug.warning("Ignored metadata with untrusted type: " + type);
				return false;
			}
		}
		return true;
	}

}
