package dev.davamu.filebotpro.util.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;

import com.formdev.flatlaf.util.UIScale;

/**
 * Matte border that looks up its color from the current look and feel when painting, so it follows theme switches.
 */
public class MatteColorBorder extends AbstractBorder {

	private final int top, left, bottom, right;
	private final String colorKey;

	public MatteColorBorder(int top, int left, int bottom, int right, String colorKey) {
		this.top = top;
		this.left = left;
		this.bottom = bottom;
		this.right = right;
		this.colorKey = colorKey;
	}

	@Override
	public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
		Color color = UIManager.getColor(colorKey);
		if (color == null) {
			return;
		}

		Insets insets = getBorderInsets(c);
		g.setColor(color);
		g.fillRect(x, y, width, insets.top);
		g.fillRect(x, y + height - insets.bottom, width, insets.bottom);
		g.fillRect(x, y, insets.left, height);
		g.fillRect(x + width - insets.right, y, insets.right, height);
	}

	@Override
	public Insets getBorderInsets(Component c, Insets insets) {
		insets.set(scale(top), scale(left), scale(bottom), scale(right));
		return insets;
	}

	private static int scale(int value) {
		return value > 0 ? Math.max(1, UIScale.scale(value)) : 0;
	}

}
