
package dev.davamu.filebotpro.ui.filter;

import javax.swing.Icon;
import javax.swing.JComponent;

import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.ui.PanelBuilder;

public class FilterPanelBuilder implements PanelBuilder {

	@Override
	public String getName() {
		return "Filter";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("panel.analyze");
	}

	@Override
	public String getDescription() {
		return "Inspect media properties, extract archives and organize folders.";
	}

	@Override
	public String getSymbol() {
		return "filter";
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof FilterPanelBuilder;
	}

	@Override
	public int hashCode() {
		return FilterPanelBuilder.class.hashCode();
	}

	@Override
	public JComponent create() {
		FilterPanel panel = new FilterPanel();
		panel.addTool(new ExtractTool());
		panel.addTool(new TypeTool());
		panel.addTool(new SplitTool());
		panel.addTool(new AttributeTool());
		panel.addTool(new MediaInfoTool());
		return panel;
	}

}
