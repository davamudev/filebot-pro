package dev.davamu.filebotpro.format;

import java.io.File;
import java.util.function.UnaryOperator;

import dev.davamu.filebotpro.media.NamingStandard;

/**
 * Path of the {plex}, {kodi}, {emby} and {jellyfin} bindings. It is a plain File (so {plex.tail}, {plex.name}, etc. still work) with
 * an id variant: {plex.id} adds database id tags to the movie or series folder, e.g. Movies/The Matrix (1999) {tmdb-603}/The Matrix (1999)
 * and a year variant: {plex.year} uses Series Name (Year) for series folders and episode names (and can be combined, e.g. {plex.year.id})
 */
public class StandardPath extends File {

	private final transient NamingStandard standard;
	private final transient Object info;
	private final String suffix;
	private final boolean year;

	// e.g. move disc extras into the extras folder of the movie (or the identity function)
	private final transient UnaryOperator<String> location;

	public StandardPath(String path, NamingStandard standard, Object info, String suffix) {
		this(path, standard, info, suffix, UnaryOperator.identity());
	}

	public StandardPath(String path, NamingStandard standard, Object info, String suffix, UnaryOperator<String> location) {
		this(path, standard, info, suffix, false, location);
	}

	private StandardPath(String path, NamingStandard standard, Object info, String suffix, boolean year, UnaryOperator<String> location) {
		super(location.apply(path) + suffix);
		this.standard = standard;
		this.info = info;
		this.suffix = suffix;
		this.year = year;
		this.location = location;
	}

	public File getId() {
		return new File(location.apply(standard.getPath(info, true, year)) + suffix);
	}

	public StandardPath getYear() {
		return new StandardPath(standard.getPath(info, false, true), standard, info, suffix, true, location);
	}

}
