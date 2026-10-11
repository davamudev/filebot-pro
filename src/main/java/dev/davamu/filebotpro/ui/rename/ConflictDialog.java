package dev.davamu.filebotpro.ui.rename;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.ui.ThemeSupport.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.swing.DefaultEventListModel;
import com.formdev.flatlaf.util.UIScale;
import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.UserFiles;
import dev.davamu.filebotpro.media.VideoQuality;
import dev.davamu.filebotpro.util.ui.DashedSeparator;
import dev.davamu.filebotpro.util.ui.DefaultFancyListCellRenderer;
import net.miginfocom.swing.MigLayout;

class ConflictDialog extends JDialog {

	private EventList<Conflict> model = new BasicEventList<Conflict>();
	private boolean cancel = true;

	private final int fileCount;
	private final JLabel summaryLabel = new JLabel();
	private final JButton continueButton;
	private final JButton overrideButton;

	public ConflictDialog(Window owner, List<Conflict> conflicts, int fileCount) {
		super(owner, "Conflicts", ModalityType.DOCUMENT_MODAL);
		this.fileCount = fileCount;
		model.addAll(conflicts);

		JList<Conflict> list = new JList<Conflict>(new DefaultEventListModel<Conflict>(model)) {

			@Override
			public boolean getScrollableTracksViewportWidth() {
				// wrap the conflict details to the window width instead of scrolling horizontally
				return true;
			}
		};
		list.setCellRenderer(new ConflictCellRenderer());
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setVisibleRowCount(Math.min(conflicts.size(), 4));

		// cell heights depend on the list width, so recompute them whenever the width changes
		list.addComponentListener(new ComponentAdapter() {

			private int width = -1;

			@Override
			public void componentResized(ComponentEvent evt) {
				if (list.getWidth() != width) {
					width = list.getWidth();
					list.setFixedCellHeight(0);
					list.setFixedCellHeight(-1);
				}
			}
		});

		// force white background (e.g. GTK LaF default table background is gray)
		setBackground(getPanelBackground());

		JComponent c = (JComponent) getContentPane();
		c.setLayout(new MigLayout("insets dialog, nogrid, fill", "", "[fill][pref!][pref!]"));

		c.add(new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), "grow, wrap");
		summaryLabel.setForeground(getSecondaryForeground());
		c.add(summaryLabel, "growx, wrap");
		c.add(newButton("Cancel", ResourceManager.getIcon("dialog.cancel"), this::cancel), "tag left");
		continueButton = accent(newButton("Continue", ResourceManager.getIcon("dialog.continue"), this::ok));
		continueButton.setToolTipText("Keep the current names of the conflicting files and rename all other files");
		c.add(continueButton, "tag ok");

		overrideButton = newButton("Override", ResourceManager.getIcon("dialog.continue.invalid"), this::override);
		overrideButton.setToolTipText("Move the existing destination files to the trash and rename. If there is no trash, they are deleted permanently");
		overrideButton.setEnabled(conflicts.stream().anyMatch(it -> it.override));
		overrideButton.addActionListener(evt -> overrideButton.setEnabled(false));
		c.add(overrideButton, "tag next");
		updateSummary();

		installAction(c, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), newAction("Cancel", this::cancel));

		list.addMouseListener(mouseClicked(evt -> {
			if (evt.getClickCount() == 2) {
				Conflict selection = list.getSelectedValue();
				if (selection != null) {
					List<File> files = Stream.of(selection.source, selection.destination).filter(File::exists).distinct().collect(toList());
					UserFiles.revealFiles(files);
				}
			}
		}));

		// give default focus to "Continue" button
		SwingUtilities.invokeLater(continueButton::requestFocusInWindow);

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setMinimumSize(new Dimension(340, 280));
		pack();
	}

	public boolean cancel() {
		return cancel;
	}

	public List<Conflict> getConflicts() {
		return model;
	}

	private void override(ActionEvent evt) {
		// delete existing destination files and create new data model
		List<Conflict> data = model.stream().map(c -> {
			// safety check
			if (!c.override) {
				return c;
			}

			try {
				UserFiles.trash(c.destination);
			} catch (Exception e) {
				Issue issue = new Issue("The existing file could not be moved to the trash: " + (e.getMessage() != null ? e.getMessage() : e), new Detail("Source", nameAndSize(c.source)), new Detail("Destination", c.destination.getPath()));
				return new Conflict(c.source, c.destination, singletonMap(Conflict.Kind.OVERRIDE_FAILED, issue), false);
			}

			// resolved => remove conflict
			return null;
		}).filter(Objects::nonNull).collect(toList());

		// insert new conflict data
		model.clear();
		model.addAll(data);
		updateSummary();

		// continue if there are no more conflicts
		if (data.isEmpty()) {
			ok(evt);
		}
	}

	private void updateSummary() {
		// Continue skips every file that is still listed as a conflict, all other files are renamed
		int skipped = model.size();
		int renamed = Math.max(0, fileCount - skipped);
		summaryLabel.setText(String.format("%s will be skipped, %s will be renamed", pluralize(skipped), pluralize(renamed)));
		continueButton.setText(skipped > 0 ? "Skip " + pluralize(skipped) : "Continue");
	}

	private static String pluralize(int count) {
		return count == 1 ? "1 file" : count + " files";
	}

	public void ok(ActionEvent evt) {
		cancel = false;
		setVisible(false);
	}

	public void cancel(ActionEvent evt) {
		cancel = true;
		setVisible(false);
	}

	private static class ConflictCellRenderer extends DefaultFancyListCellRenderer {

		private static final int DEFAULT_WIDTH = 640;

		public ConflictCellRenderer() {
			setHighlightingEnabled(false);
			setBorder(new CompoundBorder(new DashedSeparator(2, 4, getColor(0xEEEEEE), getPanelBackground()), getBorder()));
		}

		@Override
		protected void configureListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
			Conflict conflict = (Conflict) value;
			Icon icon = ResourceManager.getIcon("status.warning");
			Color labelColor = isSelected ? list.getSelectionForeground() : getSecondaryForeground();

			super.configureListCellRendererComponent(list, formatDetails(conflict, getTextWidth(list, icon), labelColor), index, isSelected, cellHasFocus);
			setIcon(icon);
			setVerticalTextPosition(SwingConstants.TOP);
			setToolTipText(formatToolTip(conflict));

			// don't paint border on last element
			setBorderPainted(index < list.getModel().getSize() - 1);

			// the label never invalidates its parent, but the cell height depends on the text
			invalidate();
		}

		private int getTextWidth(JList list, Icon icon) {
			// initial layout (the list has no size yet)
			if (list.getWidth() <= 0) {
				return UIScale.scale(DEFAULT_WIDTH);
			}

			JLabel label = (JLabel) getComponent(0);
			Insets insets = getInsets();
			Insets labelInsets = label.getInsets();
			int overhead = insets.left + insets.right + labelInsets.left + labelInsets.right + icon.getIconWidth() + label.getIconTextGap();
			return Math.max(UIScale.scale(200), list.getWidth() - overhead);
		}

		private String formatDetails(Conflict conflict, int width, Color labelColor) {
			// Swing HTML maps CSS px to 1.3 screen pixels and pt to 1 screen pixel
			StringBuilder html = new StringBuilder(512).append("<html><body style='width:").append(width).append("pt'>");
			conflict.issues.forEach((kind, issue) -> {
				html.append("<p style='margin-bottom:4px'><b>").append(escapeHTML(kind.toString())).append("</b><br>").append(escapeHTML(issue.message())).append("</p>");
				html.append("<table width='100%' cellpadding='0' cellspacing='0' style='margin-bottom:4px'>");
				for (Detail detail : issue.details()) {
					html.append("<tr><td valign='top' style='padding:1px 12px 1px 0; color:").append(toHex(labelColor)).append("'><nobr>").append(escapeHTML(detail.label())).append("</nobr></td>");
					html.append("<td valign='top' width='100%' style='padding:1px 0'>").append(escapeHTML(detail.value())).append("</td></tr>");
				}
				html.append("</table>");
			});
			return html.append("</body></html>").toString();
		}

		private String formatToolTip(Conflict conflict) {
			StringBuilder html = new StringBuilder(64).append("<html>");
			appendTooltipParagraph(html, "Conflicts", conflict.issues.keySet().stream().map(Objects::toString).collect(joining(" | ")));
			appendTooltipParagraph(html, "Source", conflict.source.getPath());
			appendTooltipParagraph(html, "Destination", conflict.destination.getPath());
			return html.append("</html>").toString();
		}

		private StringBuilder appendTooltipParagraph(StringBuilder html, String label, Object value) {
			return html.append("<p style='width:350px; margin:3px'><b>").append(label).append(":</b><br>").append(escapeHTML(value.toString())).append("</p>");
		}
	}

	record Detail(String label, String value) {
	}

	record Issue(String message, List<Detail> details) {

		Issue(String message, Detail... details) {
			this(message, Stream.of(details).filter(Objects::nonNull).collect(toList()));
		}
	}

	public static class Conflict {

		public static final Comparator<Conflict> SEVERITY_ORDER = Comparator.comparingInt(Conflict::getSeverity).thenComparing(c -> c.source.getPath());

		public final File source;
		public final File destination;

		public final Map<Kind, Issue> issues;
		public final boolean override;

		public Conflict(File source, File destination, Map<Kind, Issue> issues, boolean override) {
			this.source = source;
			this.destination = destination;
			this.issues = issues;
			this.override = override;
		}

		public int getSeverity() {
			return -issues.keySet().iterator().next().ordinal();
		}

		@Override
		public String toString() {
			return issues.toString();
		}

		public enum Kind {

			MISSING_EXTENSION, OVERLAP, DUPLICATE, FILE_EXISTS, OVERRIDE_FAILED;

			@Override
			public String toString() {
				switch (this) {
				case OVERLAP:
					return "New name is used by another file";
				case DUPLICATE:
					return "Duplicate destination path";
				case FILE_EXISTS:
					return "Destination file already exists";
				case MISSING_EXTENSION:
					return "Missing file extension";
				case OVERRIDE_FAILED:
					return "Failed to override destination file";
				}
				return null;
			}
		}
	}

	private static Map<File, List<File>> getSourceFilesForDestination(Map<File, File> renameMap, Comparator<File> order) {
		Map<File, List<File>> duplicates = renameMap.entrySet().stream().collect(groupingBy(e -> {
			return resolve(e.getKey(), e.getValue());
		}, mapping(e -> e.getKey(), toCollection(ArrayList::new))));

		// sort duplicates by video quality
		duplicates.forEach((k, v) -> v.sort(order));

		return duplicates;
	}

	private static String nameAndSize(File file) {
		return file.isFile() ? file.getName() + "  (" + formatSize(file.length()) + ")" : file.getName();
	}

	private static Detail folder(File file) {
		File parent = file.getAbsoluteFile().getParentFile();
		return parent == null ? null : new Detail("Folder", parent.getPath());
	}

	public static boolean check(Component parent, Map<File, File> renameMap) {
		List<Conflict> conflicts = new ArrayList<Conflict>();

		Map<File, List<File>> sourceFilesForDestination = getSourceFilesForDestination(renameMap, VideoQuality.DESCENDING_ORDER);

		// sanity checks
		renameMap.forEach((from, v) -> {
			// resolve relative paths
			File to = resolve(from, v);

			Map<Conflict.Kind, Issue> issues = new EnumMap<Conflict.Kind, Issue>(Conflict.Kind.class);

			// output files must have a valid file extension
			if (getExtension(to) == null && from.isFile()) {
				Issue issue = new Issue("The new file name has no file extension. Fix it via Edit Format.", new Detail("Source", from.getName()), new Detail("New name", to.getName()), folder(to));
				issues.put(Conflict.Kind.MISSING_EXTENSION, issue);
			}

			// one file per unique output path
			File[] duplicates = sourceFilesForDestination.get(to).toArray(new File[0]);
			File chosen = duplicates[0];
			if (duplicates.length > 1 && !from.equals(chosen)) {
				String message = String.format("%d files would get the same new name. Only the highest-quality file is renamed, this one is skipped.", duplicates.length);
				Issue issue = new Issue(message, new Detail("New name", to.getName()), folder(to), new Detail("Renamed", nameAndSize(chosen)), new Detail("Skipped", nameAndSize(from)));
				issues.put(Conflict.Kind.DUPLICATE, issue);
			}

			// check if input and output overlap
			if (renameMap.containsKey(to) && !to.equals(from)) {
				String message = "Another file in this batch currently has this name and is renamed too, so the order matters. Continue skips this file and keeps its current name. To rename it, change the format via Edit Format.";
				Issue issue = new Issue(message, new Detail("Source", from.getName()), new Detail("New name", to.getName()), new Detail("Other rename", to.getName() + " ➔ " + resolve(to, renameMap.get(to)).getName()), folder(to));
				issues.put(Conflict.Kind.OVERLAP, issue);
			}

			// check if destination path already exists
			if (to.exists() && !to.equals(from)) {
				String message = "A file with the new name already exists. Override moves the existing file to the trash (or deletes it if there is no trash) and replaces it, Continue skips this file.";
				Issue issue = new Issue(message, new Detail("Source", nameAndSize(from)), new Detail("Overwrites", nameAndSize(to)), folder(to));
				issues.put(Conflict.Kind.FILE_EXISTS, issue);
			}

			if (issues.size() > 0) {
				// allow override if this is the only issue
				boolean override = issues.containsKey(Conflict.Kind.FILE_EXISTS) && issues.size() == 1;
				conflicts.add(new Conflict(from, to, issues, override));
			}
		});

		if (conflicts.isEmpty()) {
			return true;
		}

		conflicts.sort(Conflict.SEVERITY_ORDER);

		ConflictDialog dialog = new ConflictDialog(getWindow(parent), conflicts, renameMap.size());
		setOffsetLocation(dialog);
		dialog.setVisible(true);

		if (dialog.cancel()) {
			return false;
		}

		// exclude conflicts from rename map
		for (Conflict it : dialog.getConflicts()) {
			renameMap.remove(it.source);
		}
		return true;
	}

}
