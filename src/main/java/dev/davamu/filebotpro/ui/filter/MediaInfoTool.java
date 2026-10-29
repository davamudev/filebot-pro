package dev.davamu.filebotpro.ui.filter;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static javax.swing.BorderFactory.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.MediaTypes.*;
import static dev.davamu.filebotpro.media.MediaDetection.releaseInfo;
import static dev.davamu.filebotpro.ui.ThemeSupport.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.swing.Action;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.Settings;
import dev.davamu.filebotpro.UserFiles;
import dev.davamu.filebotpro.mediainfo.MediaInfo;
import dev.davamu.filebotpro.mediainfo.MediaInfo.StreamKind;
import dev.davamu.filebotpro.util.PreferencesMap.PreferencesEntry;
import dev.davamu.filebotpro.util.ui.LoadingOverlayPane;
import net.miginfocom.swing.MigLayout;

class MediaInfoTool extends Tool<MediaInfoTool.MediaInfoData> {

	private static final PreferencesEntry<String> persistentSelectedView = Settings.forPackage(MediaInfoTool.class).entry("mediainfo.view").defaultValue(MediaInfoView.DEFAULT.getName());

	private static final int MIN_COLUMN_WIDTH = 50;
	private static final int DEFAULT_COLUMN_WIDTH = 150;

	private JTable table = new JTable(new MediaInfoTableModel());

	private final List<MediaInfoView> views = MediaInfoView.load();
	private final DefaultComboBoxModel<MediaInfoView> viewModel = new DefaultComboBoxModel<MediaInfoView>();
	private final JComboBox<MediaInfoView> viewCombo = new JComboBox<MediaInfoView>(viewModel);

	// column widths set by the user, by view name and column name
	private final Map<String, Map<String, Integer>> columnWidths = MediaInfoView.loadColumnWidths();
	private boolean columnWidthsChanged = false;

	private MediaInfoData data = new MediaInfoData(emptyList(), emptyMap());
	private boolean updatingViews = false;

	public MediaInfoTool() {
		super("MediaInfo");

		table.setAutoCreateRowSorter(true);
		table.setAutoCreateColumnsFromModel(true);
		table.setFillsViewportHeight(true);

		table.setCellSelectionEnabled(true);
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		table.setBackground(getPanelBackground());
		table.setGridColor(getColor(0xEEEEEE));
		table.setRowHeight(25);

		// what each property means, e.g. Video.Width
		table.setTableHeader(new JTableHeader(table.getColumnModel()) {

			@Override
			public String getToolTipText(MouseEvent evt) {
				int column = columnAtPoint(evt.getPoint());
				return column >= 0 && table.convertColumnIndexToModel(column) > 0 ? data.getToolTipText(table.getColumnName(column)) : null;
			}
		});

		JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.setBorder(createEmptyBorder());

		// remember column widths of each view while the user resizes columns, and save them once done
		table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {

			@Override
			public void columnMarginChanged(ChangeEvent evt) {
				TableColumn column = table.getTableHeader().getResizingColumn();
				if (column != null) {
					columnWidths.computeIfAbsent(getSelectedView().getName(), k -> new LinkedHashMap<String, Integer>()).put(column.getHeaderValue().toString(), column.getWidth());
					columnWidthsChanged = true;
				}
			}

			@Override
			public void columnAdded(TableColumnModelEvent evt) {
			}

			@Override
			public void columnRemoved(TableColumnModelEvent evt) {
			}

			@Override
			public void columnMoved(TableColumnModelEvent evt) {
			}

			@Override
			public void columnSelectionChanged(ListSelectionEvent evt) {
			}
		});

		table.getTableHeader().addMouseListener(new MouseAdapter() {

			@Override
			public void mouseReleased(MouseEvent evt) {
				if (columnWidthsChanged) {
					saveColumnWidths();
				}
			}
		});

		// custom views: user defined selection and order of columns
		updateViewModel(views.stream().filter(v -> v.getName().equals(persistentSelectedView.getValue())).findFirst().orElse(MediaInfoView.DEFAULT));
		viewCombo.addActionListener(evt -> {
			if (!updatingViews) {
				persistentSelectedView.setValue(getSelectedView().getName());
				applyView();
			}
		});

		Action renameAction = newAction("Rename View …", this::renameView);
		Action deleteAction = newAction("Delete View", ResourceManager.getIcon("action.clear"), this::deleteView);
		Action exportAction = newAction("Export View …", ResourceManager.getIcon("action.export"), this::exportView);

		JPopupMenu viewMenu = new JPopupMenu();
		viewMenu.add(newAction("New View …", ResourceManager.getIcon("script.add"), this::newView));
		viewMenu.add(newAction("Duplicate View …", this::duplicateView));
		viewMenu.add(renameAction);
		viewMenu.add(deleteAction);
		viewMenu.addSeparator();
		viewMenu.add(newAction("Import Views …", ResourceManager.getIcon("action.load"), this::importViews));
		viewMenu.add(exportAction);

		JButton viewMenuButton = createImageButton(newAction("View Options", ResourceManager.getIcon("action.menu"), evt -> {
			// the default view can't be renamed, deleted or exported
			boolean custom = !getSelectedView().isDefault();
			renameAction.setEnabled(custom);
			deleteAction.setEnabled(custom);
			exportAction.setEnabled(custom);

			JButton button = (JButton) evt.getSource();
			viewMenu.show(button, 0, button.getHeight());
		}));

		JPanel toolbar = new JPanel(new MigLayout("insets 12px 8px 12px 8px, nogrid, aligny center"));
		toolbar.setOpaque(false);
		toolbar.add(new JLabel("View:"));
		toolbar.add(viewCombo, "gap 10px, wmin 200, wmax 320");
		toolbar.add(newButton("Columns …", ResourceManager.getIcon("action.columns"), this::editColumns), "gap 16px");
		toolbar.add(viewMenuButton, "gap 8px");

		setLayout(new MigLayout("insets 0, gap 0, fill, flowy", "[grow, fill]", "[pref!][grow, fill]"));
		add(toolbar);
		add(new LoadingOverlayPane(scrollPane, this), "grow");
	}

	private MediaInfoView getSelectedView() {
		MediaInfoView view = (MediaInfoView) viewModel.getSelectedItem();
		return view != null ? view : MediaInfoView.DEFAULT;
	}

	private void updateViewModel(MediaInfoView selection) {
		updatingViews = true;
		try {
			viewModel.removeAllElements();
			viewModel.addElement(MediaInfoView.DEFAULT);
			views.forEach(viewModel::addElement);
			viewModel.setSelectedItem(selection);
		} finally {
			updatingViews = false;
		}

		persistentSelectedView.setValue(getSelectedView().getName());
		applyView();
	}

	private void saveViews(MediaInfoView selection) {
		try {
			MediaInfoView.store(views);
		} catch (Exception e) {
			log.log(Level.WARNING, "Failed to save MediaInfo views: " + e.getMessage(), e);
		}
		updateViewModel(selection);
	}

	private Set<String> getViewNames(MediaInfoView exclude) {
		Set<String> names = views.stream().filter(v -> v != exclude).map(MediaInfoView::getName).collect(toSet());
		names.add(MediaInfoView.DEFAULT.getName());
		return names;
	}

	private String getUniqueViewName(String name) {
		Set<String> names = getViewNames(null);
		String unique = name;
		for (int i = 2; names.contains(unique); i++) {
			unique = String.format("%s (%d)", name, i);
		}
		return unique;
	}

	private MediaInfoView showViewEditor(String title, MediaInfoView view, MediaInfoView replace, ActionEvent evt) {
		MediaInfoViewEditor editor = new MediaInfoViewEditor(getWindow(evt.getSource()), title, view, data, getViewNames(replace));
		setOffsetLocation(editor);
		editor.setVisible(true);
		return editor.getView();
	}

	private void addView(MediaInfoView view, MediaInfoView source) {
		if (view != null) {
			// new views start with the column widths of the view they are based on
			if (source != null) {
				copyColumnWidths(source.getName(), view.getName(), false);
			}
			views.add(view);
			saveViews(view);
		}
	}

	private void editColumns(ActionEvent evt) {
		MediaInfoView view = getSelectedView();

		// the default view can't be changed, start a new view with all the current columns instead
		if (view.isDefault()) {
			addView(showViewEditor("New View", new MediaInfoView(getUniqueViewName("My View"), data.getColumns()), null, evt), view);
			return;
		}

		MediaInfoView edit = showViewEditor("Edit View", view, view, evt);
		if (edit != null) {
			copyColumnWidths(view.getName(), edit.getName(), true);
			views.set(views.indexOf(view), edit);
			saveViews(edit);
		}
	}

	private void newView(ActionEvent evt) {
		addView(showViewEditor("New View", new MediaInfoView(getUniqueViewName("My View"), emptyList()), null, evt), null);
	}

	private void duplicateView(ActionEvent evt) {
		MediaInfoView view = getSelectedView();
		if (view.isDefault()) {
			addView(showViewEditor("New View", new MediaInfoView(getUniqueViewName("My View"), data.getColumns()), null, evt), view);
		} else {
			addView(showViewEditor("Duplicate View", new MediaInfoView(getUniqueViewName(view.getName() + " copy"), view.getColumns()), null, evt), view);
		}
	}

	private void copyColumnWidths(String from, String to, boolean move) {
		if (from.equals(to)) {
			return;
		}

		Map<String, Integer> widths = move ? columnWidths.remove(from) : columnWidths.get(from);
		if (widths != null) {
			columnWidths.put(to, new LinkedHashMap<String, Integer>(widths));
			saveColumnWidths();
		}
	}

	private void saveColumnWidths() {
		columnWidthsChanged = false;
		try {
			MediaInfoView.storeColumnWidths(columnWidths);
		} catch (Exception e) {
			debug.log(Level.WARNING, e, () -> "Failed to save MediaInfo column widths: " + e.getMessage());
		}
	}

	private void renameView(ActionEvent evt) {
		MediaInfoView view = getSelectedView();
		if (view.isDefault()) {
			return;
		}

		Object input = JOptionPane.showInputDialog(getWindow(evt.getSource()), "View name:", "Rename View", JOptionPane.PLAIN_MESSAGE, null, null, view.getName());
		String name = input == null ? "" : input.toString().trim();
		if (name.isEmpty() || name.equals(view.getName())) {
			return;
		}

		if (getViewNames(view).contains(name)) {
			JOptionPane.showMessageDialog(getWindow(evt.getSource()), "A view with this name already exists.", "Rename View", JOptionPane.WARNING_MESSAGE);
			return;
		}

		MediaInfoView renamed = view.rename(name);
		copyColumnWidths(view.getName(), renamed.getName(), true);
		views.set(views.indexOf(view), renamed);
		saveViews(renamed);
	}

	private void deleteView(ActionEvent evt) {
		MediaInfoView view = getSelectedView();
		if (view.isDefault()) {
			return;
		}

		int option = JOptionPane.showConfirmDialog(getWindow(evt.getSource()), String.format("Delete the view \"%s\"?", view.getName()), "Delete View", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (option == JOptionPane.OK_OPTION) {
			views.remove(view);
			if (columnWidths.remove(view.getName()) != null) {
				saveColumnWidths();
			}
			saveViews(MediaInfoView.DEFAULT);
		}
	}

	private void importViews(ActionEvent evt) {
		List<File> files = UserFiles.showLoadDialogSelectFiles(false, true, null, new ExtensionFileFilter("json"), "Import Views", evt);
		MediaInfoView selection = null;

		for (File f : files) {
			try {
				for (MediaInfoView view : MediaInfoView.fromJson(readTextFile(f))) {
					// same name and same columns => already there
					if (views.contains(view)) {
						selection = view;
						continue;
					}

					selection = view.rename(getUniqueViewName(view.getName()));
					views.add(selection);
				}
			} catch (Exception e) {
				log.log(Level.WARNING, String.format("Failed to import MediaInfo views from %s: %s", f.getName(), e.getMessage()), e);
			}
		}

		if (selection != null) {
			saveViews(selection);
		}
	}

	private void exportView(ActionEvent evt) {
		MediaInfoView view = getSelectedView();
		if (view.isDefault()) {
			return;
		}

		File file = UserFiles.showSaveDialogSelectFile(false, new File(validateFileName(view.getName()) + ".json"), "Export View", evt);
		if (file == null) {
			return;
		}

		if (!hasExtension(file, "json")) {
			file = new File(file.getPath() + ".json");
		}

		try {
			writeFile(view.toJson().getBytes(UTF_8), file);
			log.info(String.format("Exported view \"%s\" to %s", view.getName(), file));
		} catch (Exception e) {
			log.log(Level.WARNING, "Failed to export MediaInfo view: " + e.getMessage(), e);
		}
	}

	@Override
	protected MediaInfoData createModelInBackground(List<File> root) {
		if (root.isEmpty()) {
			return new MediaInfoData(emptyList(), emptyMap());
		}

		List<File> files = listFiles(root, filter(VIDEO_FILES, AUDIO_FILES, IMAGE_FILES), HUMAN_NAME_ORDER);
		Map<MediaInfoKey, String[]> data = new TreeMap<MediaInfoKey, String[]>();

		try (MediaInfo mi = new MediaInfo()) {
			IntStream.range(0, files.size()).forEach(fileIndex -> {
				File f = files.get(fileIndex);

				if ((VIDEO_FILES.accept(f) && f.length() > ONE_MEGABYTE) || (AUDIO_FILES.accept(f) && f.length() > ONE_KILOBYTE) || (IMAGE_FILES.accept(f) && f.length() > 0)) {
					try {
						mi.open(f);
						mi.snapshot().forEach((kind, streams) -> {
							IntStream.range(0, streams.size()).forEach(streamIndex -> {
								streams.get(streamIndex).forEach((name, value) -> {
									String[] values = data.computeIfAbsent(new MediaInfoKey(kind, streamIndex, name), k -> new String[files.size()]);
									values[fileIndex] = value;
								});
							});
						});
					} catch (IllegalArgumentException e) {
						debug.finest(e::toString);
					} catch (Exception e) {
						debug.warning(e::toString);
					}
				}

				if (Thread.interrupted()) {
					throw new CancellationException();
				}
			});
		}

		return new MediaInfoData(data.isEmpty() ? emptyList() : files, data);
	}

	@Override
	protected void setModel(MediaInfoData data) {
		this.data = data;
		applyView();
	}

	private void applyView() {
		MediaInfoView view = getSelectedView();
		List<String> columns = view.isDefault() ? data.getColumns() : view.getColumns();

		table.setModel(new MediaInfoTableModel(data.files, columns, data.values));
		table.setAutoResizeMode(table.getRowCount() > 0 ? JTable.AUTO_RESIZE_OFF : JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

		// restore the column widths the user has set for this view
		Map<String, Integer> widths = columnWidths.getOrDefault(view.getName(), emptyMap());
		TableColumnModel columnModel = table.getColumnModel();
		IntStream.range(0, columnModel.getColumnCount()).mapToObj(columnModel::getColumn).forEach(column -> {
			column.setMinWidth(MIN_COLUMN_WIDTH);
			column.setPreferredWidth(widths.getOrDefault(column.getHeaderValue().toString(), DEFAULT_COLUMN_WIDTH));
		});
	}

	private static class MediaInfoKey implements Comparable<MediaInfoKey> {

		public final StreamKind kind;
		public final int stream;
		public final String name;

		private static final Pattern strip = Pattern.compile("[^a-z]", Pattern.CASE_INSENSITIVE);

		public MediaInfoKey(StreamKind kind, int stream, String name) {
			this.kind = kind;
			this.stream = stream;
			this.name = strip.matcher(name).replaceAll("");
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof MediaInfoKey) {
				MediaInfoKey other = (MediaInfoKey) obj;
				return kind == other.kind && stream == other.stream && name.equals(other.name);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return kind.ordinal() + (stream << 8) + name.hashCode();
		}

		@Override
		public int compareTo(MediaInfoKey other) {
			if (kind != other.kind)
				return kind.compareTo(other.kind);
			if (stream != other.stream)
				return Integer.compare(stream, other.stream);
			else
				return name.compareTo(other.name);
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			if (kind != StreamKind.General) {
				sb.append(kind.name());
				if (stream > 0) {
					sb.append('[').append(stream).append(']');
				}
				sb.append('.');
			}
			return sb.append(name).toString();
		}

	}

	/**
	 * MediaInfo properties of all files by column name (e.g. {@code Video.Width}), in natural order.
	 */
	static class MediaInfoData {

		private final List<File> files;
		private final Map<String, String[]> values = new LinkedHashMap<String, String[]>();

		public MediaInfoData(List<File> files, Map<MediaInfoKey, String[]> values) {
			this.files = files;
			values.forEach((k, v) -> this.values.put(k.toString(), v));
		}

		public List<String> getColumns() {
			return new ArrayList<String>(values.keySet());
		}

		/**
		 * @return name and value of the first file that has this column, or {@code null}
		 */
		public Map.Entry<String, String> getExample(String column) {
			String[] v = values.get(column);
			if (v != null) {
				for (int i = 0; i < v.length; i++) {
					if (v[i] != null && !v[i].isEmpty()) {
						return Map.entry(files.get(i).getName(), v[i]);
					}
				}
			}
			return null;
		}

		private static final Pattern STREAM_NUMBER = Pattern.compile("\\[\\d+\\]");

		private static Map<String, String> descriptions;

		/**
		 * @return what MediaInfo says about this column, or {@code null}
		 */
		public static synchronized String getDescription(String column) {
			if (descriptions == null) {
				descriptions = new HashMap<String, String>();
				try {
					// e.g. Video.DisplayAspectRatio_Original => Video.DisplayAspectRatioOriginal
					releaseInfo.getMediaInfoDescriptions().forEach((property, description) -> {
						String[] k = property.split("\\.", 2);
						StreamKind kind = k[0].equals("Other") ? StreamKind.Chapters : Stream.of(StreamKind.values()).filter(s -> s.name().equals(k[0])).findFirst().orElse(null);
						if (kind != null) {
							descriptions.putIfAbsent(new MediaInfoKey(kind, 0, k[1]).toString(), description);
						}
					});
				} catch (Exception e) {
					debug.log(Level.WARNING, e, () -> "Failed to load MediaInfo descriptions: " + e.getMessage());
				}
			}
			return descriptions.get(STREAM_NUMBER.matcher(column).replaceFirst(""));
		}

		/**
		 * @return column name, description and an example value of the loaded files
		 */
		public String getToolTipText(String column) {
			String description = getDescription(column);
			Map.Entry<String, String> example = getExample(column);

			StringBuilder html = new StringBuilder("<html><b>").append(escapeHTML(column)).append("</b>");
			if (description != null) {
				html.append("<div style='width: 320px; margin-top: 4px'>").append(escapeHTML(description)).append("</div>");
			}
			if (example != null) {
				html.append("<div style='width: 320px; margin-top: 4px'>Example: <b>").append(escapeHTML(example.getValue())).append("</b> (").append(escapeHTML(example.getKey())).append(")</div>");
			}
			return html.append("</html>").toString();
		}

	}

	private static class MediaInfoTableModel extends AbstractTableModel {

		private final String[] keys;
		private final String[][] values;
		private final String[] files;
		private final Class<?>[] columnClass;

		public MediaInfoTableModel() {
			this(emptyList(), emptyList(), emptyMap());
		}

		public MediaInfoTableModel(List<File> files, List<String> columns, Map<String, String[]> values) {
			this.keys = columns.toArray(new String[0]);
			this.files = files.stream().map(File::getName).toArray(String[]::new);
			this.values = columns.stream().map(c -> values.getOrDefault(c, new String[this.files.length])).toArray(String[][]::new); // unknown columns stay empty
			this.columnClass = new Class<?>[getColumnCount()];
		}

		public int getHeaderColumnCount() {
			return 1;
		}

		@Override
		public int getColumnCount() {
			return keys.length + getHeaderColumnCount();
		}

		@Override
		public String getColumnName(int column) {
			switch (column) {
			case 0:
				return "File";
			default:
				return keys[column - getHeaderColumnCount()];
			}
		}

		private boolean isNumber(String s) {
			try {
				Double.parseDouble(s);
				return true;
			} catch (Exception e) {
				return false;
			}
		}

		@Override
		public Class<?> getColumnClass(int column) {
			int c = column - getHeaderColumnCount();
			if (c < 0) {
				return String.class;
			}

			if (columnClass[c] != null) {
				return columnClass[c];
			}

			List<String> cells = IntStream.range(0, files.length).mapToObj(i -> values[c][i]).filter(Objects::nonNull).collect(toList());
			columnClass[c] = cells.size() > 0 && cells.stream().allMatch(this::isNumber) ? Number.class : String.class;
			return columnClass[c];
		}

		@Override
		public int getRowCount() {
			return files.length;
		}

		@Override
		public String getValueAt(int row, int column) {
			switch (column) {
			case 0:
				return files[row];
			default:
				return values[column - getHeaderColumnCount()][row];
			}
		}

	}

}
