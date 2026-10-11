package dev.davamu.filebotpro;

import java.time.Duration;

public enum CacheType {

	Persistent(Duration.ofDays(180)),

	Monthly(Duration.ofDays(60)),

	Weekly(Duration.ofDays(12)),

	Daily(Duration.ofHours(18));

	private final Duration timeToLive;

	private CacheType(Duration timeToLive) {
		this.timeToLive = timeToLive;
	}

	/**
	 * Expiry of the entries of this cache (counted from the moment they were stored)
	 */
	public Duration getTimeToLive() {
		return timeToLive;
	}

}
