package dev.davamu.filebotpro.ui.rename;

import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.MediaTypes.*;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import dev.davamu.filebotpro.format.MediaBindingBean;
import dev.davamu.filebotpro.mediainfo.MediaInfo;
import dev.davamu.filebotpro.mediainfo.MediaInfo.StreamKind;
import dev.davamu.filebotpro.ui.rename.BindingDialog.BindingTableModel;
import net.miginfocom.swing.MigLayout;

/**
 * Bindings and MediaInfo of the selected match, below the file lists (toggle with F6)
 */
class MatchDetailsPanel extends JPanel {

	private final BindingTableModel bindingModel = new BindingTableModel();
	private final JPanel mediaPanel = new JPanel(new MigLayout("fill, insets 0"));
	private final JTabbedPane tabs = new JTabbedPane();

	// wait for the selection to settle (e.g. when scrolling through the list with the arrow keys)
	private final Timer updateTimer = new Timer(200, evt -> update());

	private MediaBindingBean sample;
	private SwingWorker<Map<StreamKind, List<Map<String, String>>>, Void> mediaWorker;

	public MatchDetailsPanel() {
		super(new MigLayout("fill, insets 0"));

		mediaPanel.setOpaque(false);

		tabs.addTab("Bindings", new JScrollPane(BindingDialog.createBindingTable(bindingModel)));
		tabs.addTab("Media", mediaPanel);
		tabs.addChangeListener(evt -> update());
		add(tabs, "grow");

		updateTimer.setRepeats(false);
		setSample(null);
	}

	public void setSample(MediaBindingBean sample) {
		this.sample = sample;
		updateTimer.restart();
	}

	private void update() {
		if (!isShowing()) {
			return;
		}

		// bindings of the selected match
		if (sample == null) {
			bindingModel.clear();
		} else {
			bindingModel.setModel(BindingDialog.getExpressions("details"), sample);
		}

		// media properties are only loaded when the Media tab is visible
		if (tabs.getSelectedIndex() == 1) {
			updateMediaInfo(sample == null ? null : sample.getFileObject());
		}
	}

	private void updateMediaInfo(File file) {
		if (mediaWorker != null) {
			mediaWorker.cancel(true);
		}

		if (file == null || !file.isFile() || !(VIDEO_FILES.accept(file) || AUDIO_FILES.accept(file) || IMAGE_FILES.accept(file))) {
			setMediaComponent(createMessage("No media file selected"));
			return;
		}

		setMediaComponent(createMessage("Loading …"));
		mediaWorker = new SwingWorker<Map<StreamKind, List<Map<String, String>>>, Void>() {

			@Override
			protected Map<StreamKind, List<Map<String, String>>> doInBackground() throws Exception {
				return MediaInfo.snapshot(file);
			}

			@Override
			protected void done() {
				if (isCancelled()) {
					return;
				}
				try {
					setMediaComponent(BindingDialog.createMediaInfoTabs(get()));
				} catch (Exception e) {
					debug.log(Level.WARNING, e, e::toString);
					JComponent message = createMessage("MediaInfo not available");
					message.setToolTipText(String.valueOf(e.getCause() == null ? e : e.getCause().getMessage()));
					setMediaComponent(message);
				}
			}
		};
		mediaWorker.execute();
	}

	private void setMediaComponent(JComponent component) {
		mediaPanel.removeAll();
		mediaPanel.add(component, "grow");
		mediaPanel.revalidate();
		mediaPanel.repaint();
	}

	private static JComponent createMessage(String text) {
		JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setEnabled(false);
		return label;
	}

	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);

		if (visible) {
			updateTimer.restart();
		} else {
			// stop background work while hidden
			bindingModel.clear();
			if (mediaWorker != null) {
				mediaWorker.cancel(true);
			}
		}
	}

}
