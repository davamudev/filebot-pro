package dev.davamu.filebotpro.cli;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.media.MediaDetection.*;

import java.io.Console;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.IntPredicate;

import dev.davamu.filebotpro.RenameAction;
import dev.davamu.filebotpro.similarity.Match;
import dev.davamu.filebotpro.web.SearchResult;

/**
 * Plain line-based prompts for consoles where the full screen text UI is not available (e.g. cmd.exe and PowerShell on Windows). The console reads and
 * writes in the console code page, so non-ASCII file names are displayed correctly.
 */
public class CmdlineOperationsConsoleUI extends CmdlineOperations implements InteractiveCmdline {

	private final Console console;

	public CmdlineOperationsConsoleUI(Console console) {
		this.console = console;
	}

	@Override
	public List<File> renameAll(Map<File, File> renameMap, RenameAction renameAction, ConflictAction conflictAction, List<Match<File, ?>> matches, ExecCommand exec) throws Exception {
		// default behavior if rename map is empty
		if (renameMap.isEmpty()) {
			return super.renameAll(renameMap, renameAction, conflictAction, matches, exec);
		}

		List<Entry<File, File>> entries = new ArrayList<Entry<File, File>>(renameMap.entrySet());

		PrintWriter out = console.writer();
		out.printf("%n[%s / %s]%n", renameAction, conflictAction);
		for (int i = 0; i < entries.size(); i++) {
			Entry<File, File> it = entries.get(i);
			boolean checked = it.getKey().exists() && !it.getValue().exists();
			out.printf("%3d %s %s => %s%n", i + 1, checked ? "[x]" : "[ ]", it.getKey().getName(), it.getValue().getName());
		}

		BitSet selection = null;
		while (selection == null) {
			String input = console.readLine("Process [x] files? [Y]es / [n]o / numbers (e.g. 1,3-5): ");
			selection = parseSelection(input, entries.size(), i -> entries.get(i).getKey().exists() && !entries.get(i).getValue().exists());
		}

		if (selection.isEmpty()) {
			return emptyList();
		}

		Map<File, File> selectedMap = selection.stream().mapToObj(entries::get).collect(toMap(Entry::getKey, Entry::getValue, (a, b) -> a, LinkedHashMap::new));
		return super.renameAll(selectedMap, renameAction, conflictAction, matches, exec);
	}

	@Override
	protected <T extends SearchResult> List<T> selectSearchResult(String query, Collection<T> options, boolean sort, boolean alias, boolean strict, int limit) throws Exception {
		List<T> matches = getProbableMatches(sort ? query : null, options, alias, false);

		if (matches.size() <= 1) {
			return matches;
		}

		// manually select option if there is more than one
		T selection = showInputDialog(matches, "Multiple Options", String.format("Select best match for \"%s\"", query));

		if (selection == null) {
			return emptyList();
		}

		return singletonList(selection);
	}

	@Override
	public <T> T showInputDialog(Collection<T> options, String title, String message) throws Exception {
		List<T> values = new ArrayList<T>(options);

		PrintWriter out = console.writer();
		out.printf("%n[%s] %s%n", title, message);
		for (int i = 0; i < values.size(); i++) {
			out.printf("%3d %s%n", i + 1, values.get(i));
		}

		while (true) {
			String input = console.readLine("Select [1-%d] or press Enter to skip: ", values.size());
			if (input == null || input.isBlank()) {
				return null;
			}
			try {
				int i = Integer.parseInt(input.strip());
				if (i >= 1 && i <= values.size()) {
					return values.get(i - 1);
				}
			} catch (NumberFormatException e) {
				// ask again
			}
		}
	}

	/**
	 * @return selected indices, empty selection for no, or null for invalid input
	 */
	protected static BitSet parseSelection(String input, int size, IntPredicate checked) {
		BitSet selection = new BitSet(size);

		// EOF or Enter or yes => checked items
		if (input == null || input.isBlank() || input.strip().matches("(?i)y|yes")) {
			for (int i = 0; i < size; i++) {
				if (checked.test(i)) {
					selection.set(i);
				}
			}
			return selection;
		}

		if (input.strip().matches("(?i)n|no")) {
			return selection;
		}

		try {
			for (String token : input.split("[,\\s]+")) {
				if (token.isEmpty()) {
					continue;
				}
				String[] range = token.split("-", 2);
				int from = Integer.parseInt(range[0].strip());
				int to = range.length > 1 ? Integer.parseInt(range[1].strip()) : from;
				if (from < 1 || to > size || from > to) {
					return null;
				}
				selection.set(from - 1, to);
			}
			return selection;
		} catch (NumberFormatException e) {
			return null;
		}
	}

}
