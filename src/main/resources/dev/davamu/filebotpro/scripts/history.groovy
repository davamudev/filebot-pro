#!/usr/bin/env filebot-pro -script
// Modified for FileBot Pro on 2026-10-07, licensed under the GPL-3.0, see README.md


// use args to list history only for the given folders if desired
def accept(from, to) {
	return args.empty || (args.any{ to.absolutePath.startsWith(it.absolutePath) } && to.exists())
}


// read history file
def history = getPersistentRenameLog()

// sanity check
if (history.empty) {
	die "No History", ExitCode.NOOP
}

history.each{ from, to ->
	if (accept(from, to)) {
		println "${from}\t${to}"
	}
}
