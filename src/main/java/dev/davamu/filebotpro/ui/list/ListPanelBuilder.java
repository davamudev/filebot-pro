
package dev.davamu.filebotpro.ui.list;

import javax.swing.Icon;
import javax.swing.JComponent;

import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.ui.PanelBuilder;

public class ListPanelBuilder implements PanelBuilder {

	@Override
	public String getName() {
		return "List";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("panel.list");
	}

	@Override
	public String getDescription() {
		return "Generate numbered lists of names with format expressions.";
	}

	@Override
	public String getSymbol() {
		return "list";
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof ListPanelBuilder;
	}

	@Override
	public int hashCode() {
		return ListPanelBuilder.class.hashCode();
	}

	@Override
	public JComponent create() {
		return new ListPanel();
	}

}
