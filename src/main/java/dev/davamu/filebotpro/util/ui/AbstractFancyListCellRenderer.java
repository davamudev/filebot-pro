
package dev.davamu.filebotpro.util.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import com.formdev.flatlaf.util.UIScale;

public abstract class AbstractFancyListCellRenderer extends JPanel implements ListCellRenderer {

	private Color gradientBeginColor;
	private Color gradientEndColor;

	private Color highlightColor;

	private boolean borderPainted = false;
	private boolean gradientPainted = false;

	private GradientStyle gradientStyle = GradientStyle.TOP_TO_BOTTOM;
	private boolean highlightingEnabled = true;

	private final Insets margin;

	private boolean hover = false;
	private Color hoverColor;

	private static final Insets DEFAULT_PADDING = new Insets(7, 7, 7, 7);
	private static final Insets DEFAULT_MARGIN = new Insets(1, 1, 0, 1);

	public AbstractFancyListCellRenderer() {
		this(DEFAULT_PADDING, DEFAULT_MARGIN, null);
	}

	public AbstractFancyListCellRenderer(Insets padding) {
		this(padding, DEFAULT_MARGIN, null);
	}

	public AbstractFancyListCellRenderer(Insets padding, Insets margin) {
		this(padding, margin, null);
	}

	public AbstractFancyListCellRenderer(Insets padding, Insets margin, Color borderColor) {
		this.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));

		Border border = null;

		if (padding != null)
			border = new EmptyBorder(padding);

		if (borderColor != null)
			border = new CompoundBorder(new LineBorder(borderColor, 1), border);

		if (margin != null) {
			this.margin = margin;
			border = new CompoundBorder(new EmptyBorder(margin), border);
		} else {
			this.margin = new Insets(0, 0, 0, 0);
		}

		setBorder(border);
		setOpaque(false);
	}

	@Override
	protected void paintBorder(Graphics g) {
		if (borderPainted) {
			super.paintBorder(g);
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2d = (Graphics2D) g.create();
		try {
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			// rounded row "pill" inset from the list edges (same geometry as FlatLaf List.selectionArc / List.selectionInsets)
			float inset = UIScale.scale(4f);
			float arc = UIScale.scale(8f);
			RoundRectangle2D shape = new RoundRectangle2D.Float(margin.left + inset, margin.top + 0.5f, getWidth() - (margin.left + margin.right) - 2 * inset, getHeight() - (margin.top + margin.bottom) - 1, arc, arc);

			if (isOpaque()) {
				g2d.setPaint(getBackground());
				g2d.fill(shape);
			}

			if (highlightingEnabled && (highlightColor != null)) {
				g2d.setPaint(highlightColor);
				g2d.fill(shape);
			}

			if (hover && !gradientPainted && hoverColor != null) {
				g2d.setPaint(hoverColor);
				g2d.fill(shape);
			}

			if (gradientPainted) {
				g2d.setPaint(gradientEndColor != null ? gradientEndColor : gradientBeginColor);
				g2d.fill(shape);
			}
		} finally {
			g2d.dispose();
		}

		super.paintComponent(g);
	}

	@Override
	public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
		ListHoverSupport.install(list);

		configureListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
		validate();

		return this;
	}

	protected void configureListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
		setGradientPainted(isSelected);
		setBorderPainted(false);

		Color sc = list.getSelectionBackground();

		if (isSelected) {
			setGradientColors(sc, sc);
		}

		// subtle neutral stripes
		Color stripe = UIManager.getColor("App.list.stripe");
		if (highlightingEnabled && ((index % 2) == 0) && stripe != null) {
			setHighlightColor(stripe);
		} else {
			setHighlightColor(null);
		}

		hover = ListHoverSupport.getHoverIndex(list) == index;
		hoverColor = UIManager.getColor("App.list.hoverBackground");

		if (isSelected) {
			setBackground(list.getSelectionBackground());
			setForeground(list.getSelectionForeground());
		} else {
			setBackground(list.getBackground());
			setForeground(list.getForeground());
		}
	}

	public void setGradientColors(Color gradientBeginColor, Color gradientEndColor) {
		this.gradientBeginColor = gradientBeginColor;
		this.gradientEndColor = gradientEndColor;
	}

	public Color getGradientBeginColor() {
		return gradientBeginColor;
	}

	public Color getGradientEndColor() {
		return gradientEndColor;
	}

	public void setHighlightColor(Color highlightColor) {
		this.highlightColor = highlightColor;
	}

	public void setGradientStyle(GradientStyle gradientStyle) {
		this.gradientStyle = gradientStyle;
	}

	public void setHighlightingEnabled(boolean highlightingEnabled) {
		this.highlightingEnabled = highlightingEnabled;
	}

	public void setBorderPainted(boolean borderPainted) {
		this.borderPainted = borderPainted;
	}

	public void setGradientPainted(boolean gradientPainted) {
		this.gradientPainted = gradientPainted;
	}

	public Color getHighlightColor() {
		return highlightColor;
	}

	public boolean isBorderPainted() {
		return borderPainted;
	}

	public GradientStyle getGradientStyle() {
		return gradientStyle;
	}

	public boolean isHighlightingEnabled() {
		return highlightingEnabled;
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void repaint() {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void repaint(long tm, int x, int y, int width, int height) {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void repaint(Rectangle r) {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void firePropertyChange(String propertyName, byte oldValue, byte newValue) {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void firePropertyChange(String propertyName, char oldValue, char newValue) {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void firePropertyChange(String propertyName, short oldValue, short newValue) {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void firePropertyChange(String propertyName, int oldValue, int newValue) {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void firePropertyChange(String propertyName, long oldValue, long newValue) {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void firePropertyChange(String propertyName, float oldValue, float newValue) {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void firePropertyChange(String propertyName, double oldValue, double newValue) {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
	}

}
