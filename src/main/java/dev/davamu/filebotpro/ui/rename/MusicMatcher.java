package dev.davamu.filebotpro.ui.rename;

import static dev.davamu.filebotpro.MediaTypes.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import dev.davamu.filebotpro.media.CompanionFiles;
import dev.davamu.filebotpro.similarity.Match;
import dev.davamu.filebotpro.web.AudioTrack;
import dev.davamu.filebotpro.web.MusicIdentificationService;
import dev.davamu.filebotpro.web.SortOrder;

class MusicMatcher implements AutoCompleteMatcher {

	private MusicIdentificationService[] services;

	public MusicMatcher(MusicIdentificationService... services) {
		this.services = services;
	}

	@Override
	public List<Match<File, ?>> match(Collection<File> files, boolean strict, SortOrder order, Locale locale, boolean autodetection, Component parent) throws Exception {
		List<Match<File, ?>> matches = new ArrayList<Match<File, ?>>();
		LinkedHashSet<File> remaining = new LinkedHashSet<File>(filter(files, AUDIO_FILES, VIDEO_FILES));

		// check audio files against all services
		for (int i = 0; i < services.length && remaining.size() > 0; i++) {
			services[i].lookup(remaining).forEach((k, v) -> {
				if (v != null) {
					matches.add(new Match<File, AudioTrack>(k, v.clone()));
					remaining.remove(k);
				}
			});
		}

		// companion files (e.g. lyrics) are renamed along with their music file
		matches.addAll(CompanionFiles.matchMusicCompanionFiles(files, matches));

		return matches;
	}

}
