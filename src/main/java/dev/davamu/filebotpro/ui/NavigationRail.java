package dev.davamu.filebotpro.ui;

import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import dev.davamu.filebotpro.ResourceManager;
import com.sun.jna.Platform;
import com.formdev.flatlaf.util.Animator;
import com.formdev.flatlaf.util.CubicBezierEasing;
import com.formdev.flatlaf.util.UIScale;

import net.miginfocom.swing.MigLayout;

/**
 * Vertical navigation with an animated selection pill. Hovering with a drag-and-drop operation selects the item under the cursor.
 */
public class NavigationRail extends JPanel {

	private static final int ITEM_HEIGHT = 62;
	private static final int PILL_ARC = 14;
	private static final int DRAG_OVER_SELECT_DELAY = 300;

	private final List<PanelBuilder> items;
	private final List<Item> components = new ArrayList<Item>();
	private final List<Consumer<PanelBuilder>> listeners = new ArrayList<Consumer<PanelBuilder>>();

	private final JPanel itemPanel = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 4", "[grow, fill]"));

	private int selectedIndex = -1;

	// selection pill animation
	private float pillY = -1;
	private float pillFromY;
	private float pillToY;
	private Animator pillAnimator;

	public NavigationRail(List<PanelBuilder> items, JComponent... footer) {
		super(new MigLayout("insets 10 8 12 8, wrap 1, fill", "[grow, fill]", "[]push[]"));
		this.items = items;
		setName("navigation");
		setOpaque(true);

		itemPanel.setOpaque(false);
		for (int i = 0; i < items.size(); i++) {
			Item item = new Item(i, items.get(i));
			components.add(item);
			itemPanel.add(item, "h " + ITEM_HEIGHT + "!");
		}
		add(itemPanel);

		JPanel footerPanel = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 6", "[center, grow]"));
		footerPanel.setOpaque(false);
		for (JComponent c : footer) {
			footerPanel.add(c);
		}
		add(footerPanel);

		// drag over a navigation item to select it (e.g. drag files from the Rename panel to the SFV panel)
		new DropTarget(this, new DragOverSelection());
	}

	@Override
	public void updateUI() {
		super.updateUI();
		setBackground(UIManager.getColor("App.sidebar.background"));
		setBorder(new dev.davamu.filebotpro.util.ui.MatteColorBorder(0, 0, 0, 1, "Separator.foreground"));
	}

	@Override
	public Dimension getPreferredSize() {
		Dimension d = super.getPreferredSize();
		return new Dimension(Math.max(d.width, UIScale.scale(88)), d.height);
	}

	public void addSelectionListener(Consumer<PanelBuilder> listener) {
		listeners.add(listener);
	}

	public int getSelectedIndex() {
		return selectedIndex;
	}

	public PanelBuilder getSelectedItem() {
		return selectedIndex < 0 ? null : items.get(selectedIndex);
	}

	public void setSelectedItem(PanelBuilder builder) {
		setSelectedIndex(items.indexOf(builder));
	}

	public void setSelectedIndex(int index) {
		if (index < 0 || index >= items.size() || index == selectedIndex) {
			return;
		}

		int previous = selectedIndex;
		selectedIndex = index;
		components.forEach(Item::updateState);
		animatePill(previous < 0);

		PanelBuilder selected = items.get(index);
		listeners.forEach(l -> l.accept(selected));
	}

	private Rectangle getPillBounds(int index) {
		Item item = components.get(index);
		Point p = SwingUtilities.convertPoint(itemPanel, item.getLocation(), this);
		return new Rectangle(p.x, p.y, item.getWidth(), item.getHeight());
	}

	private void animatePill(boolean immediately) {
		if (immediately || pillY < 0 || !Animator.useAnimation() || !isShowing()) {
			pillY = -1; // snap to target on next paint
			repaint();
			return;
		}

		pillFromY = pillY;
		pillToY = getPillBounds(selectedIndex).y;

		if (pillAnimator == null) {
			pillAnimator = new Animator(240, fraction -> {
				pillY = pillFromY + (pillToY - pillFromY) * fraction;
				repaint();
			});
			pillAnimator.setInterpolator(CubicBezierEasing.STANDARD_EASING);
			pillAnimator.setResolution(10);
		}
		pillAnimator.restart();
	}

	@Override
	protected void paintChildren(Graphics g) {
		// paint selection pill below the item labels
		if (selectedIndex >= 0 && components.get(selectedIndex).getHeight() > 0) {
			Rectangle target = getPillBounds(selectedIndex);
			if (pillY < 0 || (pillAnimator == null || !pillAnimator.isRunning()) && pillY != target.y) {
				pillY = target.y;
			}

			Graphics2D g2 = (Graphics2D) g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				float arc = UIScale.scale((float) PILL_ARC);
				g2.setColor(UIManager.getColor("App.sidebar.selectionBackground"));
				g2.fill(new RoundRectangle2D.Float(target.x, pillY, target.width, target.height, arc, arc));

				// accent indicator on the left edge
				float indicatorHeight = target.height * 0.42f;
				float indicatorWidth = UIScale.scale(3f);
				g2.setColor(UIManager.getColor("App.sidebar.indicator"));
				g2.fill(new RoundRectangle2D.Float(target.x + UIScale.scale(4f), pillY + (target.height - indicatorHeight) / 2, indicatorWidth, indicatorHeight, indicatorWidth, indicatorWidth));
			} finally {
				g2.dispose();
			}
		}

		super.paintChildren(g);
	}

	/**
	 * Navigation entry with icon on top of the label and an animated hover background.
	 */
	private class Item extends JPanel {

		private final int index;
		private final PanelBuilder builder;

		private final JLabel label = new JLabel();

		private float hover = 0;
		private float hoverFrom;
		private float hoverTo;
		private Animator hoverAnimator;

		public Item(int index, PanelBuilder builder) {
			this.index = index;
			this.builder = builder;

			setLayout(new java.awt.BorderLayout());
			setOpaque(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText(String.format("<html><b>%s</b> &nbsp;<span style='color:gray'>%s+%d</span><br>%s</html>", builder.getName(), Platform.isMac() ? "⌘" : "Ctrl", index + 1, builder.getDescription()));
			getAccessibleContext().setAccessibleName(builder.getName());

			Icon icon = ResourceManager.getSvgIcon(builder.getSymbol(), 22, this::getItemForeground);
			label.setIcon(icon);
			label.setText(builder.getName());
			label.setHorizontalAlignment(SwingConstants.CENTER);
			label.setHorizontalTextPosition(SwingConstants.CENTER);
			label.setVerticalTextPosition(SwingConstants.BOTTOM);
			label.setIconTextGap(UIScale.scale(5));
			add(label);

			addMouseListener(new MouseAdapter() {

				@Override
				public void mousePressed(MouseEvent e) {
					if (SwingUtilities.isLeftMouseButton(e)) {
						setSelectedIndex(Item.this.index);
					}
				}

				@Override
				public void mouseEntered(MouseEvent e) {
					animateHover(1);
				}

				@Override
				public void mouseExited(MouseEvent e) {
					animateHover(0);
				}
			});

			updateState();
		}

		private boolean isSelected() {
			return index == selectedIndex;
		}

		private Color getItemForeground() {
			return UIManager.getColor(isSelected() ? "App.sidebar.selectionForeground" : "App.sidebar.foreground");
		}

		void updateState() {
			Font font = UIManager.getFont(isSelected() ? "semibold.font" : "defaultFont");
			if (font == null) {
				font = UIManager.getFont("Label.font");
			}
			label.setFont(font.deriveFont(font.getSize2D() - 1));
			label.setForeground(getItemForeground());
			repaint();
		}

		@Override
		public void updateUI() {
			super.updateUI();
			if (builder != null) {
				updateState();
			}
		}

		private void animateHover(float target) {
			if (!Animator.useAnimation()) {
				hover = target;
				repaint();
				return;
			}

			hoverFrom = hover;
			hoverTo = target;
			if (hoverAnimator == null) {
				hoverAnimator = new Animator(140, fraction -> {
					hover = hoverFrom + (hoverTo - hoverFrom) * fraction;
					repaint();
				});
				hoverAnimator.setResolution(10);
			}
			hoverAnimator.restart();
		}

		@Override
		protected void paintComponent(Graphics g) {
			if (hover > 0 && !isSelected()) {
				Graphics2D g2 = (Graphics2D) g.create();
				try {
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, hover));
					g2.setColor(UIManager.getColor("App.sidebar.hoverBackground"));
					float arc = UIScale.scale((float) PILL_ARC);
					g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
				} finally {
					g2.dispose();
				}
			}
		}
	}

	private class DragOverSelection extends DropTargetAdapter {

		private boolean selectEnabled = false;
		private Timer dragEnterTimer;

		@Override
		public void dragOver(DropTargetDragEvent dtde) {
			if (selectEnabled) {
				Point p = dtde.getLocation();
				for (Item item : components) {
					Rectangle bounds = SwingUtilities.convertRectangle(itemPanel, item.getBounds(), NavigationRail.this);
					if (bounds.contains(p)) {
						setSelectedIndex(item.index);
					}
				}
			}
		}

		@Override
		public void dragEnter(DropTargetDragEvent dtde) {
			dragEnterTimer = invokeLater(DRAG_OVER_SELECT_DELAY, () -> {
				selectEnabled = true;

				// bring window to front when drag-and-drop operation is in progress
				if (Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
					Desktop.getDesktop().requestForeground(true);
				} else {
					SwingUtilities.getWindowAncestor(NavigationRail.this).toFront();
				}
			});
		}

		@Override
		public void dragExit(DropTargetEvent dte) {
			selectEnabled = false;
			if (dragEnterTimer != null) {
				dragEnterTimer.stop();
			}
		}

		@Override
		public void drop(DropTargetDropEvent dtde) {
			dtde.rejectDrop();
		}
	}

}
