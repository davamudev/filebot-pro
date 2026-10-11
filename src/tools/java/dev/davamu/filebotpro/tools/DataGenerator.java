package dev.davamu.filebotpro.tools;

import static java.nio.charset.StandardCharsets.*;
import static java.util.stream.Collectors.*;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

import groovy.json.JsonSlurper;

/**
 * Generates the detection data that ships with the application, so that nothing has to be downloaded at runtime:
 * <ul>
 * <li>moviedb.txt, series.txt and osdb.txt from Wikidata (CC0), queried via QLever</li>
 * <li>anidb.txt from the anime-offline-database by manami-project (ODbL 1.0)</li>
 * <li>discdb.txt from TheDiscDB (MIT)</li>
 * </ul>
 *
 * usage: DataGenerator &lt;output folder&gt;
 */
public class DataGenerator {

	private static final String USER_AGENT = "FileBotPro-DataGenerator/1.0 (+https://filebotpro.davamu.dev)";
	private static final String WIKIDATA_SPARQL = "https://qlever.dev/api/wikidata";
	private static final String ANIME_DATABASE = "https://github.com/manami-project/anime-offline-database/releases/latest/download/anime-offline-database.jsonl";
	private static final String ANIME_DATABASE_LICENSE = "https://raw.githubusercontent.com/manami-project/anime-offline-database/master/LICENSE";
	private static final String ANIME_LISTS = "https://raw.githubusercontent.com/Anime-Lists/anime-lists/master/anime-list.xml";

	// popularity (number of Wikipedia articles) that a title needs to be included, keeps the indexes at a size that can be matched in memory quickly
	private static final int MOVIE_MIN_SITELINKS = 5;
	private static final int MOVIE_MIN_SITELINKS_SPANISH = 3;
	private static final int SERIES_MIN_SITELINKS = 1;

	private static final String PREFIXES = """
			PREFIX wdt: <http://www.wikidata.org/prop/direct/>
			PREFIX wikibase: <http://wikiba.se/ontology#>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
			PREFIX schema: <http://schema.org/>
			""";

	// movies with TMDb ID (P4947), IMDb ID (P345) and publication date (P577)
	private static final String MOVIES = PREFIXES + """
			SELECT ?item (MIN(?tmdb) AS ?id) (MIN(?imdbId) AS ?imdb) (MIN(YEAR(?date)) AS ?year) (MAX(?sitelinks) AS ?links) (SAMPLE(?labelEn) AS ?en) (SAMPLE(?labelEs) AS ?es) (SAMPLE(?wiki) AS ?eswiki)
			  (GROUP_CONCAT(DISTINCT ?title; separator="\\t") AS ?titles) (GROUP_CONCAT(DISTINCT ?alias; separator="\\t") AS ?aliases)
			WHERE {
			  ?item wdt:P4947 ?tmdb ; wdt:P345 ?imdbId ; wdt:P577 ?date ; wikibase:sitelinks ?sitelinks .
			  OPTIONAL { ?item rdfs:label ?labelEn FILTER(LANG(?labelEn) = "en") }
			  OPTIONAL { ?item rdfs:label ?labelEs FILTER(LANG(?labelEs) = "es") }
			  OPTIONAL { ?article schema:about ?item ; schema:isPartOf <https://es.wikipedia.org/> ; schema:name ?wiki }
			  OPTIONAL { ?item wdt:P1476 ?title }
			  OPTIONAL { ?item skos:altLabel ?alias FILTER(LANG(?alias) = "en" || LANG(?alias) = "es") }
			}
			GROUP BY ?item
			""";

	// series with the given ID property, start time (P580) or publication date (P577)
	private static String series(String idProperty) {
		return PREFIXES + """
				SELECT ?item (MIN(?key) AS ?id) (MIN(?imdbId) AS ?imdb) (MIN(YEAR(?start)) AS ?year) (MIN(YEAR(?date)) AS ?published) (MAX(?sitelinks) AS ?links) (SAMPLE(?labelEn) AS ?en) (SAMPLE(?labelEs) AS ?es) (SAMPLE(?wiki) AS ?eswiki)
				  (GROUP_CONCAT(DISTINCT ?title; separator="\\t") AS ?titles) (GROUP_CONCAT(DISTINCT ?alias; separator="\\t") AS ?aliases)
				WHERE {
				  ?item wdt:%s ?key ; wikibase:sitelinks ?sitelinks .
				  OPTIONAL { ?item wdt:P345 ?imdbId }
				  OPTIONAL { ?item wdt:P580 ?start }
				  OPTIONAL { ?item wdt:P577 ?date }
				  OPTIONAL { ?item rdfs:label ?labelEn FILTER(LANG(?labelEn) = "en") }
				  OPTIONAL { ?item rdfs:label ?labelEs FILTER(LANG(?labelEs) = "es") }
				  OPTIONAL { ?article schema:about ?item ; schema:isPartOf <https://es.wikipedia.org/> ; schema:name ?wiki }
				  OPTIONAL { ?item wdt:P1476 ?title }
				  OPTIONAL { ?item skos:altLabel ?alias FILTER(LANG(?alias) = "en" || LANG(?alias) = "es") }
				}
				GROUP BY ?item
				""".formatted(idProperty);
	}

	private static final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build();

	public static void main(String[] args) throws Exception {
		Path folder = Paths.get(args[0]);
		Files.createDirectories(folder);

		// only update TheDiscDB data (gradlew updateData -Ponly=discdb)
		if (args.length > 1 && args[1].equals("discdb")) {
			discdb(folder);
			return;
		}

		// NOTE: popularity is filtered here because QLever returns no results for FILTER on wikibase:sitelinks
		List<Title> movies = query(MOVIES).stream().filter(m -> m.links >= MOVIE_MIN_SITELINKS || (m.spanish && m.links >= MOVIE_MIN_SITELINKS_SPANISH)).toList();

		// series with TMDb TV ID (P4983), plus series that only have a TheTVDB ID (P4835)
		// NOTE: separate queries because QLever aggregates of a UNION drop the values of groups that come from both sides
		Map<String, Title> seriesByItem = new LinkedHashMap<String, Title>();
		query(series("P4983")).forEach(s -> seriesByItem.putIfAbsent(s.item, s));
		query(series("P4835")).forEach(s -> seriesByItem.putIfAbsent(s.item, s));
		List<Title> series = seriesByItem.values().stream().filter(s -> s.links >= SERIES_MIN_SITELINKS).toList();

		// imdb, tmdb, year, name, alias names
		write(folder.resolve("moviedb.txt.xz"), movies.stream().filter(m -> m.year > 0).sorted(Comparator.comparingInt(m -> m.id)).map(m -> row(m.imdb, m.id, m.year, m.names)).toList());

		// tmdb (or tvdb), name, alias names
		write(folder.resolve("series.txt.xz"), series.stream().sorted(Comparator.comparingInt(s -> s.id)).map(s -> row(s.id, s.withYear())).toList());

		// kind, score, imdb, year, name, alias names
		List<String> subtitles = new ArrayList<String>();
		movies.stream().filter(m -> m.imdb > 0 && m.year > 0).forEach(m -> subtitles.add(row("m", m.links, m.imdb, m.year, m.names)));
		series.stream().filter(s -> s.imdb > 0 && s.year > 0).forEach(s -> subtitles.add(row("s", s.links, s.imdb, s.year, s.names)));
		write(folder.resolve("osdb.txt.xz"), subtitles);

		// anidb, name, alias names (and anidb, picture url)
		anime(folder);

		// the anime list is a derivative database of the anime-offline-database and has to ship with its license (ODbL / DbCL)
		Files.write(folder.resolve("anidb.LICENSE.txt"), fetch(HttpRequest.newBuilder(URI.create(ANIME_DATABASE_LICENSE)).header("User-Agent", USER_AGENT).build()));

		// AniDB / TheTVDB episode mappings for local builds only: the Anime-Lists project has no license that allows publishing a copy, so the file is git-ignored
		System.out.println("Fetch " + ANIME_LISTS);
		byte[] animeLists = fetch(HttpRequest.newBuilder(URI.create(ANIME_LISTS)).header("User-Agent", USER_AGENT).build());
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (OutputStream xz = new XZOutputStream(buffer, new LZMA2Options(LZMA2Options.PRESET_MAX))) {
			xz.write(animeLists);
		}
		Files.write(folder.resolve("anime-list.xml.xz"), buffer.toByteArray());
		System.out.format("anime-list.xml.xz: %,d bytes (local builds only)%n", buffer.size());

		// episode titles of Blu-ray and DVD discs (MIT)
		discdb(folder);
	}

	private static final String DISCDB_TREE = "https://api.github.com/repos/TheDiscDb/data/git/trees/main?recursive=1";
	private static final String DISCDB_RAW = "https://raw.githubusercontent.com/TheDiscDb/data/main/";

	/**
	 * discdb.txt from TheDiscDB: the content of each title of a series or movie disc, i.e. episodes, main movies and
	 * extras (title index as listed by MakeMKV without a minimum title length, duration and size identify the title when
	 * the index is different)
	 */
	private static void discdb(Path folder) throws Exception {
		System.out.println("Fetch " + DISCDB_TREE);
		Map<?, ?> tree = (Map<?, ?>) new JsonSlurper().parse(fetch(HttpRequest.newBuilder(URI.create(DISCDB_TREE)).header("User-Agent", USER_AGENT).build()));
		List<String> paths = ((List<?>) tree.get("tree")).stream().map(it -> String.valueOf(((Map<?, ?>) it).get("path"))).toList();

		Pattern metadata = Pattern.compile("^data/(series|movie)/([^/]+)/metadata\\.json$");
		Pattern disc = Pattern.compile("^data/(series|movie)/([^/]+)/([^/]+)/disc(\\d+)\\.(json|ref)$");

		// TheMovieDB id of each series or movie folder
		Map<String, Integer> tmdbIds = new TreeMap<String, Integer>();
		parallel(paths.stream().filter(p -> metadata.matcher(p).matches()).toList(), (p, json) -> {
			if (json.get("ExternalIds") instanceof Map<?, ?> ids && number(String.valueOf(ids.get("Tmdb"))) > 0) {
				Matcher m = metadata.matcher(p);
				m.matches();
				synchronized (tmdbIds) {
					tmdbIds.put(m.group(1) + "/" + m.group(2), number(String.valueOf(ids.get("Tmdb"))));
				}
			}
		});

		// discs of each release (references point to the same disc in another release)
		Map<String, Map<?, ?>> discs = new TreeMap<String, Map<?, ?>>();
		List<String> files = paths.stream().filter(p -> disc.matcher(p).matches()).toList();
		parallel(files, (p, json) -> {
			synchronized (discs) {
				discs.put(p, json);
			}
		});

		List<String> rows = new ArrayList<String>();
		for (String p : files) {
			Matcher m = disc.matcher(p);
			m.matches();

			String kind = m.group(1).equals("series") ? "tv" : "movie";
			Integer tmdb = tmdbIds.get(m.group(1) + "/" + m.group(2));
			Map<?, ?> json = discs.get(p);
			if (json != null && json.containsKey("releasePath")) {
				json = discs.get("data/" + json.get("releasePath") + "/" + json.get("disc") + ".json");
			}
			if (tmdb == null || json == null || !(json.get("Titles") instanceof List<?> titles)) {
				continue;
			}

			for (Object it : titles) {
				Map<?, ?> title = (Map<?, ?>) it;
				if (!(title.get("Item") instanceof Map<?, ?> item) || item.get("Type") == null) {
					continue; // e.g. Play All titles or duplicates
				}

				String type = clean(String.valueOf(item.get("Type")));
				String name = clean(String.valueOf(item.get("Title")));
				int index = number(String.valueOf(title.get("Index")));
				int seconds = seconds(String.valueOf(title.get("Duration")));
				int season = number(String.valueOf(item.get("Season")));
				int episode = number(String.valueOf(item.get("Episode")));
				Object size = title.get("Size");

				// episodes need season and episode numbers, extras of a series may have them
				if (index < 0 || seconds <= 0 || type.isEmpty() || (type.equals("Episode") && (season < 0 || episode <= 0))) {
					continue;
				}

				// kind, tmdb, release, disc, title index, seconds, bytes, type, season, episode, title
				rows.add(row(kind, tmdb, m.group(3), Integer.parseInt(m.group(4)), index, seconds, size instanceof Number n ? n.longValue() : 0, type, season, episode, name));
			}
		}

		write(folder.resolve("discdb.txt.xz"), rows);
		Files.write(folder.resolve("discdb.LICENSE.txt"), fetch(HttpRequest.newBuilder(URI.create(DISCDB_RAW + "LICENSE")).header("User-Agent", USER_AGENT).build()));
	}

	private interface JsonConsumer {
		void accept(String path, Map<?, ?> json) throws Exception;
	}

	private static void parallel(List<String> paths, JsonConsumer consumer) throws Exception {
		System.out.format("Fetch %,d files from %s%n", paths.size(), DISCDB_RAW);

		ExecutorService executor = Executors.newFixedThreadPool(16);
		try {
			List<Future<?>> tasks = new ArrayList<Future<?>>();
			for (String path : paths) {
				tasks.add(executor.submit(() -> {
					String url = DISCDB_RAW + Stream.of(path.split("/")).map(s -> URLEncoder.encode(s, UTF_8).replace("+", "%20")).collect(joining("/"));
					byte[] bytes = fetch(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(1)).header("User-Agent", USER_AGENT).build());
					consumer.accept(path, (Map<?, ?>) new JsonSlurper().parseText(new String(bytes, UTF_8).replace("﻿", "")));
					return null;
				}));
			}
			for (Future<?> task : tasks) {
				task.get();
			}
		} finally {
			executor.shutdownNow();
		}
	}

	// e.g. 1:06:55
	private static int seconds(String duration) {
		int seconds = 0;
		for (String s : duration.trim().split(":")) {
			seconds = seconds * 60 + number(s);
		}
		return seconds;
	}

	private static class Title {

		String item;
		int id, imdb, year, links;
		boolean spanish;
		Set<String> names = new LinkedHashSet<String>();

		Collection<String> withYear() {
			Set<String> names = new LinkedHashSet<String>(this.names);
			if (year > 0) {
				names.add(this.names.iterator().next() + " (" + year + ")");
			}
			return names;
		}
	}

	private static List<Title> query(String sparql) throws Exception {
		System.out.println("Query " + WIKIDATA_SPARQL);

		HttpRequest request = HttpRequest.newBuilder(URI.create(WIKIDATA_SPARQL)).timeout(Duration.ofMinutes(10)).header("User-Agent", USER_AGENT).header("Accept", "text/csv").header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("query=" + URLEncoder.encode(sparql, UTF_8))).build();
		byte[] response = fetch(request);

		List<Title> titles = new ArrayList<Title>();
		try (BufferedReader csv = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(response), UTF_8))) {
			List<String> header = parseCsvRecord(csv);
			Map<String, Integer> column = new TreeMap<String, Integer>();
			for (int i = 0; i < header.size(); i++) {
				column.put(header.get(i), i);
			}

			for (List<String> record = parseCsvRecord(csv); record != null; record = parseCsvRecord(csv)) {
				Title t = new Title();
				t.item = record.get(column.get("item"));
				t.id = number(record.get(column.get("id")));
				t.imdb = number(record.get(column.get("imdb")));
				t.year = number(record.get(column.get("year")));
				t.links = number(record.get(column.get("links")));
				if (t.year <= 0 && column.containsKey("published")) {
					t.year = number(record.get(column.get("published")));
				}

				String en = clean(record.get(column.get("en")));
				String es = clean(record.get(column.get("es")));
				t.spanish = !es.isEmpty();

				add(t.names, en);
				split(record.get(column.get("titles"))).forEach(n -> add(t.names, n));
				add(t.names, es);
				// Spanish Wikipedia article, often named after the Spanish title, e.g. La teoría del todo (película de 2014)
				add(t.names, clean(record.get(column.get("eswiki"))));
				split(record.get(column.get("aliases"))).forEach(n -> add(t.names, n));

				if (t.id > 0 && !t.names.isEmpty()) {
					titles.add(t);
				}
			}
		}

		System.out.println("Received " + titles.size() + " titles");
		return titles;
	}

	private static void anime(Path folder) throws Exception {
		System.out.println("Fetch " + ANIME_DATABASE);

		byte[] response = fetch(HttpRequest.newBuilder(URI.create(ANIME_DATABASE)).timeout(Duration.ofMinutes(10)).header("User-Agent", USER_AGENT).build());

		Pattern anidb = Pattern.compile("^https://anidb\\.net/anime/(\\d+)$");
		Pattern image = Pattern.compile("(?i)^https://\\S+\\.(jpe?g|png|webp)$");
		Map<Integer, String> rows = new TreeMap<Integer, String>();
		Map<Integer, String> pictures = new TreeMap<Integer, String>();
		JsonSlurper json = new JsonSlurper();

		try (BufferedReader jsonl = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(response), UTF_8))) {
			for (String line = jsonl.readLine(); line != null; line = jsonl.readLine()) {
				if (!(json.parseText(line) instanceof Map<?, ?> anime) || !(anime.get("sources") instanceof List<?> sources)) {
					continue; // e.g. license header
				}

				for (Object source : sources) {
					Matcher m = anidb.matcher(String.valueOf(source));
					if (m.matches()) {
						Set<String> names = new LinkedHashSet<String>();
						add(names, clean(String.valueOf(anime.get("title"))));
						if (anime.get("synonyms") instanceof List<?> synonyms) {
							synonyms.forEach(s -> add(names, clean(String.valueOf(s))));
						}
						if (!names.isEmpty()) {
							rows.putIfAbsent(Integer.parseInt(m.group(1)), row(Integer.parseInt(m.group(1)), names));
						}
						if (image.matcher(String.valueOf(anime.get("picture"))).matches()) {
							pictures.putIfAbsent(Integer.parseInt(m.group(1)), row(Integer.parseInt(m.group(1)), anime.get("picture")));
						}
					}
				}
			}
		}

		write(folder.resolve("anidb.txt.xz"), new ArrayList<String>(rows.values()));
		write(folder.resolve("anidb-pictures.txt.xz"), new ArrayList<String>(pictures.values()));
	}

	/**
	 * Public endpoints with rate limits (HTTP 429), occasional overload (HTTP 5xx) and dropped connections, so try a few times.
	 */
	private static byte[] fetch(HttpRequest request) throws Exception {
		for (int attempt = 1; true; attempt++) {
			try {
				HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
				if (response.statusCode() == 200) {
					return response.body();
				}
				if (attempt >= 5 || (response.statusCode() != 429 && response.statusCode() < 500)) {
					throw new IllegalStateException("HTTP " + response.statusCode() + ": " + request.uri() + "\n" + new String(response.body(), UTF_8));
				}
				System.out.println("HTTP " + response.statusCode() + ": try again in " + attempt + " min");
			} catch (IOException e) {
				if (attempt >= 5) {
					throw e;
				}
				System.out.println(e + ": try again in " + attempt + " min");
			}
			Thread.sleep(Duration.ofMinutes(attempt));
		}
	}

	private static void write(Path file, List<String> rows) throws Exception {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (OutputStream xz = new XZOutputStream(buffer, new LZMA2Options(LZMA2Options.PRESET_MAX)); Writer out = new OutputStreamWriter(xz, UTF_8)) {
			for (String row : rows) {
				out.write(row);
				out.write('\n');
			}
		}
		Files.write(file, buffer.toByteArray());
		System.out.format("%s: %,d entries, %,d bytes%n", file.getFileName(), rows.size(), buffer.size());
	}

	private static String row(Object... values) {
		List<String> columns = new ArrayList<String>();
		for (Object value : values) {
			if (value instanceof Collection<?> collection) {
				collection.forEach(v -> columns.add(String.valueOf(v)));
			} else {
				columns.add(String.valueOf(value));
			}
		}
		return String.join("\t", columns);
	}

	// disambiguation of Wikipedia titles and labels, e.g. Ariel (film), Wolf (película de 2021), To Be or Not to Be (1942)
	private static final Pattern DISAMBIGUATION = Pattern.compile("(?i)\\s*\\((?:[^()]*\\b(?:película|pelicula|film|serie|series|miniserie|telenovela|programa|documental|anime|tv)\\b[^()]*|\\d{4})\\)$");

	private static void add(Set<String> names, String name) {
		name = DISAMBIGUATION.matcher(name).replaceFirst("").trim();

		// ignore short alias names (e.g. BB for Breaking Bad) that would match random words in file names
		if (name.length() >= (names.isEmpty() ? 1 : 3) && names.stream().noneMatch(name::equalsIgnoreCase)) {
			names.add(name);
		}
	}

	private static List<String> split(String values) {
		return values == null || values.isEmpty() ? List.of() : Pattern.compile("\t").splitAsStream(values).map(DataGenerator::clean).filter(s -> !s.isEmpty()).toList();
	}

	private static String clean(String value) {
		return value == null || value.equals("null") ? "" : value.replaceAll("\\s+", " ").trim();
	}

	private static int number(String value) {
		Matcher m = Pattern.compile("\\d+").matcher(value == null ? "" : value);
		return m.find() ? Integer.parseInt(m.group()) : -1;
	}

	/**
	 * RFC 4180 record (quoted fields may contain separators, quotes and line breaks)
	 */
	private static List<String> parseCsvRecord(BufferedReader in) throws Exception {
		List<String> fields = new ArrayList<String>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;

		int c = in.read();
		if (c < 0) {
			return null;
		}

		for (; c >= 0; c = in.read()) {
			if (quoted) {
				if (c == '"') {
					in.mark(1);
					if (in.read() == '"') {
						field.append('"');
					} else {
						in.reset();
						quoted = false;
					}
				} else {
					field.append((char) c);
				}
			} else if (c == '"') {
				quoted = true;
			} else if (c == ',') {
				fields.add(field.toString());
				field.setLength(0);
			} else if (c == '\n') {
				break;
			} else if (c != '\r') {
				field.append((char) c);
			}
		}

		fields.add(field.toString());
		return fields;
	}

}
