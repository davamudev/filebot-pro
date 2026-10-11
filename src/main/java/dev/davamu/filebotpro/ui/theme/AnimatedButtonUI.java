package dev.davamu.filebotpro.ui.theme;

import java.awt.Color;
import java.awt.Graphics;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;

import com.formdev.flatlaf.ui.FlatButtonUI;

/**
 * {@link FlatButtonUI} with animated state transitions (installed via ButtonUI in FlatLaf.properties).
 */
public class AnimatedButtonUI extends FlatButtonUI {

	public static ComponentUI createUI(JComponent c) {
		return new AnimatedButtonUI();
	}

	private ButtonAnimation animation;

	protected AnimatedButtonUI() {
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
