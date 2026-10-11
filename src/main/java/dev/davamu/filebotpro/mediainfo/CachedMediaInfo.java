package dev.davamu.filebotpro.mediainfo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import dev.davamu.filebotpro.Cache;
import dev.davamu.filebotpro.CacheType;

/**
 * MediaInfo backed by a snapshot of all the fields, which is kept in a persistent cache so that files that were already read (e.g. when
 * processing the same files again, or files on slow network shares) don't have to be read again
 */
public class CachedMediaInfo extends MediaInfo {

	public static MediaInfo read(File file) throws Exception {
		if (!file.isFile() || file.length() <= 0) {
			throw new IllegalArgumentException("Invalid media file: " + file);
		}

		Object snapshot = getCache().computeIfAbsent(getCacheKey(file), element -> {
			return snapshot(file);
		});

		@SuppressWarnings("unchecked")
		Map<StreamKind, List<Map<String, String>>> data = (Map<StreamKind, List<Map<String, String>>>) snapshot;
		return new CachedMediaInfo(data);
	}

	/**
	 * Same path, size and last modified time, so modified files are read again
	 */
	public static String getCacheKey(File file) throws IOException {
		return file.getCanonicalPath() + "|" + file.length() + "|" + file.lastModified();
	}

	public static Cache getCache() {
		return Cache.getCache("mediainfo", CacheType.Monthly);
	}

	private final Map<StreamKind, List<Map<String, String>>> data;

	public CachedMediaInfo(Map<StreamKind, List<Map<String, String>>> data) {
		super(null);
		this.data = data;
	}

	private Map<String, String> getStream(StreamKind streamKind, int streamNumber) {
		List<Map<String, String>> streams = data.get(streamKind);
		return streams == null || streamNumber < 0 || streamNumber >= streams.size() ? Map.of() : streams.get(streamNumber);
	}

	@Override
	public synchronized MediaInfo open(File file) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized String option(String option, String value) {
		return "";
	}

	@Override
	public synchronized String inform() {
		StringBuilder text = new StringBuilder();
		data.forEach((streamKind, streams) -> {
			for (Map<String, String> stream : streams) {
				text.append(streamKind).append('\n');
				stream.forEach((k, v) -> text.append(String.format("%-40s: %s%n", k, v)));
				text.append('\n');
			}
		});
		return text.toString();
	}

	@Override
	public synchronized String get(StreamKind streamKind, int streamNumber, String parameter, InfoKind infoKind, InfoKind searchKind) {
		if (searchKind != InfoKind.Name) {
			return "";
		}

		Map<String, String> stream = getStream(streamKind, streamNumber);
		switch (infoKind) {
		case Text:
			return stream.getOrDefault(parameter, "");
		case Name:
			return stream.containsKey(parameter) ? parameter : "";
		default:
			return "";
		}
	}

	@Override
	public synchronized String get(StreamKind streamKind, int streamNumber, int parameterIndex, InfoKind infoKind) {
		List<Entry<String, String>> parameters = new ArrayList<Entry<String, String>>(getStream(streamKind, streamNumber).entrySet());
		if (parameterIndex < 0 || parameterIndex >= parameters.size()) {
			return "";
		}

		switch (infoKind) {
		case Text:
			return parameters.get(parameterIndex).getValue();
		case Name:
			return parameters.get(parameterIndex).getKey();
		default:
			return "";
		}
	}

	@Override
	public synchronized int streamCount(StreamKind streamKind) {
		List<Map<String, String>> streams = data.get(streamKind);
		return streams == null ? 0 : streams.size();
	}

	@Override
	public synchronized int parameterCount(StreamKind streamKind, int streamNumber) {
		return getStream(streamKind, streamNumber).size();
	}

	@Override
	public Map<StreamKind, List<Map<String, String>>> snapshot() {
		Map<StreamKind, List<Map<String, String>>> copy = new EnumMap<StreamKind, List<Map<String, String>>>(StreamKind.class);
		data.forEach((k, v) -> {
			List<Map<String, String>> streams = new ArrayList<Map<String, String>>(v.size());
			v.forEach(it -> streams.add(new LinkedHashMap<String, String>(it)));
			copy.put(k, streams);
		});
		return copy;
	}

	@Override
	public Map<String, String> snapshot(StreamKind streamKind, int streamNumber) {
		return new LinkedHashMap<String, String>(getStream(streamKind, streamNumber));
	}

	@Override
	public synchronized void close() {
		// nothing to release
	}

}
