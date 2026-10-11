package dev.davamu.filebotpro.ui.rename;

import static java.util.Collections.*;
import static java.util.Comparator.*;
import static java.util.stream.Collectors.*;
import static javax.swing.BorderFactory.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.MediaTypes.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.media.MediaDetection.*;
import static dev.davamu.filebotpro.similarity.CommonSequenceMatcher.*;
import static dev.davamu.filebotpro.similarity.Normalization.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.prefs.Preferences;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JLabel;

import dev.davamu.filebotpro.media.DiscTitles;
import dev.davamu.filebotpro.similarity.Match;
import dev.davamu.filebotpro.similarity.NameSimilarityMetric;
import dev.davamu.filebotpro.similarity.SimilarityMetric;
import dev.davamu.filebotpro.ui.SelectDialog;
import dev.davamu.filebotpro.util.FileUtilities.ParentFilter;
import dev.davamu.filebotpro.web.Movie;
import dev.davamu.filebotpro.web.MovieIdentificationService;
import dev.davamu.filebotpro.web.MoviePart;
import dev.davamu.filebotpro.web.SearchResult;
import dev.davamu.filebotpro.web.SortOrder;
import dev.davamu.filebotpro.web.TMDbClient;
import dev.davamu.filebotpro.web.ThumbnailProvider;
import dev.davamu.filebotpro.web.ThumbnailProvider.ResolutionVariant;

class MovieMatcher implements AutoCompleteMatcher {

	private MovieIdentificationService service;

	// remember user decisions and only bother user once
	private Set<AutoSelection> autoSelectionMode = EnumSet.noneOf(AutoSelection.class);
	private Map<String, Movie> selectionMemory = new TreeMap<String, Movie>(getLenientCollator(Locale.ENGLISH));
	private Map<String, String> inputMemory = new TreeMap<String, String>(getLenientCollator(Locale.ENGLISH));

	public MovieMatcher(MovieIdentificationService service) {
		this.service = service;
	}

	@Override
	public List<Match<File, ?>> match(Collection<File> files, boolean strict, SortOrder sortOrder, Locale locale, boolean autodetect, Component parent) throws Exception {
		if (files.isEmpty()) {
			return justFetchMovieInfo(locale, parent);
		}

		// ignore sample files
		List<File> fileset = autodetect ? filter(files, not(getClutterFileFilter())) : new ArrayList<File>(files);

		// handle movie files
		Set<File> movieFiles = new TreeSet<File>(filter(fileset, VIDEO_FILES));
		Set<File> nfoFiles = new TreeSet<File>(filter(fileset, NFO_FILES));

		List<File> orphanedFiles = new ArrayList<File>(filter(fileset, FILES));
		orphanedFiles.removeAll(movieFiles);
		orphanedFiles.removeAll(nfoFiles);

		Map<File, List<File>> derivatesByMovieFile = new HashMap<File, List<File>>();
		for (File movieFile : movieFiles) {
			derivatesByMovieFile.put(movieFile, new ArrayList<File>());
		}
		for (File file : orphanedFiles) {
			List<File> orphanParent = listPath(file);
			for (File movieFile : movieFiles) {
				if (orphanParent.contains(movieFile.getParentFile()) && isDerived(file, movieFile)) {
					derivatesByMovieFile.get(movieFile).add(file);
					break;
				}
			}
		}
		for (List<File> derivates : derivatesByMovieFile.values()) {
			orphanedFiles.removeAll(derivates);
		}

		// match movie hashes online
		Map<File, Movie> movieByFile = new TreeMap<File, Movie>();

		// collect useful nfo files even if they are not part of the selected fileset
		Set<File> effectiveNfoFileSet = new TreeSet<File>(nfoFiles);
		for (File dir : mapByFolder(movieFiles).keySet()) {
			effectiveNfoFileSet.addAll(getChildren(dir, NFO_FILES));
		}
		for (File dir : filter(fileset, FOLDERS)) {
			effectiveNfoFileSet.addAll(getChildren(dir, NFO_FILES));
		}

		for (File nfo : effectiveNfoFileSet) {
			try {
				Movie movie = grepMovie(nfo, service, locale);

				// ignore illegal nfos
				if (movie == null) {
					continue;
				}

				if (nfoFiles.contains(nfo)) {
					movieByFile.put(nfo, movie);
				}

				if (isDiskFolder(nfo.getParentFile())) {
					// special handling for disk folders
					for (File folder : fileset) {
						if (nfo.getParentFile().equals(folder)) {
							movieByFile.put(folder, movie);
						}
					}
				} else {
					// match movie info to movie files that match the nfo file name
					SortedSet<File> siblingMovieFiles = new TreeSet<File>(filter(movieFiles, new ParentFilter(nfo.getParentFile())));
					String baseName = stripReleaseInfo(getName(nfo)).toLowerCase();

					for (File movieFile : siblingMovieFiles) {
						if (!baseName.isEmpty() && stripReleaseInfo(getName(movieFile)).toLowerCase().startsWith(baseName)) {
							movieByFile.put(movieFile, movie);
						}
					}
				}
			} catch (Exception e) {
				debug.log(Level.WARNING, "Failed to grep IMDbID: " + nfo.getName(), e);
			}
		}

		// disc rips (e.g. title_t01.mkv by MakeMKV) of movies that can be identified by their disc titles (TheDiscDB)
		Map<File, Movie> discRips = new LinkedHashMap<File, Movie>(DiscTitles.matchMovies(movieFiles, movieByFile, locale));
		discRips.forEach(movieByFile::putIfAbsent);

		// collect files that will be matched one by one
		List<File> movieMatchFiles = new ArrayList<File>();
		movieMatchFiles.addAll(movieFiles);
		movieMatchFiles.addAll(nfoFiles);
		movieMatchFiles.addAll(filter(files, FOLDERS));
		movieMatchFiles.addAll(filter(orphanedFiles, SUBTITLE_FILES)); // run movie detection only on orphaned subtitle files

		// match remaining movies file by file in parallel
		ExecutorService workerThreadPool = Executors.newFixedThreadPool(getPreferredThreadPoolSize());
		try {
			List<Future<Map<File, List<Movie>>>> tasks = movieMatchFiles.stream().filter(f -> movieByFile.get(f) == null).map(f -> {
				return workerThreadPool.submit(() -> {
					List<Movie> options = detectMovieWithYear(f, service, locale, strict);

					// ignore files that cannot yield any acceptable matches (e.g. movie files without year in strict mode)
					if (options == null) {
						return (Map<File, List<Movie>>) EMPTY_MAP;
					}

					return singletonMap(f, options);
				});
			}).collect(toList());

			for (Future<Map<File, List<Movie>>> future : tasks) {
				Map<File, List<Movie>> detected;
				try {
					detected = future.get();
				} catch (java.util.concurrent.ExecutionException e) {
					// a failed lookup (e.g. network error) must not abort the files that can still be matched
					if (dev.davamu.filebotpro.util.ExceptionUtilities.findCause(e, CancellationException.class) != null) {
						throw e;
					}
					log.log(Level.WARNING, e, cause("Failed to match movie", e));
					continue;
				}

				for (Entry<File, List<Movie>> it : detected.entrySet()) {
					File file = it.getKey();
					List<Movie> options = it.getValue();

					// auto-select movie or ask user
					Movie movie = grabMovieName(file, options, strict, locale, autodetect, parent);

					// make sure to use language-specific movie object if possible
					movie = getLocalizedMovie(service, movie, locale);

					if (movie != null) {
						movieByFile.put(file, movie);
					}
				}
			}
		} finally {
			workerThreadPool.shutdownNow();
		}

		// main movie and extras of the disc rips of detected movies
		discRips.putAll(DiscTitles.matchMovies(filter(movieFiles, f -> !discRips.containsKey(f)), movieByFile, locale));

		// collect all File/MoviePart matches
		List<Match<File, ?>> matches = new ArrayList<Match<File, ?>>();

		// disc rips are never movie parts
		discRips.forEach((f, movie) -> {
			movieByFile.remove(f);
			matches.add(new Match<File, Movie>(f, movie.clone()));
			derivatesByMovieFile.getOrDefault(f, emptyList()).forEach(derivate -> matches.add(new Match<File, Movie>(derivate, movie.clone())));
		});

		// map movies to (possibly multiple) files (in natural order)
		Map<Movie, Set<File>> filesByMovie = movieByFile.entrySet().stream().collect(groupingBy(Entry::getValue, LinkedHashMap::new, mapping(Entry::getKey, toCollection(TreeSet::new))));

		filesByMovie.forEach((movie, fs) -> {
			groupByMediaCharacteristics(fs).forEach(moviePartFiles -> {
				// resolve movie parts
				for (int i = 0; i < moviePartFiles.size(); i++) {
					Movie moviePart = moviePartFiles.size() == 1 ? movie : new MoviePart(movie, i + 1, moviePartFiles.size());
					matches.add(new Match<File, Movie>(moviePartFiles.get(i), moviePart.clone()));

					// automatically add matches for derived files
					List<File> derivates = derivatesByMovieFile.get(moviePartFiles.get(i));
					if (derivates != null) {
						for (File derivate : derivates) {
							matches.add(new Match<File, Movie>(derivate, moviePart.clone()));
						}
					}
				}
			});
		});

		// restore original order
		matches.sort(comparing(Match::getValue, OriginalOrder.of(files)));

		return matches;
	}

	protected Movie grabMovieName(File movieFile, List<Movie> options, boolean strict, Locale locale, boolean autodetect, Component parent) throws Exception {
		// allow manual user input
		synchronized (selectionMemory) {
			// badly named files (e.g. 01.mkv) would be auto-ignored by selectMovie() so ask the user instead
			boolean badlyNamed = autodetect && options.size() > 1 && isBadlyNamed(movieFile, strict);

			if (!strict && (!autodetect || options.isEmpty() || badlyNamed) && !(autodetect && autoSelectionMode.size() > 0)) {
				String suggestion = options.isEmpty() ? stripReleaseInfo(getName(movieFile)) : options.iterator().next().getName();
				String input = inputMemory.get(suggestion);

				if (input == null || suggestion == null || suggestion.isEmpty()) {
					File movieFolder = guessMovieFolder(movieFile);
					input = showInputDialog(getQueryInputMessage("Please identify the following files:", "Enter movie name or id:", movieFile), suggestion != null && suggestion.length() > 0 ? suggestion : getName(movieFile), movieFolder == null ? movieFile.getName() : String.join(" / ", movieFolder.getName(), movieFile.getName()), parent);
					inputMemory.put(suggestion, input);
				}

				if (input != null && input.length() > 0) {
					options = searchMovie(input, locale);
					if (options.size() > 0) {
						return selectMovie(movieFile, strict, input, options, parent);
					}
				}
			}
		}

		return options.isEmpty() ? null : selectMovie(movieFile, strict, null, options, parent);
	}

	/**
	 * Neither file name nor folder name yield a usable query
	 */
	protected boolean isBadlyNamed(File movieFile, boolean strict) throws Exception {
		File movieFolder = guessMovieFolder(movieFile);
		return checkMovieStripReleaseInfo(movieFile, strict).length() < 2 && (movieFolder == null || checkMovieStripReleaseInfo(movieFolder, strict).length() < 2);
	}

	/**
	 * Search by name and lookup by id (e.g. tt0133093 for IMDb or 603 for TheMovieDB)
	 */
	protected List<Movie> searchMovie(String input, Locale locale) throws Exception {
		List<Movie> options = new ArrayList<Movie>(lookupMovie(input, locale));

		// IMDb ids are never movie names
		if (grepImdbId(input).isEmpty()) {
			for (Movie movie : service.searchMovie(input, locale)) {
				if (!options.contains(movie)) {
					options.add(movie);
				}
			}
		}

		return options;
	}

	protected List<Movie> lookupMovie(String input, Locale locale) {
		String query = input.trim();
		Movie id = null;

		if (query.matches("(?i)tt\\d{7,8}")) {
			id = new Movie(grepImdbId(query).get(0));
		} else if (query.matches("\\d{1,9}")) {
			// numeric ids are TheMovieDB ids for TheMovieDB and IMDb ids for OMDb
			int number = Integer.parseInt(query);
			id = service instanceof TMDbClient ? new Movie(null, new String[0], 0, 0, number, locale) : new Movie(number);
		}

		if (id != null) {
			try {
				Movie movie = service.getMovieDescriptor(id, locale);
				if (movie != null) {
					return singletonList(movie);
				}
			} catch (Exception e) {
				debug.finest(cause("Failed to lookup movie by id", e));
			}
		}

		return emptyList();
	}

	protected String getQueryInputMessage(String header, String message, File file) throws Exception {
		StringBuilder html = new StringBuilder(512);
		html.append("<html>");
		if (header != null) {
			html.append(escapeHTML(header)).append("<br>");
		}

		File path = getStructurePathTail(file);
		if (path == null) {
			path = getRelativePathTail(file, 3);
		}
		TextColorizer colorizer = new TextColorizer("<nobr>• ", "</nobr><br>");
		colorizer.colorizePath(html, path, file.isFile());

		html.append("<br>");
		if (message != null) {
			html.append(escapeHTML(message));
		}
		html.append("</html>");
		return html.toString();
	}

	protected Movie selectMovie(File movieFile, boolean strict, String userQuery, List<Movie> options, Component parent) throws Exception {
		// just auto-pick singleton results
		if (options.size() == 1) {
			return options.iterator().next();
		}

		// 1. movie by filename
		String fileQuery = userQuery != null ? userQuery : checkMovieStripReleaseInfo(movieFile, strict);

		// 2. movie by directory
		File movieFolder = guessMovieFolder(movieFile);
		String folderQuery = userQuery != null || movieFolder == null ? "" : checkMovieStripReleaseInfo(movieFolder, strict);

		// auto-ignore invalid files
		if (userQuery == null && fileQuery.length() < 2 && folderQuery.length() < 2) {
			return null;
		}

		// auto-select perfect match
		for (Movie movie : options) {
			String movieIdentifier = normalizePunctuation(movie.toString()).toLowerCase();
			if (fileQuery.toLowerCase().startsWith(movieIdentifier) || folderQuery.toLowerCase().startsWith(movieIdentifier)) {
				return movie;
			}
		}

		// auto-select most probable search result
		List<Movie> probableMatches = new LinkedList<Movie>();

		SimilarityMetric metric = new NameSimilarityMetric();
		float threshold = 0.9f;

		// find probable matches using name similarity >= 0.9
		for (Movie result : options) {
			float maxSimilarity = 0;
			for (String query : new String[] { fileQuery, folderQuery }) {
				for (String name : strict ? result.getEffectiveNames() : result.getEffectiveNamesWithoutYear()) {
					if (maxSimilarity >= threshold)
						continue;

					maxSimilarity = Math.max(maxSimilarity, metric.getSimilarity(query, name));
				}
			}
			if (maxSimilarity >= threshold) {
				probableMatches.add(result);
			}
		}

		// auto-select first and only probable search result
		if (probableMatches.size() == 1) {
			return probableMatches.get(0);
		}

		// if we haven't confirmed a match at this point then the file is probably badly named and should be ignored
		if (strict) {
			return null;
		}

		// prepare thumbnail images
		Function<Movie, Icon> thumbnail = thumbnail(options, parent);

		// show selection dialog on EDT
		Callable<Movie> showSelectDialog = () -> {
			String query = fileQuery.length() >= 2 || folderQuery.length() <= 2 ? fileQuery : folderQuery;
			JLabel header = new JLabel(getQueryInputMessage("Failed to identify some of the following files:", null, movieFile));
			header.setBorder(createCompoundBorder(createTitledBorder(""), createEmptyBorder(3, 3, 3, 3)));

			// multiple results have been found, user must select one
			SelectDialog<Movie> selectDialog = new SelectDialog<Movie>(parent, options, thumbnail, true, false, header);

			selectDialog.setTitle(service.getName());
			selectDialog.getMessageLabel().setText("<html>Select best match for \"<b>" + escapeHTML(query) + "</b>\":</html>");
			selectDialog.getCancelAction().putValue(Action.NAME, "Skip");
			selectDialog.pack();

			// show dialog
			selectDialog.restoreState(Preferences.userNodeForPackage(MovieMatcher.class));
			setOffsetLocation(selectDialog);
			selectDialog.setVisible(true);

			// remember dialog state
			selectDialog.saveState(Preferences.userNodeForPackage(MovieMatcher.class));

			// remember if we should auto-repeat the chosen action in the future
			if (selectDialog.getAutoRepeatCheckBox().isSelected() || selectDialog.getSelectedAction() == null) {
				autoSelectionMode.add(selectDialog.getSelectedValue() == null ? AutoSelection.Skip : AutoSelection.First);
			}

			if (selectDialog.getSelectedAction() == null) {
				throw new CancellationException();
			}

			// selected value or null if the dialog was canceled by the user
			return selectDialog.getSelectedValue();
		};

		// allow only one select dialog at a time
		synchronized (selectionMemory) {
			String selectionKey = fileQuery.length() >= 2 || folderQuery.length() <= 2 ? fileQuery : folderQuery;
			if (selectionMemory.containsKey(selectionKey)) {
				return selectionMemory.get(selectionKey);
			}

			// check auto-selection settings
			if (autoSelectionMode.contains(AutoSelection.First)) {
				return options.iterator().next();
			}
			if (autoSelectionMode.contains(AutoSelection.Skip)) {
				return null;
			}

			// allow only one select dialog at a time
			Movie userSelection = showInputDialog(showSelectDialog);

			// cache selected value
			selectionMemory.put(selectionKey, userSelection);
			return userSelection;
		}
	}

	private enum AutoSelection {
		First, Skip;
	}

	protected Function<Movie, Icon> thumbnail(List<Movie> options, Component parent) {
		if (service instanceof ThumbnailProvider) {
			try {
				Map<SearchResult, Icon> thumbnails = ((ThumbnailProvider) service).getThumbnails((List) options, ResolutionVariant.fromScaleFactor(parent));
				if (thumbnails.size() > 0) {
					return key -> thumbnails.getOrDefault(key, BlankThumbnail.BLANK_POSTER);
				}
			} catch (Exception e) {
				debug.log(Level.SEVERE, e, e::toString);
			}
		}
		return null;
	}

	public List<Match<File, ?>> justFetchMovieInfo(Locale locale, Component parent) throws Exception {
		// require user input
		String input = showInputDialog("Enter movie name or id:", "", "Fetch Movie Info", parent);

		List<Match<File, ?>> matches = new ArrayList<Match<File, ?>>();
		if (input != null && input.length() > 0) {
			// lookup by id or detect movie by name
			List<Movie> options = lookupMovie(input, locale);
			if (options.isEmpty()) {
				options = detectMovie(new File(input), service, locale, false);
			}

			for (Movie movie : options) {
				// make sure to use language-specific movie object if possible
				movie = getLocalizedMovie(service, movie, locale);

				if (movie != null) {
					matches.add(new Match<File, Movie>(null, movie));
				}
			}
		}
		return matches;
	}

}
