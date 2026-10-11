package dev.davamu.filebotpro.media;

import static dev.davamu.filebotpro.MediaTypes.*;
import static dev.davamu.filebotpro.media.MediaDetection.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import dev.davamu.filebotpro.similarity.Match;
import dev.davamu.filebotpro.web.AudioTrack;

/**
 * Companion files are files that belong to a media file and should be renamed along with it (e.g. 01 - Song.lrc next to 01 - Song.mp3)
 */
public final class CompanionFiles {

	/**
	 * @return matches for files in the same folder that are named after a matched music file (e.g. lyrics, artwork or cue sheets), with
	 *         the same track as their music file
	 */
	public static List<Match<File, ?>> matchMusicCompanionFiles(Collection<File> files, List<Match<File, ?>> matches) {
		List<Match<File, ?>> companionMatches = new ArrayList<Match<File, ?>>();

		for (File file : files) {
			if (AUDIO_FILES.accept(file) || VIDEO_FILES.accept(file) || file.isDirectory() || getClutterFileFilter().accept(file)) {
				continue;
			}

			for (Match<File, ?> match : matches) {
				File music = match.getValue();
				if (match.getCandidate() instanceof AudioTrack && Objects.equals(file.getParentFile(), music.getParentFile()) && isDerived(file, music)) {
					companionMatches.add(new Match<File, AudioTrack>(file, ((AudioTrack) match.getCandidate()).clone()));
					break;
				}
			}
		}

		return companionMatches;
	}

	private CompanionFiles() {
		throw new UnsupportedOperationException();
	}

}
