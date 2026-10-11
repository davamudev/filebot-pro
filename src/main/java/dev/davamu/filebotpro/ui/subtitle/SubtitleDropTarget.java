package dev.davamu.filebotpro.ui.subtitle;

import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.MediaTypes.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.UserFiles.*;
import static dev.davamu.filebotpro.ui.ThemeSupport.*;
import static dev.davamu.filebotpro.ui.transfer.FileTransferable.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.util.FileUtilities.ExtensionFileFilter;
import dev.davamu.filebotpro.web.OpenSubtitlesClient;
import dev.davamu.filebotpro.web.SubtitleProvider;
import dev.davamu.filebotpro.web.VideoHashSubtitleService;

abstract class SubtitleDropTarget extends JButton {

	public enum DropAction {
		Accept, Cancel
	}

	public SubtitleDropTarget() {
		setHorizontalAlignment(CENTER);
		setHorizontalTextPosition(CENTER);
		setVerticalAlignment(CENTER);
		setVerticalTextPosition(TOP);
		setIconTextGap(0);

		setHideActionText(true);
		setContentAreaFilled(false);
		setFocusPainted(false);
		setBorderPainted(false);

		// initialize with default mode
		setDropAction(DropAction.Accept);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		// install mouse listener
		addActionListener(clickHandler);

		// install drop target
		new DropTarget(this, dropHandler);
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2d = (Graphics2D) g.create();
		try {
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			boolean hover = getModel().isRollover();
			Shape shape = new Ellipse2D.Float(0.5f, 0.5f, getWidth() - 1.5f, getHeight() - 1.5f);

			g2d.setColor(UIManager.getColor(hover ? "Button.hoverBackground" : "App.surface"));
			g2d.fill(shape);

			g2d.setColor(UIManager.getColor(hover ? "Component.focusedBorderColor" : "Component.borderColor"));
			g2d.draw(shape);
		} finally {
			g2d.dispose();
		}

		super.paintComponent(g);
	}

	protected void setDropAction(DropAction dropAction) {
		setIcon(getIcon(dropAction));
	}

	protected abstract OpenSubtitlesClient getSubtitleService();

	protected abstract boolean handleDrop(List<File> files);

	protected abstract DropAction getDropAction(List<File> files);

	protected abstract Icon getIcon(DropAction dropAction);

	private final DropTargetAdapter dropHandler = new DropTargetAdapter() {

		@Override
		public void dragEnter(DropTargetDragEvent dtde) {
			DropAction dropAction = DropAction.Accept;

			try {
				dropAction = getDropAction(getFilesFromTransferable(dtde.getTransferable()));
			} catch (Exception e) {
				// just accept the drag if we can't access the transferable,
				// because on some implementations we can't access transferable data before we accept the drag,
				// but accepting or rejecting the drag depends on the files dragged
			}

			// update visual representation
			setDropAction(dropAction);

			// accept or reject
			if (dropAction != DropAction.Cancel) {
				dtde.acceptDrag(DnDConstants.ACTION_REFERENCE);
			} else {
				dtde.rejectDrag();
			}
		}

		@Override
		public void dragExit(DropTargetEvent dte) {
			// reset to default state
			setDropAction(DropAction.Accept);
		};

		@Override
		public void drop(DropTargetDropEvent dtde) {
			dtde.acceptDrop(DnDConstants.ACTION_REFERENCE);
			boolean accept = false;

			try {
				List<File> files = getFilesFromTransferable(dtde.getTransferable());
				accept = getDropAction(files) != DropAction.Cancel;

				if (accept) {
					// invoke later so we don't block the DnD operation with the download dialog
					SwingUtilities.invokeLater(() -> handleDrop(files));
				}
			} catch (Exception e) {
				log.log(Level.WARNING, e.getMessage(), e);
			}

			dtde.dropComplete(accept);
			dragExit(dtde);
		}

	};

	private final ActionListener clickHandler = new ActionListener() {

		@Override
		public void actionPerformed(ActionEvent evt) {
			// collect media file extensions (video and subtitle files)
			List<File> files = showLoadDialogSelectFiles(true, true, null, ExtensionFileFilter.union(VIDEO_FILES, SUBTITLE_FILES), "Select Video Folder", evt);

			if (files.size() > 0 && getDropAction(files) != DropAction.Cancel) {
				handleDrop(files);
			}
		}
	};

	public static abstract class Download extends SubtitleDropTarget {

		public abstract VideoHashSubtitleService[] getVideoHashSubtitleServices();

		public abstract SubtitleProvider[] getSubtitleProviders();

		public abstract Locale getQueryLanguage();

		@Override
		protected DropAction getDropAction(List<File> selection) {
			// accept video files and folders
			return filter(selection, VIDEO_FILES, FOLDERS).size() > 0 ? DropAction.Accept : DropAction.Cancel;
		}

		@Override
		protected boolean handleDrop(List<File> selection) {
			if (getQueryLanguage() == null) {
				log.info("Please select your preferred subtitle language.");
				return false;
			}

			List<File> files = listFiles(selection, VIDEO_FILES, HUMAN_NAME_ORDER);

			if (files.size() > 0) {
				handleDownload(files);
				return true;
			}

			return false;
		}

		protected boolean handleDownload(Collection<File> videoFiles) {
			SubtitleAutoMatchDialog dialog = new SubtitleAutoMatchDialog(getWindow(this));

			// initialize download parameters
			dialog.setVideoFiles(videoFiles.toArray(new File[0]));

			for (VideoHashSubtitleService service : getVideoHashSubtitleServices()) {
				dialog.addSubtitleService(service);
			}

			for (SubtitleProvider service : getSubtitleProviders()) {
				dialog.addSubtitleService(service);
			}

			// start looking for subtitles
			dialog.startQuery(getQueryLanguage());

			// initialize window properties
			dialog.setIconImage(getImage(getIcon(DropAction.Accept)));
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			dialog.setSize(1050, 600);

			// show dialog
			dialog.setLocationRelativeTo(dialog.getOwner());
			dialog.setVisible(true);

			return true;
		}

		@Override
		protected Icon getIcon(DropAction dropAction) {
			switch (dropAction) {
			case Accept:
				return ResourceManager.getIcon("subtitle.exact.download");
			default:
				return ResourceManager.getIcon("message.error");
			}
		}

	}

}
