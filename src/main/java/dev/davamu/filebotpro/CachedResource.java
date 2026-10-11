package dev.davamu.filebotpro;

import static dev.davamu.filebotpro.Logging.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.w3c.dom.Document;

import dev.davamu.filebotpro.util.ByteBufferInputStream;
import dev.davamu.filebotpro.util.ByteBufferOutputStream;
import dev.davamu.filebotpro.util.JsonUtilities;
import dev.davamu.filebotpro.web.WebRequest;

public class CachedResource<K, R> implements Resource<R> {

	public static final int DEFAULT_RETRY_LIMIT = 2;
	public static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(5);

	private K key;

	private Transform<K, URL> resource;
	private Fetch fetch;
	private Transform<ByteBuffer, ? extends Object> parse;
	private Transform<? super Object, R> cast;

	private Duration expirationTime;

	private int retryLimit;
	private Duration retryWait;

	private final Cache cache;

	public CachedResource(K key, Transform<K, URL> resource, Fetch fetch, Transform<ByteBuffer, ? extends Object> parse, Transform<? super Object, R> cast, Duration expirationTime, Cache cache) {
		this(key, resource, fetch, parse, cast, DEFAULT_RETRY_LIMIT, DEFAULT_RETRY_DELAY, expirationTime, cache);
	}

	public CachedResource(K key, Transform<K, URL> resource, Fetch fetch, Transform<ByteBuffer, ? extends Object> parse, Transform<? super Object, R> cast, int retryLimit, Duration retryWait, Duration expirationTime, Cache cache) {
		this.key = key;
		this.resource = resource;
		this.fetch = fetch;
		this.parse = parse;
		this.cast = cast;
		this.expirationTime = expirationTime;
		this.retryLimit = retryLimit;
		this.retryWait = retryWait;
		this.cache = cache;
	}

	public synchronized CachedResource<K, R> fetch(Fetch fetch) {
		this.fetch = fetch;
		return this;
	}

	public synchronized CachedResource<K, R> expire(Duration expirationTime) {
		this.expirationTime = expirationTime;
		return this;
	}

	public synchronized CachedResource<K, R> retry(int retryLimit) {
		this.retryLimit = retryLimit;
		return this;
	}

	@Override
	public synchronized R get() throws Exception {
		Object value = cache.computeIf(key, Cache.isStale(expirationTime), element -> {
			URL url = resource.transform(key);
			long lastModified = element == null ? 0 : element.getLatestOfCreationAndUpdateTime();

			try {
				ByteBuffer data = retry(() -> fetch.fetch(url, lastModified), retryLimit, retryWait);
				debug.finest(WebRequest.log(data));

				// 304 Not Modified
				if (data == null && element != null && element.getObjectValue() != null) {
					return element.getObjectValue();
				}

				if (data == null) {
					throw new IOException(String.format("Response data is null: %s => %s", key, url));
				}

				return parse.transform(data);
			} catch (Exception e) {
				// authentication errors (e.g. invalid API key): report without the request URL, which contains the key
				int status = getAuthenticationErrorStatus(e);
				if (status > 0) {
					e = new IOException(String.format("%s rejected the request (HTTP %d). Please check your API key.", url.getHost(), status));
				} else if (!redact(String.valueOf(e.getMessage())).equals(String.valueOf(e.getMessage()))) {
					// error messages of HttpURLConnection contain the request URL (including API keys)
					e = new IOException(redact(e.getMessage()));
				}

				debug.log(Level.SEVERE, "Fetch failed: " + redact(url), e);

				// use previously cached data if possible
				if (element == null || element.getObjectValue() == null) {
					throw e;
				}

				return element.getObjectValue();
			}
		});

		try {
			return cast.transform(value);
		} catch (Exception e) {
			throw new IllegalStateException(String.format("Failed to cast cached value: %s => %s (%s)", key, value, cache), e);
		}
	}

	protected <T> T retry(Callable<T> callable, int retryCount, Duration retryWaitTime) throws Exception {
		try {
			return callable.call();
		} catch (FileNotFoundException e) {
			// resource does not exist, do not retry
			throw e;
		} catch (IOException e) {
			// retry or rethrow exception (authentication errors such as an invalid API key are permanent)
			if (retryCount <= 0 || getAuthenticationErrorStatus(e) > 0) {
				throw e;
			}

			debug.warning(format("Fetch failed: Try again in %d seconds (%d more) => %s", retryWaitTime.getSeconds(), retryCount, redact(String.valueOf(e))));
			Thread.sleep(retryWaitTime.toMillis());
			return retry(callable, retryCount - 1, retryWaitTime.multipliedBy(2));
		}
	}

	private static int getAuthenticationErrorStatus(Exception e) {
		String message = String.valueOf(e.getMessage());
		for (int status : new int[] { 401, 403 }) {
			if (message.contains("HTTP response code: " + status)) {
				return status;
			}
		}
		return 0;
	}

	/**
	 * Hide API keys in log messages.
	 */
	private static String redact(URL url) {
		return redact(String.valueOf(url));
	}

	private static String redact(String text) {
		return WebRequest.redact(text);
	}

	@FunctionalInterface
	public interface Transform<T, R> {
		R transform(T object) throws Exception;
	}

	public static Transform<ByteBuffer, byte[]> getBytes() {
		return data -> {
			byte[] bytes = new byte[data.remaining()];
			data.get(bytes, 0, bytes.length);
			return bytes;
		};
	}

	public static Transform<ByteBuffer, byte[]> getBytes(Transform<InputStream, InputStream> decompressor) {
		return data -> {
			ByteBufferOutputStream buffer = new ByteBufferOutputStream(data.remaining());
			try (InputStream in = decompressor.transform(new ByteBufferInputStream(data))) {
				buffer.transferFully(in);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			return buffer.getByteArray();
		};
	}

	public static Transform<ByteBuffer, String> getText(Charset charset) {
		return data -> charset.decode(data).toString();
	}

	public static <T> Transform<T, String> validateXml(Transform<T, String> parse) {
		return object -> {
			String xml = parse.transform(object);
			try {
				WebRequest.validateXml(xml);
				return xml;
			} catch (Exception e) {
				throw new InvalidResponseException("Invalid XML", xml, e);
			}
		};
	}

	public static <T> Transform<T, String> validateJson(Transform<T, String> parse) {
		return object -> {
			String json = parse.transform(object);
			try {
				JsonUtilities.readJson(json);
				return json;
			} catch (Exception e) {
				throw new InvalidResponseException("Invalid JSON", json, e);
			}
		};
	}

	public static <T> Transform<T, Document> getXml(Transform<T, String> parse) {
		return object -> {
			return WebRequest.getDocument(parse.transform(object));
		};
	}

	public static <T> Transform<T, Object> getJson(Transform<T, String> parse) {
		return object -> {
			return JsonUtilities.readJson(parse.transform(object));
		};
	}

	@FunctionalInterface
	public interface Fetch {
		ByteBuffer fetch(URL url, long lastModified) throws Exception;
	}

	public static Fetch fetchIfModified() {
		return fetchIfModified(Collections::emptyMap);
	}

	public static Fetch fetchIfModified(Supplier<Map<String, String>> requestParameters) {
		return (url, lastModified) -> {
			debug.fine(WebRequest.log(url, lastModified, null));
			try {
				return WebRequest.fetch(url, lastModified, null, requestParameters.get(), null);
			} catch (FileNotFoundException e) {
				return fileNotFound(url, e);
			}
		};
	}

	public static Fetch fetchIfNoneMatch(Transform<URL, ?> key, Cache cache) {
		// create cache with the same config
		Resource<Cache> etagStorage = Resource.lazy(() -> Cache.getCache(cache.getName() + "_etag", cache.getCacheType()));

		// make sure value cache contains key, otherwise ignore previously stored etag
		return fetchIfNoneMatch(url -> {
			try {
				return cache.get(key.transform(url)) == null ? null : etagStorage.get().get(key.transform(url));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}, (url, etag) -> {
			try {
				etagStorage.get().put(key.transform(url), etag);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	public static Fetch fetchIfNoneMatch(Function<URL, Object> etagRetrieve, BiConsumer<URL, String> etagStore) {
		return (url, lastModified) -> {
			Object etagValue = etagRetrieve.apply(url);
			debug.fine(WebRequest.log(url, lastModified, etagValue));
			try {
				return WebRequest.fetch(url, etagValue == null ? lastModified : 0, etagValue, null, storeETag(url, etagStore, etag -> !etag.equals(etagValue)));
			} catch (FileNotFoundException e) {
				return fileNotFound(url, e);
			}
		};
	}

	private static Consumer<Map<String, List<String>>> storeETag(URL url, BiConsumer<URL, String> etagStore, Predicate<String> etagFilter) {
		return responseHeaders -> {
			WebRequest.getETag(responseHeaders).filter(etagFilter).ifPresent(etag -> {
				debug.finest(format("Store ETag: %s", etag));
				etagStore.accept(url, etag);
			});
		};
	}

	private static ByteBuffer fileNotFound(URL url, FileNotFoundException e) {
		debug.warning(format("Resource not found: %s", redact(url)));
		return ByteBuffer.allocate(0);
	}

	@FunctionalInterface
	public interface Permit {
		void acquire(URL resource) throws Exception;
	}

	public static Fetch withPermit(Fetch fetch, Permit permit) {
		return (url, lastModified) -> {
			permit.acquire(url);
			return fetch.fetch(url, lastModified);
		};
	}

}
