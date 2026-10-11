package dev.davamu.filebotpro.platform.bash;

import static dev.davamu.filebotpro.WebServices.*;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Stream;

import dev.davamu.filebotpro.Language;
import dev.davamu.filebotpro.StandardRenameAction;
import dev.davamu.filebotpro.cli.ConflictAction;
import dev.davamu.filebotpro.cli.FileOrder;
import dev.davamu.filebotpro.cli.PostProcess;
import dev.davamu.filebotpro.web.Datasource;
import dev.davamu.filebotpro.web.SortOrder;
import picocli.AutoComplete;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

public class BashCompletionBuilder {

	@Option(names = "--mode", description = "Enable CLI interactive mode", completionCandidates = ModeCompletionCandidates.class)
	public String mode;

	@Option(names = "-rename", description = "Rename media files")
	public boolean rename = false;

	@Option(names = "--db", description = "Database", completionCandidates = DatabaseCompletionCandidates.class)
	public String db;

	@Option(names = "--order", description = "Episode order", completionCandidates = SortOrderCompletionCandidates.class)
	public SortOrder order = SortOrder.Airdate;

	@Option(names = "--action", description = "Rename action", completionCandidates = RenameActionCompletionCandidates.class)
	public StandardRenameAction action = StandardRenameAction.MOVE;

	@Option(names = "--conflict", description = "Conflict resolution", completionCandidates = ConflictActionCompletionCandidates.class)
	public ConflictAction conflict = ConflictAction.SKIP;

	@Option(names = "--file-order", description = "Process files in the given order", completionCandidates = FileOrderCompletionCandidates.class)
	public String fileOrder;

	@Option(names = "--filter", description = "Filter expression")
	public File filter;

	@Option(names = "--mapper", description = "Mapper expression")
	public File mapper;

	@Option(names = "--format", description = "Format expression")
	public File format;

	@Option(names = "--apply", arity = "1..*", description = "Apply post-processing features to the renamed files", completionCandidates = ApplyCompletionCandidates.class)
	public List<String> apply;

	@Option(names = "-non-strict", description = "Enable advanced matching and more aggressive guessing")
	public boolean nonStrict = false;

	@Option(names = "-get-subtitles", description = "Fetch subtitles")
	public boolean getSubtitles = false;

	@Option(names = "--q", description = "Force lookup query")
	public String query;

	@Option(names = "--lang", description = "Language", completionCandidates = LanguageCompletionCandidates.class)
	public String lang = "en";

	@Option(names = "-check", description = "Create / Check verification files")
	public boolean check = false;

	@Option(names = "--output", description = "Output path")
	public File output;

	@Option(names = "--encoding", description = "Output character encoding", completionCandidates = CharsetCompletionCandidates.class)
	public Charset encoding;

	@Option(names = "-list", description = "Print episode list")
	public boolean list = false;

	@Option(names = "-mediainfo", description = "Print media info")
	public boolean mediaInfo = false;

	@Option(names = "-find", description = "Find files matching --file-filter or --filter and print them or run -exec for each")
	public boolean find = false;

	@Option(names = "-revert", description = "Revert files")
	public boolean revert = false;

	@Option(names = "-extract", description = "Extract archives")
	public boolean extract = false;

	@Option(names = "-script", description = "Run Groovy script")
	public File script;

	@Option(names = "--def", description = "Define script variables", completionCandidates = DefineCompletionCandidates.class)
	public Map<String, String> defines;

	@Option(names = "-r", description = "Recursively process folders")
	public boolean recursive = false;

	@Option(names = "-d", description = "Folder mode: process the given folders as a whole")
	public boolean folderMode = false;

	@Option(names = "--file-filter", description = "Input file filter expression")
	public File inputFileFilter;

	@Option(names = "-exec", arity = "1..*", description = "Execute command")
	public List<String> exec;

	@Option(names = "-unixfs", description = "Allow special characters in file paths")
	public boolean unixfs = false;

	@Option(names = "-no-xattr", description = "Disable extended attributes")
	public boolean disableExtendedAttributes = false;

	@Option(names = "-no-history", description = "Disable history")
	public boolean disableHistory = false;

	@Option(names = "-no-probe", description = "Disable media parser (match files without reading file contents)")
	public boolean disableMediaParser = false;

	@Option(names = "-no-index", description = "Disable local media indices (use less memory)")
	public boolean disableLocalIndex = false;

	@Option(names = "--log", description = "Log level", completionCandidates = LogLevelCompletionCandidates.class)
	public String log = "all";

	@Option(names = "--log-file", description = "Log file")
	public File logFile;

	@Option(names = "--log-lock", description = "Lock log file", completionCandidates = YesNoCompletionCandidates.class)
	public String logLock;

	@Option(names = "-clear-cache", description = "Clear cached and temporary data")
	public boolean clearCache = false;

	@Option(names = "-clear-prefs", description = "Clear application settings")
	public boolean clearPrefs = false;

	@Option(names = "-clear-history", description = "Clear application history")
	public boolean clearHistory = false;

	@Option(names = "-export-data", description = "Export settings, presets, history and API keys into a zip file")
	public boolean exportData = false;

	@Option(names = "-import-data", description = "Restore settings, presets, history and API keys from a zip file")
	public boolean importData = false;

	@Option(names = "-version", description = "Print version identifier")
	public boolean version = false;

	@Option(names = "-help", description = "Print this help message")
	public boolean help = false;

	@Parameters
	public List<File> arguments;

	private static class DatabaseCompletionCandidates implements Iterable<String> {

		@Override
		public Iterator<String> iterator() {
			return Stream.of(getEpisodeListProviders(), getMovieIdentificationServices(), getMusicIdentificationServices(), getLocalDatasources()).flatMap(Stream::of).map(Datasource::getIdentifier).iterator();
		}
	}

	private static class SortOrderCompletionCandidates implements Iterable<String> {

		@Override
		public Iterator<String> iterator() {
			return Stream.of(SortOrder.values()).map(Enum::name).iterator();
		}
	}

	private static class RenameActionCompletionCandidates implements Iterable<String> {

		@Override
		public Iterator<String> iterator() {
			return Stream.of(StandardRenameAction.values()).map(Enum::name).map(String::toLowerCase).iterator();
		}
	}

	private static class FileOrderCompletionCandidates implements Iterable<String> {

		@Override
		public Iterator<String> iterator() {
			return Stream.of(FileOrder.VALUES).iterator();
		}
	}

	private static class ConflictActionCompletionCandidates implements Iterable<String> {

		@Override
		public Iterator<String> iterator() {
			return Stream.of(ConflictAction.values()).map(Enum::name).map(String::toLowerCase).iterator();
		}
	}

	private static class ModeCompletionCandidates implements Iterable<String> {

		@Override
		public Iterator<String> iterator() {
			return Stream.of("interactive").iterator();
		}
	}

	private static class LanguageCompletionCandidates implements Iterable<String> {

		@Override
		public Iterator<String> iterator() {
			return Language.availableLanguages().stream().map(Language::getISO2).iterator();
		}
	}

	private static class LogLevelCompletionCandidates implements Iterable<String> {

		@Override
		public Iterator<String> iterator() {
			return Stream.of(Level.class.getFields()).map(Field::getName).iterator();
		}
	}

	private static class CharsetCompletionCandidates implements Iterable<String> {

		@Override
		public Iterator<String> iterator() {
			return Stream.of("UTF-8", "Windows-1252").iterator();
		}
	}

	private static class ApplyCompletionCandidates implements Iterable<String> {

		@Override
		public Iterator<String> iterator() {
			return PostProcess.Feature.names().iterator();
		}
	}

	private static class YesNoCompletionCandidates implements Iterable<String> {

		@Override
		public Iterator<String> iterator() {
			return Stream.of("yes", "no").iterator();
		}
	}

	private static class DefineCompletionCandidates implements Iterable<String> {

		@Override
		public Iterator<String> iterator() {
			return Stream.of("name=value").iterator();
		}
	}

	/**
	 * usage: BashCompletionBuilder &lt;command name&gt; &lt;output file&gt;
	 */
	public static void main(String[] args) {
		AutoComplete.main("--name", args[0], "--completionScript", args[1], BashCompletionBuilder.class.getName(), "--force");
	}

}
