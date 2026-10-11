package dev.davamu.filebotpro.ui.theme;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.Animator;
import com.formdev.flatlaf.util.CubicBezierEasing;
import com.formdev.flatlaf.util.UIScale;

/**
 * Micro-interactions shared by {@link AnimatedButtonUI} and {@link AnimatedToggleButtonUI}:
 * <ul>
 * <li>smooth color transitions between rest, hover, pressed and selected states</li>
 * <li>a subtle "press" scale effect</li>
 * <li>a ripple that spreads from the click position on accent buttons</li>
 * </ul>
 */
class ButtonAnimation extends MouseAdapter {

	private static final int COLOR_DURATION = 160;
	private static final int PRESS_DURATION = 110;
	private static final int RIPPLE_DURATION = 520;

	private static final float PRESS_SCALE = 0.035f;

	private final AbstractButton button;

	// background color transition
	private Color fromColor;
	private Color toColor;
	private Color displayedColor;
	private float colorFraction = 1;
	private Animator colorAnimator;

	// press effect
	private float press = 0;
	private float pressFrom = 0;
	private float pressTo = 0;
	private Animator pressAnimator;

	// ripple effect
	private Point ripplePoint;
	private float ripple = 1;
	private Animator rippleAnimator;

	ButtonAnimation(AbstractButton button) {
		this.button = button;
	}

	void install() {
		button.addMouseListener(this);
	}

	void uninstall() {
		button.removeMouseListener(this);
		stop(colorAnimator);
		stop(pressAnimator);
		stop(rippleAnimator);
	}

	private static void stop(Animator animator) {
		if (animator != null) {
			animator.stop();
		}
	}

	private static boolean enabled() {
		return Animator.useAnimation();
	}

	/**
	 * @param target
	 *            the instantaneous state color computed by FlatLaf (may be null for transparent buttons)
	 * @return the animated color to paint
	 */
	Color animateBackground(Color target) {
		if (!enabled() || !button.isShowing()) {
			fromColor = toColor = displayedColor = target;
			colorFraction = 1;
			return target;
		}

		if (!same(target, toColor)) {
			fromColor = displayedColor;
			toColor = target;
			colorFraction = 0;

			if (colorAnimator == null) {
				colorAnimator = new Animator(COLOR_DURATION, fraction -> {
					colorFraction = fraction;
					button.repaint();
				});
				colorAnimator.setInterpolator(CubicBezierEasing.STANDARD_EASING);
				colorAnimator.setResolution(10);
			}
			colorAnimator.restart();
		}

		displayedColor = colorFraction >= 1 ? toColor : blend(fromColor, toColor, colorFraction);
		return displayedColor;
	}

	private static boolean same(Color a, Color b) {
		return a == null ? b == null : a.equals(b);
	}

	private static Color blend(Color from, Color to, float f) {
		if (from == null && to == null) {
			return null;
		}

		// transparent buttons (e.g. toolbar buttons) fade in and out
		if (from == null) {
			from = new Color(to.getRGB() & 0x00FFFFFF, true);
		}
		if (to == null) {
			to = new Color(from.getRGB() & 0x00FFFFFF, true);
		}

		int a = Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * f);
		int r = Math.round(from.getRed() + (to.getRed() - from.getRed()) * f);
		int g = Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * f);
		int b = Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * f);
		return new Color(r, g, b, a);
	}

	/* ---------------------------------------------------------------------------------------------------------------- */

	@Override
	public void mousePressed(MouseEvent e) {
		if (!button.isEnabled() || !enabled()) {
			return;
		}

		animatePress(1);

		if (isAccent(button)) {
			ripplePoint = e.getPoint();
			if (rippleAnimator == null) {
				rippleAnimator = new Animator(RIPPLE_DURATION, fraction -> {
					ripple = fraction;
					button.repaint();
				});
				rippleAnimator.setInterpolator(CubicBezierEasing.EASE_OUT);
				rippleAnimator.setResolution(10);
			}
			ripple = 0;
			rippleAnimator.restart();
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		animatePress(0);
	}

	@Override
	public void mouseExited(MouseEvent e) {
		if (press > 0) {
			animatePress(0);
		}
	}

	private void animatePress(float target) {
		if (pressTo == target) {
			return;
		}

		pressFrom = press;
		pressTo = target;

		if (pressAnimator == null) {
			pressAnimator = new Animator(PRESS_DURATION, fraction -> {
				press = pressFrom + (pressTo - pressFrom) * fraction;
				button.repaint();
			});
			pressAnimator.setInterpolator(CubicBezierEasing.STANDARD_EASING);
			pressAnimator.setResolution(10);
		}
		pressAnimator.restart();
	}

	/* ---------------------------------------------------------------------------------------------------------------- */

	interface Painter {
		void paint(Graphics g);
	}

	/**
	 * Paint the button with press scale and ripple effects.
	 */
	void paint(Graphics g, JComponent c, Painter painter) {
		float scale = 1 - PRESS_SCALE * press;
		if (scale >= 0.999f) {
			painter.paint(g);
		} else {
			Graphics2D g2 = (Graphics2D) g.create();
			try {
				double cx = c.getWidth() / 2.0;
				double cy = c.getHeight() / 2.0;
				g2.translate(cx, cy);
				g2.scale(scale, scale);
				g2.translate(-cx, -cy);
				painter.paint(g2);
			} finally {
				g2.dispose();
			}
		}

		if (ripplePoint != null && ripple < 1) {
			paintRipple(g, c);
		}
	}

	private void paintRipple(Graphics g, JComponent c) {
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			float focusWidth = UIScale.scale((float) UIManager.getInt("Component.focusWidth"));
			float arc = UIScale.scale((float) UIManager.getInt("Button.arc"));
			Shape clip = new RoundRectangle2D.Float(focusWidth, focusWidth, c.getWidth() - focusWidth * 2, c.getHeight() - focusWidth * 2, arc, arc);
			g2.clip(clip);

			float maxRadius = (float) Math.hypot(c.getWidth(), c.getHeight());
			float radius = maxRadius * ripple;
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f * (1 - ripple)));
			g2.setColor(Color.WHITE);
			g2.fill(new Ellipse2D.Float(ripplePoint.x - radius, ripplePoint.y - radius, radius * 2, radius * 2));
		} finally {
			g2.dispose();
		}
	}

	static boolean isAccent(JComponent c) {
		Object styleClass = c.getClientProperty(FlatClientProperties.STYLE_CLASS);
		if (styleClass != null && styleClass.toString().contains("accent")) {
			return true;
		}
		return c instanceof javax.swing.JButton && ((javax.swing.JButton) c).isDefaultButton();
	}

}
