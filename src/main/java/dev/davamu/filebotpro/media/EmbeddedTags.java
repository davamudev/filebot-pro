package dev.davamu.filebotpro.media;

import static java.nio.charset.StandardCharsets.*;
import static dev.davamu.filebotpro.Execute.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import dev.davamu.filebotpro.ExecuteException;
import dev.davamu.filebotpro.web.Episode;
import dev.davamu.filebotpro.web.Movie;
import dev.davamu.filebotpro.web.SimpleDate;

/**
 * Write metadata into the media files themselves: Matroska tags via mkvpropedit (MKVToolNix) and MP4 atoms via
 * AtomicParsley. Both tools are optional and looked up like the other native tools (native folder or PATH).
 */
public class EmbeddedTags {

	public static final String MKVPROPEDIT = "dev.davamu.filebotpro.media.mkvpropedit";
	public static final String ATOMICPARSLEY = "dev.davamu.filebotpro.media.AtomicParsley";

	public static String getMkvPropEdit() {
		return System.getProperty(MKVPROPEDIT, "mkvpropedit");
	}

	public static String getAtomicParsley() {
		return System.getProperty(ATOMICPARSLEY, "AtomicParsley");
	}

	public static boolean isMatroska(File f) {
		return hasExtension(f, "mkv", "mka", "mk3d", "webm");
	}

	public static boolean isMP4(File f) {
		return hasExtension(f, "mp4", "m4v", "m4a", "mov");
	}

	/**
	 * @param cover
	 *            cover image to attach (replaces the previous cover), or null
	 * @return true if tags have been written, false if the file type is not supported
	 */
	public static boolean write(File file, Object metadata, SimpleDate date, File cover) throws Exception {
		if (isMatroska(file)) {
			writeMatroskaTags(file, metadata, date, cover);
			return true;
		}
		if (isMP4(file)) {
			writeMP4Tags(file, metadata, date, cover);
			return true;
		}
		return false;
	}

	private static void writeMatroskaTags(File file, Object metadata, SimpleDate date, File cover) throws Exception {
		StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Tags>\n");

		if (metadata instanceof Episode) {
			Episode e = (Episode) metadata;
			tag(xml, 70, "TITLE", e.getSeriesName(), getSeriesIdTag(e), getSeriesId(e));
			if (e.getSeason() != null) {
				tag(xml, 60, "PART_NUMBER", e.getSeason().toString());
			}
			tag(xml, 50, "TITLE", e.getTitle(), "PART_NUMBER", e.getEpisode() != null ? e.getEpisode().toString() : e.getSpecial() != null ? e.getSpecial().toString() : null, "DATE_RELEASED", date != null ? date.toString() : null);
		} else if (metadata instanceof Movie) {
			Movie m = (Movie) metadata;
			tag(xml, 50, "TITLE", m.getName(), "DATE_RELEASED", date != null ? date.toString() : m.getYear() > 0 ? String.valueOf(m.getYear()) : null, "IMDB", m.getImdbId() > 0 ? String.format("tt%07d", m.getImdbId()) : null, "TMDB", m.getTmdbId() > 0 ? "movie/" + m.getTmdbId() : null);
		}

		xml.append("</Tags>\n");

		File tags = File.createTempFile("tags", ".xml");
		try {
			Files.write(tags.toPath(), xml.toString().getBytes(UTF_8));

			List<String> command = new ArrayList<String>();
			command.add(getMkvPropEdit());
			command.add(file.getPath());
			command.add("--edit");
			command.add("info");
			command.add("--set");
			command.add("title=" + metadata);
			command.add("--tags");
			command.add("global:" + tags.getPath());

			log.fine(String.format("[metadata] Write Matroska tags to [%s]", file));
			execute(command, null);
		} finally {
			tags.delete();
		}

		if (cover != null) {
			writeMatroskaCover(file, cover);
		}
	}

	/**
	 * Attach the cover image as cover.jpg or cover.png (Matroska attachment convention), replacing a previous cover
	 */
	private static void writeMatroskaCover(File file, File cover) throws Exception {
		String extension = hasExtension(cover, "png") ? "png" : "jpg";
		String name = "cover." + extension;

		List<String> command = new ArrayList<String>();
		command.add(getMkvPropEdit());
		command.add(file.getPath());
		for (String it : new String[] { "cover.jpg", "cover.png" }) {
			command.add("--delete-attachment");
			command.add("name:" + it);
		}
		command.add("--attachment-name");
		command.add(name);
		command.add("--attachment-mime-type");
		command.add(extension.equals("png") ? "image/png" : "image/jpeg");
		command.add("--add-attachment");
		command.add(cover.getPath());

		log.fine(String.format("[metadata] Attach [%s] to [%s]", cover, file));
		try {
			execute(command, null);
		} catch (ExecuteException e) {
			// exit code 1 means warnings only (e.g. there was no previous cover to delete)
			if (e.getExitCode() != 1) {
				throw e;
			}
		}
	}

	private static void writeMP4Tags(File file, Object metadata, SimpleDate date, File cover) throws Exception {
		List<String> command = new ArrayList<String>();
		command.add(getAtomicParsley());
		command.add(file.getPath());

		if (metadata instanceof Episode) {
			Episode e = (Episode) metadata;
			add(command, "--stik", "TV Show");
			add(command, "--title", e.getTitle());
			add(command, "--TVShowName", e.getSeriesName());
			add(command, "--TVSeasonNum", e.getSeason());
			add(command, "--TVEpisodeNum", e.getEpisode() != null ? e.getEpisode() : e.getSpecial());
			add(command, "--TVEpisode", e.getSeason() != null && e.getEpisode() != null ? String.format("S%02dE%02d", e.getSeason(), e.getEpisode()) : null);
		} else if (metadata instanceof Movie) {
			Movie m = (Movie) metadata;
			add(command, "--stik", "Movie");
			add(command, "--title", m.getName());
		} else {
			add(command, "--title", metadata.toString());
		}

		add(command, "--year", date != null ? date.toString() : null);

		if (cover != null) {
			add(command, "--artwork", "REMOVE_ALL");
			add(command, "--artwork", cover.getPath());
		}

		command.add("--overWrite");

		log.fine(String.format("[metadata] Write MP4 tags to [%s]", file));
		execute(command, null);
	}

	private static void tag(StringBuilder xml, int target, String... nameValuePairs) {
		StringBuilder simple = new StringBuilder();
		for (int i = 0; i + 1 < nameValuePairs.length; i += 2) {
			if (nameValuePairs[i] != null && nameValuePairs[i + 1] != null && nameValuePairs[i + 1].length() > 0) {
				simple.append(String.format("    <Simple><Name>%s</Name><String>%s</String></Simple>\n", nameValuePairs[i], escapeXml(nameValuePairs[i + 1])));
			}
		}
		if (simple.length() > 0) {
			xml.append(String.format("  <Tag>\n    <Targets><TargetTypeValue>%d</TargetTypeValue></Targets>\n", target)).append(simple).append("  </Tag>\n");
		}
	}

	private static String getSeriesIdTag(Episode e) {
		String db = e.getSeriesInfo() == null ? null : e.getSeriesInfo().getDatabase();
		if ("TheTVDB".equalsIgnoreCase(db)) {
			return "TVDB";
		}
		if ("TheMovieDB::TV".equalsIgnoreCase(db)) {
			return "TMDB";
		}
		return null;
	}

	private static String getSeriesId(Episode e) {
		if (e.getSeriesInfo() == null || e.getSeriesInfo().getId() == null) {
			return null;
		}
		return "TMDB".equals(getSeriesIdTag(e)) ? "tv/" + e.getSeriesInfo().getId() : "series/" + e.getSeriesInfo().getId();
	}

	private static void add(List<String> command, String option, Object value) {
		if (value != null && value.toString().length() > 0) {
			command.add(option);
			command.add(value.toString());
		}
	}

	private static String escapeXml(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	public static String version(String executable, String option) throws Exception {
		String output = execute(executable, option).toString().trim();
		return output.isEmpty() ? output : output.lines().findFirst().orElse(output);
	}

	private EmbeddedTags() {
		throw new UnsupportedOperationException();
	}

}
