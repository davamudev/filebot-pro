/*
 * Created on 19.03.2005
 */

package dev.davamu.filebotpro.util.ui.notification;

import static dev.davamu.filebotpro.util.ui.SwingUI.*;
import static dev.davamu.filebotpro.util.ui.notification.Direction.*;

import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class QueueNotificationLayout implements NotificationLayout {

	private final List<NotificationWindow> notifications = new ArrayList<NotificationWindow>();

	private final Direction alignment;
	private final Direction direction;
	private final Direction growAnchor;

	public QueueNotificationLayout() {
		this(SOUTH_EAST, WEST);
	}

	public QueueNotificationLayout(Direction alignment, Direction direction) {
		this.alignment = alignment;
		this.growAnchor = alignment;
		this.direction = direction;
	}

	public QueueNotificationLayout(Direction orientation, Direction direction, Direction growAnchor) {
		this.alignment = orientation;
		this.direction = direction;
		this.growAnchor = growAnchor;
	}

	private Point getBaseAnchor(Rectangle screen) {
		Point p = new Point();

		p.x = (int) (alignment.ax * screen.width);
		p.y = (int) (alignment.ay * screen.height);

		p.x += screen.x;
		p.y += screen.y;

		return p;
	}

	private Point getLocation(Point anchor, Dimension size) {
		Point p = new Point();

		p.x = (int) (anchor.x - size.width * growAnchor.ax);
		p.y = (int) (anchor.y - size.height * growAnchor.ay);

		return p;
	}

	private Point getNextAnchor(Point anchor, Dimension size) {
		Point p = new Point();

		p.x = anchor.x + size.width * direction.vx;
		p.y = anchor.y + size.height * direction.vy;

		return p;
	}

	@Override
	public void add(NotificationWindow notification) {
		notifications.add(notification);
		align(notification.getGraphicsConfiguration());
	}

	private void align(GraphicsConfiguration gc) {
		// show notifications on the screen of the application window (multi-monitor setups)
		Rectangle screen = getUsableScreenBounds(getApplicationScreen(gc));

		// avoid flickering by moving windows in reverse order
		Point anchor = getBaseAnchor(screen);
		align(anchor, screen, notifications.iterator());
	}

	private void align(Point anchor, Rectangle screen, Iterator<NotificationWindow> seq) {
		if (!seq.hasNext()) {
			return;
		}

		NotificationWindow window = seq.next();

		Dimension size = window.getSize();
		size.width = Math.min(size.width, (int) (screen.width * 0.8));
		size.height = Math.min(size.height, (int) (screen.height * 0.2));

		Point p = getLocation(anchor, size);
		align(getNextAnchor(anchor, size), screen, seq);

		window.setBounds(p.x, p.y, size.width, size.height);
	}

	@Override
	public void remove(NotificationWindow notification) {
		if (notifications.remove(notification)) {
			align(notification.getGraphicsConfiguration());
		}
	}

	@Override
	public int size() {
		return notifications.size();
	}

}
