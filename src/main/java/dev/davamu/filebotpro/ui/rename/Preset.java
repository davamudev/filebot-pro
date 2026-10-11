package dev.davamu.filebotpro.ui.rename;

import static java.util.Collections.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.WebServices.*;
import static dev.davamu.filebotpro.media.XattrMetaInfo.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Stream;

import javax.swing.Icon;

import dev.davamu.filebotpro.CachedResource.Transform;
import dev.davamu.filebotpro.Language;
import dev.davamu.filebotpro.StandardRenameAction;
import dev.davamu.filebotpro.cli.FileOrder;
import dev.davamu.filebotpro.format.ExpressionFileFilter;
import dev.davamu.filebotpro.format.ExpressionFileFormat;
import dev.davamu.filebotpro.format.ExpressionFilter;
import dev.davamu.filebotpro.format.ExpressionFormat;
import dev.davamu.filebotpro.media.LocalDatasource;
import dev.davamu.filebotpro.web.Datasource;
import dev.davamu.filebotpro.web.EpisodeListProvider;
import dev.davamu.filebotpro.web.MovieIdentificationService;
import dev.davamu.filebotpro.web.MusicIdentificationService;
import dev.davamu.filebotpro.web.SortOrder;

public class Preset {

	public String name;
	public String path;
	public String includes;
	public String format;
	public String database;
	public String sortOrder;
	public String matchMode;
	public String language;
	public String action;

	// number key 1..9, "none" or null for the next free number key in alphabetical order
	public String shortcut;

	// order of the selected files: name, path, date, size, resolution, a {expression} or null for the default order
	public String fileOrder;

	public static final String SHORTCUT_NONE = "none";

	public Preset(String name, File path, ExpressionFilter includes, ExpressionFormat format, Datasource database, SortOrder sortOrder, String matchMode, Language language, StandardRenameAction action) {
		this.name = name;
		this.path = path == null ? null : path.getPath();
		this.includes = includes == null ? null : includes.getExpression();
		this.format = format == null ? null : format.getExpression();
		this.database = database == null ? null : database.getIdentifier();
		this.sortOrder = sortOrder == null ? null : sortOrder.name();
		this.matchMode = matchMode == null ? null : matchMode;
		this.language = language == null ? null : language.getCode();
		this.action = action == null ? null : action.name();
	}

	public String getName() {
		return name;
	}

	public File getInputFolder() {
		return getValue(path, File::new);
	}

	public ExpressionFileFilter getIncludeFilter() {
		return getInputFolder() == null ? null : getValue(includes, expression -> new ExpressionFileFilter(expression, xattr::getMetaInfo));
	}

	public ExpressionFileFormat getFormat() {
		return getValue(format, ExpressionFileFormat::new);
	}

	public String getMatchMode() {
		return getValue(matchMode, mode -> mode);
	}

	public SortOrder getSortOrder() {
		return getValue(sortOrder, SortOrder::forName);
	}

	public Language getLanguage() {
		return getValue(language, Language::getLanguage);
	}

	public StandardRenameAction getRenameAction() {
		return getValue(action, StandardRenameAction::forName);
	}

	/**
	 * @return number key 1..9, 0 for automatic or -1 for none
	 */
	public int getShortcut() {
		if (shortcut == null || shortcut.isEmpty()) {
			return 0;
		}
		if (SHORTCUT_NONE.equals(shortcut)) {
			return -1;
		}
		Integer key = getValue(shortcut, Integer::parseInt);
		return key != null && key >= 1 && key <= 9 ? key : 0;
	}

	public Preset withShortcut(String shortcut) {
		this.shortcut = shortcut;
		return this;
	}

	public String getFileOrderValue() {
		return fileOrder;
	}

	/**
	 * @return comparator for the selected files or null for the default order
	 */
	public Comparator<File> getFileOrder() {
		return getValue(fileOrder, FileOrder::forName);
	}

	public Preset withFileOrder(String fileOrder) {
		this.fileOrder = fileOrder == null || fileOrder.isBlank() ? null : fileOrder.trim();
		return this;
	}

	/**
	 * Sort files by the file order of this preset
	 */
	public List<File> sortFiles(List<File> files) {
		Comparator<File> order = getFileOrder();
		if (order == null) {
			return files;
		}

		List<File> sorted = new ArrayList<File>(files);
		sorted.sort(order);
		return sorted;
	}

	public Datasource getDatasource() {
		return getValue(database, id -> getService(id, getSupportedServices()));
	}

	public Icon getIcon() {
		return getValue(database, id -> getService(id, getSupportedServices()).getIcon());
	}

	private <T> T getValue(String s, Transform<String, T> t) {
		try {
			return s == null || s.isEmpty() ? null : t.transform(s);
		} catch (Exception e) {
			debug.log(Level.WARNING, e, e::toString);
		}
		return null;
	}

	public List<File> selectFiles() {
		File folder = getInputFolder();
		if (folder == null || !folder.isDirectory()) {
			return emptyList();
		}

		FileFilter filter = getIncludeFilter();

		return sortFiles(listFiles(folder, filter == null ? FILES : f -> FILES.accept(f) && filter.accept(f), HUMAN_NAME_ORDER));
	}

	public AutoCompleteMatcher getAutoCompleteMatcher() {
		Datasource db = getDatasource();

		if (db instanceof MovieIdentificationService) {
			return new MovieMatcher((MovieIdentificationService) db);
		}

		if (db instanceof EpisodeListProvider) {
			return new EpisodeListMatcher((EpisodeListProvider) db, db == AniDB);
		}

		if (db instanceof MusicIdentificationService) {
			return new MusicMatcher((MusicIdentificationService) db);
		}

		if (db instanceof LocalDatasource) {
			return new LocalFileMatcher((LocalDatasource) db);
		}

		throw new IllegalStateException("Illegal datasource: " + db);
	}

	@Override
	public String toString() {
		return name;
	}

	public static Datasource[] getSupportedServices() {
		return Stream.of(getEpisodeListProviders(), getMovieIdentificationServices(), getMusicIdentificationServices(), getLocalDatasources()).flatMap(Stream::of).toArray(Datasource[]::new);
	}

	public static StandardRenameAction[] getSupportedActions() {
		if (isWindowsApp()) {
			// CoW clones not supported on Windows
			return new StandardRenameAction[] { StandardRenameAction.MOVE, StandardRenameAction.COPY, StandardRenameAction.KEEPLINK, StandardRenameAction.SYMLINK, StandardRenameAction.HARDLINK };
		} else {
			// CoW clones / reflinks supported on macOS and Linux
			return new StandardRenameAction[] { StandardRenameAction.MOVE, StandardRenameAction.COPY, StandardRenameAction.KEEPLINK, StandardRenameAction.SYMLINK, StandardRenameAction.HARDLINK, StandardRenameAction.CLONE };
		}

	}

	public static Language[] getSupportedLanguages() {
		return Stream.of(Language.preferredLanguages(), Language.availableLanguages()).flatMap(List::stream).toArray(Language[]::new);
	}

	public static String[] getSupportedMatchModes() {
		return new String[] { RenamePanel.MATCH_MODE_OPPORTUNISTIC, RenamePanel.MATCH_MODE_STRICT };
	}

}
