package dev.davamu.filebotpro.util.ui;

import static javax.swing.BorderFactory.*;
import static dev.davamu.filebotpro.ui.ThemeSupport.*;

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.swing.DefaultSingleSelectionModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SingleSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatClientProperties;

public class SelectButton<T> extends JButton {

	public static final String SELECTED_VALUE = "selected value";

	private final SelectIcon selectIcon = new SelectIcon();

	private List<T> model = Collections.emptyList();
	private SingleSelectionModel selectionModel = new DefaultSingleSelectionModel();

	private LabelProvider<T> labelProvider = new NullLabelProvider<T>();

	public SelectButton() {
		setFocusable(false);

		super.setIcon(selectIcon);

		setHorizontalAlignment(SwingConstants.CENTER);
		setVerticalAlignment(SwingConstants.CENTER);
		putClientProperty(FlatClientProperties.STYLE, "margin: 2,7,2,7; minimumWidth: 0");

		addActionListener(new OpenPopupOnClick());
	}

	public void setModel(Collection<T> model) {
		this.model = new ArrayList<T>(model);
		setSelectedIndex(0);
	}

	public LabelProvider<T> getLabelProvider() {
		return labelProvider;
	}

	public void setLabelProvider(LabelProvider<T> labelProvider) {
		this.labelProvider = labelProvider;

		// update icon
		this.setIcon(labelProvider.getIcon(getSelectedValue()));
	}

	@Override
	public void setIcon(Icon icon) {
		selectIcon.setInnerIcon(icon);
		repaint();
	}

	public void setSelectedValue(T value) {
		setSelectedIndex(model.indexOf(value));
	}

	public T getSelectedValue() {
		if (!selectionModel.isSelected())
			return null;

		return model.get(selectionModel.getSelectedIndex());
	}

	public void setSelectedIndex(int i) {
		if (i < 0 || i >= model.size()) {
			selectionModel.clearSelection();
			setIcon(null);
			return;
		}

		selectionModel.setSelectedIndex(i);
		T value = model.get(i);
		setIcon(labelProvider.getIcon(value));
		firePropertyChange(SELECTED_VALUE, null, value);
	}

	public int getSelectedIndex() {
		return selectionModel.getSelectedIndex();
	}

	public SingleSelectionModel getSelectionModel() {
		return selectionModel;
	}

	public void spinValue(int spin) {
		int size = model.size();

		spin = spin % size;

		int next = getSelectedIndex() + spin;

		if (next < 0)
			next += size;
		else if (next >= size)
			next -= size;

		setSelectedIndex(next);
	}

	private class OpenPopupOnClick implements ActionListener {

		@Override
		public void actionPerformed(ActionEvent e) {
			JPopupMenu popup = new JPopupMenu();

			for (T value : model) {
				SelectPopupMenuItem item = new SelectPopupMenuItem(labelProvider.getText(value), labelProvider.getIcon(value), value);

				if (value == getSelectedValue())
					item.setSelected(true);

				popup.add(item);
			}

			popup.show(SelectButton.this, 0, getHeight() - 1);
		}
	}

	private class SelectPopupMenuItem extends JMenuItem implements ActionListener {

		private final T value;

		public SelectPopupMenuItem(String text, Icon icon, T value) {
			super(text, icon);

			this.value = value;

			setMargin(new Insets(4, 8, 4, 12));
			addActionListener(this);
		}

		@Override
		public void setSelected(boolean selected) {
			setFont(getFont().deriveFont(selected ? Font.BOLD : Font.PLAIN));
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			setSelectedValue(value);
		}

	}

	private static class SelectIcon implements Icon {

		private static final int SIZE = 16;
		private static final int ARROW = 8;
		private static final int GAP = 5;

		private Icon icon;

		public void setInnerIcon(Icon icon) {
			this.icon = icon;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			Graphics2D g2d = (Graphics2D) g.create();
			try {
				if (icon != null) {
					icon.paintIcon(c, g2d, x + (SIZE - icon.getIconWidth()) / 2, y + (SIZE - icon.getIconHeight()) / 2);
				}

				// chevron
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2d.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2d.setColor(UIManager.getColor("ComboBox.buttonArrowColor"));

				float ax = x + SIZE + GAP;
				float ay = y + SIZE / 2f - 2;
				Path2D arrow = new Path2D.Float();
				arrow.moveTo(ax, ay);
				arrow.lineTo(ax + ARROW / 2f, ay + ARROW / 2f);
				arrow.lineTo(ax + ARROW, ay);
				g2d.draw(arrow);
			} finally {
				g2d.dispose();
			}
		}

		@Override
		public int getIconWidth() {
			return SIZE + GAP + ARROW;
		}

		@Override
		public int getIconHeight() {
			return SIZE;
		}
	}

}
