package dev.davamu.filebotpro.web;

import static java.nio.charset.StandardCharsets.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.util.JsonUtilities.*;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Minimal client for the OpenSubtitles.com REST API (https://opensubtitles.stoplight.io).
 *
 * Every request sends the application API key and User-Agent, and the JWT of the logged in user if there is one. The
 * API allows 5 requests per second, so requests are serialized and spaced out, and HTTP 429 responses are retried.
 */
public class OpenSubtitlesApi {

	public static final String DEFAULT_HOST = "api.opensubtitles.com";

	private final String apikey;
	private final String userAgent;

	private String host = DEFAULT_HOST;
	private String token;

	private final Object lock = new Object();
	private long lastRequest = 0;

	public OpenSubtitlesApi(String apikey, String userAgent) {
		this.apikey = apikey;
		this.userAgent = userAgent;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public synchronized void setSession(String host, String token) {
		this.host = host == null || host.isEmpty() ? DEFAULT_HOST : host;
		this.token = token;
	}

	public synchronized boolean hasSession() {
		return token != null;
	}

	/**
	 * GET request with parameters sorted and in lowercase as recommended by the API (avoids redirects and improves
	 * caching)
	 */
	public Object get(String path, Map<String, ?> parameters) throws IOException, InterruptedException {
		String query = new TreeMap<String, Object>(parameters).entrySet().stream().filter(it -> it.getValue() != null).map(it -> {
			return encode(it.getKey()) + "=" + encode(it.getValue().toString().toLowerCase());
		}).collect(Collectors.joining("&"));

		return send(path + (query.isEmpty() ? "" : "?" + query), null, "GET");
	}

	public Object post(String path, Map<String, ?> body) throws IOException, InterruptedException {
		return send(path, body, "POST");
	}

	public Object delete(String path) throws IOException, InterruptedException {
		return send(path, null, "DELETE");
	}

	protected Object send(String path, Map<String, ?> body, String method) throws IOException, InterruptedException {
		ApiKeys.require("OpenSubtitles", "opensubtitles", apikey);

		for (int retry = 0;; retry++) {
			HttpRequest.Builder request = HttpRequest.newBuilder(getEndpoint(path)).timeout(Duration.ofSeconds(60));
			request.header("Api-Key", apikey);
			request.header("User-Agent", userAgent);
			request.header("Accept", "application/json");

			String jwt = getToken();
			if (jwt != null) {
				request.header("Authorization", "Bearer " + jwt);
			}

			if (body != null) {
				request.header("Content-Type", "application/json");
				request.method(method, BodyPublishers.ofString(toJson(body), UTF_8));
			} else {
				request.method(method, BodyPublishers.noBody());
			}

			HttpResponse<String> response = throttle(() -> HttpClients.get().send(request.build(), BodyHandlers.ofString(UTF_8)));
			int status = response.statusCode();
			debug.finest(() -> String.format("[OpenSubtitles] %s %s => %d", method, path, status));

			// too many requests => wait and try again
			if (status == 429 && retry < 3) {
				long wait = response.headers().firstValue("Retry-After").map(Long::parseLong).orElse(1L);
				Thread.sleep(Math.max(1, Math.min(wait, 10)) * 1000);
				continue;
			}

			Object json = response.body().isEmpty() ? null : readJson(response.body());

			if (status >= 400) {
				String message = json == null ? null : getString(json, "message");
				throw new OpenSubtitlesException(status, message != null ? message : response.body());
			}

			return json;
		}
	}

	protected URI getEndpoint(String path) {
		String host;
		synchronized (this) {
			host = this.host;
		}
		return URI.create("https://" + host + "/api/v1/" + path);
	}

	protected synchronized String getToken() {
		return token;
	}

	/**
	 * Allow at most 4 requests per second
	 */
	private <T> T throttle(Request<T> request) throws IOException, InterruptedException {
		synchronized (lock) {
			long wait = lastRequest + 250 - System.currentTimeMillis();
			if (wait > 0) {
				Thread.sleep(wait);
			}
			try {
				return request.send();
			} finally {
				lastRequest = System.currentTimeMillis();
			}
		}
	}

	/**
	 * Plain JSON object for flat request bodies (the default JSON writer adds Java type information)
	 */
	protected static String toJson(Map<String, ?> body) {
		return body.entrySet().stream().map(it -> {
			Object value = it.getValue();
			String json = value instanceof Number || value instanceof Boolean ? value.toString() : value == null ? "null" : quote(value.toString());
			return quote(it.getKey()) + ":" + json;
		}).collect(Collectors.joining(",", "{", "}"));
	}

	private static String quote(String s) {
		StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
		for (char c : s.toCharArray()) {
			switch (c) {
			case '"':
				sb.append("\\\"");
				break;
			case '\\':
				sb.append("\\\\");
				break;
			default:
				if (c < 0x20) {
					sb.append(String.format("\\u%04x", (int) c));
				} else {
					sb.append(c);
				}
			}
		}
		return sb.append('"').toString();
	}

	private static String encode(String s) {
		return URLEncoder.encode(s, UTF_8).replace("+", "%20");
	}

	@FunctionalInterface
	private interface Request<T> {
		T send() throws IOException, InterruptedException;
	}

	public static class OpenSubtitlesException extends IOException {

		private final int status;

		public OpenSubtitlesException(int status, String message) {
			super(String.format("%d %s", status, message));
			this.status = status;
		}

		public int getStatus() {
			return status;
		}

	}

}
