#!/usr/bin/env filebot-pro -script
// Modified for FileBot Pro on 2026-10-07, licensed under the GPL-3.0, see README.md


/*
 * Extract all zip and rar archives into their current location
 */
args.getFiles{ it.isArchive() }.each {
	extract(file: it, output: it.dir)
}
