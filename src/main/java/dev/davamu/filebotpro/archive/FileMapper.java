package dev.davamu.filebotpro.archive;

import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

import dev.davamu.filebotpro.vfs.FileInfo;

public class FileMapper implements ExtractOutProvider {

	private File outputDir;
	private boolean flatten;

	public FileMapper(File outputDir) {
		this(outputDir, false);
	};

	public FileMapper(File outputDir, boolean flatten) {
		this.outputDir = outputDir;
		this.flatten = flatten;
	};

	public File getOutputDir() {
		return outputDir;
	}

	public File getOutputFile(File entry) {
		return new File(outputDir, flatten ? entry.getName() : entry.getPath());
	}

	@Override
	public OutputStream getStream(File entry) throws IOException {
		File outputFile = getOutputFile(entry);

		// archive entries are untrusted: never write outside of the output folder (e.g. "../" or absolute paths)
		Path root = outputDir.toPath().toAbsolutePath().normalize();
		Path target = outputFile.toPath().toAbsolutePath().normalize();
		if (!target.startsWith(root) || target.equals(root)) {
			throw new IOException("Illegal archive entry path: " + entry.getPath());
		}

		File outputFolder = outputFile.getParentFile();

		// create parent folder if necessary
		createFolders(outputFolder);

		return new FileOutputStream(outputFile);
	}

	public FileFilter newPathFilter(Collection<FileInfo> selection) {
		return newPathFilter(selection.stream().map(FileInfo::getPath).collect(toSet()));
	}

	public FileFilter newPathFilter(Set<String> selection) {
		return f -> selection.contains(getOutputFile(f).getPath());
	}

}
