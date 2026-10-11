package dev.davamu.filebotpro.ui.rename;

import static java.util.Comparator.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.MediaTypes.*;
import static dev.davamu.filebotpro.WebServices.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.awt.Component;
import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.davamu.filebotpro.media.LocalDatasource;
import dev.davamu.filebotpro.similarity.Match;
import dev.davamu.filebotpro.web.SortOrder;

/**
 * Match files to the metadata stored in the files themselves: extended attributes, Exif metadata (photos), ID3 tags
 * (music) and series / episode tags (e.g. mp4 atoms or mkv tags).
 */
class AttributeMatcher implements AutoCompleteMatcher {

	@Override
	public List<Match<File, ?>> match(Collection<File> files, boolean strict, SortOrder order, Locale locale, boolean autodetection, Component parent) throws Exception {
		Map<File, Object> matches = new LinkedHashMap<File, Object>(files.size());
		Set<File> remaining = new LinkedHashSet<File>(files);

		// 1. xattr metadata (e.g. written by previous rename operations)
		add(matches, remaining, LocalDatasource.XATTR.match(remaining, true));

		// 2. exif metadata (e.g. photos)
		add(matches, remaining, LocalDatasource.EXIF.match(filter(remaining, IMAGE_FILES), true));

		// 3. id3 tags (e.g. music)
		add(matches, remaining, MediaInfoID3.lookup(filter(remaining, AUDIO_FILES)));

		// 4. series and episode tags (e.g. mp4 atoms)
		add(matches, remaining, MediaInfoID3.lookupEpisodes(filter(remaining, VIDEO_FILES)));

		// restore original order
		return matches.entrySet().stream().map(it -> new Match<File, Object>(it.getKey(), it.getValue())).sorted(comparing(Match::getValue, OriginalOrder.of(files))).collect(toList());
	}

	private void add(Map<File, Object> matches, Set<File> remaining, Map<File, ?> metadata) {
		metadata.forEach((f, m) -> {
			if (m != null) {
				matches.put(f, m);
				remaining.remove(f);
			}
		});
	}

}
