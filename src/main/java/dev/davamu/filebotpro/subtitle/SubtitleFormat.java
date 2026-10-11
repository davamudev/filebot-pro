
package dev.davamu.filebotpro.subtitle;

import java.util.Scanner;

import dev.davamu.filebotpro.MediaTypes;
import dev.davamu.filebotpro.util.FileUtilities.ExtensionFileFilter;

public enum SubtitleFormat {

	SubRip {

		@Override
		public SubtitleDecoder getDecoder() {
			return content -> new SubRipReader(new Scanner(content)).stream();
		}

		@Override
		public ExtensionFileFilter getFilter() {
			return MediaTypes.getTypeFilter("subtitle/SubRip");
		}
	},

	MicroDVD {

		@Override
		public SubtitleDecoder getDecoder() {
			return content -> new MicroDVDReader(new Scanner(content)).stream();
		}

		@Override
		public ExtensionFileFilter getFilter() {
			return MediaTypes.getTypeFilter("subtitle/MicroDVD");
		}
	},

	SubViewer {

		@Override
		public SubtitleDecoder getDecoder() {
			return content -> new SubViewerReader(new Scanner(content)).stream();
		}

		@Override
		public ExtensionFileFilter getFilter() {
			return MediaTypes.getTypeFilter("subtitle/SubViewer");
		}
	},

	SubStationAlpha {

		@Override
		public SubtitleDecoder getDecoder() {
			return content -> new SubStationAlphaReader(new Scanner(content)).stream();
		}

		@Override
		public ExtensionFileFilter getFilter() {
			return MediaTypes.getTypeFilter("subtitle/SubStationAlpha");
		}
	},

	SAMI {

		@Override
		public SubtitleDecoder getDecoder() {
			return new SamiDecoder();
		}

		@Override
		public ExtensionFileFilter getFilter() {
			return MediaTypes.getTypeFilter("subtitle/SAMI");
		}
	},

	TMPlayer {

		@Override
		public SubtitleDecoder getDecoder() {
			return new LineTimedSubtitleDecoder.TMPlayer();
		}

		@Override
		public ExtensionFileFilter getFilter() {
			return new ExtensionFileFilter("txt");
		}
	},

	MPL2 {

		@Override
		public SubtitleDecoder getDecoder() {
			return new LineTimedSubtitleDecoder.MPL2();
		}

		@Override
		public ExtensionFileFilter getFilter() {
			return new ExtensionFileFilter("txt");
		}
	};

	public abstract SubtitleDecoder getDecoder();

	public abstract ExtensionFileFilter getFilter();

}
