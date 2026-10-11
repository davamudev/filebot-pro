package dev.davamu.filebotpro.ui.rename;

import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;

import javax.swing.AbstractAction;

import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.similarity.EpisodeMetrics;
import dev.davamu.filebotpro.similarity.Match;
import dev.davamu.filebotpro.similarity.Matcher;
import dev.davamu.filebotpro.util.ui.ProgressMonitor;

class MatchAction extends AbstractAction {

	private final RenameModel model;

	public MatchAction(RenameModel model) {
		this.model = model;

		// initialize with default values
		setMatchMode(false);
	}

	public void setMatchMode(boolean strict) {
		putValue(NAME, "Match");
		putValue(SMALL_ICON, ResourceManager.getIcon(strict ? "action.match.strict" : "action.match"));
	}

	@Override
	public void actionPerformed(ActionEvent evt) {
		if (model.names().isEmpty() || model.files().isEmpty()) {
			return;
		}

		withWaitCursor(evt.getSource(), () -> {
			try {
				Matcher<Object, File> matcher = new Matcher<Object, File>(model.values(), model.candidates(), false, new EpisodeMetrics().matchFileSequence());
				List<Match<Object, File>> matches = ProgressMonitor.runTask("Match", "Finding optimal alignment. This may take a while.", (message, progress, cancelled) -> {
					message.accept(String.format("Checking %d combinations...", matcher.remainingCandidates().size() * matcher.remainingValues().size()));
					return matcher.match();
				}).get();

				// put new data into model
				model.clear();
				model.addAll(matches);

				// insert objects that could not be matched at the end of the model
				model.addAll(matcher.remainingValues(), matcher.remainingCandidates());
			} catch (CancellationException e) {
				debug.finest(e::toString);
			} catch (Throwable e) {
				log.log(Level.WARNING, e.getMessage(), e);
			}
		});
	}

}
