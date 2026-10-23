package dev.davamu.filebotpro;

import static java.util.Collections.*;
import static dev.davamu.filebotpro.Logging.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.logging.Level;

import dev.davamu.filebotpro.History.Sequence;

/**
 * Steps shared by the command line and the desktop app when files are renamed, so that both behave the same way
 */
public final class RenameService {

	private RenameService() {
		throw new UnsupportedOperationException();
	}

	/**
	 * @return true if the file does not exist anymore (e.g. it was moved or renamed by another program), broken symlinks still exist
	 */
	public static boolean isMissing(File file) {
		return !Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS);
	}

	/**
	 * Rename a single file. A source that has disappeared since it was loaded is skipped with a warning instead of failing the whole batch.
	 *
	 * @return the actual destination, or null if the source no longer exists
	 */
	public static File rename(RenameAction action, File source, File destination) throws Exception {
		if (isMissing(source)) {
			log.log(Level.WARNING, "File no longer exists: " + source);
			return null;
		}
		return action.rename(source, destination);
	}

	/**
	 * Add a renamed file to the history right away, so that nothing is lost if the process is killed (e.g. a long running batch)
	 *
	 * @param sequence
	 *            sequence of the current batch, or null to start a new one
	 * @return the sequence the file has been added to
	 */
	public static Sequence appendHistory(RenameAction action, Sequence sequence, File source, File destination) {
		if (action.canRevert()) {
			sequence = HistorySpooler.getInstance().append(sequence, singletonMap(source, destination).entrySet());
			HistorySpooler.getInstance().commitPeriodically();
		}
		return sequence;
	}

}
