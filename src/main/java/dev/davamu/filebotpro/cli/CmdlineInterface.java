package dev.davamu.filebotpro.cli;

import static java.util.Collections.*;

import java.io.File;
import java.io.FileFilter;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import dev.davamu.filebotpro.Language;
import dev.davamu.filebotpro.RenameAction;
import dev.davamu.filebotpro.format.ExpressionFileFormat;
import dev.davamu.filebotpro.format.ExpressionFilter;
import dev.davamu.filebotpro.format.ExpressionFormat;
import dev.davamu.filebotpro.format.ExpressionMapper;
import dev.davamu.filebotpro.hash.HashType;
import dev.davamu.filebotpro.subtitle.SubtitleFormat;
import dev.davamu.filebotpro.subtitle.SubtitleNaming;
import dev.davamu.filebotpro.web.Datasource;
import dev.davamu.filebotpro.web.EpisodeListProvider;
import dev.davamu.filebotpro.web.SortOrder;

public interface CmdlineInterface {

	List<File> rename(Collection<File> files, Datasource db, String query, SortOrder order, Locale locale, ExpressionFilter filter, ExpressionMapper mapper, boolean strict, ExpressionFileFormat format, File output, RenameAction action, ConflictAction conflict, ExecCommand exec) throws Exception;

	List<File> renameLinear(List<File> files, EpisodeListProvider db, String query, SortOrder order, Locale locale, ExpressionFilter filter, ExpressionMapper mapper, ExpressionFileFormat format, File output, RenameAction action, ConflictAction conflict, ExecCommand exec) throws Exception;

	List<File> rename(Map<File, File> rename, RenameAction action, ConflictAction conflict) throws Exception;

	/**
	 * @return source and destination of the files processed by the last rename operation
	 */
	default Map<File, File> getRenameLog() {
		return emptyMap();
	}

	List<File> revert(Collection<File> files, FileFilter filter, RenameAction action) throws Exception;

	List<File> getSubtitles(Collection<File> files, String query, Language language, SubtitleFormat output, Charset encoding, SubtitleNaming format, boolean strict) throws Exception;

	List<File> getMissingSubtitles(Collection<File> files, String query, Language language, SubtitleFormat output, Charset encoding, SubtitleNaming format, boolean strict) throws Exception;

	boolean check(Collection<File> files) throws Exception;

	File compute(Collection<File> files, HashType hash, File output, Charset encoding) throws Exception;

	Stream<String> fetchEpisodeList(EpisodeListProvider db, String query, SortOrder order, Locale locale, ExpressionFilter filter, ExpressionMapper mapper, ExpressionFormat format, boolean strict) throws Exception;

	Stream<String> getMediaInfo(Collection<File> files, FileFilter filter, ExpressionFormat format) throws Exception;

	IntStream execute(Collection<File> files, FileFilter filter, ExpressionFormat format, ExecCommand exec) throws Exception;

	List<File> extract(Collection<File> files, File output, ConflictAction conflict, FileFilter filter, boolean forceExtractAll) throws Exception;

}
