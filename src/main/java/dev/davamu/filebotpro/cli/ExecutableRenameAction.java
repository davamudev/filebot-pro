package dev.davamu.filebotpro.cli;

import static dev.davamu.filebotpro.Execute.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import dev.davamu.filebotpro.RenameAction;

public class ExecutableRenameAction implements RenameAction {

	private final String executable;
	private final File directory;

	public ExecutableRenameAction(String executable, File directory) {
		this.executable = executable;
		this.directory = directory;
	}

	@Override
	public File rename(File from, File to) throws Exception {
		List<String> command = new ArrayList<String>();

		// PowerShell scripts cannot be started directly
		if (hasExtension(executable, "ps1")) {
			command.addAll(List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File"));
		}

		command.add(executable);
		command.add(from.getCanonicalPath());
		command.add(getRelativePath(directory, to));

		system(command, directory);

		return to.exists() ? to : null;
	}

	private String getRelativePath(File dir, File f) {
		return dir == null ? f.toString() : dir.toPath().relativize(f.toPath()).toString();
	}

	@Override
	public boolean canRevert() {
		return false;
	}

	@Override
	public String toString() {
		return executable;
	}

}
