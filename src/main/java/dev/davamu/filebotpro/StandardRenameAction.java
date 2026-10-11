package dev.davamu.filebotpro;

import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.UserFiles.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import dev.davamu.filebotpro.util.CopyOnWrite;

public enum StandardRenameAction implements RenameAction {

	MOVE {

		@Override
		public File rename(File from, File to) throws Exception {
			return moveRename(from, to);
		}
	},

	COPY {

		@Override
		public File rename(File from, File to) throws Exception {
			return copyAs(from, to);
		}
	},

	KEEPLINK {

		@Override
		public File rename(File from, File to) throws Exception {
			// move file
			File dest = MOVE.rename(from, to);

			// symlink file back into the original location
			SYMLINK.rename(dest, from);

			return dest;
		}
	},

	SYMLINK {

		@Override
		public File rename(File from, File to) throws Exception {
			return createRelativeSymlink(resolveDestination(from, to), from, true);
		}
	},

	HARDLINK {

		@Override
		public File rename(File from, File to) throws Exception {
			return createHardLinkStructure(resolveDestination(from, to), from);
		}
	},

	CLONE {

		@Override
		public File rename(File from, File to) throws Exception {
			// clonefile or reflink requires filesystem that supports copy-on-write (e.g. apfs, btrfs, xfs or refs) and copy otherwise
			return CopyOnWrite.cloneOrCopy(from, to);
		}
	},

	DUPLICATE {

		@Override
		public File rename(File from, File to) throws Exception {
			// try to hardlink
			try {
				return HARDLINK.rename(from, to);
			} catch (Exception e) {
				debug.finest(cause(HARDLINK, e));
			}

			// copy if necessary
			return COPY.rename(from, to);
		}
	},

	TEST {

		@Override
		public File rename(File from, File to) throws IOException {
			return resolve(from, to);
		}

		@Override
		public boolean canRevert() {
			return false;
		}
	};

	public String getDisplayName() {
		switch (this) {
		case MOVE:
			return "Rename";
		case COPY:
			return "Copy";
		case KEEPLINK:
			return "Keeplink";
		case SYMLINK:
			return "Symlink";
		case HARDLINK:
			return "Hardlink";
		case CLONE:
			return "Clone";
		case DUPLICATE:
			return "Hardlink or Copy";
		default:
			return "Test";
		}
	}

	public String getDisplayVerb() {
		switch (this) {
		case MOVE:
			return "Moving";
		case COPY:
			return "Copying";
		case KEEPLINK:
			return "Moving and symlinking";
		case SYMLINK:
			return "Symlinking";
		case HARDLINK:
			return "Hardlinking";
		case CLONE:
			return "Cloning";
		case DUPLICATE:
			return "Duplicating";
		default:
			return "Testing";
		}
	}

	public static List<String> names() {
		return stream(values()).map(Enum::name).collect(toList());
	}

	public static StandardRenameAction forName(String name) {
		for (StandardRenameAction action : values()) {
			if (action.name().equalsIgnoreCase(name)) {
				return action;
			}
		}

		throw new IllegalArgumentException(String.format("%s not in %s", name, names()));
	}

	public static File revert(File current, File original) throws IOException {
		// do nothing if current and original path is exactly the same
		if (current.equals(original)) {
			return original;
		}

		// reverse move
		if (current.exists() && !original.exists()) {
			return moveRename(current, original);
		}

		BasicFileAttributes currentAttr = Files.readAttributes(current.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
		BasicFileAttributes originalAttr = Files.readAttributes(original.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

		// reverse symlink
		if (currentAttr.isSymbolicLink() && !originalAttr.isSymbolicLink()) {
			trash(current);
			return original;
		}

		// reverse keeplink
		if (!currentAttr.isSymbolicLink() && originalAttr.isSymbolicLink()) {
			trash(original);
			return moveRename(current, original);
		}

		// reverse copy / hardlink
		if (currentAttr.isRegularFile() && originalAttr.isRegularFile()) {
			trash(current);
			return original;
		}

		// reverse folder copy
		if (currentAttr.isDirectory() && originalAttr.isDirectory()) {
			trash(original);
			return moveRename(current, original);
		}

		throw new IllegalArgumentException(String.format("Cannot revert file: %s => %s", current, original));
	}

}
