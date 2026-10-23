package dev.davamu.filebotpro.cli;

import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import dev.davamu.filebotpro.archive.Archive;
import dev.davamu.filebotpro.archive.FileMapper;
import dev.davamu.filebotpro.vfs.FileInfo;

/**
 * Extracts archive entries while applying the conflict action to each entry on its own, so that existing files are only
 * replaced when the conflict action says so.
 */
public final class ExtractOperation {

	/**
	 * @param entries
	 *            archive entries mapped to their output paths by the given output mapper
	 * @return the files that now hold the extracted entries, or an empty list if there was nothing to extract
	 */
	public static List<File> extract(Archive archive, FileMapper outputMapper, Collection<FileInfo> entries, ConflictAction conflict) throws Exception {
		Set<FileInfo> extract = new LinkedHashSet<FileInfo>();
		Set<FileInfo> index = new LinkedHashSet<FileInfo>();
		List<File> unchanged = new ArrayList<File>();

		for (FileInfo entry : entries) {
			File target = entry.toFile();

			if (!target.exists()) {
				extract.add(entry);
				continue;
			}

			switch (conflict == null ? ConflictAction.SKIP : conflict) {
				case OVERRIDE:
					extract.add(entry);
					break;
				case AUTO:
					// same size means the entry has already been extracted, otherwise replace the (likely incomplete) file
					if (entry.getLength() == target.length()) {
						unchanged.add(target);
					} else {
						extract.add(entry);
					}
					break;
				case INDEX:
					index.add(entry);
					break;
				case FAIL:
					throw new CmdlineException(String.format("Failed to extract [%s] because [%s] already exists", entry.toFile().getName(), target));
				default:
					log.fine(format("Skipped [%s] because it already exists", target));
					break;
			}
		}

		if (extract.isEmpty() && index.isEmpty()) {
			log.finest("Skipped extracting files " + entries);
			return new ArrayList<File>();
		}

		List<File> extractedFiles = new ArrayList<File>(unchanged);

		if (extract.size() > 0) {
			log.finest("Extracting files " + extract);
			archive.extract(outputMapper.getOutputDir(), outputMapper.newPathFilter(extract));

			for (FileInfo it : extract) {
				extractedFiles.add(it.toFile());
			}
		}

		if (index.size() > 0) {
			extractedFiles.addAll(extractIndexed(archive, outputMapper, index));
		}

		return extractedFiles;
	}

	private static List<File> extractIndexed(Archive archive, FileMapper outputMapper, Collection<FileInfo> entries) throws Exception {
		// extract into a temporary folder next to the existing files and then move each file to the next available indexed name
		File outputDir = outputMapper.getOutputDir();
		File tempDir = Files.createTempDirectory(outputDir.toPath(), ".extract-").toFile();

		try {
			FileMapper tempMapper = new FileMapper(tempDir);
			Set<String> selection = new LinkedHashSet<String>();
			for (FileInfo it : entries) {
				selection.add(tempMapper.getOutputFile(relativeEntry(outputDir, it.toFile())).getPath());
			}

			log.finest("Extracting files " + entries);
			archive.extract(tempDir, tempMapper.newPathFilter(selection));

			List<File> extractedFiles = new ArrayList<File>();
			for (FileInfo it : entries) {
				File source = tempMapper.getOutputFile(relativeEntry(outputDir, it.toFile()));
				File destination = nextAvailableIndexedName(it.toFile());

				log.fine(format("[INDEX] Extract [%s] to [%s]", it.toFile().getName(), destination));
				extractedFiles.add(moveRename(source, destination));
			}
			return extractedFiles;
		} finally {
			try {
				delete(tempDir);
			} catch (IOException e) {
				debug.warning(cause("Failed to delete temporary folder " + tempDir, e));
			}
		}
	}

	private static File relativeEntry(File outputDir, File target) {
		return outputDir.toPath().relativize(target.toPath()).toFile();
	}

	private static File nextAvailableIndexedName(File file) {
		File parent = file.getParentFile();
		String name = getName(file);
		String ext = getExtension(file);
		return IntStream.range(1, 1000).mapToObj(i -> new File(parent, ext == null ? name + '.' + i : name + '.' + i + '.' + ext)).filter(f -> !f.exists()).findFirst().orElseThrow(() -> {
			return new CmdlineException(String.format("Failed to extract [%s] because no indexed file name is available", file));
		});
	}

	private ExtractOperation() {
		throw new UnsupportedOperationException();
	}

}
