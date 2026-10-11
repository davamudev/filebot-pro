package dev.davamu.filebotpro;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.CachedResource.*;
import static dev.davamu.filebotpro.Logging.*;

import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.w3c.dom.Document;

import dev.davamu.filebotpro.CachedResource.Transform;

public class Cache {

	// grace period to make sure data is always fresh when TTL is almost about to be reached
	public static final Duration ONE_DAY = Duration.ofHours(18);
	public static final Duration ONE_WEEK = Duration.ofDays(6);
	public static final Duration ONE_MONTH = Duration.ofDays(24);

	public static Cache getCache(String name, CacheType type) {
		return CacheManager.getInstance().getCache(name.toLowerCase() + "_" + type.ordinal(), type);
	}

	public <T> CachedResource<T, byte[]> bytes(T key, Transform<T, URL> resource) {
		return new CachedResource<T, byte[]>(key, resource, fetchIfModified(), getBytes(), byte[].class::cast, ONE_DAY, this);
	}

	public <T> CachedResource<T, byte[]> bytes(T key, Transform<T, URL> resource, Transform<InputStream, InputStream> decompressor) {
		return new CachedResource<T, byte[]>(key, resource, fetchIfModified(), getBytes(decompressor), byte[].class::cast, ONE_DAY, this);
	}

	public <T> CachedResource<T, String> text(T key, Transform<T, URL> resource) {
		return new CachedResource<T, String>(key, resource, fetchIfModified(), getText(UTF_8), String.class::cast, ONE_DAY, this);
	}

	public <T> CachedResource<T, Document> xml(T key, Transform<T, URL> resource) {
		return new CachedResource<T, Document>(key, resource, fetchIfModified(), validateXml(getText(UTF_8)), getXml(String.class::cast), ONE_DAY, this);
	}

	public <T> CachedResource<T, Object> json(T key, Transform<T, URL> resource) {
		return new CachedResource<T, Object>(key, resource, fetchIfModified(), validateJson(getText(UTF_8)), getJson(String.class::cast), ONE_DAY, this);
	}

	/**
	 * A stored value together with the time it was stored.
	 */
	public static final class Entry implements Serializable {

		private static final long serialVersionUID = 1L;

		private final Object key;
		private final Object value;
		private final long created;
		private final long timeToLiveMillis;

		Entry(Object key, Object value, long timeToLiveMillis) {
			this.key = key;
			this.value = value;
			this.created = System.currentTimeMillis();
			this.timeToLiveMillis = timeToLiveMillis;
		}

		public Object getKey() {
			return key;
		}

		public Object getObjectValue() {
			return value;
		}

		public long getLatestOfCreationAndUpdateTime() {
			return created;
		}

		/** Expiry of this entry in milliseconds, or 0 for the expiry of the cache type */
		long getTimeToLiveMillis() {
			return timeToLiveMillis;
		}
	}

	private final String name;
	private final DiskCache cache;
	private final CacheType cacheType;

	Cache(String name, DiskCache cache, CacheType cacheType) {
		this.name = name;
		this.cache = cache;
		this.cacheType = cacheType;
	}

	public String getName() {
		return name;
	}

	public CacheType getCacheType() {
		return cacheType;
	}

	public Object get(Object key) {
		try {
			return getElementValue(cache.get(key));
		} catch (Exception e) {
			debug.warning(format("Cache get: %s => %s", key, e));
			evict(key);
		}
		return null;
	}

	/**
	 * Remove an entry that can't be read anymore (e.g. serialized by an incompatible version of its class) so it can be
	 * replaced
	 */
	private void evict(Object key) {
		try {
			cache.remove(key);
		} catch (Exception e) {
			debug.warning(format("Cache remove: %s => %s", key, e));
		}
	}

	public Object computeIf(Object key, Predicate<Entry> condition, Compute<?> compute) throws Exception {
		// get if present
		Entry element = null;
		try {
			element = cache.get(key);
			if (element != null && !condition.test(element)) {
				return getElementValue(element);
			}
		} catch (Exception e) {
			debug.warning(format("Cache computeIf: %s => %s", key, e));
			evict(key);
		}

		// compute if absent
		Object value = compute.apply(element);
		put(key, value);
		return value;
	}

	public Object computeIfAbsent(Object key, Compute<?> compute) throws Exception {
		return computeIf(key, it -> it == null, compute);
	}

	public void put(Object key, Object value) {
		put(key, value, null);
	}

	/**
	 * @param timeToLive
	 *            expiry of this entry instead of the expiry of the cache type (e.g. longer for data that no longer
	 *            changes), or null
	 */
	public void put(Object key, Object value, Duration timeToLive) {
		try {
			cache.put(createEntry(key, value, timeToLive == null ? 0 : timeToLive.toMillis()));
		} catch (Exception e) {
			debug.warning(format("Cache put: %s => %s", key, e));
		}
	}

	protected Object getElementValue(Entry element) {
		return element == null ? null : element.getObjectValue();
	}

	protected Entry createEntry(Object key, Object value, long timeToLiveMillis) {
		return new Entry(key, value, timeToLiveMillis);
	}

	public void remove(Object key) {
		try {
			cache.remove(key);
		} catch (Exception e) {
			debug.warning(format("Cache remove: %s => %s", key, e));
		}
	}

	/**
	 * Entries are written to disk as soon as they are stored, there is nothing left to flush.
	 */
	public void flush() {
	}

	public void clear() {
		try {
			cache.clear();
		} catch (Exception e) {
			debug.warning(format("Cache clear: %s => %s", name, e));
		}
	}

	@Override
	public String toString() {
		return name;
	}

	public static Predicate<Entry> isStale(Duration expirationTime) {
		return element -> System.currentTimeMillis() - element.getLatestOfCreationAndUpdateTime() > expirationTime.toMillis();
	}

	@FunctionalInterface
	public interface Compute<R> {
		R apply(Entry element) throws Exception;
	}

	public <V> TypedCache<V> typed(Function<Object, V> read, Function<V, Object> write) {
		return new TypedCache<V>(name, cache, cacheType, read, write);
	}

	public <V> TypedCache<V> cast(Class<V> cls) {
		return new TypedCache<V>(name, cache, cacheType, it -> cls.cast(it), it -> it);
	}

	public <V> TypedCache<List<V>> castList(Class<V> cls) {
		return new TypedCache<List<V>>(name, cache, cacheType, it -> it == null ? null : stream((Object[]) it).map(cls::cast).collect(toList()), it -> it == null ? null : it.toArray());
	}

	public static class TypedCache<V> extends Cache {

		private final Function<Object, V> read;
		private final Function<V, Object> write;

		TypedCache(String name, DiskCache cache, CacheType cacheType, Function<Object, V> read, Function<V, Object> write) {
			super(name, cache, cacheType);
			this.read = read;
			this.write = write;
		}

		@Override
		public V get(Object key) {
			return (V) super.get(key);
		}

		@Override
		public V computeIf(Object key, Predicate<Entry> condition, Compute<?> compute) throws Exception {
			return (V) super.computeIf(key, condition, compute);
		}

		@Override
		public V computeIfAbsent(Object key, Compute<?> compute) throws Exception {
			return (V) super.computeIfAbsent(key, compute);
		}

		@Override
		protected Object getElementValue(Entry element) {
			return read.apply(super.getElementValue(element));
		}

		@Override
		protected Entry createEntry(Object key, Object value, long timeToLiveMillis) {
			return super.createEntry(key, write.apply((V) value), timeToLiveMillis);
		}
	}

}
