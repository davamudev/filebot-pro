package dev.davamu.filebotpro.cli;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.MediaTypes.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.WebServices.*;
import static dev.davamu.filebotpro.hash.VerificationUtilities.*;
import static dev.davamu.filebotpro.media.MediaDetection.*;
import static dev.davamu.filebotpro.media.XattrMetaInfo.*;
import static dev.davamu.filebotpro.similarity.Normalization.*;
import static dev.davamu.filebotpro.subtitle.SubtitleUtilities.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Comparator;
import java.util.function.ToDoubleFunction;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import groovy.lang.Closure;

import dev.davamu.filebotpro.CacheManager;
import dev.davamu.filebotpro.History;
import dev.davamu.filebotpro.HistorySpooler;
import dev.davamu.filebotpro.Language;
import dev.davamu.filebotpro.RenameAction;
import dev.davamu.filebotpro.StandardRenameAction;
import dev.davamu.filebotpro.archive.Archive;
import dev.davamu.filebotpro.archive.FileMapper;
import dev.davamu.filebotpro.format.ExpressionFileFormat;
import dev.davamu.filebotpro.format.ExpressionFilter;
import dev.davamu.filebotpro.format.ExpressionFormat;
import dev.davamu.filebotpro.format.ExpressionMapper;
import dev.davamu.filebotpro.format.MediaBindingBean;
import dev.davamu.filebotpro.hash.HashType;
import dev.davamu.filebotpro.hash.VerificationFileReader;
import dev.davamu.filebotpro.hash.VerificationFileWriter;
import dev.davamu.filebotpro.media.AutoDetection;
import dev.davamu.filebotpro.media.AutoDetection.Group;
import dev.davamu.filebotpro.media.AutoDetection.Type;
import dev.davamu.filebotpro.media.CompanionFiles;
import dev.davamu.filebotpro.media.DiscTitles;
import dev.davamu.filebotpro.media.LocalDatasource;
import dev.davamu.filebotpro.media.VideoQuality;
import dev.davamu.filebotpro.platform.windows.SessionEndHandler;
import dev.davamu.filebotpro.similarity.CommonSequenceMatcher;
import dev.davamu.filebotpro.similarity.EpisodeMatcher;
import dev.davamu.filebotpro.similarity.Match;
import dev.davamu.filebotpro.similarity.NameSimilarityMetric;
import dev.davamu.filebotpro.subtitle.SubtitleFormat;
import dev.davamu.filebotpro.subtitle.SubtitleNaming;
import dev.davamu.filebotpro.util.EntryList;
import dev.davamu.filebotpro.util.FileUtilities.ParentFilter;
import dev.davamu.filebotpro.vfs.FileInfo;
import dev.davamu.filebotpro.vfs.MemoryFile;
import dev.davamu.filebotpro.vfs.SimpleFileInfo;
import dev.davamu.filebotpro.web.AudioTrack;
import dev.davamu.filebotpro.web.Datasource;
import dev.davamu.filebotpro.web.Episode;
import dev.davamu.filebotpro.web.EpisodeListProvider;
import dev.davamu.filebotpro.web.MappedEpisode;
import dev.davamu.filebotpro.web.Movie;
import dev.davamu.filebotpro.web.MovieIdentificationService;
import dev.davamu.filebotpro.web.MoviePart;
import dev.davamu.filebotpro.web.MusicIdentificationService;
import dev.davamu.filebotpro.web.OpenSubtitlesClient;
import dev.davamu.filebotpro.web.SearchResult;
import dev.davamu.filebotpro.web.SortOrder;
import dev.davamu.filebotpro.web.SubtitleDescriptor;
import dev.davamu.filebotpro.web.SubtitleProvider;
import dev.davamu.filebotpro.web.VideoHashSubtitleService;

public class CmdlineOperations implements CmdlineInterface {

	@Override
	public List<File> rename(Collection<File> files, Datasource db, String query, SortOrder order, Locale locale, ExpressionFilter filter, ExpressionMapper mapper, boolean strict, ExpressionFileFormat format, File output, RenameAction action, ConflictAction conflict, ExecCommand exec) throws Exception {
		// movie mode
		if (db instanceof MovieIdentificationService) {
			return renameMovie(files, action, conflict, output, format, (MovieIdentificationService) db, query, filter, locale, strict, exec);
		}

		// series mode
		if (db instanceof EpisodeListProvider) {
			return renameSeries(files, action, conflict, output, format, (EpisodeListProvider) db, query, order, filter, mapper, locale, strict, exec);
		}

		// music mode
		if (db instanceof MusicIdentificationService) {
			return renameMusic(files, action, conflict, output, format, singletonList((MusicIdentificationService) db), exec);
		}

		// photo / xattr / plain file mode
		if (db instanceof LocalDatasource) {
			return renameFiles(files, action, conflict, output, format, (LocalDatasource) db, filter, strict, exec);
		}

		// auto-detect mode for each fileset
		AutoDetection auto = new AutoDetection(files, false, locale);
		List<File> results = new ArrayList<File>();

		for (Entry<Group, Set<File>> it : auto.group().entrySet()) {
			if (it.getKey().types().length == 1) {
				for (Type key : it.getKey().types()) {
					switch (key) {
					case Movie:
						results.addAll(renameMovie(it.getValue(), action, conflict, output, format, TheMovieDB, query, filter, locale, strict, exec));
						break;
					case Series:
						results.addAll(renameSeries(it.getValue(), action, conflict, output, format, TheTVDB, query, order, filter, mapper, locale, strict, exec));
						break;
					case Anime:
						results.addAll(renameSeries(it.getValue(), action, conflict, output, format, TheTVDB, query, SortOrder.Absolute, filter, mapper, locale, strict, exec));
						break;
					case Music:
						results.addAll(renameMusic(it.getValue(), action, conflict, output, format, asList(MediaInfoID3, AcoustID), exec)); // prefer existing ID3 tags and use acoustid only when necessary
						break;
					}
				}
			} else {
				debug.warning(format("Failed to process group: %s => %s", it.getKey(), it.getValue()));
			}
		}

		if (results.isEmpty()) {
			throw new CmdlineException("Failed to identify or process any files");
		}

		return results;
	}

	@Override
	public List<File> renameLinear(List<File> files, EpisodeListProvider db, String query, SortOrder order, Locale locale, ExpressionFilter filter, ExpressionMapper mapper, ExpressionFileFormat format, File output, RenameAction action, ConflictAction conflict, ExecCommand exec) throws Exception {
		// match files and episodes in linear order
		List<Episode> episodes = fetchEpisodeList(db, query, filter, mapper, order, locale, false);

		// the mapper only changes the order, the new names are always formatted with the original episode data (same as -rename)
		List<Match<File, ?>> matches = new ArrayList<Match<File, ?>>();
		for (int i = 0; i < files.size() && i < episodes.size(); i++) {
			matches.add(unmap(new Match<File, Episode>(files.get(i), episodes.get(i))));
		}

		// rename episodes
		return renameAll(formatMatches(matches, format, output), action, conflict, matches, exec);
	}

	@Override
	public List<File> rename(Map<File, File> renameMap, RenameAction renameAction, ConflictAction conflict) throws Exception {
		// generic rename function that can be passed any set of files
		return renameAll(renameMap, renameAction, conflict, null, null);
	}

	public List<File> renameSeries(Collection<File> files, RenameAction renameAction, ConflictAction conflictAction, File outputDir, ExpressionFileFormat format, EpisodeListProvider db, String query, SortOrder sortOrder, ExpressionFilter filter, ExpressionMapper mapper, Locale locale, boolean strict, ExecCommand exec) throws Exception {
		log.config(format("Rename episodes using [%s] with [%s]", db.getName(), db.vetoRequestParameter(sortOrder)));

		// ignore sample files
		List<File> fileset = sortByUniquePath(filter(files, not(getClutterFileFilter())));

		List<File> mediaFiles = filter(fileset, VIDEO_FILES, SUBTITLE_FILES);
		if (mediaFiles.isEmpty()) {
			throw new CmdlineException("No media files: " + files);
		}

		// similarity metrics for matching
		List<Match<File, ?>> matches = new ArrayList<Match<File, ?>>();

		// auto-determine optimal batch sets
		for (Entry<Set<File>, Set<String>> sameSeriesGroup : mapSeriesNamesByFiles(mediaFiles, locale, db == AniDB).entrySet()) {
			List<List<File>> batchSets = new ArrayList<List<File>>();

			if (sameSeriesGroup.getValue() != null && sameSeriesGroup.getValue().size() > 0) {
				// handle series name batch set all at once
				batchSets.add(new ArrayList<File>(sameSeriesGroup.getKey()));
			} else {
				// these files don't seem to belong to any series -> handle folder per folder
				batchSets.addAll(mapByFolder(sameSeriesGroup.getKey()).values());
			}

			for (List<File> batch : batchSets) {
				// fetch episode data
				List<Episode> episodes;

				if (query == null) {
					Collection<String> seriesNames = detectSeriesNames(batch, db == AniDB, locale); // detect series name by common word sequence
					log.config("Auto-detected query: " + seriesNames);

					if (seriesNames.size() == 0) {
						log.warning("Failed to detect query for files: " + batch);
						continue;
					}

					if (strict && seriesNames.size() > 1) {
						throw new CmdlineException("Multiple queries: Processing multiple shows at once requires -non-strict matching: " + seriesNames);
					}

					episodes = fetchEpisodeSet(db, seriesNames, sortOrder, locale, strict, 5); // consider episodes of up to N search results for each query
				} else {
					Set<Episode> episodeSet = new LinkedHashSet<Episode>();
					for (String q : splitQuery(query)) {
						Integer id = getSeriesID(q);
						if (id != null) {
							episodeSet.addAll(db.getEpisodeList(id, sortOrder, locale));
						} else {
							episodeSet.addAll(fetchEpisodeSet(db, singleton(q), sortOrder, locale, false, 1)); // use --q option and pick first result
						}
					}
					episodes = new ArrayList<Episode>(episodeSet);
				}

				if (episodes.isEmpty()) {
					continue;
				}

				// filter episodes and apply custom mappings
				episodes = applyExpressionFilter(episodes, filter);
				episodes = applyEpisodeExpressionMapper(episodes, mapper);

				// disc rips (e.g. title_t01.mkv by MakeMKV) via the episode titles of TheDiscDB
				List<File> batchFiles = filter(batch, VIDEO_FILES, SUBTITLE_FILES);
				List<Match<File, ?>> discMatches = DiscTitles.matchEpisodes(batchFiles, episodes, locale);
				discMatches.stream().map(this::unmap).forEach(matches::add);

				Set<File> discFiles = discMatches.stream().map(Match::getValue).collect(toSet());
				batchFiles = filter(batchFiles, f -> !discFiles.contains(f));

				for (List<File> filesPerType : mapByMediaExtension(batchFiles).values()) {
					matchEpisodes(filesPerType, episodes, strict).stream().map(this::unmap).forEach(matches::add);
				}
			}
		}

		if (matches.isEmpty()) {
			throw new CmdlineException("Failed to match files to episode data");
		}

		// handle derived files
		List<Match<File, ?>> derivateMatches = new ArrayList<Match<File, ?>>();
		SortedSet<File> derivateFiles = new TreeSet<File>(fileset);
		derivateFiles.removeAll(mediaFiles);

		for (File file : derivateFiles) {
			for (Match<File, ?> match : matches) {
				if (file.getPath().startsWith(match.getValue().getParentFile().getPath()) && isDerived(file, match.getValue()) && match.getCandidate() instanceof Episode) {
					derivateMatches.add(new Match<File, Object>(file, ((Episode) match.getCandidate()).clone()));
					break;
				}
			}
		}

		// add matches from other files that are linked via filenames
		matches.addAll(derivateMatches);

		// rename episodes
		return renameAll(formatMatches(matches, format, outputDir), renameAction, conflictAction, matches, exec);
	}

	private Match<File, ?> unmap(Match<File, ?> match) {
		// add matches and unmap mapped episodes
		if (match.getCandidate() instanceof MappedEpisode) {
			MappedEpisode mapping = (MappedEpisode) match.getCandidate();
			log.fine(format("Reverse Map [%s] to [%s]", mapping.getMapping(), mapping.getOriginal()));
			return new Match<File, Episode>(match.getValue(), mapping.getOriginal());
		}
		return match;
	}

	private List<Match<File, Object>> matchEpisodes(Collection<File> files, Collection<Episode> episodes, boolean strict) throws Exception {
		// always use strict fail-fast matcher
		EpisodeMatcher matcher = new EpisodeMatcher(files, episodes, strict);
		List<Match<File, Object>> matches = matcher.match();

		for (File failedMatch : matcher.remainingValues()) {
			log.warning("No matching episode: " + failedMatch.getName());
		}

		// in non-strict mode just pass back results as we got it from the matcher
		if (!strict) {
			return matches;
		}

		// in strict mode sanity check the result and only pass back good matches
		List<Match<File, Object>> validMatches = new ArrayList<Match<File, Object>>();
		for (Match<File, Object> it : matches) {
			if (isEpisodeNumberMatch(it.getValue(), (Episode) it.getCandidate())) {
				validMatches.add(it);
			}
		}
		return validMatches;
	}

	private List<Episode> fetchEpisodeSet(EpisodeListProvider db, Collection<String> names, SortOrder sortOrder, Locale locale, boolean strict, int limit) throws Exception {
		Set<SearchResult> shows = new LinkedHashSet<SearchResult>();
		Set<Episode> episodes = new LinkedHashSet<Episode>();

		// detect series names and create episode list fetch tasks
		for (String query : names) {
			List<SearchResult> results = db.search(query, locale);

			// select search result
			if (results.size() > 0) {
				List<SearchResult> selectedSearchResults = selectSearchResult(query, results, true, true, strict, limit);

				if (selectedSearchResults != null) {
					for (SearchResult it : selectedSearchResults) {
						if (shows.add(it)) {
							try {
								log.fine(format("Fetching episode data for [%s]", it.getName()));
								episodes.addAll(db.getEpisodeList(it, sortOrder, locale));
							} catch (IOException e) {
								throw new CmdlineException(String.format("Failed to fetch episode data for [%s]: %s", it, e.getMessage()), e);
							}
						}
					}
				}
			}
		}

		if (episodes.isEmpty()) {
			log.warning("Failed to fetch episode data: " + names);
		}

		return new ArrayList<Episode>(episodes);
	}

	public List<File> renameMovie(Collection<File> files, RenameAction renameAction, ConflictAction conflictAction, File outputDir, ExpressionFileFormat format, MovieIdentificationService service, String query, ExpressionFilter filter, Locale locale, boolean strict, ExecCommand exec) throws Exception {
		log.config(format("Rename movies using [%s]", service.getName()));

		// ignore sample files
		List<File> fileset = sortByUniquePath(filter(files, not(getClutterFileFilter())));

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
		List<Movie> queryOptions = null;
		if (query == null) {
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
					log.log(Level.WARNING, "Failed to grep IMDbID: " + nfo.getName(), e);
				}
			}

			// lookup movies by file hash (e.g. --db OpenSubtitles)
			if (service instanceof OpenSubtitlesClient) {
				try {
					List<File> remaining = movieFiles.stream().filter(f -> !movieByFile.containsKey(f)).collect(toList());
					((OpenSubtitlesClient) service).getMovieDescriptors(remaining, locale).forEach((f, movie) -> {
						if (movie != null) {
							log.finest(format("Movie hash match: %s => %s", f.getName(), movie));
							movieByFile.put(f, movie);
						}
					});
				} catch (Exception e) {
					// continue with file name matching
					log.warning(format("Failed to lookup movie hashes: %s", e.getMessage()));
				}
			}
		} else {
			log.fine(format("Looking up movie by query [%s]", query));
			List<Movie> results = searchMovieByQuery(service, query, locale);
			List<Movie> options = applyExpressionFilter(results, filter);
			if (options.isEmpty()) {
				throw new CmdlineException("Failed to find a valid match: " + results);
			}

			if (splitQuery(query).size() == 1) {
				// force all mappings
				Movie movie = selectMovie(query, options);
				for (File file : files) {
					movieByFile.put(file, movie);
				}
			} else {
				// multiple queries: match each file against the movies of all queries
				queryOptions = options;
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

		// sanity check that we have something to do
		if (fileset.isEmpty() || movieMatchFiles.isEmpty()) {
			throw new CmdlineException("No media files: " + files);
		}

		// map movies to (possibly multiple) files (in natural order)
		Map<Movie, SortedSet<File>> filesByMovie = new HashMap<Movie, SortedSet<File>>();

		// map all files by movie
		for (File file : movieMatchFiles) {
			Movie movie = movieByFile.get(file);

			// multiple queries: pick the movie that is most similar to the file or folder name
			if (movie == null && queryOptions != null) {
				movie = selectMovieByName(file, queryOptions);
				log.fine(format("Select [%s] for [%s]", movie, file.getName()));
				movie = getLocalizedMovie(service, movie, locale);
			}

			// unknown hash, try via imdb id from nfo file
			if (movie == null) {
				log.fine(format("Auto-detect movie from context: [%s]", file));
				List<Movie> options = detectMovieWithYear(file, service, locale, strict);

				// ignore files that cannot yield any acceptable matches (e.g. movie files without year in strict mode)
				if (options == null) {
					log.warning(format("Skip [%s]: no year in file or folder name (strict mode)", file));
					continue;
				}

				// apply filter if defined
				options = applyExpressionFilter(options, filter);

				if (options.isEmpty()) {
					log.warning(format("Skip [%s]: no movie found (use --q to specify the movie)", file));
				}

				// reduce options to perfect matches if possible
				List<Movie> perfectMatches = matchMovieByWordSequence(getName(file), options, 0);

				// narrow down options if possible
				if (perfectMatches.size() > 0) {
					options = perfectMatches;
				}

				try {
					// select first element if matches are reliable
					if (options.size() > 0) {
						movie = selectMovie(checkMovieStripReleaseInfo(file, strict), options);

						// make sure to get the language-specific movie object for the selected option
						movie = getLocalizedMovie(service, movie, locale);
					}
				} catch (Exception e) {
					log.warning(cause(e));
				}
			}

			// check if we managed to lookup the movie descriptor
			if (movie != null) {
				// add to file list for movie
				filesByMovie.computeIfAbsent(movie, k -> new TreeSet<File>()).add(file);
			}
		}

		// main movie and extras of the disc rips of detected movies
		Map<File, Movie> detected = new HashMap<File, Movie>();
		filesByMovie.forEach((movie, fs) -> fs.forEach(f -> detected.put(f, movie)));
		discRips.putAll(DiscTitles.matchMovies(filter(movieFiles, f -> !discRips.containsKey(f)), detected, locale));

		// collect all File/MoviePart matches
		List<Match<File, ?>> matches = new ArrayList<Match<File, ?>>();

		// disc rips are never movie parts
		discRips.forEach((f, movie) -> {
			filesByMovie.values().forEach(fs -> fs.remove(f));
			matches.add(new Match<File, Movie>(f, movie.clone()));
			derivatesByMovieFile.getOrDefault(f, emptyList()).forEach(derivate -> matches.add(new Match<File, Movie>(derivate, movie.clone())));
		});
		filesByMovie.values().removeIf(Set::isEmpty);

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

		// rename movies
		return renameAll(formatMatches(matches, format, outputDir), renameAction, conflictAction, matches, exec);
	}

	protected Movie selectMovieByName(File file, List<Movie> options) throws Exception {
		NameSimilarityMetric metric = new NameSimilarityMetric();
		List<String> names = Stream.of(file, guessMovieFolder(file)).filter(Objects::nonNull).map(f -> stripReleaseInfo(getName(f))).filter(s -> s.length() > 0).collect(toList());

		ToDoubleFunction<Movie> similarity = m -> names.stream().flatMap(n -> m.getEffectiveNamesWithoutYear().stream().map(it -> metric.getSimilarity(n, it))).mapToDouble(Float::doubleValue).max().orElse(0);
		return options.stream().max(Comparator.comparingDouble(similarity)).orElse(null);
	}

	protected Movie selectMovie(String query, Collection<Movie> options) throws Exception {
		query = normalizePunctuation(query).toLowerCase();

		// auto-select perfect match
		for (Movie movie : options) {
			String movieIdentifier = normalizePunctuation(movie.getNameWithYear()).toLowerCase();
			if (query.startsWith(movieIdentifier)) {
				return movie;
			}
		}

		List<Movie> selection = selectSearchResult(query, options, false, false, false, 1);
		return selection.isEmpty() ? null : selection.get(0);
	}

	public List<File> renameMusic(Collection<File> files, RenameAction renameAction, ConflictAction conflictAction, File outputDir, ExpressionFileFormat format, List<MusicIdentificationService> services, ExecCommand exec) throws Exception {
		List<File> audioFiles = sortByUniquePath(filter(files, AUDIO_FILES, VIDEO_FILES));

		// check audio files against all services if necessary
		List<Match<File, ?>> matches = new ArrayList<Match<File, ?>>();
		LinkedHashSet<File> remaining = new LinkedHashSet<File>(audioFiles);

		// check audio files against all services
		for (MusicIdentificationService service : services) {
			if (remaining.size() > 0) {
				log.config(format("Rename music using %s", service.getIdentifier()));
				service.lookup(remaining).forEach((file, music) -> {
					if (music != null) {
						matches.add(new Match<File, AudioTrack>(file, music.clone()));
						remaining.remove(file);
					}
				});
			}
		}

		// error logging
		remaining.forEach(f -> log.warning(format("Failed to process music file: %s", f)));

		// companion files (e.g. lyrics) are renamed along with their music file
		matches.addAll(CompanionFiles.matchMusicCompanionFiles(files, matches));

		// rename music
		return renameAll(formatMatches(matches, format, outputDir), renameAction, conflictAction, null, exec);
	}

	public List<File> renameFiles(Collection<File> files, RenameAction renameAction, ConflictAction conflictAction, File outputDir, ExpressionFileFormat format, LocalDatasource service, ExpressionFilter filter, boolean strict, ExecCommand exec) throws Exception {
		log.config(format("Rename files using [%s]", service.getName()));

		Map<File, File> renameMap = new LinkedHashMap<File, File>();

		// match to xattr metadata object or the file itself
		Map<File, Object> matches = service.match(files, strict);

		service.match(files, strict).forEach((k, v) -> {
			MediaBindingBean bindingBean = new MediaBindingBean(v, k, matches);

			if (filter == null || filter.matches(bindingBean)) {
				String destinationPath = format != null ? format.format(bindingBean) : v instanceof File ? v.toString() : validateFileName(v.toString());
				renameMap.put(k, getDestinationFile(k, destinationPath, outputDir));
			}
		});

		return renameAll(renameMap, renameAction, conflictAction, null, exec);
	}

	private Map<File, Object> getContext(List<Match<File, ?>> matches) {
		return new AbstractMap<File, Object>() {

			@Override
			public Set<Entry<File, Object>> entrySet() {
				return matches.stream().collect(toMap(it -> it.getValue(), it -> (Object) it.getCandidate(), (a, b) -> a, LinkedHashMap::new)).entrySet();
			}
		};
	}

	private File getDestinationFile(File original, String newName, File outputDir) {
		String extension = getExtension(original);
		File newFile = new File(extension != null ? newName + '.' + extension.toLowerCase() : newName);

		// resolve against output dir
		if (outputDir != null && !newFile.isAbsolute()) {
			newFile = new File(outputDir, newFile.getPath());
		}

		if (isInvalidFilePath(newFile) && !isUnixFS()) {
			log.config("Stripping invalid characters from new path: " + newName);
			newFile = validateFilePath(newFile);
		}

		return newFile;
	}

	private Map<File, File> formatMatches(List<Match<File, ?>> matches, ExpressionFileFormat format, File outputDir) throws Exception {
		// map old files to new paths by applying formatting and validating filenames
		Map<File, File> renameMap = new LinkedHashMap<File, File>();

		for (Match<File, ?> match : matches) {
			File file = match.getValue();
			Object object = match.getCandidate();
			String destinationPath = format != null ? format.format(new MediaBindingBean(object, file, getContext(matches))) : validateFileName(object.toString());

			renameMap.put(file, getDestinationFile(file, destinationPath, outputDir));
		}

		return renameMap;
	}

	private Map<File, File> renameLog = emptyMap();

	private Closure<?> conflictResolver = null;

	private Comparator<File> fileOrder = null;

	/**
	 * @param fileOrder
	 *            process files in this order (e.g. best quality first, so that it wins any conflicts), or null
	 */
	public void setFileOrder(Comparator<File> fileOrder) {
		this.fileOrder = fileOrder;
	}

	/**
	 * @param conflictResolver
	 *            custom conflict action that is called with the source and the existing destination, and returns a new
	 *            destination, a conflict action (e.g. 'override' or 'index') or a boolean (true for override, false for
	 *            skip)
	 */
	public void setConflictResolver(Closure<?> conflictResolver) {
		this.conflictResolver = conflictResolver;
	}

	private static ConflictAction getConflictAction(Object resolution) {
		if (resolution instanceof ConflictAction) {
			return (ConflictAction) resolution;
		}
		if (resolution instanceof Boolean) {
			return (Boolean) resolution ? ConflictAction.OVERRIDE : ConflictAction.SKIP;
		}
		return resolution == null ? ConflictAction.SKIP : ConflictAction.forName(resolution.toString());
	}

	@Override
	public Map<File, File> getRenameLog() {
		return unmodifiableMap(renameLog);
	}

	protected List<File> renameAll(Map<File, File> renameMap, RenameAction renameAction, ConflictAction conflictAction, List<Match<File, ?>> matches, ExecCommand exec) throws Exception {
		// flush all memory caches to disk (before starting any long running file system operations that might be cancelled by the user)
		CacheManager.getInstance().flushAll();

		if (renameMap.isEmpty()) {
			throw new CmdlineException("Failed to identify or process any files");
		}

		// custom file order (e.g. --file-order resolution)
		if (fileOrder != null) {
			Map<File, File> sortedMap = new LinkedHashMap<File, File>();
			renameMap.entrySet().stream().sorted(Entry.comparingByKey(fileOrder)).forEach(it -> sortedMap.put(it.getKey(), it.getValue()));
			renameMap = sortedMap;
		}

		// rename files
		Map<File, File> renameLog = new LinkedHashMap<File, File>();
		this.renameLog = renameLog;

		// matched info of each file for xattr metadata
		Map<File, Object> metaInfo = new HashMap<File, Object>();
		if (matches != null) {
			for (Match<File, ?> match : matches) {
				if (match.getCandidate() != null) {
					metaInfo.put(match.getValue(), match.getCandidate());
				}
			}
		}

		// history entry of this batch, written after each renamed file so that nothing is lost if the process is killed (e.g. long running fn:watcher)
		History.Sequence historySequence = null;

		// Windows: ask the user to wait before logging off or shutting down until all files are renamed
		SessionEndHandler.Block shutdownBlock = SessionEndHandler.block("Renaming files");

		try {
			for (Entry<File, File> it : renameMap.entrySet()) {
				try {
					File source = it.getKey();
					File destination = it.getValue();
					ConflictAction action = conflictAction;

					// resolve destination
					if (!destination.isAbsolute()) {
						// same folder, different name
						destination = resolve(source, destination);
					}

					if (!destination.equals(source) && existsNoFollowLinks(destination) && conflictResolver != null) {
						// custom conflict action may choose another destination or a standard conflict action
						Object resolution = conflictResolver.call(source, destination);
						if (resolution instanceof File) {
							log.fine(format("[CONFLICT] Resolve [%s] to [%s]", destination, resolution));
							destination = ((File) resolution).isAbsolute() ? (File) resolution : resolve(source, (File) resolution);
						} else {
							action = getConflictAction(resolution);
							log.fine(format("[CONFLICT] Resolve [%s] with [%s]", destination, action));
						}
					}

					if (!destination.equals(source) && existsNoFollowLinks(destination)) {
						if (action == ConflictAction.FAIL) {
							throw new CmdlineException(String.format("Failed to process [%s] because [%s] already exists", source, destination));
						}

						// do not allow abuse of online databases by repeatedly processing the same files
						if (matches != null && renameAction.canRevert() && source.length() > 0 && equalsLastModified(source, destination, 2000) && equalsFileContent(source, destination)) {
							throw new CmdlineException(String.format("Failed to process [%s] because [%s] is an exact copy and already exists [Last-Modified: %tc]", source, destination, destination.lastModified()));
						}

						// destination is already a link to the source file (e.g. hardlink action applied twice) so there is nothing to override or index
						if (isSameFile(source, destination)) {
							log.info(format("Skipped [%s] because [%s] is a link to the same file", source, destination));
							continue;
						}

						// delete existing destination path if necessary
						if (action == ConflictAction.OVERRIDE || (action == ConflictAction.AUTO && VideoQuality.isBetter(source, destination))) {
							log.fine(format("[%s] Delete [%s]", action, destination));

							// do not actually delete files in test mode
							if (renameAction != StandardRenameAction.TEST) {
								try {
									delete(destination);
								} catch (Exception e) {
									log.warning(format("[%s] Failed to delete [%s]: %s", action, destination, e));
								}
							}
						}

						// generate indexed destination path if necessary
						if (action == ConflictAction.INDEX) {
							destination = nextAvailableIndexedName(destination);
						}
					}

					// rename file, throw exception on failure
					if (!destination.equals(source) && !destination.exists()) {
						log.info(format("[%s] from [%s] to [%s]", renameAction, source, destination));
						destination = renameAction.rename(source, destination);

						// in-memory xattr metadata of both paths is outdated now
						xattr.invalidate(source, destination);

						// remember successfully renamed matches for history entry and possible revert
						renameLog.put(source, destination);

						// update history and xattr metadata right away
						historySequence = writeHistory(renameAction, source, destination, metaInfo.get(source), historySequence);
					} else {
						log.info(format("Skipped [%s] because [%s] already exists", source, destination));
					}
				} catch (IOException e) {
					log.warning(format("[%s] Failure: %s", renameAction, e));
					throw e;
				}
			}
		} finally {
			// make sure the history of this batch is written to disk
			if (historySequence != null) {
				HistorySpooler.getInstance().commit();
			}

			// print number of processed files
			log.fine(format("Processed %d files", renameLog.size()));

			shutdownBlock.close();
		}

		// execute command
		if (exec != null) {
			exec.execute(renameLog.values().stream().map(f -> new MediaBindingBean(xattr.getMetaInfo(f), f))).forEach(r -> {
				// consume stream and ignore exit codes
			});
		}

		return new ArrayList<File>(renameLog.values());
	}

	protected History.Sequence writeHistory(RenameAction action, File source, File destination, Object metaInfo, History.Sequence sequence) {
		// write xattr metadata
		if (metaInfo != null && destination.isFile()) {
			// remember Last Modified date
			long timestamp = source.isFile() ? source.lastModified() : destination.lastModified();

			// store xattr
			xattr.setMetaInfo(destination, metaInfo, source.getName());

			// restore Last Modified date
			destination.setLastModified(timestamp);
		}

		// write rename history
		if (action.canRevert()) {
			sequence = HistorySpooler.getInstance().append(sequence, singletonMap(source, destination).entrySet());
			HistorySpooler.getInstance().commitPeriodically();
		}

		return sequence;
	}

	protected File nextAvailableIndexedName(File file) {
		File parent = file.getParentFile();
		String name = getName(file);
		String ext = getExtension(file);
		return IntStream.range(1, 100).mapToObj(i -> new File(parent, name + '.' + i + '.' + ext)).filter(f -> !f.exists()).findFirst().get();
	}

	@Override
	public List<File> getSubtitles(Collection<File> files, String query, Language language, SubtitleFormat output, Charset encoding, SubtitleNaming format, boolean strict) throws Exception {
		// ignore anything that is not a video
		files = filter(files, VIDEO_FILES);

		// ignore sample files
		files = sortByUniquePath(filter(files, not(getClutterFileFilter())));

		// try to find subtitles for each video file
		List<File> remainingVideos = new ArrayList<File>(files);

		// parallel download
		List<File> subtitleFiles = new ArrayList<File>();

		log.finest(format("Get [%s] subtitles for %d files", language.getName(), remainingVideos.size()));
		if (remainingVideos.isEmpty()) {
			throw new CmdlineException("No video files: " + files);
		}

		// lookup subtitles by hash
		for (VideoHashSubtitleService service : getVideoHashSubtitleServices(language.getLocale())) {
			if (remainingVideos.isEmpty() || !requireLogin(service)) {
				continue;
			}

			try {
				log.fine("Looking up subtitles by hash via " + service.getName());
				Map<File, List<SubtitleDescriptor>> options = lookupSubtitlesByHash(service, remainingVideos, language.getLocale(), false, strict);
				Map<File, File> downloads = downloadSubtitleBatch(service, options, output, encoding, format);
				remainingVideos.removeAll(downloads.keySet());
				subtitleFiles.addAll(downloads.values());
			} catch (Exception e) {
				log.warning("Lookup by hash failed: " + e.getMessage());
			}
		}

		for (SubtitleProvider service : getSubtitleProviders(language.getLocale())) {
			if (strict || remainingVideos.isEmpty() || !requireLogin(service)) {
				continue;
			}

			try {
				log.fine(format("Looking up subtitles by name via %s", service.getName()));
				Map<File, List<SubtitleDescriptor>> options = findSubtitlesByName(service, remainingVideos, language.getLocale(), query, false, strict);
				Map<File, File> downloads = downloadSubtitleBatch(service, options, output, encoding, format);
				remainingVideos.removeAll(downloads.keySet());
				subtitleFiles.addAll(downloads.values());
			} catch (Exception e) {
				log.warning(format("Search by name failed: %s", e.getMessage()));
			}
		}

		// no subtitles for remaining video files
		for (File it : remainingVideos) {
			log.warning("No matching subtitles found: " + it);
		}

		return subtitleFiles;
	}

	private static boolean anonymousNotice = false;

	protected static boolean requireLogin(Object service) {
		if (service instanceof OpenSubtitlesClient) {
			OpenSubtitlesClient osdb = (OpenSubtitlesClient) service;
			if (osdb.isAnonymous() && !anonymousNotice) {
				anonymousNotice = true;
				// anonymous downloads are allowed, but limited
				log.fine(String.format("%s: Anonymous downloads are limited to 5 subtitles per day. Enter your login details by calling `%s -script fn:configure` to download more.", osdb.getName(), getApplicationId()));
			}
		}
		return true;
	}

	@Override
	public List<File> getMissingSubtitles(Collection<File> files, String query, Language language, SubtitleFormat output, Charset encoding, SubtitleNaming format, boolean strict) throws Exception {
		List<File> videoFiles = filter(filter(files, VIDEO_FILES), new FileFilter() {

			// save time on repeating filesystem calls
			private Map<File, List<File>> cache = new HashMap<File, List<File>>();

			public boolean matchesLanguageCode(File f) {
				Language languageSuffix = Language.getLanguage(releaseInfo.getSubtitleLanguageTag(getName(f)));
				if (languageSuffix != null) {
					return languageSuffix.getCode().equals(language.getCode());
				}
				return false;
			}

			@Override
			public boolean accept(File video) {
				if (!video.isFile()) {
					return false;
				}

				List<File> subtitleFiles = cache.computeIfAbsent(video.getParentFile(), parent -> {
					return getChildren(parent, SUBTITLE_FILES);
				});

				// can't tell which subtitle belongs to which file -> if any subtitles exist skip the whole folder
				if (format == SubtitleNaming.ORIGINAL) {
					return subtitleFiles.size() == 0;
				}

				return subtitleFiles.stream().allMatch(f -> {
					if (isDerived(f, video)) {
						return format != SubtitleNaming.MATCH_VIDEO && !matchesLanguageCode(f);
					}
					return true;
				});
			}
		});

		if (videoFiles.isEmpty()) {
			log.info("No missing subtitles");
			return emptyList();
		}

		return getSubtitles(videoFiles, query, language, output, encoding, format, strict);
	}

	private Map<File, File> downloadSubtitleBatch(Datasource service, Map<File, List<SubtitleDescriptor>> subtitles, SubtitleFormat outputFormat, Charset outputEncoding, SubtitleNaming naming) {
		// flush all memory caches to disk (before starting any long running file system operations that might be cancelled by the user)
		CacheManager.getInstance().flushAll();

		Map<File, File> downloads = new LinkedHashMap<File, File>();

		// fetch subtitle
		subtitles.forEach((movie, options) -> {
			if (options.size() > 0) {
				SubtitleDescriptor subtitle = options.get(0);
				try {
					downloads.put(movie, downloadSubtitle(service, subtitle, movie, outputFormat, outputEncoding, naming));
				} catch (Exception e) {
					log.warning(format("Failed to download %s: %s", subtitle, e));
				}
			}
		});

		return downloads;
	}

	private File downloadSubtitle(Datasource service, SubtitleDescriptor descriptor, File movieFile, SubtitleFormat outputFormat, Charset outputEncoding, SubtitleNaming naming) throws Exception {
		// fetch subtitle archive
		log.config(format("Fetching [%s] subtitles [%s] from [%s]", descriptor.getLanguageName(), descriptor.getPath(), service.getName()));
		MemoryFile subtitleFile = fetchSubtitle(descriptor);

		// subtitle filename is based on movie filename
		String extension = getExtension(subtitleFile.getName());
		ByteBuffer data = subtitleFile.getData();

		if (outputFormat != null || outputEncoding != null) {
			// adjust extension of the output file
			if (outputFormat != null) {
				extension = outputFormat.getFilter().extension();
			}

			// default to UTF-8 if no other encoding is given
			if (outputEncoding == null) {
				outputEncoding = UTF_8;
			}

			log.finest(format("Export [%s] as [%s / %s]", subtitleFile.getName(), outputFormat, outputEncoding));
			data = exportSubtitles(subtitleFile, outputFormat, 0, outputEncoding);
		}

		File destination = new File(movieFile.getParentFile(), naming.format(movieFile, descriptor, extension));
		log.info(format("Writing [%s] to [%s]", subtitleFile.getName(), destination.getName()));

		writeFile(data, destination);
		return destination;
	}

	protected <T> List<T> applyExpressionFilter(List<T> input, ExpressionFilter filter) {
		if (filter == null) {
			return input;
		}

		log.fine(formatSingleLine("Apply filter [%s] on [%d] items", filter.getExpression(), input.size()));

		// support context bindings
		Map<File, ?> context = new EntryList<File, T>(null, input);

		return input.stream().filter(it -> {
			if (filter.matches(new MediaBindingBean(it, null, context))) {
				log.finest(format("Include [%s]", it));
				return true;
			}
			return false;
		}).collect(toList());
	}

	protected List<Episode> applyEpisodeExpressionMapper(List<Episode> episodes, ExpressionMapper mapper) {
		if (mapper == null) {
			return episodes;
		}

		log.fine(formatSingleLine("Apply mapper [%s] on [%d] items", mapper.getExpression(), episodes.size()));

		// support episode list context
		Map<File, Episode> context = new EntryList<File, Episode>(null, episodes);

		return episodes.stream().flatMap(episode -> {
			try {
				Episode[] mapping = mapper.map(new MediaBindingBean(episode, null, context), Episode[].class);
				log.finest(format("Map [%s] to [%s]", episode, stream(mapping).map(Objects::toString).collect(joining(" | "))));
				return stream(mapping).map(e -> new MappedEpisode(episode, e));
			} catch (Exception e) {
				debug.warning(format("Exclude [%s] due to map failure: %s", episode, e));
				return Stream.empty();
			}
		}).filter(Objects::nonNull).collect(toList());
	}

	protected <T extends SearchResult> List<T> selectSearchResult(String query, Collection<T> options, boolean sort, boolean alias, boolean strict, int limit) throws Exception {
		List<T> probableMatches = getProbableMatches(sort ? query : null, options, alias, strict);

		if (probableMatches.isEmpty() || (strict && probableMatches.size() != 1)) {
			// allow single search results to just pass through in non-strict mode even if match confidence is low
			if (options.size() == 1 && !strict) {
				return options.stream().collect(toList());
			}

			if (strict) {
				throw new CmdlineException("Multiple options: Advanced auto-selection requires -non-strict matching: " + probableMatches);
			}

			// just pick the best N matches
			if (sort) {
				probableMatches = sortBySimilarity(options, singleton(query), getSeriesMatchMetric()).stream().collect(toList());
			}
		}

		// return first and only value
		return probableMatches.size() <= limit ? probableMatches : probableMatches.subList(0, limit); // trust that the correct match is in the Top N
	}

	@Override
	public boolean check(Collection<File> files) throws Exception {
		// only check existing hashes
		boolean result = true;

		for (File it : filter(files, VERIFICATION_FILES)) {
			result &= check(it, it.getParentFile());
		}

		return result;
	}

	@Override
	public File compute(Collection<File> files, HashType hash, File output, Charset encoding) throws Exception {
		// ignore folders and any sort of special files
		files = filter(files, FILES);

		if (files.isEmpty()) {
			throw new CmdlineException("No files: " + files);
		}

		// find common parent folder of all files
		File[] fileList = files.toArray(new File[0]);
		File[][] pathArray = new File[fileList.length][];
		for (int i = 0; i < fileList.length; i++) {
			pathArray[i] = listPath(fileList[i].getParentFile()).toArray(new File[0]);
		}

		CommonSequenceMatcher csm = new CommonSequenceMatcher(null, 0, true);
		File[] common = csm.matchFirstCommonSequence(pathArray);

		if (common == null) {
			throw new CmdlineException("All paths must be on the same filesystem: " + files);
		}

		// last element in the common sequence must be the root folder
		File root = common[common.length - 1];

		if (output == null) {
			output = new File(root, root.getName() + '.' + hash.getFilter().extension());
		} else if (!output.isAbsolute()) {
			output = new File(root, output.getPath());
		}

		log.info(format("Compute %s hash for %s files [%s]", hash, files.size(), output));
		compute(root, files, output, hash, encoding);

		return output;
	}

	private boolean check(File verificationFile, File root) throws Exception {
		HashType type = getHashType(verificationFile);

		// check if type is supported
		if (type == null) {
			throw new CmdlineException("Unsupported format: " + verificationFile);
		}

		// add all file names from verification file
		log.fine(format("Checking [%s]", verificationFile.getName()));
		VerificationFileReader parser = new VerificationFileReader(createTextReader(verificationFile), type.getFormat());
		boolean status = true;

		try {
			while (parser.hasNext()) {
				try {
					Entry<File, String> it = parser.next();

					File file = new File(root, it.getKey().getPath()).getAbsoluteFile();
					String current = computeHash(new File(root, it.getKey().getPath()), type);
					log.info(format("%s %s", current, file));

					if (current.compareToIgnoreCase(it.getValue()) != 0) {
						throw new IOException(String.format("Corrupted file found: %s [hash mismatch: %s vs %s]", it.getKey(), current, it.getValue()));
					}
				} catch (IOException e) {
					status = false;
					log.warning(e.getMessage());
				}
			}
		} finally {
			parser.close();
		}

		return status;
	}

	private void compute(File root, Collection<File> files, File outputFile, HashType hashType, Charset encoding) throws IOException, Exception {
		// compute hashes recursively and write to file
		VerificationFileWriter out = new VerificationFileWriter(outputFile, hashType.getFormat(), encoding != null ? encoding : UTF_8);

		try {
			for (File it : files) {
				if (it.isHidden() || VERIFICATION_FILES.accept(it)) {
					continue;
				}

				String relativePath = VerificationFileWriter.getRelativePath(it, outputFile.getAbsoluteFile().getParentFile()); // relative to the verification file
				String hash = computeHash(it, hashType);
				log.info(format("%s %s", hash, relativePath));

				out.write(relativePath, hash);
			}
		} catch (Exception e) {
			outputFile.deleteOnExit(); // delete only partially written files
			throw e;
		} finally {
			out.close();
		}
	}

	private List<Episode> fetchEpisodeList(EpisodeListProvider db, String query, ExpressionFilter filter, ExpressionMapper mapper, SortOrder order, Locale locale, boolean strict) throws Exception {
		// sanity check
		if (query == null) {
			throw new CmdlineException(String.format("%s: query parameter is required", db.getName()));
		}

		// collect all episode objects first
		Set<Episode> episodeSet = new LinkedHashSet<Episode>();

		for (String q : splitQuery(query)) {
			Integer id = getSeriesID(q);
			if (id != null) {
				// lookup by id
				episodeSet.addAll(db.getEpisodeList(id, order, locale));
			} else {
				// search by name and select search result
				List<SearchResult> options = selectSearchResult(q, db.search(q, locale), true, true, false, strict ? 1 : 5);

				// fetch episodes
				for (SearchResult option : options) {
					episodeSet.addAll(db.getEpisodeList(option, order, locale));
				}
			}
		}

		List<Episode> episodes = new ArrayList<Episode>(episodeSet);

		// sanity check
		if (episodes.isEmpty()) {
			throw new CmdlineException(String.format("%s: no results", db.getName()));
		}

		// filter episodes and apply custom mappings
		episodes = applyExpressionFilter(episodes, filter);
		episodes = applyEpisodeExpressionMapper(episodes, mapper);

		return episodes;
	}

	/**
	 * --q may contain multiple queries separated by | (e.g. "Firefly | Serenity")
	 */
	protected List<String> splitQuery(String query) {
		return stream(query.split("\\|")).map(String::trim).filter(s -> s.length() > 0).collect(toList());
	}

	// e.g. id:1234, tvdb:73255, tmdb=1399, anidb:69 (the database is selected via --db)
	private static final Pattern ID_QUERY = Pattern.compile("(?i)(?:id|tvdb|tmdb|anidb|tvmaze)\\s*[:=]\\s*(\\d+)");

	// e.g. tt0379786 or imdb:379786
	private static final Pattern IMDB_QUERY = Pattern.compile("(?i)(?:imdb\\s*[:=]\\s*)?(?:tt)?(\\d{1,8})");

	/**
	 * Series id given as id:1234 or as plain number with 5 to 9 digits (shorter numbers are usually names, e.g. 24 or 1923)
	 */
	protected Integer getSeriesID(String query) {
		Matcher id = ID_QUERY.matcher(query);
		if (id.matches()) {
			return Integer.parseInt(id.group(1));
		}
		if (query.matches("\\d{5,9}")) {
			return Integer.parseInt(query);
		}
		return null;
	}

	/**
	 * Movie id given as IMDb id (tt0379786, imdb:379786) or TheMovieDB id (id:16320, tmdb:16320), plain numbers are always names (e.g. 1917)
	 */
	protected Movie getMovieID(String query, Locale locale) {
		Matcher id = ID_QUERY.matcher(query);
		if (id.matches()) {
			return new Movie(null, new String[0], 0, 0, Integer.parseInt(id.group(1)), locale);
		}
		Matcher imdb = IMDB_QUERY.matcher(query);
		if (imdb.matches() && !query.matches("\\d+")) {
			return new Movie(Integer.parseInt(imdb.group(1)));
		}
		return null;
	}

	protected List<Movie> searchMovieByQuery(MovieIdentificationService service, String query, Locale locale) throws Exception {
		Set<Movie> options = new LinkedHashSet<Movie>();
		for (String q : splitQuery(query)) {
			Movie id = getMovieID(q, locale);
			if (id != null) {
				Movie movie = service.getMovieDescriptor(id, locale);
				if (movie != null) {
					options.add(movie);
				} else {
					log.warning(format("Movie not found: %s", q));
				}
			} else {
				options.addAll(service.searchMovie(q, locale));
			}
		}
		return new ArrayList<Movie>(options);
	}

	@Override
	public Stream<String> fetchEpisodeList(EpisodeListProvider db, String query, SortOrder order, Locale locale, ExpressionFilter filter, ExpressionMapper mapper, ExpressionFormat format, boolean strict) throws Exception {
		// collect all episode objects first
		List<Episode> episodes = fetchEpisodeList(db, query, filter, mapper, order, locale, strict);

		// instant format
		if (format == null) {
			return episodes.stream().map(Episode::toString);
		}

		// lazy format
		return episodes.stream().map(episode -> {
			try {
				return format.format(new MediaBindingBean(episode, null, new EntryList<File, Episode>(null, episodes)));
			} catch (Exception e) {
				debug.warning(e::getMessage);
			}
			return null;
		}).filter(Objects::nonNull);
	}

	@Override
	public Stream<String> getMediaInfo(Collection<File> files, FileFilter filter, ExpressionFormat format) throws Exception {
		// use default expression format if not set
		if (format == null) {
			return getMediaInfo(files, filter, new ExpressionFormat("{fn} [{resolution} {vc} {channels} {ac} {hours} {mbps}]"));
		}

		return files.stream().filter(filter::accept).map(f -> {
			try {
				return format.format(new MediaBindingBean(xattr.getMetaInfo(f), f));
			} catch (Exception e) {
				debug.warning(e::getMessage);
			}
			return null;
		}).filter(Objects::nonNull);
	}

	@Override
	public IntStream execute(Collection<File> files, FileFilter filter, ExpressionFormat format, ExecCommand exec) {
		// filter / map / execute
		return exec.execute(files.stream().filter(filter::accept).map(f -> new MediaBindingBean(xattr.getMetaInfo(f), f)).peek(b -> {
			if (format != null) {
				try {
					log.info(format.format(b));
				} catch (Exception e) {
					debug.warning(e::getMessage);
				}
			}
		}));
	}

	@Override
	public List<File> revert(Collection<File> files, FileFilter filter, RenameAction action) throws Exception {
		History history = HistorySpooler.getInstance().getCompleteHistory();

		// no input paths: revert the most recent operation, otherwise revert everything that was renamed from or into the given paths
		List<Entry<File, File>> revertList;
		if (files.isEmpty()) {
			List<History.Sequence> operation = history.getLastOperation();
			if (operation.isEmpty()) {
				throw new CmdlineException("History is empty");
			}
			log.fine(format("Revert most recent operation [%tc]", operation.get(operation.size() - 1).date()));
			revertList = History.getRevertList(operation);
		} else {
			Set<File> whitelist = new HashSet<File>(files);
			revertList = History.getRevertList(history.sequences()).stream().filter(it -> {
				return Stream.of(it.getValue(), it.getKey()).flatMap(f -> listPath(f).stream()).anyMatch(whitelist::contains);
			}).collect(toList());
		}

		// revert most recent renames first, so that files renamed multiple times (A to B, B to C) are reverted step by step (C to B, B to A)
		List<File> reverted = new ArrayList<File>();
		for (Entry<File, File> it : revertList) {
			File original = it.getKey();
			File current = it.getValue();

			// renamed folders (e.g. disk folders) are reverted as well, the filter only applies to files
			if (!current.exists() || !(current.isDirectory() || filter.accept(current))) {
				continue;
			}

			log.info(format("Revert [%s] to [%s]", current, original));
			if (action.canRevert()) {
				try {
					File file = StandardRenameAction.revert(current, original);
					if (file != null) {
						// metadata of the match moved back with the file and is no longer valid (copies are trashed and the original is left alone)
						xattr.invalidate(current, file);
						if (file.isFile() && !current.exists()) {
							xattr.clear(file);
						}
						reverted.add(file);
					}
				} catch (Exception e) {
					log.warning("Failed to revert file: " + e);
				}
			}
		}
		return reverted;
	}

	@Override
	public List<File> extract(Collection<File> files, File output, ConflictAction conflict, FileFilter filter, boolean forceExtractAll) throws Exception {
		// only keep single-volume archives or first part of multi-volume archives
		List<File> archiveFiles = filter(files, Archive.VOLUME_ONE_FILTER);
		List<File> extractedFiles = new ArrayList<File>();

		for (File file : archiveFiles) {
			Archive archive = Archive.open(file);
			try {
				File outputFolder = output;

				if (outputFolder == null || !outputFolder.isAbsolute()) {
					outputFolder = new File(file.getParentFile(), outputFolder == null ? getName(file) : outputFolder.getPath()).getCanonicalFile();
				}

				log.info(format("Read archive [%s] and extract to [%s]", file.getName(), outputFolder));

				createFolders(outputFolder);
				FileMapper outputMapper = new FileMapper(outputFolder);

				List<FileInfo> outputMapping = new ArrayList<FileInfo>();
				for (FileInfo it : archive.listFiles()) {
					File outputPath = outputMapper.getOutputFile(it.toFile());
					outputMapping.add(new SimpleFileInfo(outputPath.getPath(), it.getLength()));
				}

				// print warning message if archive appears empty
				if (outputMapping.isEmpty()) {
					log.warning(format("[%s] contains [%s] files", file.getName(), outputMapping.size()));
				}

				Set<FileInfo> selection = new TreeSet<FileInfo>();
				for (FileInfo future : outputMapping) {
					if (filter == null || filter.accept(future.toFile())) {
						selection.add(future);
					}
				}

				// check if there is anything to extract at all
				if (selection.isEmpty()) {
					continue;
				}

				// apply the conflict action to each file so that existing files are only replaced if requested
				extractedFiles.addAll(ExtractOperation.extract(archive, outputMapper, filter == null || forceExtractAll ? outputMapping : selection, conflict));
			} finally {
				archive.close();
			}
		}

		return extractedFiles;
	}

}
