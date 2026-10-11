package dev.davamu.filebotpro.ui.filter;

import static java.util.stream.Collectors.*;
import static javax.swing.BorderFactory.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.MediaTypes.*;
import static dev.davamu.filebotpro.media.XattrMetaInfo.*;
import static dev.davamu.filebotpro.ui.ThemeSupport.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.io.File;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import java.util.stream.IntStream;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import com.sun.jna.Platform;

import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.cli.PostProcess;
import dev.davamu.filebotpro.cli.PostProcess.Feature;
import dev.davamu.filebotpro.util.ui.LoadingOverlayPane;
import dev.davamu.filebotpro.util.ui.ProgressMonitor;
import dev.davamu.filebotpro.web.Episode;
import dev.davamu.filebotpro.web.Movie;
import dev.davamu.filebotpro.web.SeriesInfo;
import net.miginfocom.swing.MigLayout;

class AttributeTool extends Tool<TableModel> {

	private JTable table = new JTable(new FileAttributesTableModel());

	public AttributeTool() {
		super("Attributes");

		table.setAutoCreateRowSorter(true);
		table.setAutoCreateColumnsFromModel(true);
		table.setFillsViewportHeight(true);

		table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		table.setBackground(getPanelBackground());
		table.setGridColor(getColor(0xEEEEEE));
		table.setRowHeight(25);

		JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.setBorder(createEmptyBorder());

		// post-processing features for the selected files (or all files)
		JPanel toolbar = new JPanel(new MigLayout("insets 12px 8px 12px 8px, nogrid, aligny center"));
		toolbar.setOpaque(false);
		toolbar.add(new JLabel("Apply:"));
		for (Feature feature : Feature.values()) {
			// extras and prune only make sense right after moving files
			if (feature == Feature.extras || feature == Feature.prune) {
				continue;
			}
			// Finder tags only exist on macOS
			if (feature == Feature.finder && !Platform.isMac()) {
				continue;
			}
			// POSIX permissions do not exist on Windows
			if (feature == Feature.chmod && !FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
				continue;
			}
			JCheckBox checkbox = new JCheckBox(getFeatureLabel(feature), feature == Feature.artwork || feature == Feature.nfo);
			checkbox.setToolTipText(getFeatureDescription(feature));
			features.put(feature, checkbox);
			toolbar.add(checkbox, "gap 12px");
		}
		toolbar.add(applyButton, "gap 16px");

		setLayout(new MigLayout("insets 0, gap 0, fill, flowy", "[grow, fill]", "[pref!][grow, fill]"));
		add(toolbar);
		add(new LoadingOverlayPane(scrollPane, this), "grow");
	}

	private final Map<Feature, JCheckBox> features = new EnumMap<Feature, JCheckBox>(Feature.class);

	private final JButton applyButton = newButton("Apply", ResourceManager.getIcon("action.attributes"), evt -> apply());

	private void apply() {
		Set<Feature> selection = features.entrySet().stream().filter(it -> it.getValue().isSelected()).map(Map.Entry::getKey).collect(toCollection(() -> EnumSet.noneOf(Feature.class)));
		List<File> files = getSelectedFiles();

		if (selection.isEmpty() || files.isEmpty()) {
			log.info("Select the features to apply and load files that have been renamed before");
			return;
		}

		// modal progress dialog with cancel (artwork downloads and tagging may take a while)
		try {
			ProgressMonitor.runTask("Apply", String.format("Apply %s to %d files", selection, files.size()), (message, progress, cancelled) -> {
				PostProcess.apply(files, selection, message, progress, cancelled);
				return null;
			}).get();
			log.info(String.format("Applied %s to %d files", selection, files.size()));
		} catch (CancellationException e) {
			log.info("Cancelled");
		} catch (Exception e) {
			log.log(Level.WARNING, e, e::toString);
		}
	}

	private List<File> getSelectedFiles() {
		TableModel model = table.getModel();
		int[] rows = table.getSelectedRows().length > 0 ? table.getSelectedRows() : IntStream.range(0, table.getRowCount()).toArray();
		return IntStream.of(rows).map(table::convertRowIndexToModel).mapToObj(i -> (File) model.getValueAt(i, 3)).collect(toList());
	}

	private static String getFeatureLabel(Feature feature) {
		switch (feature) {
		case artwork:
			return "Artwork";
		case cover:
			return "Cover";
		case nfo:
			return "NFO";
		case url:
			return "URL";
		case metadata:
			return "Tags";
		case srt:
			return "SRT";
		case finder:
			return "Finder Tags";
		case touch:
			return "Touch";
		case chmod:
			return "Chmod";
		default:
			return "Date";
		}
	}

	private static String getFeatureDescription(Feature feature) {
		switch (feature) {
		case artwork:
			return "Download posters, backdrops and episode thumbnails";
		case cover:
			return "Write cover.jpg for each folder, reusing local posters before downloading one";
		case nfo:
			return "Write .nfo files for Kodi, Plex, Emby and Jellyfin";
		case url:
			return "Write a .url shortcut to the movie or series page";
		case metadata:
			return "Write title, series, season, episode and date into mkv (mkvpropedit) and mp4 (AtomicParsley) files, and attach the cover";
		case srt:
			return "Convert subtitle files to SubRip (.srt) with UTF-8 encoding";
		case finder:
			return "Add Finder tags for the media type, video format and HDR";
		case touch:
			return "Set the last modified date to now";
		case chmod:
			return "Set file permissions to " + PostProcess.Options.DEFAULT_PERMISSIONS;
		default:
			return "Set the last modified date to the release or air date";
		}
	}

	@Override
	protected TableModel createModelInBackground(List<File> root) {
		FileAttributesTableModel model = new FileAttributesTableModel();

		if (root.isEmpty()) {
			return model;
		}

		List<File> files = listFiles(root, filter(VIDEO_FILES, SUBTITLE_FILES), HUMAN_NAME_ORDER);

		for (File file : files) {
			Object metaObject = xattr.getMetaInfo(file);
			String originalName = xattr.getOriginalName(file);

			if (metaObject instanceof Episode) {
				SeriesInfo seriesInfo = ((Episode) metaObject).getSeriesInfo();
				if (seriesInfo != null) {
					model.addRow(String.format("%s::%d", seriesInfo.getDatabase(), seriesInfo.getId()), metaObject, originalName, file);
				}
			} else if (metaObject instanceof Movie) {
				Movie movie = (Movie) metaObject;
				if (movie.getTmdbId() > 0) {
					model.addRow(String.format("%s::%d", "TheMovieDB", movie.getTmdbId()), metaObject, originalName, file);
				} else if (movie.getImdbId() > 0) {
					model.addRow(String.format("%s::%d", "OMDb", movie.getImdbId()), metaObject, originalName, file);
				}
			}

			if (Thread.interrupted()) {
				throw new CancellationException();
			}
		}

		return model;
	}

	@Override
	protected void setModel(TableModel model) {
		table.setModel(model);
	}

	private static class FileAttributesTableModel extends AbstractTableModel {

		private final List<Object[]> rows = new ArrayList<Object[]>();

		public boolean addRow(Object... row) {
			if (row.length != getColumnCount())
				return false;

			return rows.add(row);
		}

		@Override
		public int getColumnCount() {
			return 4;
		}

		@Override
		public String getColumnName(int column) {
			switch (column) {
			case 0:
				return "Meta ID";
			case 1:
				return "Meta Attributes";
			case 2:
				return "Original Name";
			case 3:
				return "File Path";
			}
			return null;
		}

		@Override
		public int getRowCount() {
			return rows.size();
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			return rows.get(rowIndex)[columnIndex];
		}

	}

}
