package dev.davamu.filebotpro.media;

import static dev.davamu.filebotpro.MediaTypes.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.File;

import dev.davamu.filebotpro.Settings;
import dev.davamu.filebotpro.mediainfo.CachedMediaInfo;
import dev.davamu.filebotpro.util.SystemProperty;

public enum MediaCharacteristicsParser {

	libmediainfo {

		@Override
		public MediaCharacteristics open(File f) throws Exception {
			return CachedMediaInfo.read(f);
		}
	},

	ffprobe {

		@Override
		public MediaCharacteristics open(File f) throws Exception {
			return new FFProbe().open(f);
		}
	};

	public abstract MediaCharacteristics open(File f) throws Exception;

	public boolean acceptVideoFile(File f) {
		// match files without reading file contents (e.g. -no-probe)
		return Settings.useMediaParser() && VIDEO_FILES.accept(f) && f.length() > ONE_MEGABYTE;
	}

	public static MediaCharacteristicsParser getDefault() {
		return DEFAULT;
	}

	public static final MediaCharacteristicsParser DEFAULT = SystemProperty.of("dev.davamu.filebotpro.media.parser", MediaCharacteristicsParser::valueOf, libmediainfo).get();

}
