package dev.davamu.filebotpro.ui.rename;

import static java.awt.event.KeyEvent.*;
import static java.util.Collections.*;
import static java.util.Comparator.*;
import static javax.swing.KeyStroke.*;
import static javax.swing.SwingUtilities.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.MediaTypes.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.media.MediaDetection.*;
import static dev.davamu.filebotpro.media.XattrMetaInfo.*;
import static dev.davamu.filebotpro.util.ExceptionUtilities.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;
import static dev.davamu.filebotpro.util.ui.LoadingOverlayPane.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.IntStream;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;

import com.google.common.eventbus.Subscribe;

import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.ListSelection;
import ca.odell.glazedlists.swing.DefaultEventSelectionModel;
import dev.davamu.filebotpro.ApplicationFolder;
import dev.davamu.filebotpro.CacheManager;
import dev.davamu.filebotpro.History;
import dev.davamu.filebotpro.HistorySpooler;
import dev.davamu.filebotpro.InvalidResponseException;
import dev.davamu.filebotpro.Language;
import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.Settings;
import dev.davamu.filebotpro.StandardRenameAction;
import dev.davamu.filebotpro.UserFiles;
import dev.davamu.filebotpro.WebServices;
import dev.davamu.filebotpro.format.ExpressionFileFormat;
import dev.davamu.filebotpro.format.MediaBindingBean;
import dev.davamu.filebotpro.media.LocalDatasource;
import dev.davamu.filebotpro.media.MetaAttributes;
import dev.davamu.filebotpro.platform.mac.MacAppUtilities;
import dev.davamu.filebotpro.similarity.Match;
import dev.davamu.filebotpro.ui.SelectDialog;
import dev.davamu.filebotpro.ui.rename.FormatDialog.Mode;
import dev.davamu.filebotpro.ui.rename.RenameModel.FormattedFuture;
import dev.davamu.filebotpro.ui.transfer.BackgroundFileTransferablePolicy;
import dev.davamu.filebotpro.ui.transfer.TransferablePolicy;
import dev.davamu.filebotpro.ui.transfer.TransferablePolicy.TransferAction;
import dev.davamu.filebotpro.util.AlphanumComparator;
import dev.davamu.filebotpro.util.PreferencesMap.PreferencesEntry;
import dev.davamu.filebotpro.util.ui.ActionPopup;
import dev.davamu.filebotpro.util.ui.DefaultFancyListCellRenderer;
import dev.davamu.filebotpro.util.ui.LoadingOverlayPane;
import dev.davamu.filebotpro.vfs.FileInfo;
import dev.davamu.filebotpro.vfs.SimpleFileInfo;
import dev.davamu.filebotpro.web.AudioTrack;
import dev.davamu.filebotpro.web.AudioTrackFormat;
import dev.davamu.filebotpro.web.Episode;
import dev.davamu.filebotpro.web.EpisodeFormat;
import dev.davamu.filebotpro.web.Datasource;
import dev.davamu.filebotpro.web.EpisodeListProvider;
import dev.davamu.filebotpro.web.Movie;
import dev.davamu.filebotpro.web.MovieFormat;
import dev.davamu.filebotpro.web.MovieIdentificationService;
import dev.davamu.filebotpro.web.MusicIdentificationService;
import dev.davamu.filebotpro.web.SortOrder;
import dev.davamu.filebotpro.util.ui.EmptyState;
import com.formdev.flatlaf.FlatClientProperties;

import net.miginfocom.swing.MigLayout;

public class RenamePanel extends JComponent {

	public static final String MATCH_MODE_OPPORTUNISTIC = "Opportunistic";
	public static final String MATCH_MODE_STRICT = "Strict";

	protected final RenameModel renameModel = new RenameModel();

	protected final RenameList<FormattedFuture> namesList = new RenameList<FormattedFuture>(renameModel.names());

	protected final RenameList<File> filesList = new RenameList<File>(renameModel.files());

	protected final MatchAction matchAction = new MatchAction(renameModel);

	protected final RenameAction renameAction = new RenameAction(renameModel);

	private static final PreferencesEntry<String> persistentEpisodeFormat = Settings.forPackage(RenamePanel.class).entry("rename.format.episode");
	private static final PreferencesEntry<String> persistentMovieFormat = Settings.forPackage(RenamePanel.class).entry("rename.format.movie");
	private static final PreferencesEntry<String> persistentMusicFormat = Settings.forPackage(RenamePanel.class).entry("rename.format.music");
	private static final PreferencesEntry<String> persistentFileFormat = Settings.forPackage(RenamePanel.class).entry("rename.format.file");

	private static final PreferencesEntry<String> persistentLastFormatState = Settings.forPackage(RenamePanel.class).entry("rename.last.format.state").defaultValue(Mode.Episode.name());
	private static final PreferencesEntry<String> persistentPreferredMatchMode = Settings.forPackage(RenamePanel.class).entry("rename.match.mode").defaultValue(MATCH_MODE_OPPORTUNISTIC);
	private static final PreferencesEntry<String> persistentPreferredLanguage = Settings.forPackage(RenamePanel.class).entry("rename.language").defaultValue("en");
	private static final PreferencesEntry<String> persistentPreferredEpisodeOrder = Settings.forPackage(RenamePanel.class).entry("rename.episode.order").defaultValue("Airdate");

	private static final Map<String, Preset> persistentPresets = Settings.forPackage(RenamePanel.class).node("presets").asMap(Preset.class);

	private static final PreferencesEntry<String> persistentMatchDetails = Settings.forPackage(RenamePanel.class).entry("rename.details").defaultValue("false");

	protected final MatchDetailsPanel matchDetails = new MatchDetailsPanel();

	// fetch data in progress
	private boolean matching = false;
	private SwingWorker<?, ?> matchWorker = null;

	private void cancelMatch() {
		if (matching && matchWorker != null && !matchWorker.isDone()) {
			matchWorker.cancel(true);
			log.info("Fetch data has been cancelled");
		}
	}

	public RenamePanel() {
		namesList.addPropertyChangeListener(LOADING_PROPERTY, evt -> matching = (Boolean) evt.getNewValue());

		namesList.setTitle("New Names");
		namesList.setTransferablePolicy(new NamesListTransferablePolicy(renameModel.values()));

		filesList.setTitle("Original Files");
		filesList.setTransferablePolicy(new FilesListTransferablePolicy(renameModel.files()));

		// restore icon indicating current match mode
		matchAction.setMatchMode(isMatchModeStrict());

		try {
			// restore custom episode formatter
			renameModel.useFormatter(Episode.class, new ExpressionFormatter(persistentEpisodeFormat.getValue(), EpisodeFormat.SeasonEpisode, Episode.class));
		} catch (Exception e) {
			// use default formatter
		}

		try {
			// restore custom movie formatter
			renameModel.useFormatter(Movie.class, new ExpressionFormatter(persistentMovieFormat.getValue(), MovieFormat.NameYear, Movie.class));
		} catch (Exception e) {
			// use default movie formatter
			renameModel.useFormatter(Movie.class, new MovieFormatter());
		}

		try {
			// restore custom music formatter
			renameModel.useFormatter(AudioTrack.class, new ExpressionFormatter(persistentMusicFormat.getValue(), new AudioTrackFormat(), AudioTrack.class));
		} catch (Exception e) {
			// use default formatter
		}

		try {
			// restore custom music formatter
			renameModel.useFormatter(File.class, new ExpressionFormatter(persistentFileFormat.getValue(), new FileNameFormat(), File.class));
		} catch (Exception e) {
			// make sure to put File formatter at position 3
			renameModel.useFormatter(File.class, new FileNameFormatter());
		} finally {
			// use default filename formatter
			renameModel.useFormatter(FileInfo.class, new FileNameFormatter());
		}

		RenameListCellRenderer cellrenderer = new RenameListCellRenderer(renameModel, ApplicationFolder.UserHome.get());

		namesList.getListComponent().setCellRenderer(cellrenderer);
		ToolTipManager.sharedInstance().registerComponent(namesList.getListComponent()); // movie and episode details
		filesList.getListComponent().setCellRenderer(cellrenderer);

		DefaultEventSelectionModel<Match<Object, File>> selectionModel = new DefaultEventSelectionModel<Match<Object, File>>(renameModel.matches());
		selectionModel.setSelectionMode(ListSelection.MULTIPLE_INTERVAL_SELECTION);

		// use the same selection model for both lists to synchronize selection
		namesList.getListComponent().setSelectionModel(selectionModel);
		filesList.getListComponent().setSelectionModel(selectionModel);

		// synchronize viewports
		new ScrollPaneSynchronizer(namesList, filesList);

		// show bindings and media info of the selected match
		selectionModel.addListSelectionListener(evt -> {
			if (!evt.getValueIsAdjusting()) {
				updateMatchDetails();
			}
		});

		// delete items from both lists
		Action removeAction = newAction("Exclude Selected Items", ResourceManager.getIcon("dialog.cancel"), evt -> {
			RenameList list = null;
			boolean deleteCell;

			if (evt.getSource() instanceof JButton) {
				list = filesList;
				deleteCell = isShiftOrAltDown(evt);
			} else {
				list = ((RenameList) evt.getSource());
				deleteCell = isShiftOrAltDown(evt);
			}

			int[] indices = list.getListComponent().getSelectedIndices();
			if (indices.length > 0) {
				// remove from the bottom up so that the remaining indices stay valid
				for (int i = indices.length - 1; i >= 0; i--) {
					if (deleteCell) {
						EventList eventList = list.getModel();
						if (indices[i] < eventList.size()) {
							eventList.remove(indices[i]);
						}
					} else {
						renameModel.matches().remove(indices[i]);
					}
				}
				int index = Math.min(indices[0], list.getModel().size() - 1);
				if (index >= 0) {
					list.getListComponent().setSelectedIndex(index);
				}
			}
		});
		namesList.setRemoveAction(removeAction);
		filesList.setRemoveAction(removeAction);

		// create Match button
		JButton matchButton = new JButton(matchAction);
		matchButton.setVerticalTextPosition(SwingConstants.BOTTOM);
		matchButton.setHorizontalTextPosition(SwingConstants.CENTER);

		// create Rename button
		JButton renameButton = new JButton(renameAction);
		renameButton.setVerticalTextPosition(SwingConstants.BOTTOM);
		renameButton.setHorizontalTextPosition(SwingConstants.CENTER);

		// create fetch popup
		ActionPopup fetchPopup = createFetchPopup();

		Action fetchPopupAction = new ShowPopupAction("Fetch Data", ResourceManager.getIcon("action.fetch"));
		JButton fetchButton = new JButton(fetchPopupAction);
		filesList.getListComponent().setComponentPopupMenu(new FilePopupDispatcher(fetchPopup));
		namesList.getListComponent().setComponentPopupMenu(new MatchPopupDispatcher(fetchPopup));
		fetchButton.setComponentPopupMenu(fetchPopup);
		matchButton.setComponentPopupMenu(fetchPopup);
		namesList.getButtonPanel().add(fetchButton, "gap 4px, sgy button");

		// settings popup and button
		ActionPopup settingsPopup = createSettingsPopup();
		Action settingsPopupAction = new ShowPopupAction("Settings", ResourceManager.getIcon("action.settings"));
		JButton settingsButton = createImageButton(settingsPopupAction);
		settingsButton.setComponentPopupMenu(settingsPopup);
		renameButton.setComponentPopupMenu(settingsPopup);
		namesList.getButtonPanel().add(settingsButton, "gap 12px, sgy button");

		// open rename log button
		filesList.getButtonPanel().add(createImageButton(removeAction), "gap 4px, sgy button", 2);
		filesList.getButtonPanel().add(createImageButton(clearFilesAction), "gap 4px, sgy button");
		filesList.getButtonPanel().add(createImageButton(openHistoryAction), "gap 12px, sgy button");

		// create macros popup
		JButton presetsButton = createImageButton(new ShowPresetsPopupAction());
		filesList.getButtonPanel().add(presetsButton, "gap 4px, sgy button");

		// show popup on actionPerformed only when names list is empty
		matchButton.addActionListener(evt -> {
			if (renameModel.names().isEmpty()) {
				fetchPopupAction.actionPerformed(evt);
			}
		});

		// reveal file location on double click
		filesList.getListComponent().addMouseListener(mouseClicked(evt -> {
			if (evt.getClickCount() == 2) {
				getWindow(evt.getSource()).setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
				try {
					JList list = (JList) evt.getSource();
					if (list.getSelectedIndex() >= 0) {
						UserFiles.revealFiles(list.getSelectedValuesList());
					}
				} catch (Exception e) {
					debug.log(Level.WARNING, e.getMessage(), e);
				} finally {
					getWindow(evt.getSource()).setCursor(Cursor.getDefaultCursor());
				}
			}
		}));

		// reveal file location on double click
		namesList.getListComponent().addMouseListener(mouseClicked(evt -> {
			if (evt.getClickCount() == 2) {
				JList list = (JList) evt.getSource();
				if (list.getSelectedIndex() >= 0) {
					Match<Object, File> match = renameModel.getMatch(list.getSelectedIndex());
					Map<File, Object> context = renameModel.getMatchContext(match);

					if (match.getValue() != null) {
						MediaBindingBean sample = new MediaBindingBean(match.getValue(), match.getCandidate(), context);
						showFormatEditor(sample);
					}
				}
			}
		}));

		setLayout(new MigLayout("fill, insets dialog, gapx 10px", "[fill][align center, pref!][fill]", "[grow, align 33%][]"));
		add(new LoadingOverlayPane(filesList, filesList, "37px", "30px"), "grow, sizegroupx list");

		BackgroundFileTransferablePolicy<?> transferablePolicy = (BackgroundFileTransferablePolicy<?>) filesList.getTransferablePolicy();
		transferablePolicy.addPropertyChangeListener(evt -> {
			if (BackgroundFileTransferablePolicy.LOADING_PROPERTY.equals(evt.getPropertyName())) {
				filesList.firePropertyChange(LoadingOverlayPane.LOADING_PROPERTY, (boolean) evt.getOldValue(), (boolean) evt.getNewValue());
			}
		});

		// primary actions
		matchButton.putClientProperty(FlatClientProperties.STYLE, "margin: 8,14,8,14; iconTextGap: 4");
		renameButton.putClientProperty(FlatClientProperties.STYLE, "margin: 8,14,8,14; iconTextGap: 4");
		renameButton.putClientProperty(FlatClientProperties.STYLE_CLASS, "accent");

		filesList.setEmptyState(new EmptyState("rename", "Drop files and folders here", "or use Load to pick them"));
		namesList.setEmptyState(new EmptyState("episodes", "Fetch data to see the new names", "Match against TV, movie and music databases"));

		add(matchButton, "split 2, flowy, sizegroupx button");
		add(renameButton, "gapy 30px, sizegroupx button");

		// cancel fetch data by clicking the progress indicator or via ESC
		LoadingOverlayPane namesOverlay = new LoadingOverlayPane(namesList, namesList, "37px", "30px");
		namesOverlay.setCancelAction(this::cancelMatch, "Cancel (Esc)");
		add(namesOverlay, "grow, sizegroupx list");

		// match details below the lists (F6)
		matchDetails.setVisible(Boolean.parseBoolean(persistentMatchDetails.getValue()));
		add(matchDetails, "newline, span 3, growx, w 0:0, h 260px!, hidemode 3");

		// install F2, F3, F6, F7, 1..9 and Ctrl+O/L/M/N keystroke actions
		SwingUtilities.invokeLater(this::installKeyStrokeActions);
	}

	private boolean isTextInputFocused() {
		KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
		Component focusOwner = focusManager.getFocusOwner();
		return focusOwner instanceof JTextComponent || focusManager.getActiveWindow() != SwingUtilities.getWindowAncestor(this);
	}

	private void installKeyStrokeActions() {
		// plain file mode via F2
		AutoCompleteAction plainFileMode = new AutoCompleteAction("Plain File Mode", LocalDatasource.FILE.getIcon(), () -> new LocalFileMatcher(LocalDatasource.FILE));
		installAction(this, WHEN_IN_FOCUSED_WINDOW, getKeyStroke(VK_F2, 0), plainFileMode);

		// local xattr mode via F3
		AutoCompleteAction xattrMode = new AutoCompleteAction("Local Xattr Mode", LocalDatasource.XATTR.getIcon(), () -> new LocalFileMatcher(LocalDatasource.XATTR));
		installAction(this, WHEN_IN_FOCUSED_WINDOW, getKeyStroke(VK_F3, 0), xattrMode);

		// map 1..9 number keys to presets
		for (int presetNumber = 1; presetNumber <= 9; presetNumber++) {
			int key = presetNumber;

			installAction(this, WHEN_IN_FOCUSED_WINDOW, getPresetKeyStroke(presetNumber), newAction("Preset " + presetNumber, evt -> {
				// never apply presets while the user is typing numbers (e.g. into a text field or another window)
				if (isTextInputFocused()) {
					return;
				}

				try {
					Preset preset = getPresetShortcuts().get(key);

					if (preset != null) {
						new ApplyPresetAction(preset).actionPerformed(evt);
					} else {
						new ShowPresetsPopupAction().actionPerformed(evt);
					}
				} catch (Exception e) {
					debug.log(Level.WARNING, e, e::getMessage);
				}
			}));
		}

		// open, reveal, edit match and edit name for the selected file via Ctrl+O, Ctrl+L, Ctrl+M and Ctrl+N
		installSelectionKeyStroke(OPEN_KEY, "Open", i -> openFile(renameModel.files().get(i)));
		installSelectionKeyStroke(REVEAL_KEY, "Reveal", i -> UserFiles.revealFiles(singletonList(renameModel.files().get(i))));
		installSelectionKeyStroke(EDIT_MATCH_KEY, "Edit Match", i -> {
			if (i < renameModel.values().size() && getEditMatcher(renameModel.values().get(i)) != null) {
				editMatch(i);
			}
		});
		installAction(this, WHEN_IN_FOCUSED_WINDOW, EDIT_NAMES_KEY, newAction("Edit All Names", evt -> {
			if (!isTextInputFocused()) {
				editNames();
			}
		}));
		installSelectionKeyStroke(EDIT_NAME_KEY, "Edit Name", i -> {
			if (i < namesList.getListComponent().getModel().getSize()) {
				editName(i);
			}
		});

		// show or hide match details via F6 or SPACE (the lists bind SPACE to selection by default, so override it there as well)
		installAction(this, WHEN_IN_FOCUSED_WINDOW, getKeyStroke(VK_F6, 0), matchDetailsAction);

		Action toggleMatchDetails = newAction("Toggle Match Details", evt -> {
			if (!isTextInputFocused()) {
				matchDetailsAction.actionPerformed(evt);
			}
		});
		installAction(this, WHEN_IN_FOCUSED_WINDOW, getKeyStroke(VK_SPACE, 0), toggleMatchDetails);
		installAction(filesList.getListComponent(), WHEN_FOCUSED, getKeyStroke(VK_SPACE, 0), toggleMatchDetails);
		installAction(namesList.getListComponent(), WHEN_FOCUSED, getKeyStroke(VK_SPACE, 0), toggleMatchDetails);

		// cancel fetch data via ESC (only consumed while matching)
		installAction(this, WHEN_IN_FOCUSED_WINDOW, getKeyStroke(VK_ESCAPE, 0), new AbstractAction("Cancel Fetch Data") {

			@Override
			public void actionPerformed(ActionEvent evt) {
				cancelMatch();
			}

			@Override
			public boolean isEnabled() {
				return matching && matchWorker != null && !matchWorker.isDone();
			}
		});

		// copy debug information (paths and objects)
		installAction(this, WHEN_IN_FOCUSED_WINDOW, getKeyStroke(VK_F7, 0), newAction("Copy Debug Information", evt -> {
			withWaitCursor(evt.getSource(), () -> {
				String text = getDebugInfo();
				if (text.length() > 0) {
					copyToClipboard(text);
					log.info("Match model has been copied to clipboard");
				} else {
					log.warning("Match model is empty");
				}
			});
		}));
	}

	private ActionPopup createMatchPopup(int index) {
		Match<Object, File> match = renameModel.getMatch(index);
		Object value = match.getValue();
		File file = match.getCandidate();

		ActionPopup actionPopup = new ActionPopup(file != null ? file.getName() : "New Name", ResourceManager.getIcon("action.edit"));

		Action editFormat = newAction("Edit Format", ResourceManager.getIcon("action.format"), evt -> {
			showFormatEditor(new MediaBindingBean(value, file, renameModel.getMatchContext(match)));
		});
		editFormat.setEnabled(value instanceof Episode || value instanceof Movie || value instanceof AudioTrack || value instanceof File);
		actionPopup.add(editFormat);

		Action editMatch = newAction("Edit Match", ResourceManager.getIcon("action.search"), evt -> editMatch(index));
		editMatch.putValue(Action.ACCELERATOR_KEY, EDIT_MATCH_KEY);
		editMatch.setEnabled(file != null && getEditMatcher(value) != null);
		actionPopup.add(editMatch);

		Action editName = newAction("Edit Name", ResourceManager.getIcon("action.edit"), evt -> editName(index));
		editName.putValue(Action.ACCELERATOR_KEY, EDIT_NAME_KEY);
		editName.setEnabled(file != null);
		actionPopup.add(editName);

		Action editNames = newAction("Edit All Names …", ResourceManager.getIcon("action.edit"), evt -> editNames());
		editNames.putValue(Action.ACCELERATOR_KEY, EDIT_NAMES_KEY);
		editNames.setEnabled(file != null);
		actionPopup.add(editNames);

		return actionPopup;
	}

	/**
	 * Edit the new names of all files at once, one name per line
	 */
	private void editNames() {
		int size = Math.min(renameModel.files().size(), namesList.getListComponent().getModel().getSize());
		if (size == 0) {
			return;
		}

		// edit the selected names if more than one row is selected, or all names otherwise
		int[] rows = IntStream.of(namesList.getListComponent().getSelectedIndices()).filter(i -> i < size).toArray();
		if (rows.length < 2) {
			rows = IntStream.range(0, size).toArray();
		}

		List<String> names = new ArrayList<String>(rows.length);
		for (int i : rows) {
			names.add(String.valueOf(namesList.getListComponent().getModel().getElementAt(i)));
		}

		EditNamesDialog dialog = new EditNamesDialog(getWindow(this), names);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);

		List<String> edited = dialog.getNames();
		if (edited == null || edited.size() != rows.length || renameModel.files().size() < size) {
			return;
		}

		for (int j = 0; j < rows.length; j++) {
			// keep the match (and its metadata) of names that have not been changed
			if (!edited.get(j).equals(names.get(j))) {
				int i = rows[j];
				File file = renameModel.files().get(i);
				renameModel.matches().set(i, new Match<Object, File>(edited.get(j).trim() + '.' + getExtension(file), file));
			}
		}
	}

	private static final int MENU_SHORTCUT = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

	private static final KeyStroke OPEN_KEY = getKeyStroke(VK_O, MENU_SHORTCUT);
	private static final KeyStroke REVEAL_KEY = getKeyStroke(VK_L, MENU_SHORTCUT);
	private static final KeyStroke EDIT_MATCH_KEY = getKeyStroke(VK_M, MENU_SHORTCUT);
	private static final KeyStroke EDIT_NAME_KEY = getKeyStroke(VK_N, MENU_SHORTCUT);
	private static final KeyStroke EDIT_NAMES_KEY = getKeyStroke(VK_N, MENU_SHORTCUT | SHIFT_DOWN_MASK);

	/**
	 * Index of the selected row (files and names share the same selection) or -1 if there is no selected file
	 */
	private int getSelectedFileIndex() {
		int index = namesList.getListComponent().getSelectedIndex();
		if (index < 0 || index >= renameModel.files().size()) {
			return -1;
		}
		return index;
	}

	private void installSelectionKeyStroke(KeyStroke keyStroke, String name, IntConsumer action) {
		installAction(this, WHEN_IN_FOCUSED_WINDOW, keyStroke, newAction(name, evt -> {
			int index = getSelectedFileIndex();
			if (index >= 0 && !isTextInputFocused()) {
				action.accept(index);
			}
		}));
	}

	private void openFile(File file) {
		try {
			Desktop.getDesktop().open(file);
		} catch (Exception e) {
			log.log(Level.WARNING, e, cause("Failed to open file", e));
		}
	}

	private AutoCompleteMatcher getEditMatcher(Object value) {
		if (value instanceof Episode) {
			Episode episode = (Episode) value;
			if (episode.getSeriesInfo() != null) {
				EpisodeListProvider db = WebServices.getEpisodeListProvider(episode.getSeriesInfo().getDatabase());
				if (db != null) {
					return new EpisodeListMatcher(db, db == WebServices.AniDB);
				}
			}
		}

		if (value instanceof Movie) {
			Movie movie = (Movie) value;
			return new MovieMatcher(movie.getTmdbId() > 0 || movie.getImdbId() <= 0 ? WebServices.TheMovieDB : WebServices.OMDb);
		}

		return null;
	}

	private void editMatch(int index) {
		Match<Object, File> match = renameModel.getMatch(index);
		AutoCompleteMatcher matcher = getEditMatcher(match.getValue());
		File file = match.getCandidate();
		Window window = getWindow(this);

		SortOrder order = SortOrder.forName(persistentPreferredEpisodeOrder.getValue());
		Locale locale = Language.getLanguage(persistentPreferredLanguage.getValue()).getLocale();

		// ask for the series or movie name (or id) and match only the selected file
		SwingWorker<List<Match<File, ?>>, Void> worker = newSwingWorker(() -> matcher.match(singletonList(file), false, order, locale, false, window), matches -> {
			namesList.firePropertyChange(LOADING_PROPERTY, true, false);

			matches.stream().filter(m -> file.equals(m.getValue())).findFirst().ifPresent(m -> {
				// the model may have changed in the meantime
				int i = renameModel.files().indexOf(file);
				if (i >= 0 && i < renameModel.values().size()) {
					renameModel.values().set(i, m.getCandidate());
					namesList.getListComponent().setSelectedIndex(i);
				}
			});
		}, error -> {
			namesList.firePropertyChange(LOADING_PROPERTY, true, false);
			if (findCause(error, CancellationException.class) == null) {
				log.log(Level.WARNING, error, cause(getRootCause(error)));
			}
		});

		namesList.firePropertyChange(LOADING_PROPERTY, false, true);
		matchWorker = worker;
		worker.execute();
	}

	private void editName(int index) {
		File file = (File) filesList.getListComponent().getModel().getElementAt(index);
		Object object = namesList.getListComponent().getModel().getElementAt(index);

		try {
			// autofill with the original name and the other new names (e.g. to reuse the series name)
			List<String> suggestions = new ArrayList<String>();
			suggestions.add(object.toString());
			suggestions.add(getNameWithoutExtension(file.getName()));
			renameModel.names().stream().map(String::valueOf).distinct().limit(200).forEach(suggestions::add);

			String string = showInputDialog("Enter Name:", object.toString(), "Edit Name", this, suggestions);
			if (string != null && string.length() > 0) {
				renameModel.matches().set(index, new Match<Object, File>(string + '.' + getExtension(file), file));
			}
		} catch (Exception e) {
			debug.log(Level.WARNING, e, e::toString);
		}
	}

	private final Action matchDetailsAction = newAction("Match Details", ResourceManager.getIcon("action.properties"), evt -> {
		boolean visible = !matchDetails.isVisible();
		matchDetails.setVisible(visible);
		persistentMatchDetails.setValue(Boolean.toString(visible));
		updateMatchDetails();
		revalidate();
	});

	{
		matchDetailsAction.putValue(Action.ACCELERATOR_KEY, getKeyStroke(VK_F6, 0));
	}

	private void updateMatchDetails() {
		if (!matchDetails.isVisible()) {
			return;
		}

		int index = namesList.getListComponent().getSelectedIndex();
		if (index < 0 || index >= renameModel.matches().size()) {
			matchDetails.setSample(null);
			return;
		}

		Match<Object, File> match = renameModel.getMatch(index);
		Object value = match.getValue();
		File file = match.getCandidate();

		// plain names (e.g. Edit Name) have no bindings, but the file still has media properties
		Object info = value instanceof Episode || value instanceof Movie || value instanceof AudioTrack || value instanceof File ? value : null;
		matchDetails.setSample(file == null && info == null ? null : new MediaBindingBean(info, file, renameModel.getMatchContext(match)));
	}

	/**
	 * Open, reveal, rename or trash the file when right-clicking a file, and show the Fetch Data menu otherwise.
	 */
	private class FilePopupDispatcher extends JPopupMenu {

		private final JPopupMenu defaultPopup;

		public FilePopupDispatcher(JPopupMenu defaultPopup) {
			this.defaultPopup = defaultPopup;
		}

		@Override
		public void show(Component invoker, int x, int y) {
			JList<?> list = filesList.getListComponent();
			int index = list.locationToIndex(new Point(x, y));
			Rectangle bounds = index >= 0 ? list.getCellBounds(index, index) : null;

			if (bounds != null && bounds.contains(x, y) && isMultiSelection(list, index)) {
				// match or re-match the selected files
				createFetchPopup(getSelectedFiles()).show(invoker, x, y);
			} else if (bounds != null && bounds.contains(x, y) && list.getModel().getElementAt(index) instanceof File) {
				list.setSelectedIndex(index);
				createFilePopup(index).show(invoker, x, y);
			} else {
				defaultPopup.show(invoker, x, y);
			}
		}
	}

	/**
	 * Right-click on one of several selected items
	 */
	private boolean isMultiSelection(JList<?> list, int index) {
		return list.isSelectedIndex(index) && list.getSelectedIndices().length > 1 && index < renameModel.files().size();
	}

	private ActionPopup createFilePopup(int index) {
		File file = (File) filesList.getListComponent().getModel().getElementAt(index);
		Object value = index < renameModel.values().size() ? renameModel.values().get(index) : null;

		ActionPopup actionPopup = new ActionPopup(file.getName(), ResourceManager.getIcon("action.load"));

		Action open = newAction("Open", ResourceManager.getIcon("action.open"), evt -> openFile(file));
		open.putValue(Action.ACCELERATOR_KEY, OPEN_KEY);
		actionPopup.add(open);

		Action reveal = newAction("Reveal", ResourceManager.getIcon("action.load"), evt -> UserFiles.revealFiles(singletonList(file)));
		reveal.putValue(Action.ACCELERATOR_KEY, REVEAL_KEY);
		actionPopup.add(reveal);

		actionPopup.addSeparator();

		// match or re-match only this file and keep all other matches
		Action fetch = newAction("Match …", ResourceManager.getIcon("action.fetch"), evt -> {
			JList<?> list = filesList.getListComponent();
			Rectangle bounds = list.getCellBounds(index, index);
			if (bounds != null) {
				createFetchPopup(singletonList(file)).show(list, bounds.x + 20, bounds.y + bounds.height);
			}
		});
		fetch.setEnabled(!matching);
		actionPopup.add(fetch);

		actionPopup.add(newAction("Rename …", ResourceManager.getIcon("action.edit"), evt -> renameFile(file)));

		Action setAttributes = newAction("Set Attributes", ResourceManager.getIcon("action.attributes"), evt -> {
			try {
				xattr.setMetaInfo(file, value, null);
				log.info(String.format("Stored %s in the attributes of [%s]", value, file.getName()));
			} catch (Exception e) {
				log.log(Level.WARNING, e, cause("Failed to set attributes", e));
			}
		});
		setAttributes.setEnabled(value instanceof Episode || value instanceof Movie || value instanceof AudioTrack);
		actionPopup.add(setAttributes);

		actionPopup.addSeparator();

		actionPopup.add(newAction("Move to Trash", ResourceManager.getIcon("action.clear"), evt -> {
			if (JOptionPane.showConfirmDialog(getWindow(this), String.format("Move [%s] to the trash?", file.getName()), "Move to Trash", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) {
				return;
			}
			try {
				UserFiles.trash(file);
				int i = renameModel.files().indexOf(file);
				if (i >= 0) {
					renameModel.files().remove(i);
				}
				log.info(String.format("Moved [%s] to the trash", file.getName()));
			} catch (Exception e) {
				log.log(Level.WARNING, e, cause("Failed to move file to trash", e));
			}
		}));

		return actionPopup;
	}

	private void renameFile(File file) {
		try {
			String name = showInputDialog("Enter Name:", file.getName(), "Rename File", this, singletonList(file.getName()));
			if (name == null || name.isEmpty() || name.equals(file.getName())) {
				return;
			}

			File destination = new File(file.getParentFile(), validateFileName(name));
			if (destination.exists() && !destination.equals(file)) {
				log.warning(String.format("File already exists: %s", destination));
				return;
			}

			StandardRenameAction.MOVE.rename(file, destination);

			// record the rename so it can be reverted from the history dialog
			HistorySpooler.getInstance().append(singletonMap(file, destination));
			xattr.invalidate(file, destination);

			// keep the position and the match of the file
			int i = renameModel.files().indexOf(file);
			if (i >= 0) {
				renameModel.files().set(i, destination);
			}
		} catch (Exception e) {
			log.log(Level.WARNING, e, cause("Failed to rename file", e));
		}
	}

	/**
	 * Show the context menu of the selected match when right-clicking a list item, and the Fetch Data menu otherwise.
	 */
	private class MatchPopupDispatcher extends JPopupMenu {

		private final JPopupMenu defaultPopup;

		public MatchPopupDispatcher(JPopupMenu defaultPopup) {
			this.defaultPopup = defaultPopup;
		}

		@Override
		public void show(Component invoker, int x, int y) {
			JList<?> list = namesList.getListComponent();
			int index = list.locationToIndex(new Point(x, y));
			Rectangle bounds = index >= 0 ? list.getCellBounds(index, index) : null;

			if (bounds != null && bounds.contains(x, y) && isMultiSelection(list, index)) {
				// match or re-match the selected files
				createFetchPopup(getSelectedFiles()).show(invoker, x, y);
			} else if (bounds != null && bounds.contains(x, y) && index < renameModel.matches().size() && renameModel.getMatch(index).getValue() != null) {
				list.setSelectedIndex(index);
				createMatchPopup(index).show(invoker, x, y);
			} else {
				defaultPopup.show(invoker, x, y);
			}
		}
	}

	private boolean isMatchModeStrict() {
		return MATCH_MODE_STRICT.equalsIgnoreCase(persistentPreferredMatchMode.getValue());
	}

	private ActionPopup createPresetsPopup() {
		ActionPopup actionPopup = new ActionPopup("Presets", ResourceManager.getIcon("action.script"));

		List<Preset> presets = getPresets();
		if (presets.size() > 0) {
			Map<Integer, Preset> shortcuts = getPresetShortcuts();
			String group = null;
			for (Preset preset : presets) {
				// group by mode (presets are sorted by group), but no headers if all presets are in the same group
				if (presets.stream().map(RenamePanel::getPresetGroup).distinct().count() > 1 && !getPresetGroup(preset).equals(group)) {
					if (group != null) {
						actionPopup.addSeparator();
					}
					group = getPresetGroup(preset);
					actionPopup.addDescription(group);
				}

				ApplyPresetAction action = new ApplyPresetAction(preset);
				shortcuts.forEach((key, p) -> {
					if (p == preset) {
						action.putValue(Action.ACCELERATOR_KEY, getPresetKeyStroke(key));
					}
				});
				actionPopup.add(action);
			}
			actionPopup.addSeparator();
		}

		actionPopup.add(newAction("Edit Presets", ResourceManager.getIcon("script.add"), evt -> {
			Window window = getWindow(evt.getSource());

			Action newPreset = newAction("New Preset …", ResourceManager.getIcon("script.add"), a -> {
				Optional.ofNullable(JOptionPane.showInputDialog(window, "Preset Name:", a.getActionCommand(), JOptionPane.PLAIN_MESSAGE, null, null, "My Preset")).map(Object::toString).map(String::trim).filter(s -> s.length() > 0).ifPresent(n -> {
					showPresetEditor(new Preset(n, null, null, null, null, null, null, null, null), window);
				});
			});

			List<Object> options = new ArrayList<Object>(presets);
			options.add(newPreset);

			SelectDialog<Object> selectDialog = new SelectDialog<Object>(window, options) {

				@Override
				protected String convertValueToString(Object value) {
					return value instanceof Action ? (String) ((Action) value).getValue(Action.NAME) : super.convertValueToString(value);
				}

				@Override
				protected void configureValue(DefaultFancyListCellRenderer render, Object value) {
					if (value instanceof Preset) {
						Preset preset = (Preset) value;
						render.setIcon(preset.getIcon());
					} else if (value instanceof Action) {
						Action action = (Action) value;
						render.setText((String) action.getValue(Action.NAME));
						render.setIcon((Icon) action.getValue(Action.SMALL_ICON));
					}
				}
			};

			selectDialog.setTitle("Edit Presets");
			setOffsetLocation(selectDialog);
			selectDialog.setMinimumSize(new Dimension(250, 250));
			selectDialog.pack();
			selectDialog.setVisible(true);

			Object selection = selectDialog.getSelectedValue();
			if (selection instanceof Preset) {
				Preset preset = (Preset) selection;
				showPresetEditor(preset, window);
			} else if (selection instanceof Action) {
				Action action = (Action) selection;
				action.actionPerformed(new ActionEvent(newPreset, ActionEvent.ACTION_PERFORMED, "New Preset …"));
			}
		}));

		return actionPopup;
	}

	private ActionPopup createFetchPopup() {
		return createFetchPopup(null);
	}

	/**
	 * Selected files that are in the files list (or an empty list)
	 */
	private List<File> getSelectedFiles() {
		List<File> files = new ArrayList<File>();
		for (int i : filesList.getListComponent().getSelectedIndices()) {
			if (i < renameModel.files().size()) {
				files.add(renameModel.files().get(i));
			}
		}
		return files;
	}

	/**
	 * @param scope
	 *            match only these files and keep all other matches, or match all files if null
	 */
	private ActionPopup createFetchPopup(List<File> scope) {
		String title = scope == null ? "Fetch & Match Data" : scope.size() == 1 ? "Match " + scope.get(0).getName() : String.format("Match %d Selected Items", scope.size());
		ActionPopup actionPopup = new ActionPopup(title, ResourceManager.getIcon("action.fetch"));

		actionPopup.addDescription("Episode Mode:");

		// create actions for match popup episode list completion
		for (EpisodeListProvider db : WebServices.getEpisodeListProviders()) {
			actionPopup.add(new AutoCompleteAction(db.getName(), db.getIcon(), () -> new EpisodeListMatcher(db, db == WebServices.AniDB), scope));
		}

		actionPopup.addSeparator();
		actionPopup.addDescription("Movie Mode:");

		// create action for movie name completion
		for (MovieIdentificationService it : WebServices.getMovieIdentificationServices()) {
			actionPopup.add(new AutoCompleteAction(it.getName(), it.getIcon(), () -> new MovieMatcher(it), scope));
		}

		actionPopup.addSeparator();
		actionPopup.addDescription("Music Mode:");
		for (MusicIdentificationService it : WebServices.getMusicIdentificationServices()) {
			actionPopup.add(new AutoCompleteAction(it.getName(), it.getIcon(), () -> new MusicMatcher(it), scope));
		}

		actionPopup.addSeparator();
		actionPopup.addDescription("Smart Mode:");
		actionPopup.add(new AutoCompleteAction("Autodetect", ResourceManager.getIcon("action.auto"), AutoDetectMatcher::new, scope));
		actionPopup.add(new AutoCompleteAction("Attributes", ResourceManager.getIcon("action.attributes"), AttributeMatcher::new, scope));

		if (scope != null) {
			return actionPopup;
		}

		actionPopup.addSeparator();
		actionPopup.addDescription("Options:");

		actionPopup.add(newAction("Edit Format", ResourceManager.getIcon("action.format"), evt -> showFormatEditor(null)));
		actionPopup.add(matchDetailsAction);

		actionPopup.add(newAction("Preferences", ResourceManager.getIcon("action.preferences"), evt -> {
			String[] modes = new String[] { MATCH_MODE_OPPORTUNISTIC, MATCH_MODE_STRICT };
			JComboBox modeCombo = new JComboBox(modes);

			List<Language> languages = new ArrayList<Language>();
			languages.addAll(Language.preferredLanguages()); // add preferred languages first
			languages.addAll(Language.availableLanguages()); // then others

			JComboBox orderCombo = new JComboBox(SortOrder.values());
			JList languageList = new JList(languages.toArray());
			languageList.setCellRenderer(new DefaultListCellRenderer() {

				@Override
				public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
					super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
					if (value != null) {
						setText(((Language) value).getName());
						setIcon(ResourceManager.getFlagIcon(((Language) value).getCode()));
					}
					return this;
				}
			});

			// restore current preference values
			try {
				modeCombo.setSelectedItem(persistentPreferredMatchMode.getValue());
				orderCombo.setSelectedItem(SortOrder.forName(persistentPreferredEpisodeOrder.getValue()));

				String selectedLanguage = persistentPreferredLanguage.getValue();
				languages.stream().filter(l -> l.getCode().equals(selectedLanguage)).findFirst().ifPresent(l -> languageList.setSelectedValue(l, true));
			} catch (Exception e) {
				debug.log(Level.WARNING, e.getMessage(), e);
			}

			// combo boxes keep their own border; wrapping them in a scroll pane draws a second, misaligned one
			JPanel spModeCombo = new JPanel(new MigLayout("fill, insets 0"));
			spModeCombo.setBorder(new TitledBorder("Match Mode"));
			spModeCombo.add(modeCombo, "growx");
			JScrollPane spLanguageList = new JScrollPane(languageList);
			spLanguageList.setBorder(new CompoundBorder(new TitledBorder("Language"), spLanguageList.getBorder()));
			JPanel spOrderCombo = new JPanel(new MigLayout("fill, insets 0"));
			spOrderCombo.setBorder(new TitledBorder("Episode Order"));
			spOrderCombo.add(orderCombo, "growx");

			// fix background issues on OSX
			spModeCombo.setOpaque(false);
			spLanguageList.setOpaque(false);
			spOrderCombo.setOpaque(false);

			JPanel message = new JPanel(new MigLayout("fill, flowy, insets 0"));
			message.add(spModeCombo, "growx");
			message.add(spLanguageList, "grow, hmin 50px");
			message.add(spOrderCombo, "growx");
			JOptionPane pane = new JOptionPane(message, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
			pane.createDialog(getWindowAncestor(RenamePanel.this), "Preferences").setVisible(true);

			if (pane.getValue() != null && pane.getValue().equals(JOptionPane.OK_OPTION)) {
				persistentPreferredMatchMode.setValue((String) modeCombo.getSelectedItem());
				persistentPreferredLanguage.setValue(((Language) languageList.getSelectedValue()).getCode());
				persistentPreferredEpisodeOrder.setValue(((SortOrder) orderCombo.getSelectedItem()).name());

				// update UI
				matchAction.setMatchMode(isMatchModeStrict());
			}
		}));

		return actionPopup;
	}

	private ActionPopup createSettingsPopup() {
		ActionPopup actionPopup = new ActionPopup("Rename Options", ResourceManager.getIcon("action.settings"));

		actionPopup.addDescription("Extension:");
		actionPopup.add(new SetRenameMode(false, "Preserve", ResourceManager.getIcon("action.extension.preserve")));
		actionPopup.add(new SetRenameMode(true, "Override", ResourceManager.getIcon("action.extension.override")));

		actionPopup.addSeparator();

		actionPopup.addDescription("Action:");
		for (StandardRenameAction action : Preset.getSupportedActions()) {
			actionPopup.add(new SetRenameAction(action));
		}

		return actionPopup;
	}

	private Mode getFormatEditorMode(MediaBindingBean binding) {
		if (binding != null) {
			if (binding.getInfoObject() instanceof Episode) {
				return Mode.Episode;
			} else if (binding.getInfoObject() instanceof Movie) {
				return Mode.Movie;
			} else if (binding.getInfoObject() instanceof AudioTrack) {
				return Mode.Music;
			} else if (binding.getInfoObject() instanceof File) {
				return Mode.File;
			} else {
				throw new IllegalArgumentException("Cannot format class: " + binding.getInfoObjectType()); // ignore objects that cannot be formatted
			}
		}

		try {
			return Mode.valueOf(persistentLastFormatState.getValue()); // restore previous mode
		} catch (Exception e) {
			debug.log(Level.WARNING, e, e::getMessage);
		}

		return Mode.Episode; // default to Episode mode
	}

	private void showFormatEditor(MediaBindingBean binding) {
		withWaitCursor(this, () -> {
			FormatDialog dialog = new FormatDialog(getWindowAncestor(RenamePanel.this), getFormatEditorMode(binding), binding, binding != null);
			setOffsetLocation(dialog);
			dialog.setVisible(true);

			if (dialog.submit()) {
				switch (dialog.getMode()) {
				case Episode:
					renameModel.useFormatter(Episode.class, new ExpressionFormatter(dialog.getFormat().getExpression(), EpisodeFormat.SeasonEpisode, Episode.class));
					persistentEpisodeFormat.setValue(dialog.getFormat().getExpression());
					break;
				case Movie:
					renameModel.useFormatter(Movie.class, new ExpressionFormatter(dialog.getFormat().getExpression(), MovieFormat.NameYear, Movie.class));
					persistentMovieFormat.setValue(dialog.getFormat().getExpression());
					break;
				case Music:
					renameModel.useFormatter(AudioTrack.class, new ExpressionFormatter(dialog.getFormat().getExpression(), new AudioTrackFormat(), AudioTrack.class));
					persistentMusicFormat.setValue(dialog.getFormat().getExpression());
					break;
				case File:
					renameModel.useFormatter(File.class, new ExpressionFormatter(dialog.getFormat().getExpression(), new FileNameFormat(), File.class));
					persistentFileFormat.setValue(dialog.getFormat().getExpression());
					break;
				}

				if (binding == null) {
					persistentLastFormatState.setValue(dialog.getMode().name());
				}
			}
		});
	}

	private void showPresetEditor(Preset preset, Window owner) {
		try {
			PresetEditor presetEditor = new PresetEditor(owner);
			presetEditor.setPreset(preset);

			setOffsetLocation(presetEditor);
			presetEditor.setVisible(true);

			switch (presetEditor.getResult()) {
			case SET:
				persistentPresets.put(preset.getName(), presetEditor.getPreset());
				break;
			case DELETE:
				persistentPresets.remove(preset.getName());
				break;
			case CANCEL:
				break;
			}
		} catch (Exception e) {
			debug.log(Level.WARNING, e, e::toString);
		}
	}

	private static KeyStroke getPresetKeyStroke(int key) {
		return getKeyStroke(Character.forDigit(key, 10), 0);
	}

	/**
	 * Presets by number key: presets with a fixed number key first, then all other presets in alphabetical order on the remaining number keys
	 */
	private Map<Integer, Preset> getPresetShortcuts() {
		List<Preset> presets = getPresets();
		Map<Integer, Preset> shortcuts = new TreeMap<Integer, Preset>();

		for (Preset p : presets) {
			if (p.getShortcut() > 0) {
				shortcuts.putIfAbsent(p.getShortcut(), p);
			}
		}

		PrimitiveIterator.OfInt free = IntStream.rangeClosed(1, 9).filter(i -> !shortcuts.containsKey(i)).iterator();
		for (Preset p : presets) {
			if (p.getShortcut() == 0 && free.hasNext()) {
				shortcuts.put(free.nextInt(), p);
			}
		}

		return shortcuts;
	}

	private List<Preset> getPresets() {
		// load Presets and ensure Preset order on all platforms (e.g. Windows Registry Preferences are sorted alphabetically, but the same is not guaranteed for other platforms)
		List<Preset> presets = new ArrayList<Preset>(persistentPresets.values());

		// group by mode (episodes, movies, music, files, format only), then alphabetical order within each group
		presets.sort(comparing(RenamePanel::getPresetGroupIndex).thenComparing(Preset::getName, new AlphanumComparator(Locale.getDefault())));
		return presets;
	}

	private static int getPresetGroupIndex(Preset preset) {
		try {
			Datasource db = preset.getDatasource();
			return db == null ? Mode.values().length : Mode.getMode(db).ordinal();
		} catch (Exception e) {
			return Mode.values().length; // unknown data source
		}
	}

	private static String getPresetGroup(Preset preset) {
		int index = getPresetGroupIndex(preset);
		return index < Mode.values().length ? Mode.values()[index].name() + " Mode:" : "Format Only:";
	}

	private String getDebugInfo() throws Exception {
		StringBuilder sb = new StringBuilder();

		for (Match<Object, File> m : renameModel.matches()) {
			String f = getStructurePathTail(m.getCandidate()).getPath();
			Object v = m.getValue();

			// convert FastFile items
			if (v instanceof File) {
				v = new SimpleFileInfo(getStructurePathTail((File) v).getPath(), ((File) v).length());
			}

			sb.append(f).append('\t').append(MetaAttributes.toJson(v)).append('\n');
		}

		return sb.toString();
	}

	private final Action clearFilesAction = newAction("Clear All", ResourceManager.getIcon("action.clear"), evt -> {
		if (isShiftOrAltDown(evt)) {
			renameModel.files().clear();
		} else {
			renameModel.clear();
		}
	});

	private final Action openHistoryAction = newAction("Open History", ResourceManager.getIcon("action.report"), evt -> {
		try {
			History model = HistorySpooler.getInstance().getCompleteHistory();

			HistoryDialog dialog = new HistoryDialog(getWindow(RenamePanel.this));
			dialog.setLocationRelativeTo(RenamePanel.this);
			dialog.setModel(model);

			// show and block
			dialog.setVisible(true);
		} catch (Exception e) {
			log.log(Level.WARNING, e, cause(getRootCause(e)));
		}
	});

	@Subscribe
	public void handle(Transferable transferable) throws Exception {
		for (TransferablePolicy handler : new TransferablePolicy[] { filesList.getTransferablePolicy(), namesList.getTransferablePolicy() }) {
			if (handler != null && handler.accept(transferable)) {
				handler.handleTransferable(transferable, TransferAction.PUT);
				return;
			}
		}
	}

	private static class ShowPopupAction extends AbstractAction {

		public ShowPopupAction(String name, Icon icon) {
			super(name, icon);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			JComponent source = (JComponent) e.getSource();
			source.getComponentPopupMenu().show(source, -3, source.getHeight() + 4);
		}
	};

	private class ShowPresetsPopupAction extends AbstractAction {

		public ShowPresetsPopupAction() {
			super("Presets", ResourceManager.getIcon("action.script"));
		}

		@Override
		public void actionPerformed(ActionEvent evt) {
			try {
				JComponent source = (JComponent) evt.getSource();
				createPresetsPopup().show(source, -3, source.getHeight() + 4);
			} catch (Exception e) {
				debug.log(Level.WARNING, e, e::toString);
			}
		}
	};

	private class ApplyPresetAction extends AutoCompleteAction {

		private Preset preset;

		public ApplyPresetAction(Preset preset) {
			super(preset.getName(), preset.getIcon(), preset::getAutoCompleteMatcher);
			this.preset = preset;
		}

		@Override
		public List<File> getFiles(ActionEvent evt) {
			File inputFolder = preset.getInputFolder();

			if (inputFolder == null) {
				if (preset.getFileOrder() != null) {
					try {
						// sort the original files (all matches are replaced anyway), on secondary EDT since sort keys may read media info
						List<File> original = new ArrayList<File>(renameModel.files());
						List<File> files = onSecondaryLoop(() -> preset.sortFiles(original));
						renameModel.clear();
						renameModel.files().addAll(files);
						return files;
					} catch (Exception e) {
						log.log(Level.WARNING, e, e::toString);
					}
				}
				return super.getFiles(evt); // default behaviour
			}


			try {
				List<File> selection = onSecondaryLoop(preset::selectFiles); // run potentially long-running operations on secondary EDT

				if (selection.size() > 0) {
					renameModel.clear();
					renameModel.files().addAll(selection);
					return selection;
				}

				log.info("No files have been selected.");
			} catch (Exception e) {
				log.log(Level.WARNING, e, e::toString);
			}

			return null; // cancel operation
		}

		@Override
		public boolean isStrict(ActionEvent evt) {
			return preset.getMatchMode() != null ? MATCH_MODE_STRICT.equals(preset.getMatchMode()) : super.isStrict(evt);
		}

		@Override
		public SortOrder getSortOrder(ActionEvent evt) {
			return preset.getSortOrder() != null ? preset.getSortOrder() : super.getSortOrder(evt);
		}

		@Override
		public Locale getLocale(ActionEvent evt) {
			return preset.getLanguage() != null ? preset.getLanguage().getLocale() : super.getLocale(evt);
		}

		@Override
		public void actionPerformed(ActionEvent evt) {
			SwingWorker<ExpressionFormatter, Void> worker = newSwingWorker(() -> {
				ExpressionFileFormat format = preset.getFormat();

				if (format != null && preset.getDatasource() != null) {
					switch (Mode.getMode(preset.getDatasource())) {
					case Episode:
						return new ExpressionFormatter(format, EpisodeFormat.SeasonEpisode, Episode.class);
					case Movie:
						return new ExpressionFormatter(format, MovieFormat.NameYear, Movie.class);
					case Music:
						return new ExpressionFormatter(format, new AudioTrackFormat(), AudioTrack.class);
					case File:
						return new ExpressionFormatter(format, new FileNameFormat(), File.class);
					}
				}

				return null;
			}, formatter -> {
				if (formatter != null) {
					renameModel.useFormatter(formatter.getTargetClass(), formatter);
				}

				if (preset.getRenameAction() != null) {
					new SetRenameAction(preset.getRenameAction()).actionPerformed(evt);
				}

				super.actionPerformed(evt);
			});

			// auto-match in progress
			namesList.firePropertyChange(LOADING_PROPERTY, false, true);
			worker.execute();
		}
	}

	private class SetRenameMode extends AbstractAction {

		private final boolean activate;

		private SetRenameMode(boolean activate, String name, Icon icon) {
			super(name, icon);
			this.activate = activate;
		}

		@Override
		public void actionPerformed(ActionEvent evt) {
			renameModel.setPreserveExtension(!activate);

			// display changed state
			filesList.repaint();
		}
	}

	private class SetRenameAction extends AbstractAction {

		private final StandardRenameAction action;

		public SetRenameAction(StandardRenameAction action) {
			super(action.getDisplayName(), ResourceManager.getIcon("rename.action." + action.name().toLowerCase()));
			this.action = action;
		}

		@Override
		public void actionPerformed(ActionEvent evt) {
			if (action == StandardRenameAction.MOVE) {
				renameAction.resetValues();
			} else {
				renameAction.putValue(RenameAction.RENAME_ACTION, action);
				renameAction.putValue(NAME, this.getValue(NAME));
				renameAction.putValue(SMALL_ICON, ResourceManager.getIcon("action." + action.name().toLowerCase()));
			}
		}
	}

	/**
	 * Alternative settings that are suggested when files could not be matched with the current settings
	 */
	private enum Retry {
		OPPORTUNISTIC, ABSOLUTE, MANUAL
	}

	private class AutoCompleteAction extends AbstractAction {

		protected final Supplier<AutoCompleteMatcher> matcher;

		// match only these files and keep the matches of all other files (or match all files if null)
		private final List<File> scope;

		public AutoCompleteAction(String name, Icon icon, Supplier<AutoCompleteMatcher> matcher) {
			this(name, icon, matcher, null);
		}

		public AutoCompleteAction(String name, Icon icon, Supplier<AutoCompleteMatcher> matcher, List<File> scope) {
			super(name, icon);

			// create matcher when required
			this.matcher = matcher;
			this.scope = scope;

			if (scope == null) {
				// disable action while episode list matcher is working
				namesList.addPropertyChangeListener(LOADING_PROPERTY, evt -> {
					// disable action while loading is in progress
					setEnabled(!(Boolean) evt.getNewValue());
				});
			} else {
				// short-lived popup action
				setEnabled(!matching);
			}
		}

		public List<File> getFiles(ActionEvent evt) {
			return scope != null ? scope : renameModel.files();
		}

		public boolean isStrict(ActionEvent evt) {
			return isMatchModeStrict();
		}

		public SortOrder getSortOrder(ActionEvent evt) {
			return SortOrder.forName(persistentPreferredEpisodeOrder.getValue());
		}

		public Locale getLocale(ActionEvent evt) {
			return Language.getLanguage(persistentPreferredLanguage.getValue()).getLocale();
		}

		private boolean isAutoDetectionEnabled(ActionEvent evt) {
			return !isShiftOrAltDown(evt); // skip name auto-detection if SHIFT is pressed
		}

		@Override
		public void actionPerformed(ActionEvent evt) {
			// select files
			List<File> files = getFiles(evt);
			if (files == null) {
				namesList.firePropertyChange(LOADING_PROPERTY, true, false);
				return;
			}

			if (scope == null) {
				// clear names list
				renameModel.values().clear();
			} else {
				// the model may have changed since the popup was opened
				files = filter(files, new HashSet<File>(renameModel.files())::contains);
			}

			match(files, scope != null, isStrict(evt), getSortOrder(evt), getLocale(evt), isAutoDetectionEnabled(evt), EnumSet.noneOf(Retry.class));
		}

		/**
		 * @param partial
		 *            keep the existing matches of all other files
		 * @param retries
		 *            alternative settings that have already been suggested
		 */
		private void match(List<File> files, boolean partial, boolean strict, SortOrder order, Locale locale, boolean autodetection, Set<Retry> retries) {
			List<File> remainingFiles = new LinkedList<File>(files);


			SwingWorker<List<Match<File, ?>>, Void> worker = new SwingWorker<List<Match<File, ?>>, Void>() {

				private AutoCompleteMatcher instance;

				@Override
				protected List<Match<File, ?>> doInBackground() throws Exception {
					instance = matcher.get();

					List<Match<File, ?>> matches = instance.match(remainingFiles, strict, order, locale, autodetection, getWindow(RenamePanel.this));

					// flush all memory caches to disk (before starting any long running file system operations that might be cancelled by the user)
					CacheManager.getInstance().flushAll();

					// remove matched files
					for (Match<File, ?> match : matches) {
						remainingFiles.remove(match.getValue());
					}

					return matches;
				}

				@Override
				protected void done() {
					try {
						List<Match<File, ?>> result = get();

						if (partial) {
							mergeMatches(result, files);
						} else {
							List<Match<Object, File>> matches = new ArrayList<Match<Object, File>>();

							for (Match<File, ?> match : result) {
								matches.add(new Match<Object, File>(match.getCandidate(), match.getValue()));
							}

							renameModel.clear();
							renameModel.addAll(matches);

							// add remaining file entries
							renameModel.files().addAll(remainingFiles);
						}

						// suggest other settings if some files could not be matched (after the loading state has been reset)
						Retry retry = getRetrySuggestion(instance, remainingFiles, strict, order, autodetection, retries);
						if (retry != null) {
							invokeLater(() -> suggestRetry(retry, remainingFiles, strict, order, locale, autodetection, retries));
						}
					} catch (Exception e) {
						// ignore cancellation exception
						if (findCause(e, CancellationException.class) != null) {
							return;
						}

						// common error message
						if (findCause(e, InvalidResponseException.class) != null) {
							log.log(Level.WARNING, findCause(e, InvalidResponseException.class).getMessage());
							return;
						}

						// generic error message
						log.log(Level.WARNING, e, cause(getRootCause(e)));
					} finally {
						// auto-match finished
						namesList.firePropertyChange(LOADING_PROPERTY, true, false);
					}
				}
			};

			// auto-match in progress
			namesList.firePropertyChange(LOADING_PROPERTY, false, true);
			matchWorker = worker;
			worker.execute();
		}

		/**
		 * Replace the matches of the given files and keep all other matches (matched files first, unmatched files at the end)
		 */
		private void mergeMatches(List<Match<File, ?>> result, List<File> scope) {
			Map<File, Object> updates = new HashMap<File, Object>();
			for (Match<File, ?> match : result) {
				updates.put(match.getValue(), match.getCandidate());
			}

			List<File> files = new ArrayList<File>(renameModel.files());
			List<Object> values = new ArrayList<Object>(renameModel.values());

			List<Match<Object, File>> matches = new ArrayList<Match<Object, File>>();
			List<File> remainingFiles = new ArrayList<File>();

			for (int i = 0; i < files.size(); i++) {
				File file = files.get(i);
				Object value = updates.containsKey(file) ? updates.get(file) : i < values.size() ? values.get(i) : null;

				if (value != null) {
					matches.add(new Match<Object, File>(value, file));
				} else {
					remainingFiles.add(file);
				}
			}

			renameModel.clear();
			renameModel.addAll(matches);
			renameModel.files().addAll(remainingFiles);

			// keep the matched files selected
			Set<File> selected = new HashSet<File>(scope);
			int[] selection = IntStream.range(0, renameModel.files().size()).filter(i -> selected.contains(renameModel.files().get(i))).toArray();
			namesList.getListComponent().setSelectedIndices(selection);
		}

		private Retry getRetrySuggestion(AutoCompleteMatcher instance, List<File> remainingFiles, boolean strict, SortOrder order, boolean autodetection, Set<Retry> retries) {
			// only episode and movie matching have alternative settings
			if (!(instance instanceof EpisodeListMatcher || instance instanceof MovieMatcher || instance instanceof AutoDetectMatcher)) {
				return null;
			}

			// ignore unmatched sidecar files and samples
			List<File> videos = filter(filter(remainingFiles, VIDEO_FILES), not(getClutterFileFilter()));
			if (videos.isEmpty()) {
				return null;
			}

			if (strict) {
				return retries.contains(Retry.OPPORTUNISTIC) ? null : Retry.OPPORTUNISTIC;
			}

			if (order == SortOrder.Airdate && !retries.contains(Retry.ABSOLUTE)) {
				if (instance instanceof EpisodeListMatcher || (instance instanceof AutoDetectMatcher && videos.stream().anyMatch(f -> isEpisode(f, false)))) {
					return Retry.ABSOLUTE;
				}
			}

			// manual selection asks for the series or movie name, which only works for a specific datasource
			if (autodetection && !(instance instanceof AutoDetectMatcher) && !retries.contains(Retry.MANUAL)) {
				return Retry.MANUAL;
			}

			return null;
		}

		private void suggestRetry(Retry retry, List<File> remainingFiles, boolean strict, SortOrder order, Locale locale, boolean autodetection, Set<Retry> retries) {
			long count = filter(remainingFiles, VIDEO_FILES).size();
			String label = count == 1 ? "1 file" : count + " files";

			String message;
			String option;
			switch (retry) {
			case OPPORTUNISTIC:
				message = String.format("%s could not be matched in Strict mode.", label);
				option = "Try Opportunistic Mode";
				break;
			case ABSOLUTE:
				message = String.format("%s could not be matched in Airdate order.", label);
				option = "Try Absolute Order";
				break;
			default:
				message = String.format("%s could not be matched automatically.", label);
				option = "Select Manually";
				break;
			}

			String[] options = { option, "Cancel" };
			int choice = JOptionPane.showOptionDialog(getWindow(RenamePanel.this), message, "Fetch Data", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
			if (choice != 0 || matching) {
				return;
			}

			Set<Retry> next = EnumSet.copyOf(retries);
			next.add(retry);

			// match only the remaining files with the alternative setting
			List<File> files = filter(remainingFiles, new HashSet<File>(renameModel.files())::contains);
			switch (retry) {
			case OPPORTUNISTIC:
				match(files, true, false, order, locale, autodetection, next);
				break;
			case ABSOLUTE:
				match(files, true, strict, SortOrder.Absolute, locale, autodetection, next);
				break;
			default:
				match(files, true, strict, order, locale, false, next);
				break;
			}
		}

	}

}
