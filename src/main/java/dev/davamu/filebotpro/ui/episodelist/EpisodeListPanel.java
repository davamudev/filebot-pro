package dev.davamu.filebotpro.ui.episodelist;

import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.ui.episodelist.SeasonSpinnerModel.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;
import static dev.davamu.filebotpro.web.EpisodeUtilities.*;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;

import dev.davamu.filebotpro.Language;
import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.Settings;
import dev.davamu.filebotpro.WebServices;
import dev.davamu.filebotpro.media.MediaDetection;
import dev.davamu.filebotpro.similarity.Normalization;
import dev.davamu.filebotpro.ui.AbstractSearchPanel;
import dev.davamu.filebotpro.ui.TitledList;
import dev.davamu.filebotpro.ui.LanguageComboBox;
import dev.davamu.filebotpro.ui.PanelBuilder;
import dev.davamu.filebotpro.ui.SelectDialog;
import dev.davamu.filebotpro.ui.TargetTransferable;
import dev.davamu.filebotpro.ui.transfer.SaveAction;
import dev.davamu.filebotpro.util.ui.LabelProvider;
import dev.davamu.filebotpro.util.ui.SelectButton;
import dev.davamu.filebotpro.util.ui.SimpleLabelProvider;
import dev.davamu.filebotpro.util.ui.SwingEventBus;
import dev.davamu.filebotpro.web.Episode;
import dev.davamu.filebotpro.web.EpisodeListProvider;
import dev.davamu.filebotpro.web.MultiEpisode;
import dev.davamu.filebotpro.web.SearchResult;
import dev.davamu.filebotpro.web.SeasonOutOfBoundsException;
import dev.davamu.filebotpro.web.SortOrder;

public class EpisodeListPanel extends AbstractSearchPanel<EpisodeListProvider, Episode> {

	private SeasonSpinnerModel seasonSpinnerModel = new SeasonSpinnerModel();
	private LanguageComboBox languageComboBox = new LanguageComboBox(Language.getLanguage("en"), getSettings());
	private JComboBox sortOrderComboBox = new JComboBox(SortOrder.values());

	public EpisodeListPanel() {
		historyPanel.setColumnHeader(0, "TV Series");
		historyPanel.setColumnHeader(1, "Number of Episodes");

		JSpinner seasonSpinner = new JSpinner(seasonSpinnerModel);
		seasonSpinner.setEditor(new SeasonSpinnerEditor(seasonSpinner));

		// set minimum size to "All Seasons" preferred size
		Dimension d = seasonSpinner.getPreferredSize();
		d.width += 12;
		seasonSpinner.setMinimumSize(d);

		// add after text field
		add(seasonSpinner, "sgy button, gap indent", 1);
		add(sortOrderComboBox, "sgy button, gap rel", 2);
		add(languageComboBox, "sgy button, gap indent+5", 3);

		searchTextField.getSelectButton().addPropertyChangeListener(SelectButton.SELECTED_VALUE, selectButtonListener);

		installAction(this, KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.SHIFT_DOWN_MASK), new SpinSeasonAction(1));
		installAction(this, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.SHIFT_DOWN_MASK), new SpinSeasonAction(-1));
	}

	@Override
	protected Collection<String> getHistory(EpisodeListProvider engine) throws Exception {
		final List<String> names = new ArrayList<String>(100000);
		final SearchResult[] index = (engine == WebServices.AniDB) ? MediaDetection.releaseInfo.getAnidbIndex() : MediaDetection.releaseInfo.getSeriesIndex();
		for (SearchResult it : index) {
			for (String n : it.getEffectiveNames()) {
				names.add(Normalization.removeTrailingBrackets(n));
			}
		}
		return names;
	}

	@Override
	protected EpisodeListProvider[] getSearchEngines() {
		return WebServices.getEpisodeListProviders();
	}

	@Override
	protected LabelProvider<EpisodeListProvider> getSearchEngineLabelProvider() {
		return SimpleLabelProvider.forClass(EpisodeListProvider.class);
	}

	@Override
	protected Settings getSettings() {
		return Settings.forPackage(EpisodeListPanel.class);
	}

	@Override
	protected EpisodeListRequestProcessor createRequestProcessor() {
		EpisodeListProvider provider = searchTextField.getSelectButton().getSelectedValue();
		String text = searchTextField.getText().trim();
		int season = seasonSpinnerModel.getSeason();
		SortOrder order = (SortOrder) sortOrderComboBox.getSelectedItem();
		Locale language = languageComboBox.getModel().getSelectedItem().getLocale();

		return new EpisodeListRequestProcessor(new EpisodeListRequest(provider, text, season, order, language));
	};

	private final PropertyChangeListener selectButtonListener = new PropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			EpisodeListProvider provider = searchTextField.getSelectButton().getSelectedValue();

			// lock season spinner on "All Seasons" if provider doesn't support fetching of single seasons
			if (!provider.hasSeasonSupport()) {
				seasonSpinnerModel.lock(ALL_SEASONS);
			} else {
				seasonSpinnerModel.unlock();
			}
		}
	};

	private class SpinSeasonAction extends AbstractAction {

		public SpinSeasonAction(int spin) {
			super(String.format("Spin%+d", spin));
			putValue("spin", spin);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			seasonSpinnerModel.spin((Integer) getValue("spin"));
		}
	}

	protected static class EpisodeListRequest extends Request {

		public final EpisodeListProvider provider;
		public final int season;
		public final SortOrder order;
		public final Locale language;

		public EpisodeListRequest(EpisodeListProvider provider, String searchText, int season, SortOrder order, Locale language) {
			super(searchText);
			this.provider = provider;
			this.season = season;
			this.order = order;
			this.language = language;
		}
	}

	protected static class EpisodeListRequestProcessor extends RequestProcessor<EpisodeListRequest, Episode> {

		public EpisodeListRequestProcessor(EpisodeListRequest request) {
			super(request, new EpisodeListTab());
		}

		@Override
		public Collection<SearchResult> search() throws Exception {
			return request.provider.search(request.getSearchText(), request.language);
		}

		@Override
		public Collection<Episode> fetch() throws Exception {
			List<Episode> episodes = request.provider.getEpisodeList(getSearchResult(), request.order, request.language);

			if (request.season != ALL_SEASONS) {
				List<Episode> episodeForSeason = filterBySeason(episodes, request.season);
				if (episodeForSeason.isEmpty()) {
					throw new SeasonOutOfBoundsException(getSearchResult().getName(), request.season, getLastSeason(episodes));
				}
				episodes = episodeForSeason;
			}

			return episodes;
		}

		@Override
		public URI getLink() {
			return request.provider.getEpisodeListLink(getSearchResult());
		}

		@Override
		public void process(Collection<Episode> episodes) {
			// set a proper title for the export handler before adding episodes
			getComponent().setTitle(getTitle());

			getComponent().getModel().addAll(episodes);
		}

		@Override
		public String getStatusMessage(Collection<Episode> result) {
			return (result.isEmpty()) ? "No episodes found" : String.format("%d episodes", result.size());
		}

		@Override
		public EpisodeListTab getComponent() {
			return (EpisodeListTab) super.getComponent();
		}

		@Override
		public String getTitle() {
			if (request.season == ALL_SEASONS)
				return super.getTitle();

			// add additional information to default title
			return String.format("%s - Season %d", super.getTitle(), request.season);
		}

		@Override
		public Icon getIcon() {
			return request.provider.getIcon();
		}

		@Override
		protected void configureSelectDialog(SelectDialog<SearchResult> selectDialog) {
			super.configureSelectDialog(selectDialog);
			selectDialog.getMessageLabel().setText("Select a Show:");
		}

	}

	protected static class EpisodeListTab extends TitledList<Episode> {

		public EpisodeListTab() {
			// initialize dnd and clipboard export handler for episode list
			EpisodeListExportHandler exportHandler = new EpisodeListExportHandler(this);
			setExportHandler(exportHandler);
			getTransferHandler().setClipboardHandler(exportHandler);

			// allow removal of episode list entries
			getRemoveAction().setEnabled(true);

			// remove borders
			listScrollPane.setBorder(null);
			setBorder(null);

			JPopupMenu popup = new JPopupMenu("Episodes");

			JMenu menu = new JMenu("Send to");
			for (PanelBuilder panel : PanelBuilder.episodeHandlerSequence()) {
				menu.add(newAction(panel.getName(), panel.getIcon(), evt -> {
					// switch to Rename panel and load episode data
					SwingEventBus.getInstance().post(new TargetTransferable(panel, exportHandler.export(this, false)));
				}));
			}

			popup.add(menu);
			popup.addSeparator();

			// group episodes in pairs for files that contain two episodes each (e.g. 11 minute episodes), or split them again
			popup.add(newAction("Double Episodes", ResourceManager.getIcon("action.join"), evt -> setEpisodes(groupDoubleEpisodes(getEpisodes()))));
			popup.add(newAction("Single Episodes", ResourceManager.getIcon("action.split"), evt -> setEpisodes(getEpisodes())));
			popup.addSeparator();

			popup.add(newAction("Copy", ResourceManager.getIcon("rename.action.copy"), evt -> {
				getTransferHandler().getClipboardHandler().exportToClipboard(this, Toolkit.getDefaultToolkit().getSystemClipboard(), TransferHandler.COPY);
			}));
			popup.add(new SaveAction(getExportHandler()));
			getListComponent().setComponentPopupMenu(popup);
		}

		/**
		 * @return all episodes, with multi-episodes split into single episodes
		 */
		private List<Episode> getEpisodes() {
			return getModel().stream().flatMap(e -> e instanceof MultiEpisode ? ((MultiEpisode) e).stream() : Stream.of(e)).collect(toList());
		}

		private void setEpisodes(List<Episode> episodes) {
			getModel().clear();
			getModel().addAll(episodes);
		}

		/**
		 * Pairs of consecutive regular episodes of the same season (e.g. S01E01-E02, S01E03-E04), specials and an odd
		 * last episode of a season stay single episodes
		 */
		protected static List<Episode> groupDoubleEpisodes(List<Episode> episodes) {
			List<Episode> grouped = new ArrayList<Episode>(episodes.size());
			Episode pending = null;

			for (Episode e : episodes) {
				if (e.getSpecial() != null || e.getEpisode() == null) {
					if (pending != null) {
						grouped.add(pending);
						pending = null;
					}
					grouped.add(e);
				} else if (pending != null && Objects.equals(pending.getSeason(), e.getSeason())) {
					grouped.add(new MultiEpisode(pending, e));
					pending = null;
				} else {
					if (pending != null) {
						grouped.add(pending);
					}
					pending = e;
				}
			}

			if (pending != null) {
				grouped.add(pending);
			}
			return grouped;
		}

	}

}
