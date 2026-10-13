package dev.davamu.filebotpro.cli;

import static dev.davamu.filebotpro.Execute.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import com.sun.jna.Native;
import com.sun.jna.Platform;

import dev.davamu.filebotpro.ApplicationFolder;
import dev.davamu.filebotpro.UpdateCheck;
import dev.davamu.filebotpro.WebServices;
import dev.davamu.filebotpro.archive.Archive;
import dev.davamu.filebotpro.archive.SevenZipLoader;
import dev.davamu.filebotpro.archive.ShellExecutables;
import dev.davamu.filebotpro.media.EmbeddedTags;
import dev.davamu.filebotpro.media.FFProbe;
import dev.davamu.filebotpro.media.MediaCharacteristicsParser;
import dev.davamu.filebotpro.media.MetaAttributes;
import dev.davamu.filebotpro.mediainfo.MediaInfo;
import groovy.lang.GroovySystem;

/**
 * Versions, native tools and environment of this installation, as printed by {@code filebot-pro -script fn:sysinfo}.
 */
public final class SystemInfo {

	private static final Pattern VERSION_NUMBER = Pattern.compile("\\d+(\\.\\d+)+");

	public static void print(Consumer<String> out) {
		out.accept(getApplicationIdentifier());

		line(out, "JNA Native", SystemInfo::getNativeVersion);
		line(out, "MediaInfo", () -> versionNumber(MediaInfo.version()));

		if (Archive.getExtractor() == Archive.Extractor.SevenZipNativeBindings) {
			line(out, "7-Zip-JBinding", SevenZipLoader::getNativeVersion);
		}

		line(out, "Tools", SystemInfo::getTools);
		line(out, "Extended Attributes", SystemInfo::checkExtendedAttributes);
		line(out, "Unicode Filesystem", SystemInfo::checkUnicodeFileSystem);
		line(out, "Groovy", GroovySystem::getVersion);
		line(out, "JRE", () -> getJavaRuntimeIdentifier());
		line(out, "JVM", () -> System.getProperty("java.vm.name") + (Platform.is64Bit() ? "" : " (32-bit)"));

		for (String name : new String[] { "JAVA_OPTS", getApplicationId().toUpperCase().replace('-', '_') + "_OPTS" }) {
			String value = System.getenv(name);
			if (value != null && !value.isEmpty()) {
				out.accept(name + ": " + value);
			}
		}

		Runtime runtime = Runtime.getRuntime();
		out.accept(String.format("CPU/MEM: %d Core / %s Max Memory / %s Used Memory", runtime.availableProcessors(), formatSize(runtime.maxMemory()), formatSize(runtime.totalMemory() - runtime.freeMemory())));
		out.accept("OS: " + getSystemIdentifier());

		if (!Platform.isWindows()) {
			line(out, "HW", () -> execute("uname", "-a").toString().trim());
		}

		line(out, "Storage", SystemInfo::getStorage);
		out.accept("Data: " + ApplicationFolder.AppData.get());
		out.accept("Package: " + getApplicationDeployment().toUpperCase());

		// this application's own release feed (disabled when update.url is empty)
		if (UpdateCheck.isAvailable()) {
			try {
				UpdateCheck.Update update = UpdateCheck.getLatest();
				if (UpdateCheck.isNewer(update)) {
					out.accept("");
					out.accept(String.format(" UPDATE AVAILABLE: %s (r%d) ", update.title(), update.revision()));
					out.accept(" " + update.download());
					if (!UpdateCheck.getInstructions().isEmpty()) {
						out.accept(" " + UpdateCheck.getInstructions());
					}
				}
			} catch (Exception e) {
				out.accept("Update check failed: " + e);
			}
		}
	}

	private static void line(Consumer<String> out, String label, Callable<Object> value) {
		try {
			out.accept(label + ": " + value.call());
		} catch (Throwable e) {
			out.accept(label + ": " + e);
		}
	}

	private static String versionNumber(String version) {
		Matcher m = VERSION_NUMBER.matcher(version);
		return m.find() ? m.group() : version;
	}

	private static Object getNativeVersion() throws Exception {
		// version of the native library that was actually loaded (Native.VERSION_NATIVE is only the one this JNA build expects)
		Method version = Native.class.getDeclaredMethod("getNativeVersion");
		version.setAccessible(true);
		return version.invoke(null);
	}

	private static String getTools() {
		Map<String, Callable<String>> tools = new LinkedHashMap<String, Callable<String>>();
		tools.put("fpcalc", () -> WebServices.AcoustID.version());
		tools.put("mkvpropedit", () -> EmbeddedTags.version(EmbeddedTags.getMkvPropEdit(), "--version"));
		tools.put("AtomicParsley", () -> EmbeddedTags.version(EmbeddedTags.getAtomicParsley(), "--version"));

		if (Archive.getExtractor() == Archive.Extractor.ShellExecutables) {
			for (ShellExecutables.Command c : ShellExecutables.Command.values()) {
				tools.put(c.name(), c::version);
			}
		}

		if (MediaCharacteristicsParser.getDefault() == MediaCharacteristicsParser.ffprobe) {
			tools.put("ffprobe", () -> new FFProbe().version());
		}

		List<String> found = new ArrayList<String>();
		tools.forEach((name, version) -> {
			try {
				found.add(name + "/" + versionNumber(version.call()));
			} catch (Exception e) {
				// tool not installed
			}
		});
		return found.isEmpty() ? "NONE" : String.join(" ", found);
	}

	private static String checkExtendedAttributes() throws Exception {
		if (!useExtendedFileAttributes()) {
			return "DISABLED";
		}

		File f = ApplicationFolder.TemporaryFiles.resolve("xattr.txt");
		f.createNewFile();
		f.deleteOnExit();

		Date value = new Date();
		MetaAttributes xattr = new MetaAttributes(f);
		xattr.setObject(value);

		if (!value.equals(xattr.getObject())) {
			throw new IllegalStateException("xattr value mismatch");
		}
		return "OK";
	}

	private static String checkUnicodeFileSystem() throws Exception {
		Path f = ApplicationFolder.TemporaryFiles.resolve("測試 éñ ✓.txt").toPath();
		Files.deleteIfExists(f);
		Files.createFile(f);
		Files.delete(f);
		return "OK";
	}

	private static String getStorage() throws Exception {
		List<String> stores = new ArrayList<String>();
		for (FileStore store : FileSystems.getDefault().getFileStores()) {
			long space = store.getUsableSpace();
			String type = store.type();
			if (space > 0 && !type.matches("(?i)rootfs|tmpfs|devtmpfs|overlay|squashfs")) {
				stores.add(String.format("%s [%s] @ %s", type, store, formatSize(space)));
			}
		}
		return stores.isEmpty() ? "NONE" : String.join(" | ", stores);
	}

	private SystemInfo() {
		throw new UnsupportedOperationException();
	}

}
