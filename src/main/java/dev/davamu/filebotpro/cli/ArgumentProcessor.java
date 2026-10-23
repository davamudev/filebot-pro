package dev.davamu.filebotpro.cli;

import static java.nio.charset.StandardCharsets.*;
import static dev.davamu.filebotpro.ExitCode.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.MediaTypes.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.util.ExceptionUtilities.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.Console;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Stream;

import javax.script.Bindings;
import javax.script.SimpleBindings;

import dev.davamu.filebotpro.format.ExpressionFormat;
import dev.davamu.filebotpro.StandardRenameAction;

public class ArgumentProcessor {

	public int run(ArgumentBean args) {
		try {
			// interactive mode enables basic selection and confirmation dialogs in the CLI
			CmdlineInterface cli = createCmdlineInterface(args);

			// execute simple command
			if (args.script == null) {
				return runCommand(cli, args);
			}

			// execute user script
			runScript(cli, args);

			// script finished successfully
			return finish(SUCCESS);
		} catch (Throwable e) {
			// script failed with exception -> exit with non-zero exit code (and use positive code to avoid issues with launch4j launcher)
			return finish(getExitCode(e));
		}
	}

	private CmdlineInterface createCmdlineInterface(ArgumentBean args) {
		if (!args.isInteractive()) {
			return new CmdlineOperations();
		}

		Console console = System.console();
		if (console == null || !console.isTerminal()) {
			log.warning("Interactive mode requires an interactive console: continue in non-interactive mode");
			return new CmdlineOperations();
		}

		// the full screen text UI needs a Unix-like terminal on Windows (e.g. Cygwin or MSYS), so use plain prompts in cmd.exe and PowerShell
		if (!isWindowsConsole()) {
			try {
				return new CmdlineOperationsTextUI();
			} catch (Throwable e) {
				debug.log(Level.WARNING, "Text UI not available: " + e, e);
			}
		}

		return new CmdlineOperationsConsoleUI(console);
	}

	private boolean isWindowsConsole() {
		return File.separatorChar == '\\' && System.getenv("TERM") == null;
	}

	private int finish(int code) {
		// consoles with a legacy code page (e.g. Windows cmd) would print the kaomoji as question marks
		String kaomoji = getErrorKaomoji(code);
		if (System.out.charset().newEncoder().canEncode(kaomoji)) {
			log.finest(format("%s %s", getErrorMessage(code), kaomoji));
		} else {
			log.finest(getErrorMessage(code));
		}
		return code;
	}

	private int getExitCode(Throwable e) {
		if (findCause(e, CmdlineException.class) != null) {
			log.log(Level.WARNING, findCause(e, CmdlineException.class)::getMessage);
			return FAILURE;
		}

		if (findCause(e, ScriptDeath.class) != null) {
			ScriptDeath d = findCause(e, ScriptDeath.class);
			log.log(Level.WARNING, d::getMessage);
			return d.getExitCode();
		}

		log.log(Level.SEVERE, e, e::getMessage);
		return ERROR;
	}

	public int runCommand(CmdlineInterface cli, ArgumentBean args) throws Exception {
		// custom conflict action (e.g. --conflict "{ from, to -> 'index' }")
		if (cli instanceof CmdlineOperations) {
			((CmdlineOperations) cli).setConflictResolver(args.getConflictResolver());
			((CmdlineOperations) cli).setFileOrder(args.getFileOrder());
		}

		// print episode info or rename files in linear order
		if (args.list) {
			if (args.rename) {
				// rename files in linear order
				return cli.renameLinear(args.getFiles(true), args.getEpisodeListProvider(), args.getSearchQuery(), args.getSortOrder(), args.getLanguage().getLocale(), args.getExpressionFilter(), args.getExpressionMapper(), args.getExpressionFileFormat(), args.getAbsoluteOutputFolder(), args.getRenameAction(), args.getConflictAction(), args.getExecCommand()).size() > 0 ? SUCCESS : ERROR;
			} else {
				// print episode info
				return print(cli.fetchEpisodeList(args.getEpisodeListProvider(), args.getSearchQuery(), args.getSortOrder(), args.getLanguage().getLocale(), args.getExpressionFilter(), args.getExpressionMapper(), args.getExpressionFormat(), args.isStrict()));
			}
		}

		// print media info or execute commands based on media info
		if (args.mediaInfo || args.find) {
			ExecCommand exec = args.getExecCommand();
			if (exec != null) {
				// execute command for each file
				// run the command for every file (do not stop at the first failure)
				int[] results = cli.execute(args.getFiles(true), args.getFileFilter(), args.getExpressionFormat(), exec).toArray();
				if (results.length == 0) {
					return NOOP;
				}
				return java.util.Arrays.stream(results).allMatch(r -> r == SUCCESS) ? SUCCESS : ERROR;
			} else {
				// print media info, or the file paths for -find
				ExpressionFormat format = args.getExpressionFormat() == null && args.find ? new ExpressionFormat("{f}") : args.getExpressionFormat();
				return print(cli.getMediaInfo(args.getFiles(true), args.getFileFilter(), format));
			}
		}

		// revert files
		if (args.revert) {
			return cli.revert(args.getFiles(false), args.getFileFilter(), args.getRenameAction()).size() > 0 ? SUCCESS : ERROR;
		}

		// file operations
		Set<File> files = new LinkedHashSet<File>(args.getFiles(true));

		if (args.extract) {
			files.addAll(cli.extract(files, args.getOutputPath(), args.getConflictAction(), null, args.isStrict()));
		}

		if (args.getSubtitles) {
			files.addAll(cli.getMissingSubtitles(files, args.getSearchQuery(), args.getLanguage(), args.getSubtitleOutputFormat(), args.getEncoding(), args.getSubtitleNamingFormat(), args.isStrict()));
		}

		int exitCode = SUCCESS;

		if (args.rename) {
			// with --apply, run -exec after post-processing, so that commands see the final files (e.g. with embedded tags)
			boolean postProcess = args.apply.size() > 0 && args.getRenameAction() != StandardRenameAction.TEST;
			ExecCommand exec = args.getExecCommand();

			boolean onlyRename = !args.extract && !args.getSubtitles && !args.check;
			List<File> destinationFiles = cli.rename(files, args.getDatasource(), args.getSearchQuery(), args.getSortOrder(), args.getLanguage().getLocale(), args.getExpressionFilter(), args.getExpressionMapper(), args.isStrict(), args.getExpressionFileFormat(), args.getAbsoluteOutputFolder(), args.getRenameAction(), args.getConflictAction(), postProcess ? null : exec);

			// post-process renamed files (e.g. --apply artwork nfo)
			if (destinationFiles.size() > 0 && postProcess) {
				// take extras along (before prune, so that emptied source folders can be deleted)
				if (args.getPostProcessFeatures().contains(PostProcess.Feature.extras)) {
					PostProcess.importExtras(cli.getRenameLog(), args.getRenameAction());
				}

				PostProcess.apply(destinationFiles, args.getPostProcessFeatures(), args.getPostProcessOptions());

				// delete left-behind empty source folders
				if (args.getPostProcessFeatures().contains(PostProcess.Feature.prune) && args.getRenameAction() == StandardRenameAction.MOVE) {
					PostProcess.prune(files, args.getFiles(false));
				}

				if (exec != null) {
					long failed = cli.execute(destinationFiles, FILES, null, exec).filter(r -> r != SUCCESS).count();
					if (failed > 0) {
						log.warning(format("-exec failed for %d files", failed));
						exitCode = ERROR;
					}
				}
			} else if (cli instanceof CmdlineOperations && ((CmdlineOperations) cli).getExecFailures() > 0) {
				exitCode = ERROR;
			} else if (destinationFiles.isEmpty() && onlyRename) {
				// everything was skipped (e.g. the destination files already exist)
				exitCode = NOOP;
			}
		}

		if (args.check) {
			// check verification file
			if (containsOnly(files, VERIFICATION_FILES)) {
				if (!cli.check(files)) {
					throw new Exception("Data corruption detected"); // one or more hashes do not match
				}
			} else {
				cli.compute(files, args.getOutputHashType(), args.getOutputPath(), args.getEncoding());
			}
		}

		return exitCode;
	}

	private int print(Stream<?> values) {
		return values.peek(System.out::println).mapToInt(v -> 1).sum() > 0 ? SUCCESS : ERROR;
	}

	public void runScript(CmdlineInterface cli, ArgumentBean args) throws Throwable {
		Bindings bindings = new SimpleBindings();
		bindings.put(ScriptShell.SHELL_ARGS_BINDING_NAME, args);
		// pass folders as they are, unless -r or --file-filter ask to select files before calling the script
		bindings.put(ScriptShell.ARGV_BINDING_NAME, args.getFiles(args.recursive || args.inputFileFilter != null));

		ScriptSource source = ScriptSource.findScriptProvider(args.script);
		ScriptShell shell = new ScriptShell(source.getScriptProvider(args.script), cli, args.defines);
		shell.runScript(source.accept(args.script), bindings);
	}

}
