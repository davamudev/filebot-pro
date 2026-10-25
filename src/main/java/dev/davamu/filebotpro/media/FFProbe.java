package dev.davamu.filebotpro.media;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.Execute.*;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import dev.davamu.filebotpro.Cache;
import dev.davamu.filebotpro.CacheType;
import dev.davamu.filebotpro.mediainfo.CachedMediaInfo;
import dev.davamu.filebotpro.util.JsonUtilities;

public class FFProbe implements MediaCharacteristics {

	public String getFFProbeCommand() {
		return System.getProperty("dev.davamu.filebotpro.media.ffprobe", "ffprobe");
	}

	public String version() throws IOException {
		return execute(getFFProbeCommand(), "-show_program_version", "-hide_banner").toString().trim();
	}

	protected Map<String, Object> parse(File file) throws IOException, InterruptedException {
		String[] command = { getFFProbeCommand(), "-show_streams", "-show_format", "-print_format", "json", "-v", "error", file.getCanonicalPath() };

		// cache ffprobe output (same path, size and last modified time)
		Object output;
		try {
			output = Cache.getCache("ffprobe", CacheType.Monthly).computeIfAbsent(CachedMediaInfo.getCacheKey(file), element -> {
				return execute(command, file.getParentFile()).toString();
			});
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException(e);
		}

		// parse process standard output
		return (Map) JsonUtilities.readJson(output.toString());
	}

	private Map<String, Object> json;

	public synchronized FFProbe open(File file) throws IOException, InterruptedException {
		json = parse(file);
		return this;
	}

	@Override
	public synchronized void close() {
		json = null;
	}

	@Override
	public String getVideoCodec() {
		return getString("video", "codec_name");
	}

	@Override
	public String getAudioCodec() {
		return getString("audio", "codec_name");
	}

	@Override
	public String getAudioLanguage() {
		return getString("audio", "tags", "language");
	}

	@Override
	public String getSubtitleCodec() {
		return getString("subtitle", "codec_name");
	}

	@Override
	public String getSubtitleLanguage() {
		return getString("subtitle", "tags", "language");
	}

	@Override
	public Duration getDuration() {
		long d = (long) (Double.parseDouble(getFormat().get("duration").toString()) * 1000);
		return Duration.ofMillis(d);
	}

	@Override
	public Integer getWidth() {
		return getInteger("video", "width");
	}

	@Override
	public Integer getHeight() {
		return getInteger("video", "height");
	}

	@Override
	public Double getBitRate() {
		return Double.parseDouble(getFormat().get("bit_rate").toString());
	}

	@Override
	public Float getFrameRate() {
		return find("video", "avg_frame_rate").map(fps -> {
			switch (fps) {
			case "500/21":
				return 23.976f; // normalize FPS value (using MediaInfo standards)
			default:
				// ffprobe reports frame rates as fractions (e.g. 24000/1001), rounded like MediaInfo (23.976)
				String[] f = fps.split("/");
				float value = f.length == 2 ? Float.parseFloat(f[0]) / Float.parseFloat(f[1]) : Float.parseFloat(fps);
				return Math.round(value * 1000) / 1000f;
			}
		}).get();
	}

	@Override
	public String getTitle() {
		return getTag("title").orElse(null);
	}

	@Override
	public Instant getCreationTime() {
		return getTag("creation_time").map(this::parseDateTime).orElse(null);
	}

	private Instant parseDateTime(String s) {
		// ISO 8601 (e.g. 2024-05-06T07:08:09.000000Z) or the older 2024-05-06 07:08:09
		return LocalDateTime.parse(s.replace(' ', 'T').replaceFirst("Z$", ""), DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC);
	}

	public Map<String, Object> getFormat() {
		return (Map) json.get("format");
	}

	public Map<String, Object> getTags() {
		return (Map<String, Object>) getFormat().get("tags");
	}

	public Optional<String> getTag(String tag) {
		return Optional.ofNullable(getTags()).map(m -> (String) m.get(tag));
	}

	public List<Map<String, Object>> getStreams() {
		return (List) asList((Object[]) json.get("streams"));
	}

	protected String getString(String streamKind, String key) {
		return stream(streamKind, key).map(Objects::toString).collect(joining(" "));
	}

	protected String getString(String streamKind, String objectKey, String valueKey) {
		return stream(streamKind, objectKey).map(t -> ((Map) t).get(valueKey)).map(Objects::toString).collect(joining(" / "));
	}

	protected Stream<Object> stream(String streamKind, String property) {
		return getStreams().stream().filter(s -> streamKind.equals(s.get("codec_type"))).map(s -> s.get(property)).filter(Objects::nonNull);
	}

	protected Integer getInteger(String streamKind, String property) {
		return find(streamKind, property).map(Integer::parseInt).get();
	}

	protected Optional<String> find(String streamKind, String property) {
		return stream(streamKind, property).map(Objects::toString).findFirst();
	}

	@Override
	public String toString() {
		return JsonUtilities.json(json, false);
	}

}
