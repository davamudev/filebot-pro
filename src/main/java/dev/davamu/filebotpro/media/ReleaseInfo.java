package dev.davamu.filebotpro.media;

import static java.lang.Integer.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.ResourceBundle.*;
import static java.util.regex.Pattern.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.similarity.Normalization.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;
import static dev.davamu.filebotpro.util.RegularExpressions.*;
import static dev.davamu.filebotpro.util.StringUtilities.*;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.text.Collator;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.tukaani.xz.XZInputStream;

import dev.davamu.filebotpro.ApplicationFolder;
import dev.davamu.filebotpro.Cache;
import dev.davamu.filebotpro.CacheType;
import dev.davamu.filebotpro.Resource;
import dev.davamu.filebotpro.util.FileUtilities.RegexFindFilter;
import dev.davamu.filebotpro.util.FileUtilities.RegexMatchFilter;
import dev.davamu.filebotpro.web.AnimeLists;
import dev.davamu.filebotpro.web.Movie;
import dev.davamu.filebotpro.web.SearchResult;
import dev.davamu.filebotpro.web.SubtitleSearchResult;

public class ReleaseInfo {

	private String[] videoSources;
	private Pattern videoSourcePattern;

	public String getVideoSource(String... input) {
		if (videoSources == null || videoSourcePattern == null) {
			videoSources = PIPE.split(getProperty("pattern.video.source"));
			videoSourcePattern = getVideoSourcePattern();
		}

		// check parent and itself for group names
		return matchLast(videoSourcePattern, videoSources, input);
	}

	private Map<String, String> standardVideoSources;

	public String getStandardVideoSource(String source) {
		if (source == null) {
			return null;
		}

		if (standardVideoSources == null) {
			Map<String, String> map = new LinkedHashMap<String, String>();
			for (String group : SEMICOLON.split(getProperty("standard.video.source"))) {
				String[] entry = group.split("=", 2);
				for (String it : PIPE.split(entry[1])) {
					map.put(normalizeVideoSource(it), entry[0].trim());
				}
			}
			standardVideoSources = map;
		}

		// unknown sources keep their matched value
		return standardVideoSources.getOrDefault(normalizeVideoSource(source), source);
	}

	private String normalizeVideoSource(String source) {
		return source.replaceAll("\\P{Alnum}", "").toLowerCase(Locale.ROOT);
	}

	private Pattern videoTagPattern;

	public List<String> getVideoTags(String... input) {
		if (videoTagPattern == null) {
			videoTagPattern = getVideoTagPattern();
		}

		List<String> tags = new ArrayList<String>();
		for (String s : input) {
			if (s == null)
				continue;

			Matcher m = videoTagPattern.matcher(s);
			while (m.find()) {
				tags.add(m.group());
			}
		}
		return tags;
	}

	public String getStereoscopic3D(String... input) {
		Pattern pattern = getStereoscopic3DPattern();
		for (String s : input) {
			Matcher m = pattern.matcher(s);
			if (m.find()) {
				return m.group();
			}
		}
		return null;
	}

	public String getReleaseGroup(String... name) throws Exception {
		// check file and folder for release group names
		String[] groups = releaseGroup.get();

		for (boolean strict : new boolean[] { true, false }) {
			String match = matchLast(getReleaseGroupPattern(strict), groups, name);

			if (match != null) {
				// group pattern does not match closing brackets in GROUP[INDEX] patterns
				if (match.lastIndexOf(']') < match.lastIndexOf('[')) {
					return match + ']';
				}
				return match;
			}
		}

		return null;
	}

	private Pattern languageTag;

	public Locale getSubtitleLanguageTag(CharSequence... name) {
		// match locale identifier and lookup Locale object
		if (languageTag == null) {
			languageTag = getSubtitleLanguageTagPattern();
		}

		String lang = matchLast(languageTag, null, name);
		return lang == null ? null : getDefaultLanguageMap().get(lang);
	}

	private Pattern categoryTag;
	private String[] categoryTags;

	public String getSubtitleCategoryTag(CharSequence... name) {
		// match locale identifier and lookup Locale object
		if (categoryTag == null || categoryTags == null) {
			categoryTag = getSubtitleCategoryTagPattern();
			categoryTags = getSubtitleCategoryTags();
		}

		return matchLast(categoryTag, categoryTags, name);
	}

	protected String matchLast(Pattern pattern, String[] paragon, CharSequence... sequence) {
		// match last occurrence
		String lastMatch = stream(sequence).filter(Objects::nonNull).map(s -> matchLastOccurrence(s, pattern)).filter(Objects::nonNull).findFirst().orElse(null);

		// prefer standard value over matched value
		if (lastMatch != null && paragon != null) {
			for (String it : paragon) {
				lastMatch = compile("(?<!\\p{Alnum})" + quote(it) + "(?!\\p{Alnum})", CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS).matcher(lastMatch).replaceAll(it);
			}
		}

		return lastMatch;
	}

	// cached patterns
	private final Pattern[][] stopwords = new Pattern[2][];
	private final Pattern[][] blacklist = new Pattern[2][];

	public List<String> cleanRelease(Collection<String> items, boolean strict) throws Exception {
		int b = strict ? 1 : 0;

		// initialize cached patterns
		if (stopwords[b] == null || blacklist[b] == null) {
			Pattern clutterBracket = getClutterBracketPattern(strict);
			// case-sensitive in both modes, otherwise titles starting or ending with a group name lose that word (e.g. Slow Horses -> Horses because of the group SLOW)
			Pattern releaseGroup = getReleaseGroupPattern(true);
			Pattern releaseGroupTrim = getReleaseGroupTrimPattern();
			Pattern languageSuffix = getSubtitleLanguageTagPattern();
			Pattern languageTag = getLanguageTagPattern(strict);
			Pattern videoSource = getVideoSourcePattern();
			Pattern videoTags = getVideoTagPattern();
			Pattern videoFormat = getVideoFormatPattern(strict);
			Pattern stereoscopic3d = getStereoscopic3DPattern();
			Pattern resolution = getResolutionPattern();
			Pattern queryBlacklist = getBlacklistPattern();

			stopwords[b] = new Pattern[] { languageSuffix, languageTag, videoSource, videoTags, videoFormat, resolution, stereoscopic3d };
			blacklist[b] = new Pattern[] { EMBEDDED_CHECKSUM, languageSuffix, releaseGroupTrim, queryBlacklist, languageTag, clutterBracket, releaseGroup, videoSource, videoTags, videoFormat, resolution, stereoscopic3d };
		}

		return items.stream().map(it -> {
			String head = strict ? clean(it, stopwords[b]) : substringBefore(it, stopwords[b]);
			String norm = normalizePunctuation(clean(head, blacklist[b]));
			return norm;
		}).filter(s -> s.length() > 0).collect(toList());
	}

	public String clean(String item, Pattern... blacklisted) {
		for (Pattern it : blacklisted) {
			item = it.matcher(item).replaceAll("");
		}
		return item;
	}

	public String substringBefore(String item, Pattern... stopwords) {
		for (Pattern it : stopwords) {
			Matcher matcher = it.matcher(item);
			if (matcher.find()) {
				String substring = item.substring(0, matcher.start()); // use substring before the matched stopword
				if (normalizePunctuation(substring).length() >= 3) {
					item = substring; // make sure that the substring has enough data
				}
			}
		}
		return item;
	}

	// cached patterns
	private Set<File> volumeRoots;
	private Pattern structureRootFolderPattern;

	public Set<File> getVolumeRoots() {
		if (volumeRoots == null) {
			Set<File> volumes = new HashSet<File>();

			File home = ApplicationFolder.UserHome.get();
			List<File> roots = getFileSystemRoots();

			// user root folder
			volumes.add(home);

			// Windows / Linux / Mac system roots
			volumes.addAll(roots);

			// Linux / Mac
			if (!isWindowsApp()) {
				// Linux and Mac system root folders
				for (File root : roots) {
					volumes.addAll(getChildren(root, FOLDERS));
				}

				for (File mediaRoot : getMediaRoots()) {
					volumes.addAll(getChildren(mediaRoot, FOLDERS));
					volumes.add(mediaRoot);
				}
			}

			// Windows / Linux
			volumes.addAll(getChildren(home, FOLDERS));

			volumeRoots = unmodifiableSet(volumes);
		}
		return volumeRoots;
	}

	public Pattern getStructureRootPattern() throws Exception {
		if (structureRootFolderPattern == null) {
			List<String> folders = new ArrayList<String>();
			for (String it : queryBlacklist.get()) {
				if (it.startsWith("^") && it.endsWith("$")) {
					folders.add(it);
				}
			}
			structureRootFolderPattern = compile(or(folders.toArray()), CASE_INSENSITIVE);
		}
		return structureRootFolderPattern;
	}

	public Pattern getLanguageTagPattern(boolean strict) {
		// [en]
		if (strict) {
			return compile("(?<=[-\\[\\{\\(])" + or(quoteAll(getDefaultLanguageMap().keySet())) + "(?=[-\\]\\}\\)]|$)", CASE_INSENSITIVE);
		}

		// FR
		List<String> allCapsLanguageTags = getDefaultLanguageMap().keySet().stream().map(String::toUpperCase).collect(toList());
		return compile("(?<!\\p{Alnum})" + or(quoteAll(allCapsLanguageTags)) + "(?!\\p{Alnum})", UNICODE_CHARACTER_CLASS);
	}

	public Pattern getSubtitleCategoryTagPattern() {
		// e.g. ".en.srt" or ".en.forced.srt"
		return compile("(?<=[._-](" + or(quoteAll(getDefaultLanguageMap().keySet())) + ")[._-])" + or(getSubtitleCategoryTags()) + "$", CASE_INSENSITIVE);
	}

	public Pattern getSubtitleLanguageTagPattern() {
		// e.g. ".en.srt" or ".en.forced.srt"
		return compile("(?<=[._-])" + or(quoteAll(getDefaultLanguageMap().keySet())) + "(?=([._-]" + or(getSubtitleCategoryTags()) + ")?$)", CASE_INSENSITIVE);
	}

	public Pattern getResolutionPattern() {
		// match screen resolutions 640x480, 1280x720, etc
		return compile("(?<!\\p{Alnum})(\\d{4}|[6-9]\\d{2})x(\\d{4}|[4-9]\\d{2})(?!\\p{Alnum})");
	}

	public Pattern getVideoFormatPattern(boolean strict) {
		// pattern matching any video source name
		String pattern = getProperty("pattern.video.format");
		return strict ? compile("(?<!\\p{Alpha})(" + pattern + ")(?!\\p{Alpha})", CASE_INSENSITIVE) : compile(pattern, CASE_INSENSITIVE);
	}

	public Pattern getVideoSourcePattern() {
		return compileWordPattern(getProperty("pattern.video.source")); // pattern matching any video source name, like BluRay
	}

	public Pattern getVideoTagPattern() {
		return compileWordPattern(getProperty("pattern.video.tags")); // pattern matching any video tag, like Directors Cut
	}

	public Pattern getStereoscopic3DPattern() {
		return compileWordPattern(getProperty("pattern.video.s3d")); // pattern matching any 3D flags like 3D.HSBS
	}

	public Pattern getRepackPattern() {
		return compileWordPattern(getProperty("pattern.video.repack"));
	}

	public Pattern getExcludePattern() {
		return compileWordPattern(getProperty("pattern.clutter.excludes"));
	}

	public Pattern getClutterBracketPattern(boolean strict) {
		// match patterns like [Action, Drama] or {ENG-XViD-MP3-DVDRiP} etc
		String brackets = "()[]{}";
		String contains = strict ? "[[^a-z0-9]&&[^" + quote(brackets) + "]]" : "\\p{Alpha}";

		return IntStream.range(0, brackets.length() / 2).map(i -> i * 2).mapToObj(i -> {
			String open = quote(brackets.substring(i, i + 1));
			String close = quote(brackets.substring(i + 1, i + 2));
			String notOpenClose = "[^" + open + close + "]+?";
			return open + "(" + notOpenClose + contains + notOpenClose + ")" + close;
		}).collect(collectingAndThen(joining("|"), pattern -> compile(pattern, CASE_INSENSITIVE)));
	}

	public Pattern getReleaseGroupPattern(boolean strict) throws Exception {
		// match 1..N group patterns (e.g. GROUP[INDEX])
		String group = "((?<!\\p{Alnum})" + or(releaseGroup.get()) + "(?!\\p{Alnum})[\\p{Punct}]??)+";

		// group pattern at beginning or ending of the string
		String[] groupHeadTail = { "(?<=^[\\P{Alnum}]*)" + group, group + "(?=[\\P{Alnum}]*$)" };

		return compile(or(groupHeadTail), strict ? UNICODE_CHARACTER_CLASS : CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS);
	}

	public Pattern getReleaseGroupTrimPattern() throws Exception {
		// pattern matching any release group name enclosed in specific separators or at the start/end
		return compile("(?<=\\[|\\(|^)" + or(releaseGroup.get()) + "(?=\\]|\\)|\\-)|(?<=\\[|\\(|\\-)" + or(releaseGroup.get()) + "(?=\\]|\\)|$)", CASE_INSENSITIVE);
	}

	public Pattern getBlacklistPattern() throws Exception {
		return compileWordPattern(queryBlacklist.get()); // pattern matching any release group name enclosed in separators
	}

	// Unicode character classes, so that accented letters are part of a word (e.g. policía is not policí + a)
	private Pattern compileWordPattern(String[] patterns) {
		return compile("(?<!\\p{Alnum})" + or(patterns) + "(?!\\p{Alnum})", CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS); // use | to join patterns
	}

	private Pattern compileWordPattern(String pattern) {
		return compile("(?<!\\p{Alnum})(" + pattern + ")(?!\\p{Alnum})", CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS);
	}

	public Map<Pattern, String> getSeriesMappings() throws Exception {
		return seriesMappings.get();
	}

	public SearchResult[] getSeriesIndex() throws Exception {
		return seriesIndex.get();
	}

	public Map<Integer, String> getAnidbPictures() throws Exception {
		return anidbPictures.get();
	}

	public SearchResult[] getAnidbIndex() throws Exception {
		return anidbIndex.get();
	}

	public Movie[] getMovieList() throws Exception {
		return movieIndex.get();
	}

	public SubtitleSearchResult[] getOpenSubtitlesIndex() throws Exception {
		return osdbIndex.get();
	}

	public Map<String, List<DiscTitles.Title>> getDiscTitles() throws Exception {
		return discTitles.get();
	}

	public Map<String, String> getMediaInfoDescriptions() throws Exception {
		return mediaInfoDescriptions.get();
	}

	public AnimeLists.Model getAnimeListModel() throws Exception {
		return animeListModel.get();
	}

	private static FolderEntryFilter diskFolderFilter;

	public FileFilter getDiskFolderFilter() {
		if (diskFolderFilter == null) {
			diskFolderFilter = new FolderEntryFilter(compile(getProperty("pattern.diskfolder.entry")));
		}
		return diskFolderFilter;
	}

	private static RegexMatchFilter diskFolderEntryFilter;

	public FileFilter getDiskFolderEntryFilter() {
		if (diskFolderEntryFilter == null) {
			diskFolderEntryFilter = new RegexMatchFilter(compile(getProperty("pattern.diskfolder.entry")));
		}
		return diskFolderEntryFilter;
	}

	private static ClutterFileFilter clutterTypeFilter;

	public FileFilter getClutterTypeFilter() {
		if (clutterTypeFilter == null) {
			clutterTypeFilter = new ClutterFileFilter(new RegexFindFilter(compile(getProperty("pattern.clutter.types"), CASE_INSENSITIVE)), Long.parseLong(getProperty("number.clutter.maxfilesize")));
		}
		return clutterTypeFilter;
	}

	private static ClutterFileFilter clutterFileFilter;

	public FileFilter getClutterFileFilter() {
		if (clutterFileFilter == null) {
			clutterFileFilter = new ClutterFileFilter(new FileFolderNameFilter(getExcludePattern()), Long.parseLong(getProperty("number.clutter.maxfilesize")));
		}
		return clutterFileFilter;
	}

	private static RegexMatchFilter systemFilesFilter;

	public FileFilter getSystemFilesFilter() {
		if (systemFilesFilter == null) {
			systemFilesFilter = new RegexMatchFilter(compile(getProperty("pattern.system.files"), CASE_INSENSITIVE));
		}
		return systemFilesFilter;
	}

	public List<File> getMediaRoots() {
		String roots = getProperty("folder.media.roots");
		return COMMA.splitAsStream(roots).map(File::new).filter(File::exists).collect(toList());
	}

	public String[] getSubtitleCategoryTags() {
		String tags = getProperty("pattern.subtitle.tags");
		return PIPE.split(tags);
	}

	private final Resource<Map<Pattern, String>> seriesMappings = resource("series-mappings.txt", Function.identity(), String[]::new).transform(lines -> {
		Map<Pattern, String> map = new LinkedHashMap<Pattern, String>(lines.length);
		stream(lines).map(s -> TAB.split(s, 2)).filter(v -> v.length == 2).forEach(v -> {
			Pattern pattern = compile("(?<!\\p{Alnum})(" + v[0] + ")(?!\\p{Alnum})", CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS);
			map.put(pattern, v[1]);
		});
		return unmodifiableMap(map);
	}).memoize();

	// AniDB / TheTVDB episode mappings by the Anime-Lists project, which has no license that allows publishing a copy:
	// local builds may include it (gradlew updateData, git-ignored), otherwise it is downloaded and cached
	private final Resource<AnimeLists.Model> animeListModel = Resource.lazy(() -> {
		byte[] bytes;
		try {
			bytes = getData("anime-list.xml");
		} catch (FileNotFoundException e) {
			Cache cache = Cache.getCache("data", CacheType.Persistent);
			bytes = cache.bytes("anime-list.xml", n -> new URL("https://raw.githubusercontent.com/Anime-Lists/anime-lists/master/" + n)).expire(Cache.ONE_WEEK).get();
		}
		return AnimeLists.unmarshal(bytes, AnimeLists.Model.class);
	});

	private final Resource<String[]> releaseGroup = lines("release-groups.txt");
	private final Resource<String[]> queryBlacklist = lines("query-blacklist.txt");

	private final Resource<SearchResult[]> seriesIndex = tsv("series.txt", this::parseSeries, SearchResult[]::new);
	private final Resource<SearchResult[]> anidbIndex = tsv("anidb.txt", this::parseSeries, SearchResult[]::new);

	private final Resource<Map<Integer, String>> anidbPictures = tsv("anidb-pictures.txt", Function.identity(), String[][]::new).transform(rows -> {
		Map<Integer, String> map = new LinkedHashMap<Integer, String>(rows.length);
		stream(rows).filter(v -> v.length == 2).forEach(v -> map.put(parseInt(v[0]), v[1]));
		return unmodifiableMap(map);
	}).memoize();

	private final Resource<Movie[]> movieIndex = tsv("moviedb.txt", this::parseMovie, Movie[]::new);
	private final Resource<SubtitleSearchResult[]> osdbIndex = tsv("osdb.txt", this::parseSubtitle, SubtitleSearchResult[]::new);

	// titles of series and movie discs by kind and TheMovieDB id, e.g. tv/1396 or movie/603 (TheDiscDB)
	private final Resource<Map<String, List<DiscTitles.Title>>> discTitles = tsv("discdb.txt", DiscTitles.Title::parse, DiscTitles.Title[]::new).transform(rows -> {
		return unmodifiableMap(stream(rows).collect(groupingBy(DiscTitles.Title::getKey)));
	}).memoize();

	// descriptions of the MediaInfo properties by stream kind and property name, e.g. Video.Width (MediaInfoLib)
	private final Resource<Map<String, String>> mediaInfoDescriptions = tsv("mediainfo.txt", Function.identity(), String[][]::new).transform(rows -> {
		Map<String, String> map = new LinkedHashMap<String, String>();
		stream(rows).filter(v -> v.length >= 3).forEach(v -> map.putIfAbsent(v[0] + '.' + v[1], v[2]));
		return unmodifiableMap(map);
	}).memoize();

	private SearchResult parseSeries(String[] v) {
		int id = parseInt(v[0]);
		String name = v[1];
		String[] aliasNames = copyOfRange(v, 2, v.length);
		return new SearchResult(id, name, aliasNames);
	}

	private Movie parseMovie(String[] v) {
		int imdbid = parseInt(v[0]);
		int tmdbid = parseInt(v[1]);
		int year = parseInt(v[2]);
		String name = v[3];
		String[] aliasNames = copyOfRange(v, 4, v.length);
		return new Movie(name, aliasNames, year, imdbid > 0 ? imdbid : -1, tmdbid > 0 ? tmdbid : -1, null);
	}

	private SubtitleSearchResult parseSubtitle(String[] v) {
		String kind = v[0];
		int score = parseInt(v[1]);
		int imdbId = parseInt(v[2]);
		int year = parseInt(v[3]);
		String name = v[4];
		String[] aliasNames = copyOfRange(v, 5, v.length);
		return new SubtitleSearchResult(name, aliasNames, year, imdbId, -1, Locale.ENGLISH, SubtitleSearchResult.Kind.forName(kind), score);
	}

	protected Resource<String[]> lines(String name) {
		return resource(name, Function.identity(), String[]::new).memoize();
	}

	protected <A> Resource<A[]> tsv(String name, Function<String[], A> parse, IntFunction<A[]> generator) {
		return resource(name, s -> parse.apply(TAB.split(s)), generator).memoize();
	}

	protected <A> Resource<A[]> resource(String name, Function<String, A> parse, IntFunction<A[]> generator) {
		// UTF-8 text files with one entry per line, lines starting with # are comments
		return () -> NEWLINE.splitAsStream(UTF_8.decode(ByteBuffer.wrap(getData(name)))).filter(s -> s.length() > 0 && !s.startsWith("#")).map(parse).filter(Objects::nonNull).toArray(generator);
	}

	/**
	 * Detection data that ships with the application: generated indexes are XZ compressed (gradlew updateData), hand-maintained lists are plain text.
	 */
	protected byte[] getData(String name) throws IOException {
		String path = "/dev/davamu/filebotpro/data/" + name;

		try (InputStream xz = ReleaseInfo.class.getResourceAsStream(path + ".xz")) {
			if (xz != null) {
				try (InputStream in = new XZInputStream(xz)) {
					return in.readAllBytes();
				}
			}
		}

		try (InputStream in = ReleaseInfo.class.getResourceAsStream(path)) {
			if (in != null) {
				return in.readAllBytes();
			}
		}

		throw new FileNotFoundException("Missing data file: " + path);
	}

	protected String getProperty(String name) {
		// override resource locations via Java System properties
		return System.getProperty(name, getBundle(ReleaseInfo.class.getName()).getString(name));
	}

	public static class FolderEntryFilter implements FileFilter {

		private final Pattern entryPattern;

		public FolderEntryFilter(Pattern entryPattern) {
			this.entryPattern = entryPattern;
		}

		@Override
		public boolean accept(File dir) {
			return getChildren(dir, f -> entryPattern.matcher(f.getName()).matches()).size() > 0;
		}
	}

	public static class FileFolderNameFilter implements FileFilter {

		private final Pattern namePattern;

		public FileFolderNameFilter(Pattern namePattern) {
			this.namePattern = namePattern;
		}

		@Override
		public boolean accept(File file) {
			if (file.isFile()) {
				// check file name without extension and parent folder name
				return namePattern.matcher(getNameWithoutExtension(file.getName())).find() || namePattern.matcher(file.getParentFile().getName()).find();
			} else {
				// just check folder name
				return namePattern.matcher(file.getName()).find();
			}
		}
	}

	public static class ClutterFileFilter implements FileFilter {

		private FileFilter filter;
		private long maxFileSize;

		public ClutterFileFilter(FileFilter filter, long maxFileSize) {
			this.filter = filter;
			this.maxFileSize = maxFileSize;
		}

		@Override
		public boolean accept(File file) {
			return filter.accept(file) && file.isFile() && file.length() < maxFileSize;
		}
	}

	private String or(Object[] terms) {
		return join(stream(terms).sorted(reverseOrder()), "|", "(", ")"); // non-capturing group that matches the longest occurrence
	}

	private String[] quoteAll(Collection<String> values) {
		return values.stream().map((s) -> Pattern.quote(s)).toArray(String[]::new);
	}

	private Map<String, Locale> defaultLanguageMap;

	public Map<String, Locale> getDefaultLanguageMap() {
		if (defaultLanguageMap == null) {
			defaultLanguageMap = getLanguageMap(Locale.ENGLISH, Locale.getDefault());
		}
		return defaultLanguageMap;
	}

	public Map<String, Locale> getLanguageMap(Locale... displayLanguages) {
		// unique
		displayLanguages = stream(displayLanguages).distinct().toArray(Locale[]::new);

		// use maximum strength collator by default
		Collator collator = Collator.getInstance(Locale.ENGLISH);
		collator.setDecomposition(Collator.FULL_DECOMPOSITION);
		collator.setStrength(Collator.PRIMARY);

		Map<String, Locale> languageMap = new TreeMap<String, Locale>(collator);

		for (String code : Locale.getISOLanguages()) {
			Locale locale = new Locale(code); // force ISO3 language as default toString() value
			Locale iso3locale = new Locale(locale.getISO3Language());

			languageMap.put(locale.getLanguage(), iso3locale);
			languageMap.put(locale.getISO3Language(), iso3locale);

			// map display language names for given locales
			for (Locale language : displayLanguages) {
				// make sure language name is properly normalized so accents and whatever don't break the regex pattern syntax
				String languageName = Normalizer.normalize(locale.getDisplayLanguage(language), Form.NFKD);
				languageMap.put(languageName.toLowerCase(), iso3locale);
			}
		}

		// unofficial language for pb/pob for Portuguese (Brazil)
		Locale brazil = new Locale("pob");
		languageMap.put("brazilian", brazil);
		languageMap.put("pb", brazil);
		languageMap.put("pob", brazil);

		// missing ISO 639-2 (B/T) locales (see https://github.com/TakahikoKawasaki/nv-i18n/blob/master/src/main/java/com/neovisionaries/i18n/LanguageAlpha3Code.java)
		languageMap.put("tib", new Locale("bod"));
		languageMap.put("cze", new Locale("ces"));
		languageMap.put("wel", new Locale("cym"));
		languageMap.put("ger", new Locale("deu"));
		languageMap.put("gre", new Locale("ell"));
		languageMap.put("baq", new Locale("eus"));
		languageMap.put("per", new Locale("fas"));
		languageMap.put("fre", new Locale("fra"));
		languageMap.put("arm", new Locale("hye"));
		languageMap.put("ice", new Locale("isl"));
		languageMap.put("geo", new Locale("kat"));
		languageMap.put("mac", new Locale("mkd"));
		languageMap.put("mao", new Locale("mri"));
		languageMap.put("may", new Locale("msa"));
		languageMap.put("bur", new Locale("mya"));
		languageMap.put("dut", new Locale("nld"));
		languageMap.put("rum", new Locale("ron"));
		languageMap.put("slo", new Locale("slk"));
		languageMap.put("alb", new Locale("sqi"));
		languageMap.put("chi", new Locale("zho"));

		// remove illegal tokens
		languageMap.remove("");
		languageMap.remove("II");
		languageMap.remove("III");
		languageMap.remove("hi"); // hi => typically used for hearing-impaired subtitles, NOT hindi language

		return unmodifiableMap(languageMap);
	}

}
