package dev.davamu.filebotpro.ui;

import static java.awt.event.InputEvent.*;
import static java.awt.event.KeyEvent.*;
import static javax.swing.KeyStroke.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Dialog.ModalExclusionType;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.logging.Level;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;

import com.google.common.eventbus.Subscribe;

import dev.davamu.filebotpro.CacheManager;
import dev.davamu.filebotpro.Settings;
import dev.davamu.filebotpro.cli.GroovyPad;
import dev.davamu.filebotpro.util.PreferencesMap.PreferencesEntry;
import dev.davamu.filebotpro.util.ui.SwingEventBus;
import com.formdev.flatlaf.util.Animator;
import com.formdev.flatlaf.util.CubicBezierEasing;

import net.miginfocom.swing.MigLayout;

public class MainFrame extends JFrame {

	private static final PreferencesEntry<String> persistentSelectedPanel = Settings.forPackage(MainFrame.class).entry("panel.selected").defaultValue("0");

	private final NavigationRail navigation;
	private final HeaderPanel headerPanel = new HeaderPanel();
	private final ContentPanel contentPanel = new ContentPanel();

	public MainFrame(List<PanelBuilder> panels) {
		super(getWindowTitle());

		navigation = new NavigationRail(panels, AppMenus.createAppearanceButton(), AppMenus.createHelpButton());

		JPanel page = new JPanel(new BorderLayout());
		page.add(headerPanel, BorderLayout.NORTH);
		page.add(contentPanel, BorderLayout.CENTER);

		JComponent c = (JComponent) getContentPane();
		c.setLayout(new BorderLayout());
		c.add(navigation, BorderLayout.WEST);
		c.add(page, BorderLayout.CENTER);

		navigation.addSelectionListener(builder -> {
			showPanel(builder);
			persistentSelectedPanel.setValue(Integer.toString(navigation.getSelectedIndex()));
		});

		// restore selected panel
		try {
			navigation.setSelectedIndex(Integer.parseInt(persistentSelectedPanel.getValue()));
		} catch (Exception e) {
			debug.log(Level.WARNING, e, e::getMessage);
		}

		if (navigation.getSelectedIndex() < 0) {
			navigation.setSelectedIndex(0);
		}

		setSize(1100, 680);
		setMinimumSize(new Dimension(900, 420));

		// KEYBOARD SHORTCUTS
		installAction(getRootPane(), getKeyStroke(VK_DELETE, CTRL_DOWN_MASK | SHIFT_DOWN_MASK), newAction("Clear Cache", evt -> {
			withWaitCursor(getRootPane(), () -> {
				CacheManager.getInstance().clearAll();
				log.info("Cache has been cleared");
			});
		}));

		installAction(getRootPane(), getKeyStroke(VK_F5, 0), newAction("Run", evt -> {
			withWaitCursor(getRootPane(), () -> {
				GroovyPad pad = new GroovyPad();

				pad.addWindowListener(new WindowAdapter() {

					@Override
					public void windowOpened(WindowEvent e) {
						setVisible(false);
					};

					@Override
					public void windowClosing(WindowEvent e) {
						setVisible(true);
					};
				});

				pad.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
				pad.setModalExclusionType(ModalExclusionType.TOOLKIT_EXCLUDE);
				pad.setLocationByPlatform(true);
				pad.setVisible(true);
			});
		}));

		installAction(this.getRootPane(), getKeyStroke(VK_F1, 0), newAction("Help", evt -> openURI(getEmbeddedHelpURL())));

		// Ctrl+1 ... Ctrl+9 (Cmd on macOS) switch between panels
		int menuShortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		for (int i = 0; i < Math.min(panels.size(), 9); i++) {
			int index = i;
			installAction(getRootPane(), JComponent.WHEN_IN_FOCUSED_WINDOW, getKeyStroke(VK_1 + i, menuShortcut), newAction("Show Panel " + (i + 1), evt -> navigation.setSelectedIndex(index)));
		}

		SwingEventBus.getInstance().register(this);
	}

	@Subscribe
	public void selectPanel(TargetTransferable transfer) {
		navigation.setSelectedItem(transfer.getTarget());
		showPanel(transfer.getTarget());

		SwingEventBus.getInstance().post(transfer.getTransferable());
	}

	private void showPanel(PanelBuilder selectedBuilder) {
		if (selectedBuilder == null)
			return;

		JComponent selectedPanel = null;
		JComponent previousPanel = null;

		for (int i = 0; i < contentPanel.getComponentCount(); i++) {
			JComponent panel = (JComponent) contentPanel.getComponent(i);
			PanelBuilder builder = (PanelBuilder) panel.getClientProperty(PanelBuilder.class.getName());
			if (builder != null) {
				if (builder.equals(selectedBuilder)) {
					selectedPanel = panel;
				} else if (panel.isVisible()) {
					previousPanel = panel;
				}
			}
		}

		// make visible, ignore action is visible already
		if (selectedPanel != null && selectedPanel.isVisible()) {
			return;
		}

		contentPanel.prepareTransition();

		if (previousPanel != null) {
			previousPanel.setVisible(false);
			SwingEventBus.getInstance().unregister(previousPanel);
		}

		if (selectedPanel == null) {
			selectedPanel = selectedBuilder.create();
			selectedPanel.setVisible(false); // invisible by default
			selectedPanel.putClientProperty(PanelBuilder.class.getName(), selectedBuilder);
			contentPanel.add(selectedPanel, "grow");
		}

		headerPanel.setTitle(selectedBuilder.getName());
		headerPanel.setSubtitle(selectedBuilder.getDescription());
		selectedPanel.setVisible(true);
		SwingEventBus.getInstance().register(selectedPanel);

		contentPanel.revalidate();
		contentPanel.startTransition();
	}

	/**
	 * Holds all panels (only one visible at a time) and cross-fades between them.
	 */
	private static class ContentPanel extends JPanel {

		private BufferedImage snapshot;
		private float snapshotAlpha;
		private Animator animator;

		public ContentPanel() {
			super(new MigLayout("insets 0, fill, hidemode 3", "[fill]", "[fill]"));
		}

		void prepareTransition() {
			snapshot = null;
			if (!Animator.useAnimation() || !isShowing() || getWidth() <= 0 || getHeight() <= 0) {
				return;
			}

			try {
				GraphicsConfiguration gc = getGraphicsConfiguration();
				AffineTransform tx = gc != null ? gc.getDefaultTransform() : new AffineTransform();
				double sx = tx.getScaleX();
				double sy = tx.getScaleY();

				BufferedImage image = new BufferedImage((int) Math.ceil(getWidth() * sx), (int) Math.ceil(getHeight() * sy), BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = image.createGraphics();
				g.scale(sx, sy);
				paint(g);
				g.dispose();
				snapshot = image;
			} catch (Exception e) {
				debug.log(Level.FINEST, e, e::toString);
			}
		}

		void startTransition() {
			if (snapshot == null) {
				return;
			}

			if (animator == null) {
				animator = new Animator(200, fraction -> {
					snapshotAlpha = 1 - fraction;
					repaint();
				}, () -> {
					snapshot = null;
					repaint();
				});
				animator.setInterpolator(CubicBezierEasing.EASE_IN_OUT);
				animator.setResolution(10);
			}

			snapshotAlpha = 1;
			animator.restart();
		}

		@Override
		public void paint(Graphics g) {
			super.paint(g);

			if (snapshot != null && snapshotAlpha > 0) {
				Graphics2D g2 = (Graphics2D) g.create();
				try {
					g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, snapshotAlpha));
					g2.drawImage(snapshot, 0, 0, getWidth(), getHeight(), null);
				} finally {
					g2.dispose();
				}
			}
		}
	}

}
