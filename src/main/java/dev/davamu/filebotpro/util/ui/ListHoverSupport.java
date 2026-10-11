package dev.davamu.filebotpro.util.ui;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JList;

/**
 * Tracks the list row under the mouse cursor, so cell renderers can paint a hover highlight.
 */
public final class ListHoverSupport extends MouseAdapter {

	private static final String HOVER_INDEX = "App.hoverIndex";
	private static final String INSTALLED = "App.hoverSupport";

	private final JList<?> list;

	private ListHoverSupport(JList<?> list) {
		this.list = list;
	}

	public static void install(JList<?> list) {
		if (list.getClientProperty(INSTALLED) != null) {
			return;
		}

		ListHoverSupport support = new ListHoverSupport(list);
		list.putClientProperty(INSTALLED, support);
		list.addMouseListener(support);
		list.addMouseMotionListener(support);
	}

	public static int getHoverIndex(JList<?> list) {
		Object index = list.getClientProperty(HOVER_INDEX);
		return index instanceof Integer ? (Integer) index : -1;
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		update(e.getPoint());
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		update(e.getPoint());
	}

	@Override
	public void mouseExited(MouseEvent e) {
		setHoverIndex(-1);
	}

	private void update(Point p) {
		int index = list.locationToIndex(p);
		if (index >= 0) {
			Rectangle bounds = list.getCellBounds(index, index);
			if (bounds == null || !bounds.contains(p)) {
				index = -1;
			}
		}
		setHoverIndex(index);
	}

	private void setHoverIndex(int index) {
		int previous = getHoverIndex(list);
		if (previous == index) {
			return;
		}

		list.putClientProperty(HOVER_INDEX, index);
		repaintCell(previous);
		repaintCell(index);
	}

	private void repaintCell(int index) {
		if (index >= 0 && index < list.getModel().getSize()) {
			Rectangle bounds = list.getCellBounds(index, index);
			if (bounds != null) {
				list.repaint(bounds);
			}
		}
	}

}
