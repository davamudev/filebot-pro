/*
 * Created on 19.03.2005
 */

package dev.davamu.filebotpro.util.ui.notification;

import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.GraphicsDevice.WindowTranslucency;
import java.awt.GraphicsConfiguration;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;

import javax.swing.JWindow;
import javax.swing.Timer;

import com.formdev.flatlaf.util.Animator;
import com.formdev.flatlaf.util.CubicBezierEasing;

public class NotificationWindow extends JWindow {

	private static final int FADE_IN = 180;
	private static final int FADE_OUT = 220;

	private final int timeout;

	private boolean closing = false;

	public NotificationWindow(Window owner, int timeout) {
		this(owner, timeout, true);
	}

	public NotificationWindow(Window owner, int timeout, boolean closeOnClick) {
		super(owner);
		this.timeout = timeout;

		setAlwaysOnTop(true);
		setFocusableWindowState(false);

		if (closeOnClick) {
			getGlassPane().addMouseListener(mouseClicked(evt -> close()));
			getGlassPane().setVisible(true);
		}

		addComponentListener(closeOnTimeout);
	}

	public NotificationWindow(Window owner) {
		this(owner, -1);
	}

	/**
	 * @return true if the window may have transparent pixels (rounded corners and drop shadow)
	 */
	public static boolean isPerPixelTranslucencySupported(GraphicsConfiguration gc) {
		try {
			return gc != null && gc.getDevice().isWindowTranslucencySupported(WindowTranslucency.PERPIXEL_TRANSLUCENT);
		} catch (Exception e) {
			return false;
		}
	}

	private boolean isFadeSupported() {
		try {
			return Animator.useAnimation() && getGraphicsConfiguration().getDevice().isWindowTranslucencySupported(WindowTranslucency.TRANSLUCENT);
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public void setVisible(boolean visible) {
		if (visible && !isVisible() && isFadeSupported()) {
			setOpacity(0);
			super.setVisible(true);
			fade(0, 1, FADE_IN, null);
		} else {
			super.setVisible(visible);
		}
	}

	public final void close() {
		checkEventDispatchThread();

		if (closing) {
			return;
		}
		closing = true;

		if (isShowing() && isFadeSupported()) {
			fade(getOpacity(), 0, FADE_OUT, this::closeNow);
		} else {
			closeNow();
		}
	}

	private void closeNow() {
		// window events are not fired automatically, required for layout updates
		processWindowEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));

		super.setVisible(false);

		// component events are not fired automatically, used to cancel timeout timer
		processComponentEvent(new ComponentEvent(this, ComponentEvent.COMPONENT_HIDDEN));

		dispose();
	}

	private void fade(float from, float to, int duration, Runnable end) {
		Animator animator = new Animator(duration, fraction -> {
			setOpacity(Math.max(0, Math.min(1, from + (to - from) * fraction)));
		}, end);
		animator.setInterpolator(CubicBezierEasing.EASE_OUT);
		animator.setResolution(10);
		animator.start();
	}

	private final ComponentListener closeOnTimeout = new ComponentAdapter() {

		private Timer timer = null;

		@Override
		public void componentShown(ComponentEvent e) {
			if (timeout >= 0) {
				timer = invokeLater(timeout, () -> close());
			}
		}

		@Override
		public void componentHidden(ComponentEvent e) {
			if (timer != null) {
				timer.stop();
			}
		}

	};

}
