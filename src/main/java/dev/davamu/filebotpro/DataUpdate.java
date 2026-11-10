package dev.davamu.filebotpro;

import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.util.JsonUtilities.*;
import static java.nio.charset.StandardCharsets.*;
import static java.nio.file.StandardCopyOption.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.tukaani.xz.XZInputStream;

import dev.davamu.filebotpro.media.ReleaseInfo;
import dev.davamu.filebotpro.web.WebRequest;

/**
 * Detection data updates. The indexes in the data folder (gradlew updateData) are regenerated every week by the Update data
 * workflow and published with a manifest.json as assets of the "data" pre-release. About once a week the application
 * downloads the indexes that changed into the data cache folder, in the background. Downloads are only applied on the next
 * start, and the bundled copy is used whenever there is no valid download (first start, offline, corrupt or older files).
 * Failures are only logged.
 */
public final class DataUpdate {

	/**
	 * Structure version of the indexes: raise it (here and nowhere else) only when the structure of the files changes, older
	 * versions of the application then ignore the new files
	 */
	public static final int FORMAT = 1;

	public static final String MANIFEST = "manifest.json";

	private static final String BUNDLED = "/dev/davamu/filebotpro/data/";
	private static final Pattern FILE_NAME = Pattern.compile("[a-z0-9][a-z0-9.-]*\\.xz");

	private static final String SIGNATURE_SUFFIX = ".sig";

	/**
	 * Ed25519 public key (X.509, Base64) of the manifest.json signature, the private key is the DATA_SIGNING_KEY secret
	 */
	private static final String PUBLIC_KEY = "MCowBQYDK2VwAyEArzQXNXoC+ZOBw/o6c1L/DY2wz5N3H+6SNfFBwWxE7OU=";

	private static final Duration CHECK_INTERVAL = Duration.ofDays(7);
	private static final Duration RETRY_INTERVAL = Duration.ofDays(1);

	private static final String PREF_ENABLED = "data.update";

	public record Entry(String sha256, long size, Instant date) {
	}

	/**
	 * Index files by name with their SHA-256, size and the date their content last changed
	 */
	public record Manifest(int format, Instant date, Map<String, Entry> files) {

		public static final Manifest EMPTY = new Manifest(FORMAT, Instant.EPOCH, Collections.emptyMap());

		public static Manifest parse(byte[] json) {
			Object root = readJson(new String(json, UTF_8));
			Map<String, Entry> files = new TreeMap<String, Entry>();
			getMap(root, "files").forEach((k, v) -> {
				String sha256 = getString(v, "sha256");
				Long size = getStringValue(v, "size", Long::parseLong);
				Instant date = getStringValue(v, "date", Instant::parse);
				if (sha256 == null || size == null || date == null) {
					throw new IllegalArgumentException("Invalid manifest entry: " + k);
				}
				files.put(k.toString(), new Entry(sha256.toLowerCase(), size, date));
			});
			Integer format = getInteger(root, "format");
			Instant date = getStringValue(root, "date", Instant::parse);
			if (format == null || date == null) {
				throw new IllegalArgumentException("Invalid manifest");
			}
			return new Manifest(format, date, Collections.unmodifiableMap(files));
		}

		public static Manifest of(Map<String, Entry> files) {
			Instant date = files.values().stream().map(Entry::date).max(Instant::compareTo).orElse(Instant.EPOCH);
			return new Manifest(FORMAT, date, Collections.unmodifiableMap(new TreeMap<String, Entry>(files)));
		}

		public Manifest with(String name, Entry entry) {
			Map<String, Entry> map = new TreeMap<String, Entry>(files);
			map.put(name, entry);
			return of(map);
		}

		public Manifest without(String name) {
			Map<String, Entry> map = new TreeMap<String, Entry>(files);
			map.remove(name);
			return of(map);
		}

		public String toJson() {
			String entries = files.entrySet().stream().map(e -> {
				Entry f = e.getValue();
				return String.format("    \"%s\": { \"sha256\": \"%s\", \"size\": %d, \"date\": \"%s\" }", e.getKey(), f.sha256(), f.size(), f.date());
			}).collect(Collectors.joining(",\n"));
			return String.format("{%n  \"format\": %d,%n  \"date\": \"%s\",%n  \"files\": {%n%s%n  }%n}%n", format, date, entries).replace("\r\n", "\n");
		}
	}

	public static Entry entry(byte[] bytes, Instant date) {
		return new Entry(sha256(bytes), bytes.length, date.truncatedTo(ChronoUnit.SECONDS));
	}

	public static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Manifest URL configured at build time and not disabled with -Dapplication.data.update=skip
	 */
	public static boolean isAvailable() {
		return !"skip".equals(System.getProperty("application.data.update")) && !getApplicationProperty("data.url").isEmpty();
	}

	/**
	 * Available and not turned off by the user (help menu)
	 */
	public static boolean isEnabled() {
		return isAvailable() && !"false".equals(prefs().get(PREF_ENABLED));
	}

	public static void setEnabled(boolean enabled) {
		prefs().put(PREF_ENABLED, enabled ? null : "false");
	}

	/**
	 * Apply the downloads of the last run and check for new data in the background (once per process)
	 */
	public static synchronized void start() {
		if (started) {
			return;
		}
		started = true;

		State state = state();
		if (isEnabled()) {
			Thread thread = new Thread(() -> check(state), "DataUpdate");
			thread.setDaemon(true);
			thread.setPriority(Thread.MIN_PRIORITY);
			thread.start();
		}
	}

	/**
	 * Uncompressed contents of the downloaded copy of the given index (e.g. moviedb.txt) or null if the bundled copy should be
	 * used
	 */
	public static byte[] getData(String name) {
		String file = name + ".xz";
		State state = state();
		if (!state.active().contains(file)) {
			return null;
		}

		try {
			byte[] bytes = Files.readAllBytes(state.folder().resolve(file));
			verify(file, bytes, state.local().files().get(file));
			return decompress(bytes);
		} catch (Exception e) {
			debug.log(Level.INFO, e, () -> "Failed to read downloaded detection data, using the bundled copy: " + file);
			return null;
		}
	}

	/**
	 * Date of the newest index in use and whether it was downloaded, for -script fn:sysinfo
	 */
	public static String getStatus() {
		State state = state();
		Instant date = state.bundled().date();
		for (String file : state.active()) {
			Instant d = state.local().files().get(file).date();
			date = d.isAfter(date) ? d : date;
		}
		String status = date.equals(Instant.EPOCH) ? "unknown" : date.toString().substring(0, 10);
		if (!state.active().isEmpty()) {
			status += " (" + state.active().size() + " updated)";
		}
		return status + (isEnabled() ? "" : " [updates off]");
	}

	/**
	 * Bundled manifest, manifest of the data cache folder (after applying pending downloads) and the downloaded files that are
	 * valid and newer than the bundled copy
	 */
	private record State(Path folder, Manifest bundled, Manifest local, Set<String> active) {
	}

	private static boolean started;
	private static State state;

	private static synchronized State state() {
		if (state == null) {
			state = init();
		}
		return state;
	}

	private static State init() {
		Path folder = ApplicationFolder.Cache.resolve("data").toPath();
		Manifest bundled = readBundledManifest();

		if (!isEnabled()) {
			return new State(folder, bundled, Manifest.EMPTY, Set.of());
		}

		try (Lock lock = Lock.tryAcquire(folder)) {
			if (lock != null) {
				promote(folder);
			}
		} catch (Exception e) {
			debug.log(Level.FINE, e, () -> "Failed to apply downloaded detection data");
		}

		Manifest local = readManifest(folder.resolve(MANIFEST));
		if (local.format() != FORMAT) {
			return new State(folder, bundled, Manifest.EMPTY, Set.of());
		}

		Set<String> active = local.files().entrySet().stream().filter(e -> {
			Entry b = bundled.files().get(e.getKey());
			boolean newer = b == null || (e.getValue().date().isAfter(b.date()) && !e.getValue().sha256().equals(b.sha256()));
			return newer && Files.isRegularFile(folder.resolve(e.getKey()));
		}).map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());

		if (!active.isEmpty()) {
			debug.fine(() -> "Downloaded detection data: " + active);
		}
		return new State(folder, bundled, local, active);
	}

	/**
	 * Move the files downloaded by the last check from the pending folder into the data folder
	 */
	private static void promote(Path folder) throws IOException {
		Path pendingFolder = folder.resolve("pending");
		Path pendingManifest = pendingFolder.resolve(MANIFEST);
		if (!Files.isRegularFile(pendingManifest)) {
			return;
		}

		Manifest pending = readManifest(pendingManifest);
		Manifest local = readManifest(folder.resolve(MANIFEST));
		if (local.format() != pending.format()) {
			local = new Manifest(pending.format(), Instant.EPOCH, Collections.emptyMap());
		}

		Manifest remaining = pending;
		for (Map.Entry<String, Entry> it : pending.files().entrySet()) {
			Path file = pendingFolder.resolve(it.getKey());
			try {
				if (Files.isRegularFile(file)) {
					Files.move(file, folder.resolve(it.getKey()), REPLACE_EXISTING, ATOMIC_MOVE);
					local = local.with(it.getKey(), it.getValue());
				}
				remaining = remaining.without(it.getKey());
			} catch (IOException e) {
				debug.log(Level.FINE, e, () -> "Failed to apply downloaded detection data: " + file);
			}
		}

		writeManifest(folder.resolve(MANIFEST), new Manifest(pending.format(), local.date(), local.files()));
		if (remaining.files().isEmpty()) {
			Files.delete(pendingManifest);
		} else {
			writeManifest(pendingManifest, remaining);
		}
	}

	private static void check(State state) {
		try (Lock lock = Lock.tryAcquire(state.folder())) {
			if (lock == null) {
				return;
			}

			Path statusFile = state.folder().resolve("status.properties");
			Properties status = new Properties();
			if (Files.isRegularFile(statusFile)) {
				try (Reader in = Files.newBufferedReader(statusFile, UTF_8)) {
					status.load(in);
				}
			}

			Instant now = Instant.now();
			Instant checked = Instant.parse(status.getProperty("checked", Instant.EPOCH.toString()));
			Instant attempted = Instant.parse(status.getProperty("attempted", Instant.EPOCH.toString()));
			if (now.isBefore(checked.plus(CHECK_INTERVAL)) || now.isBefore(attempted.plus(RETRY_INTERVAL))) {
				return;
			}

			status.setProperty("attempted", now.toString());
			writeStatus(statusFile, status);

			if (update(state, status)) {
				status.setProperty("checked", now.toString());
				writeStatus(statusFile, status);
			}
		} catch (Throwable e) {
			debug.log(Level.FINE, e, () -> "Failed to check for detection data updates");
		}
	}

	/**
	 * Download the indexes that changed into the pending folder
	 *
	 * @return true if everything is up to date now
	 */
	private static boolean update(State state, Properties status) throws Exception {
		URI url = URI.create(getApplicationProperty("data.url"));
		byte[] manifestBytes = fetch(url);
		verifySignature(manifestBytes, fetch(URI.create(url + SIGNATURE_SUFFIX)));
		Manifest remote = Manifest.parse(manifestBytes);

		// a signed but older manifest is a replay of an old release
		Instant accepted = Instant.parse(status.getProperty("manifest", Instant.EPOCH.toString()));
		if (remote.date().isBefore(accepted) || remote.date().isBefore(state.bundled().date())) {
			debug.fine(() -> "Ignore detection data manifest older than the last accepted one: " + remote.date());
			return true;
		}
		status.setProperty("manifest", remote.date().toString());

		if (remote.format() != FORMAT) {
			debug.fine(() -> String.format("Ignore detection data format %d (supported: %d)", remote.format(), FORMAT));
			return true;
		}

		Path pendingFolder = state.folder().resolve("pending");
		Path pendingManifest = pendingFolder.resolve(MANIFEST);
		Manifest pending = Files.isRegularFile(pendingManifest) ? readManifest(pendingManifest) : Manifest.EMPTY;
		if (pending.format() != FORMAT) {
			pending = Manifest.EMPTY;
		}

		boolean complete = true;
		for (Map.Entry<String, Entry> it : remote.files().entrySet()) {
			String name = it.getKey();
			Entry entry = it.getValue();

			// only files that this version knows about
			if (!FILE_NAME.matcher(name).matches() || (!state.bundled().files().isEmpty() && !state.bundled().files().containsKey(name))) {
				continue;
			}

			Entry waiting = pending.files().get(name);
			if (waiting != null && waiting.sha256().equals(entry.sha256())) {
				continue;
			}

			Entry bundled = state.bundled().files().get(name);
			Entry current = state.active().contains(name) ? state.local().files().get(name) : bundled;
			boolean upToDate = current != null && current.sha256().equals(entry.sha256());
			boolean older = bundled != null && !entry.date().isAfter(bundled.date());

			if (upToDate || older) {
				if (waiting != null) {
					Files.deleteIfExists(pendingFolder.resolve(name));
					pending = pending.without(name);
					writeManifest(pendingManifest, pending);
				}
				continue;
			}

			try {
				byte[] bytes = fetch(url.resolve(name));
				verify(name, bytes, entry);
				ReleaseInfo.validate(name.substring(0, name.length() - 3), decompress(bytes));

				Files.createDirectories(pendingFolder);
				Path part = pendingFolder.resolve(name + ".part");
				Files.write(part, bytes);
				Files.move(part, pendingFolder.resolve(name), REPLACE_EXISTING, ATOMIC_MOVE);

				pending = pending.with(name, entry);
				writeManifest(pendingManifest, pending);
				debug.fine(() -> "Downloaded detection data (applied on the next start): " + name);
			} catch (Exception e) {
				complete = false;
				debug.log(Level.INFO, e, () -> "Failed to download detection data: " + name);
			}
		}
		return complete;
	}

	private static byte[] fetch(URI url) throws Exception {
		ByteBuffer data = WebRequest.fetch(url.toURL());
		if (data == null) {
			throw new IOException("No content: " + url);
		}
		byte[] bytes = new byte[data.remaining()];
		data.get(bytes);
		return bytes;
	}

	/**
	 * The checksums travel in the manifest itself, so the manifest is signed (Ed25519, made by the
	 * Update data workflow) and checked against the public key built into the application
	 */
	private static void verifySignature(byte[] manifest, byte[] signature) throws Exception {
		PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)));
		Signature verifier = Signature.getInstance("Ed25519");
		verifier.initVerify(key);
		verifier.update(manifest);
		if (!verifier.verify(signature)) {
			throw new IOException("Invalid signature: " + MANIFEST);
		}
	}

	private static void verify(String name, byte[] bytes, Entry entry) throws IOException {
		if (entry == null || bytes.length != entry.size() || !sha256(bytes).equals(entry.sha256())) {
			throw new IOException("Checksum mismatch: " + name);
		}
	}

	private static byte[] decompress(byte[] xz) throws IOException {
		try (InputStream in = new XZInputStream(new ByteArrayInputStream(xz))) {
			return in.readAllBytes();
		}
	}

	private static Manifest readBundledManifest() {
		try (InputStream in = DataUpdate.class.getResourceAsStream(BUNDLED + MANIFEST)) {
			if (in != null) {
				return Manifest.parse(in.readAllBytes());
			}
		} catch (Exception e) {
			debug.log(Level.WARNING, e, () -> "Failed to read bundled data manifest");
		}
		return Manifest.EMPTY;
	}

	private static Manifest readManifest(Path file) {
		try {
			if (Files.isRegularFile(file)) {
				return Manifest.parse(Files.readAllBytes(file));
			}
		} catch (Exception e) {
			debug.log(Level.FINE, e, () -> "Failed to read data manifest: " + file);
		}
		return new Manifest(-1, Instant.EPOCH, Collections.emptyMap());
	}

	private static void writeManifest(Path file, Manifest manifest) throws IOException {
		write(file, manifest.toJson());
	}

	private static void writeStatus(Path file, Properties status) throws IOException {
		StringBuilder text = new StringBuilder();
		new TreeMap<Object, Object>(status).forEach((k, v) -> text.append(k).append('=').append(v).append('\n'));
		write(file, text.toString());
	}

	private static void write(Path file, String text) throws IOException {
		Files.createDirectories(file.getParent());
		Path temp = file.resolveSibling(file.getFileName() + ".part");
		try (Writer out = Files.newBufferedWriter(temp, UTF_8)) {
			out.write(text);
		}
		Files.move(temp, file, REPLACE_EXISTING, ATOMIC_MOVE);
	}

	/**
	 * Lock on the data cache folder, shared by all processes (GUI, CLI, ...)
	 */
	private static final class Lock implements AutoCloseable {

		private final FileChannel channel;
		private final FileLock lock;

		private Lock(FileChannel channel, FileLock lock) {
			this.channel = channel;
			this.lock = lock;
		}

		static Lock tryAcquire(Path folder) throws IOException {
			Files.createDirectories(folder);
			FileChannel channel = FileChannel.open(folder.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			try {
				FileLock lock = channel.tryLock();
				if (lock != null) {
					return new Lock(channel, lock);
				}
			} catch (OverlappingFileLockException e) {
				// locked by this process
			}
			channel.close();
			return null;
		}

		@Override
		public void close() throws IOException {
			lock.release();
			channel.close();
		}
	}

	private static Settings prefs() {
		return Settings.forPackage(DataUpdate.class);
	}

	private DataUpdate() {
		throw new UnsupportedOperationException();
	}

}
