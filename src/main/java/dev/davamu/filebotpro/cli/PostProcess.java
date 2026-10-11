package dev.davamu.filebotpro.cli;

import static java.nio.charset.StandardCharsets.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.MediaTypes.*;
import static dev.davamu.filebotpro.subtitle.SubtitleUtilities.*;
import static dev.davamu.filebotpro.media.XattrMetaInfo.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import static java.util.Arrays.*;
import static java.util.Comparator.*;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.script.Bindings;
import javax.script.SimpleBindings;

import com.sun.jna.Platform;

import groovy.lang.Closure;

import dev.davamu.filebotpro.RenameAction;
import dev.davamu.filebotpro.WebServices;
import dev.davamu.filebotpro.format.MediaBindingBean;
import dev.davamu.filebotpro.media.EmbeddedTags;
import dev.davamu.filebotpro.platform.mac.FinderTags;
import dev.davamu.filebotpro.subtitle.SubtitleFormat;
import dev.davamu.filebotpro.vfs.MemoryFile;
import dev.davamu.filebotpro.util.PlainFileXattrView;
import dev.davamu.filebotpro.web.AudioTrack;
import dev.davamu.filebotpro.web.Episode;
import dev.davamu.filebotpro.web.EpisodeListProvider;
import dev.davamu.filebotpro.web.Movie;
import dev.davamu.filebotpro.web.MovieInfo;
import dev.davamu.filebotpro.web.SearchResult;
import dev.davamu.filebotpro.web.SimpleDate;

/**
 * Post-processing features for files with xattr metadata (e.g. --apply artwork nfo url date)
 */
public class PostProcess {

	public enum Feature {

		/**
		 * Posters and backdrops (fn:artwork)
		 */
		artwork,

		/**
		 * Cover image (cover.jpg) for each folder, reusing local posters before downloading one (before metadata, which
		 * attaches it)
		 */
		cover,

		/**
		 * Video thumbnails for Synology NAS (@eaDir) and QNAP NAS (.@__thumb) made from the poster, so that their file
		 * managers and media servers show thumbnails for video files, see {@link NasThumbnails}
		 */
		thumbnail,

		/**
		 * NFO files for Kodi, Plex, Emby and Jellyfin (fn:nfo)
		 */
		nfo,

		/**
		 * Internet shortcut to the movie or series page
		 */
		url,

		/**
		 * Convert subtitle files (the renamed subtitles and the subtitles next to renamed videos) to SubRip / UTF-8, does
		 * not need metadata
		 */
		srt,

		/**
		 * Write title, series, season, episode and release date into mkv (mkvpropedit) and mp4 (AtomicParsley) files, and
		 * attach the cover image if there is one
		 */
		metadata,

		/**
		 * Add Finder tags for the media type, video format and HDR (macOS only), does not need metadata
		 */
		finder,

		/**
		 * Set the last modified date to the release date or air date (after the features that modify files)
		 */
		date,

		/**
		 * Set the last modified date to now, does not need metadata
		 */
		touch,

		/**
		 * Set POSIX file permissions (e.g. chmod=664, default rw-rw-r--), does not need metadata
		 */
		chmod,

		/**
		 * Take extras along with renamed videos: Plex extras folders (Trailers, Featurettes, ...) of single video folders
		 * and files with an extras suffix (Name-trailer.mkv), see {@link PostProcess#importExtras(Map, RenameAction)}
		 */
		extras,

		/**
		 * Delete source folders that are truly empty after moving files (no hidden files either), see
		 * {@link PostProcess#prune(Collection, Collection)}
		 */
		prune;

		public static Feature forName(String name) {
			// feature with parameter, e.g. chmod=664
			if (name.indexOf('=') > 0) {
				name = name.substring(0, name.indexOf('='));
			}

			for (Feature it : values()) {
				if (it.name().equalsIgnoreCase(name)) {
					return it;
				}
			}
			if ("tag".equalsIgnoreCase(name) || "tags".equalsIgnoreCase(name)) {
				return metadata;
			}
			if ("import".equalsIgnoreCase(name)) {
				return extras;
			}
			throw new IllegalArgumentException(String.format("%s not in %s", name, names()));
		}

		/**
		 * @return true if the feature only applies to files with xattr metadata from a previous rename
		 */
		public boolean needsMetadata() {
			return this != srt && this != finder && this != touch && this != chmod && this != extras && this != prune;
		}

		public static boolean isName(String name) {
			try {
				forName(name);
				return true;
			} catch (IllegalArgumentException e) {
				return false;
			}
		}

		public static List<String> names() {
			return Stream.of(values()).map(Enum::name).collect(toList());
		}
	}

	/**
	 * Parameters of the post-processing features and custom post-processing actions
	 */
	public static class Options {

		public static final String DEFAULT_PERMISSIONS = "rw-rw-r--";

		private Set<PosixFilePermission> permissions = PosixFilePermissions.fromString(DEFAULT_PERMISSIONS);
		private final List<Closure<?>> actions = new ArrayList<Closure<?>>();

		/**
		 * @param mode
		 *            octal (e.g. 664 or 0644) or symbolic (e.g. rw-r--r--) permissions
		 */
		public Options setPermissions(String mode) {
			if (mode.matches("0?[0-7]{3}")) {
				String octal = mode.substring(mode.length() - 3);
				StringBuilder symbolic = new StringBuilder(9);
				for (char c : octal.toCharArray()) {
					int bits = c - '0';
					symbolic.append((bits & 4) != 0 ? 'r' : '-').append((bits & 2) != 0 ? 'w' : '-').append((bits & 1) != 0 ? 'x' : '-');
				}
				mode = symbolic.toString();
			}
			permissions = PosixFilePermissions.fromString(mode);
			return this;
		}

		/**
		 * @param action
		 *            closure that is called for each file, with the file and its xattr metadata (if the closure takes two
		 *            parameters)
		 */
		public Options addAction(Closure<?> action) {
			actions.add(action);
			return this;
		}

		public Set<PosixFilePermission> getPermissions() {
			return permissions;
		}

		public List<Closure<?>> getActions() {
			return actions;
		}
	}

	public static void apply(Collection<File> files, Set<Feature> features) {
		apply(files, features, new Options());
	}

	public static void apply(Collection<File> files, Set<Feature> features, Options options) {
		apply(files, features, options, message -> {}, (done, total) -> {}, () -> false);
	}

	public static void apply(Collection<File> files, Set<Feature> features, Consumer<String> message, BiConsumer<Long, Long> progress, Supplier<Boolean> cancelled) {
		apply(files, features, new Options(), message, progress, cancelled);
	}

	/**
	 * @param message
	 *            current feature and file
	 * @param progress
	 *            done and total number of steps (one step per file and feature)
	 * @param cancelled
	 *            checked before each step
	 */
	public static void apply(Collection<File> files, Set<Feature> features, Options options, Consumer<String> message, BiConsumer<Long, Long> progress, Supplier<Boolean> cancelled) {
		// extras and prune need the source files (see importExtras and prune)
		Set<Feature> steps = EnumSet.noneOf(Feature.class);
		steps.addAll(features);
		steps.remove(Feature.extras);
		steps.remove(Feature.prune);

		if (steps.isEmpty() && options.getActions().isEmpty()) {
			return;
		}

		// as plain File objects, since read-only FastFile objects throw on modification
		List<File> existing = files.stream().map(f -> new File(f.getPath())).filter(File::isFile).collect(toList());

		// most features need metadata from previous rename operations
		List<File> withMetadata = existing.stream().filter(f -> xattr.getMetaInfo(f) != null).collect(toList());
		if (withMetadata.isEmpty() && steps.stream().anyMatch(Feature::needsMetadata)) {
			log.warning("No files with metadata to apply " + steps.stream().filter(Feature::needsMetadata).collect(toList()));
		}

		Map<Feature, List<File>> selections = new EnumMap<Feature, List<File>>(Feature.class);
		for (Feature feature : steps) {
			selections.put(feature, feature == Feature.srt ? getSubtitleFiles(existing) : feature.needsMetadata() ? withMetadata : existing);
		}

		long total = selections.values().stream().mapToLong(List::size).sum() + (long) existing.size() * options.getActions().size();
		long done = 0;

		for (Feature feature : steps) {
			List<File> selection = selections.get(feature);
			if (selection.isEmpty()) {
				continue;
			}

			if (feature == Feature.finder && !Platform.isMac()) {
				log.warning("[finder] Finder tags are only supported on macOS");
				done += selection.size();
				continue;
			}

			if (feature == Feature.chmod && !FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
				log.warning("[chmod] POSIX file permissions are not supported on this platform");
				done += selection.size();
				continue;
			}

			log.fine(String.format("Apply [%s] to %d files", feature, selection.size()));

			// scripts process all files at once
			if (feature == Feature.artwork || feature == Feature.nfo) {
				if (cancelled.get()) {
					return;
				}
				message.accept(String.format("%s · %d files", feature, selection.size()));
				progress.accept(done, total);
				runScript(feature.name(), selection);
				done += selection.size();
				continue;
			}

			for (File file : selection) {
				if (cancelled.get()) {
					return;
				}
				message.accept(String.format("%s · %s", feature, file.getName()));
				progress.accept(done++, total);

				switch (feature) {
				case url:
					writeInternetShortcut(file);
					break;
				case date:
					setReleaseDate(file);
					break;
				case cover:
					writeCover(file);
					break;
				case thumbnail:
					writeThumbnail(file);
					break;
				case metadata:
					writeEmbeddedTags(file);
					break;
				case srt:
					convertSubtitles(file);
					break;
				case finder:
					writeFinderTags(file);
					break;
				case touch:
					touch(file);
					break;
				case chmod:
					chmod(file, options.getPermissions());
					break;
				default:
					break;
				}
			}
		}

		// custom post-processing actions, e.g. --apply "{ f -> println f }"
		for (Closure<?> action : options.getActions()) {
			for (File file : existing) {
				if (cancelled.get()) {
					return;
				}
				message.accept(String.format("%s · %s", "custom", file.getName()));
				progress.accept(done++, total);

				try {
					if (action.getMaximumNumberOfParameters() >= 2) {
						action.call(file, xattr.getMetaInfo(file));
					} else {
						action.call(file);
					}
				} catch (Exception e) {
					log.log(Level.WARNING, e, cause(String.format("[apply] Failed to process [%s]", file), e));
				}
			}
		}

		progress.accept(total, total);
	}

	private static void chmod(File file, Set<PosixFilePermission> permissions) {
		try {
			Files.setPosixFilePermissions(file.toPath(), permissions);
			log.finest(String.format("[chmod] %s %s", PosixFilePermissions.toString(permissions), file));
		} catch (Exception e) {
			log.log(Level.WARNING, e, cause(String.format("[chmod] Failed to set permissions of [%s]", file), e));
		}
	}

	// Plex extras folders, see https://support.plex.tv/articles/local-files-for-trailers-and-extras/
	private static final Pattern EXTRAS_FOLDER = Pattern.compile("Behind The Scenes|Deleted Scenes|Featurettes|Interviews|Scenes|Shorts|Trailers|Other|Extras", Pattern.CASE_INSENSITIVE);

	// e.g. Name-trailer.mkv or Name-behindthescenes.mkv
	private static final Pattern EXTRAS_SUFFIX = Pattern.compile("-(trailer|featurette|behindthescenes|deleted|interview|scene|short|other)$", Pattern.CASE_INSENSITIVE);

	/**
	 * Move (or copy, link, ...) the extras of renamed videos to their new folder: extras files with the same name and an
	 * extras suffix (e.g. Movie-trailer.mkv is renamed to New Name-trailer.mkv), and Plex extras folders (e.g. Trailers) if
	 * the source folder holds only one video.
	 */
	public static void importExtras(Map<File, File> renameLog, RenameAction action) {
		Set<File> imported = new HashSet<File>();

		for (Map.Entry<File, File> it : renameLog.entrySet()) {
			File source = it.getKey().getAbsoluteFile();
			File destination = it.getValue().getAbsoluteFile();
			File sourceFolder = source.getParentFile();
			File destinationFolder = destination.getParentFile();

			if (!VIDEO_FILES.accept(destination) || sourceFolder == null || destinationFolder == null || sourceFolder.equals(destinationFolder) || !sourceFolder.isDirectory()) {
				continue;
			}

			String sourceName = getNameWithoutExtension(source.getName());
			String destinationName = getNameWithoutExtension(destination.getName());

			// e.g. Movie-trailer.mkv => New Name-trailer.mkv
			for (File f : getChildren(sourceFolder, FILES)) {
				String name = getNameWithoutExtension(f.getName());
				java.util.regex.Matcher suffix = EXTRAS_SUFFIX.matcher(name);
				if (suffix.find() && name.substring(0, suffix.start()).equalsIgnoreCase(sourceName) && imported.add(f)) {
					transferExtra(f, new File(destinationFolder, destinationName + suffix.group() + "." + getExtension(f)), action);
				}
			}

			// extras folders only belong to the video if it is the only video in the folder (i.e. a movie folder)
			if (getChildren(sourceFolder, VIDEO_FILES).size() > 1) {
				continue;
			}

			for (File folder : getChildren(sourceFolder, FOLDERS)) {
				if (!EXTRAS_FOLDER.matcher(folder.getName()).matches() || !imported.add(folder)) {
					continue;
				}

				for (File f : listFiles(folder, FILES)) {
					transferExtra(f, new File(destinationFolder, folder.getName() + File.separator + folder.toPath().relativize(f.toPath())), action);
				}

				// delete extras folders that are empty after moving
				deleteEmptyFolders(folder);
			}
		}
	}

	private static void transferExtra(File from, File to, RenameAction action) {
		if (to.exists()) {
			log.warning(String.format("[extras] File already exists: %s", to));
			return;
		}

		try {
			log.info(String.format("[extras] %s [%s] to [%s]", action, from, to));
			to.getParentFile().mkdirs();
			action.rename(from, to);
		} catch (Exception e) {
			log.log(Level.WARNING, e, cause(String.format("[extras] Failed to %s [%s]", action, from), e));
		}
	}

	private static void deleteEmptyFolders(File folder) {
		File[] children = folder.listFiles();
		if (children == null) {
			return;
		}
		for (File f : children) {
			if (f.isDirectory()) {
				deleteEmptyFolders(f);
			}
		}
		try {
			Files.delete(folder.toPath());
		} catch (Exception e) {
			// folder is not empty (e.g. the files were copied)
		}
	}

	/**
	 * Delete the folders of the given source files that are truly empty after a move (no files at all, hidden files
	 * included), then their parent folders. Folder arguments are never deleted themselves, and for file arguments only
	 * the folder of the file may be deleted.
	 */
	public static void prune(Collection<File> sources, Collection<File> arguments) {
		Set<File> stop = arguments.stream().map(File::getAbsoluteFile).map(f -> f.isDirectory() ? f : f.getParentFile() == null ? null : f.getParentFile().getParentFile()).filter(Objects::nonNull).collect(toCollection(HashSet::new));
		Set<File> folders = sources.stream().map(f -> f.getAbsoluteFile().getParentFile()).filter(Objects::nonNull).collect(toCollection(() -> new TreeSet<File>(reverseOrder())));

		// deepest folders first, so that parent folders may become empty
		for (File folder : folders) {
			for (File f = folder; f != null && isInside(f, stop) && !stop.contains(f); f = f.getParentFile()) {
				try {
					// NAS metadata folders (e.g. Synology @eaDir) and .xattr store folders only describe files that have been moved away
					File[] children = f.listFiles();
					if (children != null && children.length > 0 && stream(children).allMatch(it -> isNasMetadataFolder(it) || PlainFileXattrView.isXattrStore(it))) {
						for (File it : children) {
							delete(it);
						}
					}

					// fails for folders with any kind of content (hidden files too), so we never delete files
					Files.delete(f.toPath());
					log.info(String.format("[prune] Delete empty folder [%s]", f));
				} catch (NoSuchFileException e) {
					// already deleted
				} catch (DirectoryNotEmptyException e) {
					break;
				} catch (Exception e) {
					log.log(Level.WARNING, e, cause(String.format("[prune] Failed to delete [%s]", f), e));
					break;
				}
			}
		}
	}

	private static boolean isInside(File folder, Set<File> roots) {
		return roots.stream().anyMatch(r -> folder.toPath().startsWith(r.toPath()));
	}

	private static void writeCover(File file) {
		try {
			Object metadata = xattr.getMetaInfo(file);

			// 1. reuse local images (e.g. poster.jpg from --apply artwork)
			File local = CoverArt.findLocal(file, metadata);
			if (local != null) {
				String extension = hasExtension(local, "png") ? "png" : "jpg";
				File cover = CoverArt.getCoverFile(file, metadata, extension);
				if (cover.exists()) {
					log.finest(String.format("[cover] Cover already exists: %s", cover));
				} else {
					log.info(String.format("[cover] Copy [%s] to [%s]", local, cover));
					Files.copy(local.toPath(), cover.toPath());
				}
				return;
			}

			// 2. download the preferred online image
			File cover = CoverArt.getCoverFile(file, metadata, "jpg");
			for (URL url : CoverArt.findRemote(metadata)) {
				log.info(String.format("[cover] Fetch [%s] to [%s]", url, cover));
				ScriptShellMethods.saveAs(url, cover);
				return;
			}

			log.warning(String.format("[cover] Cover not found: %s [%s]", metadata, file));
		} catch (Exception e) {
			log.log(Level.WARNING, e, cause(String.format("[cover] Failed to write cover for [%s]", file), e));
		}
	}

	private static void writeThumbnail(File file) {
		if (!VIDEO_FILES.accept(file)) {
			return;
		}

		try {
			Object metadata = xattr.getMetaInfo(file);

			// 1. reuse local images (e.g. poster.jpg from --apply artwork)
			File local = CoverArt.findLocal(file, metadata);
			if (local != null) {
				int count = NasThumbnails.write(file, NasThumbnails.read(local));
				logThumbnail(file, local, count);
				return;
			}

			// 2. download the preferred online image
			for (URL url : CoverArt.findRemote(metadata)) {
				int count = NasThumbnails.write(file, NasThumbnails.read(url));
				logThumbnail(file, url, count);
				return;
			}

			log.warning(String.format("[thumbnail] Poster not found: %s [%s]", metadata, file));
		} catch (Exception e) {
			log.log(Level.WARNING, e, cause(String.format("[thumbnail] Failed to write thumbnail for [%s]", file), e));
		}
	}

	private static void logThumbnail(File file, Object image, int count) {
		if (count == 0) {
			log.warning(String.format("[thumbnail] No Synology NAS or QNAP NAS detected (no @eaDir or .@__thumb folder): %s", file));
		} else {
			log.info(String.format("[thumbnail] Write thumbnail for [%s] from [%s]", file, image));
		}
	}

	private static void runScript(String name, List<File> files) {
		try {
			ScriptShell shell = new ScriptShell(ScriptSource::getBuiltInScript, new CmdlineOperations(), new HashMap<String, Object>());

			Bindings bindings = new SimpleBindings();
			bindings.put(ScriptShell.SHELL_ARGS_BINDING_NAME, new ArgumentBean());
			bindings.put(ScriptShell.ARGV_BINDING_NAME, new ArrayList<File>(files));

			shell.runScript(name, bindings);
		} catch (Throwable e) {
			log.log(Level.WARNING, e, cause(String.format("[%s] Failed to apply", name), e));
		}
	}

	private static void writeInternetShortcut(File file) {
		try {
			URI link = getLink(xattr.getMetaInfo(file));
			if (link == null) {
				log.finest(String.format("[url] No link for %s", file));
				return;
			}

			File shortcut = new File(file.getParentFile(), getNameWithoutExtension(file.getName()) + ".url");
			log.info(String.format("[url] Write [%s] to [%s]", link, shortcut));
			Files.write(shortcut.toPath(), String.format("[InternetShortcut]\r\nURL=%s\r\n", link).getBytes(UTF_8));
		} catch (Exception e) {
			log.log(Level.WARNING, e, cause("[url] Failed to write internet shortcut", e));
		}
	}

	public static URI getLink(Object metadata) {
		if (metadata instanceof Episode) {
			Episode episode = (Episode) metadata;
			if (episode.getSeriesInfo() != null && episode.getSeriesInfo().getId() != null) {
				EpisodeListProvider db = WebServices.getEpisodeListProvider(episode.getSeriesInfo().getDatabase());
				if (db != null) {
					return db.getEpisodeListLink(new SearchResult(episode.getSeriesInfo().getId()));
				}
			}
		}

		if (metadata instanceof Movie) {
			Movie movie = (Movie) metadata;
			if (movie.getTmdbId() > 0) {
				return WebServices.TheMovieDB.getMoviePageLink(movie.getTmdbId());
			}
			if (movie.getImdbId() > 0) {
				return URI.create(String.format("https://www.imdb.com/title/tt%07d/", movie.getImdbId()));
			}
		}

		if (metadata instanceof AudioTrack) {
			AudioTrack track = (AudioTrack) metadata;
			if (track.getMBID() != null && track.getMBID().length() > 0) {
				return URI.create("https://musicbrainz.org/recording/" + track.getMBID());
			}
		}

		return null;
	}

	private static void writeEmbeddedTags(File file) {
		File cover = null;
		File attachment = null;
		try {
			Object metadata = xattr.getMetaInfo(file);
			String originalName = xattr.getOriginalName(file);

			// embed a smaller copy of large posters
			cover = CoverArt.findLocal(file, metadata);
			attachment = cover == null ? null : CoverArt.getAttachment(cover);

			if (!EmbeddedTags.write(file, metadata, getReleaseDate(metadata), attachment)) {
				log.finest(String.format("[metadata] Not supported: %s", file));
				return;
			}

			// AtomicParsley replaces the file with a new one, which drops our extended attributes (the in-memory cache
			// still has them, so read them from the file again)
			xattr.invalidate(file);
			if (xattr.getMetaInfo(file) == null) {
				xattr.setMetaInfo(file, metadata, originalName);
			}
		} catch (Exception e) {
			log.log(Level.WARNING, e, cause(String.format("[metadata] Failed to write tags to [%s]", file), e));
		} finally {
			if (attachment != null && !attachment.equals(cover)) {
				attachment.delete();
			}
		}
	}

	/**
	 * Renamed subtitle files and the subtitle files next to renamed video files (e.g. Movie.eng.ass for Movie.mkv)
	 */
	private static List<File> getSubtitleFiles(Collection<File> files) {
		Set<File> subtitles = new TreeSet<File>();
		for (File file : files) {
			if (SUBTITLE_FILES.accept(file) || isTextSubtitleFile(file)) {
				subtitles.add(file);
			} else if (VIDEO_FILES.accept(file) && file.getParentFile() != null) {
				String prefix = getNameWithoutExtension(file.getName()).toLowerCase() + ".";
				getChildren(file.getParentFile(), f -> SUBTITLE_FILES.accept(f) || isTextSubtitleFile(f)).stream().filter(f -> f.getName().toLowerCase().startsWith(prefix)).forEach(subtitles::add);
			}
		}
		return new ArrayList<File>(subtitles);
	}

	/**
	 * TMPlayer and MPL2 subtitles are .txt files, so they are recognized by their content
	 */
	private static boolean isTextSubtitleFile(File file) {
		if (!SubtitleFormat.TMPlayer.getFilter().accept(file) || !file.isFile() || file.length() > 1_000_000) {
			return false;
		}

		try {
			String content = new String(Files.readAllBytes(file.toPath()), UTF_8);
			return Stream.of(SubtitleFormat.TMPlayer, SubtitleFormat.MPL2).anyMatch(f -> f.getDecoder().decode(content).count() >= 3);
		} catch (Exception e) {
			debug.finest(e::toString);
			return false;
		}
	}

	private static void convertSubtitles(File subtitle) {
		try {
			byte[] bytes = Files.readAllBytes(subtitle.toPath());
			boolean subrip = SubtitleFormat.SubRip.getFilter().accept(subtitle);

			// already SubRip / UTF-8
			if (subrip && isUTF8(bytes)) {
				log.finest(String.format("[srt] Skip %s", subtitle));
				return;
			}

			// keep the original file of other formats (e.g. styles of ASS subtitles)
			File target = subrip ? subtitle : new File(subtitle.getParentFile(), getNameWithoutExtension(subtitle.getName()) + ".srt");
			if (!subrip && target.exists()) {
				log.warning(String.format("[srt] File already exists: %s", target));
				return;
			}

			ByteBuffer data = exportSubtitles(new MemoryFile(subtitle.getName(), ByteBuffer.wrap(bytes)), SubtitleFormat.SubRip, 0, UTF_8);
			byte[] output = new byte[data.remaining()];
			data.get(output);

			log.info(String.format("[srt] Convert [%s] to [%s]", subtitle, target));
			Files.write(target.toPath(), output);
		} catch (Exception e) {
			log.log(Level.WARNING, e, cause(String.format("[srt] Failed to convert [%s]", subtitle), e));
		}
	}

	private static boolean isUTF8(byte[] bytes) {
		try {
			UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
			return true;
		} catch (CharacterCodingException e) {
			return false;
		}
	}

	private static void writeFinderTags(File file) {
		try {
			List<String> tags = getFinderTags(file);
			if (tags.isEmpty()) {
				log.finest(String.format("[finder] No tags for %s", file));
				return;
			}

			log.info(String.format("[finder] Add tags %s to [%s]", tags, file));
			FinderTags.addTags(file, tags);
		} catch (Exception e) {
			log.log(Level.WARNING, e, cause(String.format("[finder] Failed to add tags to [%s]", file), e));
		}
	}

	/**
	 * Media type, video format and HDR, e.g. [Movie, 2160p, HDR]
	 */
	public static List<String> getFinderTags(File file) {
		Object metadata = xattr.getMetaInfo(file);
		List<String> tags = new ArrayList<String>();

		if (metadata instanceof Movie) {
			tags.add("Movie");
		} else if (metadata instanceof Episode) {
			tags.add(((Episode) metadata).isAnime() ? "Anime" : "TV Show");
		} else if (metadata instanceof AudioTrack) {
			tags.add("Music");
		}

		if (VIDEO_FILES.accept(file)) {
			MediaBindingBean media = new MediaBindingBean(metadata, file);
			for (Supplier<String> tag : List.<Supplier<String>> of(media::getVideoFormat, media::getHighDynamicRange)) {
				try {
					String value = tag.get();
					if (value != null) {
						tags.add(value);
					}
				} catch (Exception e) {
					debug.finest(e::toString); // no media info
				}
			}
		}
		return tags;
	}

	private static void touch(File file) {
		try {
			log.info(String.format("[touch] Set last modified date of [%s] to now", file));
			Files.setLastModifiedTime(file.toPath(), FileTime.from(Instant.now()));
		} catch (Exception e) {
			log.log(Level.WARNING, e, cause("[touch] Failed to set last modified date", e));
		}
	}

	private static void setReleaseDate(File file) {
		try {
			SimpleDate date = getReleaseDate(xattr.getMetaInfo(file));
			if (date == null) {
				log.finest(String.format("[date] No date for %s", file));
				return;
			}

			log.info(String.format("[date] Set last modified date of [%s] to [%s]", file, date));
			Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(date.getTimeStamp()));
		} catch (Exception e) {
			log.log(Level.WARNING, e, cause("[date] Failed to set last modified date", e));
		}
	}

	public static SimpleDate getReleaseDate(Object metadata) throws Exception {
		if (metadata instanceof Episode) {
			return ((Episode) metadata).getAirdate();
		}

		if (metadata instanceof Movie) {
			Movie movie = (Movie) metadata;
			if (movie.getTmdbId() > 0 || movie.getImdbId() > 0) {
				Locale locale = movie.getLanguage() != null ? movie.getLanguage() : Locale.ENGLISH;
				MovieInfo info = WebServices.TheMovieDB.getMovieInfo(movie, locale, false);
				if (info != null && info.getReleased() != null) {
					return info.getReleased();
				}
			}
			return movie.getYear() > 0 ? new SimpleDate(movie.getYear(), 1, 1) : null;
		}

		if (metadata instanceof AudioTrack) {
			return ((AudioTrack) metadata).getAlbumReleaseDate();
		}

		return null;
	}

}
