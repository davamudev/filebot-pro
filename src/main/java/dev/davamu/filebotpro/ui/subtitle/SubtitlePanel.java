package dev.davamu.filebotpro.ui.subtitle;

import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.ui.LanguageComboBoxModel.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.Cursor;
import java.awt.Dialog.ModalityType;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.border.TitledBorder;

import com.google.common.eventbus.Subscribe;

import dev.davamu.filebotpro.Language;
import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.Settings;
import dev.davamu.filebotpro.WebServices;
import dev.davamu.filebotpro.media.MediaDetection;
import dev.davamu.filebotpro.ui.AbstractSearchPanel;
import dev.davamu.filebotpro.ui.LanguageComboBox;
import dev.davamu.filebotpro.ui.SelectDialog;
import dev.davamu.filebotpro.ui.subtitle.SubtitleDropTarget.DropAction;
import dev.davamu.filebotpro.ui.transfer.FileTransferable;
import dev.davamu.filebotpro.util.ui.LabelProvider;
import dev.davamu.filebotpro.util.ui.LinkButton;
import dev.davamu.filebotpro.util.ui.SimpleLabelProvider;
import dev.davamu.filebotpro.web.OpenSubtitlesClient;
import dev.davamu.filebotpro.web.SearchResult;
import dev.davamu.filebotpro.web.SubtitleDescriptor;
import dev.davamu.filebotpro.web.SubtitleProvider;
import dev.davamu.filebotpro.web.SubtitleSearchResult;
import dev.davamu.filebotpro.web.VideoHashSubtitleService;
import net.miginfocom.swing.MigLayout;

public class SubtitlePanel extends AbstractSearchPanel<SubtitleProvider, SubtitlePackage> {

	private LanguageComboBox languageComboBox = new LanguageComboBox(ALL_LANGUAGES, getSettings());

	public SubtitlePanel() {
		historyPanel.setColumnHeader(0, "Search");
		historyPanel.setColumnHeader(1, "Number of Subtitles");

		// add after text field
		add(languageComboBox, "gap indent, sgy button", 1);
		add(createImageButton(setUserAction), "gap rel, sgy button", 2);

		// add at the top right corner
		add(downloadDropTarget, "width 54px!, height 54px!, gap before unrel, gap after 10px", 4);
	}

	@Subscribe
	public void handle(Transferable transferable) throws Exception {
		SubtitleDropTarget target = downloadDropTarget;
		List<File> files = FileTransferable.getFilesFromTransferable(transferable);

		if (files != null && files.size() > 0 && target.getDropAction(files) != DropAction.Cancel) {
			target.handleDrop(files);
		}
	}

	private final SubtitleDropTarget downloadDropTarget = new SubtitleDropTarget.Download() {

		public Locale getLocale() {
			return languageComboBox.getModel().getSelectedItem() == ALL_LANGUAGES ? Locale.ROOT : languageComboBox.getModel().getSelectedItem().getLocale();
		}

		@Override
		public VideoHashSubtitleService[] getVideoHashSubtitleServices() {
			return WebServices.getVideoHashSubtitleServices(getLocale());
		}

		@Override
		public SubtitleProvider[] getSubtitleProviders() {
			return WebServices.getSubtitleProviders(getLocale());
		}

		@Override
		public OpenSubtitlesClient getSubtitleService() {
			return WebServices.OpenSubtitles;
		};

		@Override
		public Locale getQueryLanguage() {
			// use currently selected language for drop target
			return languageComboBox.getModel().getSelectedItem() == ALL_LANGUAGES ? null : languageComboBox.getModel().getSelectedItem().getLocale();
		}
	};

	@Override
	protected Collection<String> getHistory(SubtitleProvider engine) throws Exception {
		List<String> names = new ArrayList<String>();
		for (SubtitleSearchResult it : MediaDetection.releaseInfo.getOpenSubtitlesIndex()) {
			names.add(it.toString());
		}
		return names;
	};

	@Override
	protected SubtitleProvider[] getSearchEngines() {
		return WebServices.getSubtitleProviders(getLocale());
	}

	@Override
	protected LabelProvider<SubtitleProvider> getSearchEngineLabelProvider() {
		return SimpleLabelProvider.forClass(SubtitleProvider.class);
	}

	@Override
	protected Settings getSettings() {
		return Settings.forPackage(SubtitlePanel.class);
	}

	@Override
	protected SubtitleRequestProcessor createRequestProcessor() {
		SubtitleProvider provider = searchTextField.getSelectButton().getSelectedValue();

		// parse query
		String query = searchTextField.getText();
		int season = seasonFilter.match(query);
		query = seasonFilter.remove(query).trim();
		int episode = episodeFilter.match(query);
		query = episodeFilter.remove(query).trim();

		Language language = languageComboBox.getModel().getSelectedItem();
		return new SubtitleRequestProcessor(new SubtitleRequest(provider, query, season, episode, language));
	}

	private final QueryFilter<Integer> seasonFilter = new QueryFilter<Integer>("season", s -> s == null ? -1 : Integer.parseInt(s));
	private final QueryFilter<Integer> episodeFilter = new QueryFilter<Integer>("episode", s -> s == null ? -1 : Integer.parseInt(s));

	protected static class QueryFilter<T> {

		private final Pattern pattern;
		private final Function<String, T> parser;

		public QueryFilter(String key, Function<String, T> parser) {
			this.pattern = Pattern.compile("(?:" + key + "):(\\w+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
			this.parser = parser;
		}

		public T match(String s) {
			Matcher m = pattern.matcher(s);
			if (m.find()) {
				return parser.apply(m.group(m.groupCount()));
			}
			return parser.apply(null);
		}

		public String remove(String s) {
			return pattern.matcher(s).replaceAll("");
		}
	}

	protected static class SubtitleRequest extends Request {

		private final SubtitleProvider provider;
		private final Language language;
		private final int season;
		private final int episode;

		public SubtitleRequest(SubtitleProvider provider, String searchText, int season, int episode, Language language) {
			super(searchText);
			this.season = season;
			this.episode = episode;
			this.provider = provider;
			this.language = language;
		}

		public SubtitleProvider getProvider() {
			return provider;
		}

		public Locale getLanguage() {
			return language == ALL_LANGUAGES ? null : language.getLocale();
		}

		public int[][] getEpisodeFilter() {
			return season >= 0 && episode >= 0 ? new int[][] { new int[] { season, episode } } : season >= 0 ? new int[][] { new int[] { season, -1 } } : null;
		}
	}

	protected static class SubtitleRequestProcessor extends RequestProcessor<SubtitleRequest, SubtitlePackage> {

		public SubtitleRequestProcessor(SubtitleRequest request) {
			super(request, new SubtitleDownloadComponent());
		}

		@Override
		public Collection<SubtitleSearchResult> search() throws Exception {
			return request.getProvider().search(request.getSearchText());
		}

		@Override
		public SubtitleSearchResult getSearchResult() {
			return (SubtitleSearchResult) super.getSearchResult();
		}

		private URI link;

		@Override
		public Collection<SubtitlePackage> fetch() throws Exception {
			List<SubtitlePackage> packages = new ArrayList<SubtitlePackage>();

			for (SubtitleDescriptor subtitle : request.getProvider().getSubtitleList(getSearchResult(), request.getEpisodeFilter(), request.getLanguage())) {
				packages.add(new SubtitlePackage(request.getProvider(), subtitle));
			}

			// the link may require a web request, so resolve it in the background as well
			link = request.getProvider().getSubtitleListLink(getSearchResult(), request.getLanguage());

			return packages;
		}

		@Override
		public URI getLink() {
			return link;
		}

		@Override
		public void process(Collection<SubtitlePackage> subtitles) {
			getComponent().setLanguageVisible(request.getLanguage() == null);
			getComponent().getPackageModel().addAll(subtitles);
		}

		@Override
		public SubtitleDownloadComponent getComponent() {
			return (SubtitleDownloadComponent) super.getComponent();
		}

		@Override
		public String getStatusMessage(Collection<SubtitlePackage> result) {
			return (result.isEmpty()) ? "No subtitles found" : String.format("%d subtitles", result.size());
		}

		@Override
		public Icon getIcon() {
			return request.provider.getIcon();
		}

		@Override
		protected void configureSelectDialog(SelectDialog<SearchResult> selectDialog) {
			super.configureSelectDialog(selectDialog);
			selectDialog.getMessageLabel().setText("Select a Show / Movie:");
		}

	}

	protected final Action setUserAction = new AbstractAction("Login", ResourceManager.getIcon("action.user")) {

		@Override
		public void actionPerformed(ActionEvent evt) {
			final JDialog authPanel = new JDialog(getWindow(SubtitlePanel.this), ModalityType.APPLICATION_MODAL);
			authPanel.setTitle("Login");

			JPanel osdbGroup = new JPanel(new MigLayout("fill, insets panel"));
			osdbGroup.setBorder(new TitledBorder("OpenSubtitles"));
			osdbGroup.add(new JLabel("Username:"), "gap rel");
			final JTextField osdbUser = new JTextField(12);
			osdbGroup.add(osdbUser, "growx, wrap rel");

			osdbGroup.add(new JLabel("Password:"), "gap rel");
			final JPasswordField osdbPass = new JPasswordField(12);
			osdbGroup.add(osdbPass, "growx, wrap unrel");

			// restore values
			String[] osdbAuth = WebServices.getLogin(WebServices.LOGIN_OPENSUBTITLES);
			osdbUser.setText(osdbAuth[0]);
			osdbPass.setText(osdbAuth[1]);

			if (osdbUser.getText().isEmpty()) {
				osdbGroup.add(new LinkButton("Register Account", "Register to download more than 5 subtitles per day", WebServices.OpenSubtitles.getIcon(), URI.create(OpenSubtitlesClient.WEBSITE + "/en/users/sign_up")), "spanx 2, tag left");
			} else {
				osdbGroup.add(new LinkButton("Upgrade Account", "Upgrade to increase your download quota", WebServices.OpenSubtitles.getIcon(), URI.create(OpenSubtitlesClient.WEBSITE + "/en/support_us")), "spanx 2, tag left");
			}

			// use the content pane, the root pane also holds the window decorations (title bar)
			JComponent container = (JComponent) authPanel.getContentPane();
			container.setLayout(new MigLayout("fill, insets dialog"));

			container.add(osdbGroup, "growx, wrap");

			Action ok = new AbstractAction("OK", ResourceManager.getIcon("dialog.continue")) {

				@Override
				public void actionPerformed(ActionEvent evt) {
					authPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
					boolean approved = true;

					try {
						if (osdbUser.getText().length() > 0 && osdbPass.getPassword().length > 0) {
							// check login details and show the download quota of the user
							WebServices.OpenSubtitles.setUser(osdbUser.getText(), new String(osdbPass.getPassword()));
							Map<?, ?> user = WebServices.OpenSubtitles.getUserInfo();
							log.log(Level.INFO, String.format("OpenSubtitles: %s of %s downloads left today (%s).", user.get("remaining_downloads"), user.get("allowed_downloads"), user.get("level")));
						} else if (osdbUser.getText().isEmpty()) {
							WebServices.setLogin(WebServices.LOGIN_OPENSUBTITLES, null, null); // delete login details
						}
					} catch (Exception e) {
						log.log(Level.WARNING, "OpenSubtitles: " + e.getMessage());
						approved = false;

						// keep using the stored login details
						String[] stored = WebServices.getLogin(WebServices.LOGIN_OPENSUBTITLES);
						WebServices.OpenSubtitles.setUser(stored[0], stored[1]);
					}

					authPanel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
					if (approved) {
						WebServices.setLogin(WebServices.LOGIN_OPENSUBTITLES, osdbUser.getText(), new String(osdbPass.getPassword()));
						authPanel.setVisible(false);
					}
				}
			};
			Action cancel = new AbstractAction("Cancel", ResourceManager.getIcon("dialog.cancel")) {

				@Override
				public void actionPerformed(ActionEvent evt) {
					authPanel.setVisible(false);
				}
			};
			JButton okButton = accent(new JButton(ok));
			container.add(new JButton(cancel), "tag cancel, split 2");
			container.add(okButton, "tag ok");

			authPanel.getRootPane().setDefaultButton(okButton);
			installAction(container, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), cancel);

			authPanel.pack();
			setOffsetLocation(authPanel);
			authPanel.setVisible(true);
		}
	};

}
