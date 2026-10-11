package dev.davamu.filebotpro.util.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;

import com.formdev.flatlaf.util.UIScale;

/**
 * Rounded border that fills the component background. The border color can be a look and feel key, so it follows theme switches.
 */
public class RoundBorder extends AbstractBorder {

	private final Color color;
	private final String colorKey;
	private final Insets insets;
	private final int arc;

	public RoundBorder(Color color, int arc, Insets insets) {
		this(color, null, arc, insets);
	}

	public RoundBorder(String colorKey, int arc, Insets insets) {
		this(null, colorKey, arc, insets);
	}

	private RoundBorder(Color color, String colorKey, int arc, Insets insets) {
		this.color = color;
		this.colorKey = colorKey;
		this.arc = arc;
		this.insets = insets;
	}

	@Override
	public boolean isBorderOpaque() {
		return false;
	}

	@Override
	public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
		Graphics2D g2d = (Graphics2D) g.create();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int a = UIScale.scale(arc);

		g2d.setPaint(c.getBackground());
		g2d.fillRoundRect(x, y, width - 1, height - 1, a, a);

		Color line = colorKey != null ? UIManager.getColor(colorKey) : color;
		if (line != null) {
			g2d.setPaint(line);
			g2d.drawRoundRect(x, y, width - 1, height - 1, a, a);
		}

		g2d.dispose();
	}

	@Override
	public Insets getBorderInsets(Component c) {
		return getBorderInsets(c, new Insets(0, 0, 0, 0));
	}

	@Override
	public Insets getBorderInsets(Component c, Insets insets) {
		insets.top = UIScale.scale(this.insets.top);
		insets.left = UIScale.scale(this.insets.left);
		insets.bottom = UIScale.scale(this.insets.bottom);
		insets.right = UIScale.scale(this.insets.right);

		return insets;
	}

}
