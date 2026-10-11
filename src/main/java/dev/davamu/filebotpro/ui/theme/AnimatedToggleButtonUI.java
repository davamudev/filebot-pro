package dev.davamu.filebotpro.ui.theme;

import java.awt.Color;
import java.awt.Graphics;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;

import com.formdev.flatlaf.ui.FlatToggleButtonUI;

/**
 * {@link FlatToggleButtonUI} with animated state transitions (installed via ToggleButtonUI in FlatLaf.properties).
 */
public class AnimatedToggleButtonUI extends FlatToggleButtonUI {

	public static ComponentUI createUI(JComponent c) {
		return new AnimatedToggleButtonUI();
	}

	private ButtonAnimation animation;

	protected AnimatedToggleButtonUI() {
		super(false);
	}

	@Override
	protected void installListeners(AbstractButton b) {
		super.installListeners(b);
		animation = new ButtonAnimation(b);
		animation.install();
	}

	@Override
	protected void uninstallListeners(AbstractButton b) {
		super.uninstallListeners(b);
		if (animation != null) {
			animation.uninstall();
			animation = null;
		}
	}

	@Override
	protected Color getBackground(JComponent c) {
		Color target = super.getBackground(c);
		return animation == null ? target : animation.animateBackground(target);
	}

	@Override
	public void update(Graphics g, JComponent c) {
		if (animation == null) {
			super.update(g, c);
		} else {
			animation.paint(g, c, g2 -> super.update(g2, c));
		}
	}

}
