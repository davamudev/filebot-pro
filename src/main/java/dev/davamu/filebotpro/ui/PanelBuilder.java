
package dev.davamu.filebotpro.ui;

import javax.swing.Icon;
import javax.swing.JComponent;

import dev.davamu.filebotpro.ui.episodelist.EpisodeListPanelBuilder;
import dev.davamu.filebotpro.ui.filter.FilterPanelBuilder;
import dev.davamu.filebotpro.ui.list.ListPanelBuilder;
import dev.davamu.filebotpro.ui.rename.RenamePanelBuilder;
import dev.davamu.filebotpro.ui.sfv.SfvPanelBuilder;
import dev.davamu.filebotpro.ui.subtitle.SubtitlePanelBuilder;

public interface PanelBuilder {

	public String getName();

	public Icon getIcon();

	/**
	 * Short description shown in the header and as navigation tooltip.
	 */
	public default String getDescription() {
		return getName();
	}

	/**
	 * Name of the monochrome navigation icon in resources/svg.
	 */
	public default String getSymbol() {
		return "list";
	}

	public JComponent create();

	public static PanelBuilder[] defaultSequence() {
		return new PanelBuilder[] { new RenamePanelBuilder(), new EpisodeListPanelBuilder(), new SubtitlePanelBuilder(), new SfvPanelBuilder(), new FilterPanelBuilder(), new ListPanelBuilder() };
	}

	public static PanelBuilder[] episodeHandlerSequence() {
		return new PanelBuilder[] { new RenamePanelBuilder(), new ListPanelBuilder() };
	}

	public static PanelBuilder[] fileHandlerSequence() {
		return new PanelBuilder[] { new RenamePanelBuilder(), new SfvPanelBuilder(), new ListPanelBuilder() };
	}

	public static PanelBuilder[] textHandlerSequence() {
		return new PanelBuilder[] { new RenamePanelBuilder() };
	}

}
