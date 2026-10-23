package dev.davamu.filebotpro.cli;

import static java.awt.GraphicsEnvironment.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.hash.VerificationUtilities.*;
import static dev.davamu.filebotpro.media.XattrMetaInfo.*;
import static dev.davamu.filebotpro.subtitle.SubtitleUtilities.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.File;
import java.io.FileFilter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.NamedOptionDef;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;
import org.kohsuke.args4j.spi.BooleanOptionHandler;
import org.kohsuke.args4j.spi.ExplicitBooleanOptionHandler;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.RestOfArgumentsHandler;
import org.kohsuke.args4j.spi.StopOptionHandler;

import groovy.lang.Closure;

import dev.davamu.filebotpro.ApplicationFolder;
import dev.davamu.filebotpro.Execute;
import dev.davamu.filebotpro.Language;
import dev.davamu.filebotpro.RenameAction;
import dev.davamu.filebotpro.StandardRenameAction;
import dev.davamu.filebotpro.WebServices;
import dev.davamu.filebotpro.format.ExpressionFileFilter;
import dev.davamu.filebotpro.format.ExpressionFileFormat;
import dev.davamu.filebotpro.format.ExpressionFilter;
import dev.davamu.filebotpro.format.ExpressionFormat;
import dev.davamu.filebotpro.format.ExpressionMapper;
import dev.davamu.filebotpro.hash.HashType;
import dev.davamu.filebotpro.subtitle.SubtitleFormat;
import dev.davamu.filebotpro.subtitle.SubtitleNaming;
import dev.davamu.filebotpro.ui.PanelBuilder;
import dev.davamu.filebotpro.web.Datasource;
import dev.davamu.filebotpro.web.EpisodeListProvider;
import dev.davamu.filebotpro.web.SortOrder;

public class ArgumentBean {

	@Option(name = "--mode", usage = "Enable CLI interactive mode", metaVar = "[interactive, binary]")
	public String mode = null;

	@Option(name = "-rename", usage = "Rename media files")
	public boolean rename = false;

	@Option(name = "--db", usage = "Database", metaVar = "[TheTVDB, AniDB, TheMovieDB::TV, TheMovieDB, TVmaze, OMDb, OpenSubtitles, Shooter, AnimeLists, FanartTV, AcoustID, ID3, xattr, exif, file]")
	public String db;

	@Option(name = "--order", usage = "Episode order", metaVar = "[Airdate, Official, DVD, Absolute, AbsoluteAirdate, Digital, StoryArc, Production]")
	public String order = "Airdate";

	@Option(name = "--action", usage = "Rename action", metaVar = "[move, copy, keeplink, symlink, hardlink, clone, duplicate, test]")
	public String action = "move";

	@Option(name = "--conflict", usage = "Conflict resolution", metaVar = "[skip, override, auto, index, fail] or {closure}")
	public String conflict = "skip";

	@Option(name = "--file-order", usage = "Process files in the given order (decides which file wins if multiple files are renamed to the same path)", metaVar = "[name, path, date, size, resolution] or {expression}")
	public String fileOrder = null;

	@Option(name = "--filter", usage = "Filter expression", handler = GroovyExpressionHandler.class)
	public String filter = null;

	@Option(name = "--mapper", usage = "Mapper expression", handler = GroovyExpressionHandler.class)
	public String mapper = null;

	@Option(name = "--format", usage = "Format expression", handler = GroovyExpressionHandler.class)
	public String format;

	@Option(name = "--apply", usage = "Apply post-processing features to the renamed files", handler = ApplyOptionHandler.class)
	public List<String> apply = new ArrayList<String>();

	@Option(name = "-non-strict", usage = "Enable advanced matching and more aggressive guessing")
	public boolean nonStrict = false;

	@Option(name = "-get-subtitles", usage = "Fetch subtitles")
	public boolean getSubtitles;

	@Option(name = "--q", usage = "Force lookup query", metaVar = "series / movie query")
	public String query;

	@Option(name = "--lang", usage = "Language", metaVar = "language code")
	public String lang = "en";

	@Option(name = "-check", usage = "Create / Check verification files")
	public boolean check;

	@Option(name = "--output", usage = "Output path", metaVar = "path")
	public String output;

	@Option(name = "--encoding", usage = "Output character encoding", metaVar = "[UTF-8, Windows-1252]")
	public String encoding;

	@Option(name = "-list", usage = "Print episode list")
	public boolean list = false;

	@Option(name = "-mediainfo", usage = "Print media info")
	public boolean mediaInfo = false;

	@Option(name = "-find", usage = "Find files matching --file-filter or --filter and print them or run -exec for each")
	public boolean find = false;

	@Option(name = "-revert", usage = "Revert files")
	public boolean revert = false;

	@Option(name = "-extract", usage = "Extract archives")
	public boolean extract = false;

	@Option(name = "-script", usage = "Run Groovy script", metaVar = "[fn:name] or [script.groovy]")
	public String script = null;

	@Option(name = "--def", usage = "Define script variables", handler = BindingsHandler.class)
	public Map<String, String> defines = new LinkedHashMap<String, String>();

	@Option(name = "-r", usage = "Recursively process folders")
	public boolean recursive = false;

	@Option(name = "-d", usage = "Folder mode: process the given folders as a whole (e.g. movie folders) instead of the files inside")
	public boolean folderMode = false;

	@Option(name = "--file-filter", usage = "Input file filter expression", handler = GroovyExpressionHandler.class)
	public String inputFileFilter = null;

	@Option(name = "-exec", usage = "Execute command", metaVar = "echo {f} [+]", handler = RestOfArgumentsHandler.class)
	public List<String> exec = new ArrayList<String>();

	@Option(name = "-unixfs", usage = "Allow special characters in file paths")
	public boolean unixfs = false;

	@Option(name = "-no-xattr", usage = "Disable extended attributes")
	public boolean disableExtendedAttributes = false;

	@Option(name = "-no-history", usage = "Disable history")
	public boolean disableHistory = false;

	@Option(name = "-no-probe", usage = "Disable media parser (match files without reading file contents)")
	public boolean disableMediaParser = false;

	@Option(name = "-no-index", usage = "Disable local media indices (use less memory)")
	public boolean disableLocalIndex = false;

	@Option(name = "--log", usage = "Log level", metaVar = "[all, fine, info, warning]")
	public String log = "all";

	@Option(name = "--log-file", usage = "Log file", metaVar = "log.txt")
	public String logFile = null;

	@Option(name = "--log-lock", usage = "Lock log file", metaVar = "[yes, no]", handler = ExplicitBooleanOptionHandler.class)
	public boolean logLock = true;

	@Option(name = "-clear-cache", usage = "Clear cached and temporary data")
	public boolean clearCache = false;

	@Option(name = "-clear-prefs", usage = "Clear application settings")
	public boolean clearPrefs = false;

	@Option(name = "-clear-history", usage = "Clear application history")
	public boolean clearHistory = false;

	@Option(name = "-export-data", usage = "Export settings, presets, history and API keys into a zip file")
	public boolean exportData = false;

	@Option(name = "-import-data", usage = "Restore settings, presets, history and API keys from a zip file")
	public boolean importData = false;

	@Option(name = "-version", usage = "Print version identifier")
	public boolean version = false;

	@Option(name = "-help", usage = "Print this help message")
	public boolean help = false;

	// -- ends option parsing, all following arguments are files even if they start with - or @
	@Argument
	@Option(name = "--", handler = StopOptionHandler.class, hidden = true)
	public List<String> arguments = new ArrayList<String>();

	public boolean runCLI() {
		return rename || getSubtitles || check || list || mediaInfo || find || revert || extract || script != null;
	}

	public boolean isInteractive() {
		return "interactive".equalsIgnoreCase(mode);
	}

	public boolean printVersion() {
		return version;
	}

	public boolean printHelp() {
		return help;
	}

	public boolean clearCache() {
		return clearCache;
	}

	public boolean clearUserData() {
		return clearPrefs;
	}

	public boolean clearHistory() {
		return clearHistory;
	}

	public List<File> getFiles(boolean resolveFolders) throws Exception {
		if (arguments == null || arguments.isEmpty()) {
			return emptyList();
		}

		// resolve given paths
		List<File> files = new ArrayList<File>();

		for (String it : arguments) {
			// ignore empty arguments
			if (it.trim().isEmpty()) {
				continue;
			}

			// Windows: a quoted drive root ("D:\" from the Explorer context menu) arrives as D:" because \" escapes the quote
			if (File.separatorChar == '\\' && it.endsWith("\"")) {
				it = it.substring(0, it.length() - 1) + '\\';
			}

			// resolve relative paths
			File file = new File(it);

			// since we don't want to follow symlinks, we need to take the scenic route through the Path class
			try {
				file = file.toPath().toRealPath(LinkOption.NOFOLLOW_LINKS).toFile();
			} catch (Exception e) {
				debug.warning(format("Illegal Argument: %s (%s)", e, file));
			}

			if (resolveFolders && file.isDirectory() && !folderMode) {
				if (recursive) {
					files.addAll(listFiles(file, FILES, HUMAN_NAME_ORDER));
				} else {
					files.addAll(getChildren(file, f -> f.isFile() && !f.isHidden(), HUMAN_NAME_ORDER));
				}
			} else {
				files.add(file);
			}
		}

		// input file filter (e.g. useful on Windows where find -exec is not an option)
		if (inputFileFilter != null) {
			return filter(files, new ExpressionFileFilter(inputFileFilter, f -> f));
		}

		return files;
	}

	public RenameAction getRenameAction() throws Exception {
		// support custom groovy scripts (via closures)
		if (action.startsWith("{") || action.endsWith("}")) {
			return new GroovyRenameAction(action);
		}

		// support custom groovy scripts (via files)
		if (action.endsWith(".groovy")) {
			return new GroovyRenameAction(readTextFile(new File(action)));
		}

		// standard actions (e.g. move or copy)
		if (StandardRenameAction.names().stream().anyMatch(action::equalsIgnoreCase)) {
			return StandardRenameAction.forName(action);
		}

		// support custom executables (via path, e.g. /usr/local/bin/action or C:\Tools\action.bat, or via the PATH, e.g. action => action.cmd)
		File executable = Execute.which(action);
		if (executable != null) {
			return new ExecutableRenameAction(executable.getPath(), getOutputPath());
		}

		try {
			return StandardRenameAction.forName(action);
		} catch (IllegalArgumentException e) {
			throw new CmdlineException("Invalid --action: " + e.getMessage(), e);
		}
	}

	/**
	 * @return comparator for --file-order (e.g. --file-order resolution), or null
	 */
	public Comparator<File> getFileOrder() throws Exception {
		return fileOrder == null ? null : FileOrder.forName(fileOrder);
	}

	public ConflictAction getConflictAction() {
		// custom conflict actions fall back to skip
		if (ApplyOptionHandler.isCustomAction(conflict)) {
			return ConflictAction.SKIP;
		}
		try {
			return ConflictAction.forName(conflict);
		} catch (IllegalArgumentException e) {
			throw new CmdlineException("Invalid --conflict: " + e.getMessage(), e);
		}
	}

	/**
	 * @return custom conflict action (e.g. --conflict "{ from, to -> 'index' }" or --conflict conflict.groovy), or null
	 */
	public Closure<?> getConflictResolver() throws Exception {
		if (!ApplyOptionHandler.isCustomAction(conflict)) {
			return null;
		}
		return GroovyRenameAction.compile(conflict.trim().endsWith(".groovy") ? readTextFile(new File(conflict.trim())) : conflict);
	}

	public SortOrder getSortOrder() {
		try {
			return SortOrder.forName(order);
		} catch (IllegalArgumentException e) {
			throw new CmdlineException("Invalid --order: " + e.getMessage(), e);
		}
	}

	public ExpressionFormat getExpressionFormat() throws Exception {
		return format == null ? null : new ExpressionFormat(format);
	}

	public ExpressionFileFormat getExpressionFileFormat() throws Exception {
		return format == null ? null : new ExpressionFileFormat(format);
	}

	public ExpressionFilter getExpressionFilter() throws Exception {
		return filter == null ? null : new ExpressionFilter(filter);
	}

	public FileFilter getFileFilter() throws Exception {
		return filter == null ? FILES : new ExpressionFileFilter(filter, xattr::getMetaInfo);
	}

	public ExpressionMapper getExpressionMapper() throws Exception {
		return mapper == null ? null : new ExpressionMapper(mapper);
	}

	public Datasource getDatasource() {
		if (db == null) {
			return null;
		}

		Datasource service = WebServices.getService(db);
		if (service == null) {
			throw new CmdlineException(String.format("Invalid --db value: %s (valid values: %s)", db, stream(WebServices.getServices()).map(Datasource::getIdentifier).collect(joining(", "))));
		}
		return service;
	}

	public EpisodeListProvider getEpisodeListProvider() {
		if (db == null) {
			return WebServices.TheTVDB; // default to TheTVDB if --db is not set
		}

		EpisodeListProvider service = WebServices.getEpisodeListProvider(db);
		if (service == null) {
			throw new CmdlineException(String.format("Invalid --db value for episode data: %s (valid values: %s)", db, stream(WebServices.getEpisodeListProviders()).map(Datasource::getIdentifier).collect(joining(", "))));
		}
		return service;
	}

	public String getSearchQuery() {
		return query == null || query.isEmpty() ? null : query;
	}

	public File getOutputPath() {
		return output == null ? null : new File(output);
	}

	public File getAbsoluteOutputFolder() throws Exception {
		return output == null ? null : new File(output).getCanonicalFile();
	}

	public SubtitleFormat getSubtitleOutputFormat() {
		return output == null ? null : getSubtitleFormatByName(output);
	}

	public SubtitleNaming getSubtitleNamingFormat() {
		return optional(format).map(SubtitleNaming::forName).orElse(SubtitleNaming.MATCH_VIDEO_ADD_LANGUAGE_TAG);
	}

	public HashType getOutputHashType() {
		// support --output checksum.sfv
		return optional(output).map(File::new).map(f -> getHashType(f)).orElseGet(() -> {
			// support --format SFV
			return optional(format).map(k -> getHashTypeByExtension(k)).orElse(HashType.SFV);
		});
	}

	public Charset getEncoding() {
		return encoding == null ? null : Charset.forName(encoding);
	}

	public Language getLanguage() {
		// find language code for any input (en, eng, English, etc)
		return optional(lang).map(Language::findLanguage).orElseThrow(error("Illegal language code", lang));
	}

	public File getLogFile() {
		File file = new File(logFile);

		if (file.isAbsolute()) {
			return file;
		}

		// by default resolve relative paths against {applicationFolder}/logs/{logFile}
		return ApplicationFolder.AppData.resolve("logs/" + logFile);
	}

	public boolean isStrict() {
		return !nonStrict;
	}

	public Level getLogLevel() {
		return Level.parse(log.toUpperCase());
	}

	public Set<PostProcess.Feature> getPostProcessFeatures() {
		return apply.stream().filter(s -> !ApplyOptionHandler.isCustomAction(s)).map(PostProcess.Feature::forName).collect(toCollection(() -> EnumSet.noneOf(PostProcess.Feature.class)));
	}

	public PostProcess.Options getPostProcessOptions() throws Exception {
		PostProcess.Options options = new PostProcess.Options();
		for (String it : apply) {
			if (ApplyOptionHandler.isCustomAction(it)) {
				// e.g. --apply "{ f -> println f }" or --apply apply.groovy
				String script = it.trim().endsWith(".groovy") ? readTextFile(new File(it.trim())) : it;
				options.addAction(GroovyRenameAction.compile(script));
			} else if (it.toLowerCase().startsWith("chmod=")) {
				options.setPermissions(it.substring(6).trim());
			}
		}
		return options;
	}

	public ExecCommand getExecCommand() {
		try {
			return exec == null || exec.isEmpty() ? null : ExecCommand.parse(exec, getOutputPath());
		} catch (Exception e) {
			throw new CmdlineException("Illegal exec expression: " + exec);
		}
	}

	public List<PanelBuilder> getPanelBuilders() {
		if (mode == null) {

			// default multi panel mode
			return asList(PanelBuilder.defaultSequence());
		}

		// only selected panels
		return optional(mode).map(m -> {
			Pattern pattern = Pattern.compile(mode, Pattern.CASE_INSENSITIVE);
			List<PanelBuilder> panel = stream(PanelBuilder.defaultSequence()).filter(p -> pattern.matcher(p.getName()).matches()).collect(toList());

			// throw exception if illegal pattern was passed in
			if (panel.isEmpty()) {
				return null;
			}

			return panel;
		}).orElseThrow(error("Illegal mode", mode));
	}

	private final String[] args;

	public ArgumentBean() {
		this.args = new String[0];
	}

	public ArgumentBean(String[] args) throws CmdLineException {
		this.args = args.clone();

		CmdLineParser parser = new CmdLineParser(this, ParserProperties.defaults().withAtSyntax(false));
		parser.parseArgument(expandArgumentFiles(parser, args));
	}

	/**
	 * Expand @file arguments (one argument per line, UTF-8 with or without BOM, blank lines and # comments are ignored). Option values are never expanded
	 * here, so that e.g. --format @format.groovy is read as an expression file by GroovyExpressionHandler.
	 */
	private static List<String> expandArgumentFiles(CmdLineParser parser, String[] args) throws CmdLineException {
		Map<String, OptionHandler<?>> options = new HashMap<String, OptionHandler<?>>();
		for (OptionHandler<?> handler : parser.getOptions()) {
			if (handler.option instanceof NamedOptionDef) {
				NamedOptionDef option = (NamedOptionDef) handler.option;
				options.put(option.name(), handler);
				stream(option.aliases()).forEach(a -> options.put(a, handler));
			}
		}

		List<String> expanded = new ArrayList<String>(args.length);
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			OptionHandler<?> option = options.get(arg);

			if (option instanceof RestOfArgumentsHandler || option instanceof StopOptionHandler) {
				// e.g. -exec echo @file
				expanded.addAll(asList(args).subList(i, args.length));
				break;
			}

			if (option != null && !(option instanceof BooleanOptionHandler)) {
				// option and option value
				expanded.add(arg);
				if (i + 1 < args.length) {
					expanded.add(args[++i]);
				}
				continue;
			}

			if (arg.length() > 1 && arg.startsWith("@")) {
				expanded.addAll(readArgumentFile(parser, new File(arg.substring(1))));
				continue;
			}

			expanded.add(arg);
		}
		return expanded;
	}

	private static List<String> readArgumentFile(CmdLineParser parser, File file) throws CmdLineException {
		if (!file.isFile()) {
			throw new CmdLineException(parser, "Argument file not found: " + file, null);
		}

		try {
			return readLines(file).stream().map(String::strip).filter(s -> s.length() > 0 && !s.startsWith("#")).collect(toList());
		} catch (Exception e) {
			throw new CmdLineException(parser, "Failed to read argument file: " + file + ": " + e.getMessage(), e);
		}
	}

	public String[] getArgumentArray() {
		return args.clone();
	}

	public String usage() {
		StringWriter buffer = new StringWriter();
		CmdLineParser parser = new CmdLineParser(this, ParserProperties.defaults().withShowDefaults(false).withOptionSorter(null));
		parser.printUsage(buffer, null);
		return buffer.toString();
	}

	private static <T> Optional<T> optional(T value) {
		return Optional.ofNullable(value);
	}

	private static Supplier<CmdlineException> error(String message, Object value) {
		return () -> new CmdlineException(message + ": " + value);
	}

	@Override
	public String toString() {
		return deepToString(args);
	}

	public static ArgumentBean parse(String... args) throws CmdLineException {
		try {
			return new ArgumentBean(args);
		} catch (CmdLineException e) {
			// MAS does not support or allow command-line applications and may run executables with strange arguments for no apparent reason (e.g. -psn_0_774333) so we ignore arguments completely in this case
			if (Boolean.parseBoolean(System.getProperty("apple.app.launcher"))) {
				return new ArgumentBean();
			}

			// just throw exception as usual when called from command-line and display argument errors
			throw e;
		}
	}

}
