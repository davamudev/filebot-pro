package dev.davamu.filebotpro.util.ui;

import static dev.davamu.filebotpro.Logging.*;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;
import java.util.logging.Level;

import javax.swing.JComponent;
import javax.swing.UIManager;

import com.formdev.flatlaf.util.UIScale;

/**
 * Visual feedback (tinted area with a dashed accent outline) while files are dragged over a drop target.
 */
public final class DropHighlight {

	/**
	 * Listen to drag events of the Swing drop target that was installed via {@link javax.swing.TransferHandler}.
	 */
	public static void install(JComponent component, Consumer<Boolean> listener) {
		try {
			component.getDropTarget().addDropTargetListener(new DropTargetAdapter() {

				@Override
				public void dragEnter(DropTargetDragEvent dtde) {
					listener.accept(true);
				}

				@Override
				public void dragExit(DropTargetEvent dte) {
					listener.accept(false);
				}

				@Override
				public void drop(DropTargetDropEvent dtde) {
					listener.accept(false);
				}
			});
		} catch (Exception e) {
			debug.log(Level.FINEST, e, e::toString);
		}
	}

	public static void paint(JComponent c, Graphics g) {
		Rectangle area = c.getVisibleRect();
		Color accent = UIManager.getColor("Component.accentColor");
		if (accent == null) {
			return;
		}

		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			float inset = UIScale.scale(4f);
			float arc = UIScale.scale(10f);
			RoundRectangle2D shape = new RoundRectangle2D.Float(area.x + inset, area.y + inset, area.width - 2 * inset, area.height - 2 * inset, arc, arc);

			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.08f));
			g2.setColor(accent);
			g2.fill(shape);

			g2.setComposite(AlphaComposite.SrcOver);
			g2.setStroke(new BasicStroke(UIScale.scale(1.5f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, new float[] { UIScale.scale(6f), UIScale.scale(4f) }, 0));
			g2.draw(shape);
		} finally {
			g2.dispose();
		}
	}

	private DropHighlight() {
		throw new UnsupportedOperationException();
	}

}
