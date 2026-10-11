#!/usr/bin/env filebot-pro -script
// Modified for FileBot Pro on 2026-10-10, licensed under the GPL-3.0, see README.md

import dev.davamu.filebotpro.cli.PostProcess


// --output folder must be a valid folder
outputFolder = _args.absoluteOutputFolder

if (outputFolder == null || !outputFolder.isDirectory() || !outputFolder.canWrite()) {
	die "Invalid usage: output folder must exist and must be a writable directory: $outputFolder"
}


// --def poll=60 scans the folder every 60 seconds instead of waiting for file system events (network shares such as SMB or NFS mounts do not send events)
poll = tryQuietly{ poll as int }


def process(changes) {
	// log input files
	changes.each{ f -> log.fine "Input: $f" }

	// extract archives to output directory
	if (_args.extract) {
		changes += extract(file: changes.findAll{ it.archive }, output: outputFolder / 'Archive')
	}

	// rename input files
	if (_args.rename) {
		def destinationFiles = rename(file: changes)

		// post-process renamed files (e.g. --apply artwork nfo prune)
		def features = _args.postProcessFeatures
		if (destinationFiles && features && _args.renameAction != StandardRenameAction.TEST) {
			PostProcess.apply(destinationFiles, features)

			// delete left-behind empty folders, but never the watched folder itself
			if (features.contains(PostProcess.Feature.prune) && _args.renameAction == StandardRenameAction.MOVE) {
				PostProcess.prune(changes, args)
			}
		}
	}
}


if (poll) {
	// files that have been processed before (and have not been modified since)
	def processed = [:]

	log.info "Scan $args every $poll seconds"
	while (true) {
		def now = System.currentTimeMillis()
		// all files in the folder tree (hidden files and NAS system folders excluded)
		def files = args[0].getFiles{ it.isFile() }

		// only files that have not been modified for a few seconds (e.g. still being copied)
		def changes = files.findAll{ f -> processed[f] != f.lastModified() && now - f.lastModified() > 5000 }
		processed.keySet().retainAll(files)

		if (changes) {
			changes.each{ processed[it] = it.lastModified() }
			tryLogCatch{ process(changes) }
		}

		sleep(poll * 1000)
	}
}


// watch the folder tree (downloads usually arrive in subfolders) and process files that were added / modified, in
// batches per folder after 5s of settle down time
def watchman = args[0].watchFolder(true, true, 5000){ changes -> process(changes) }

if (System.console() != null && System.console().isTerminal()) {
	println "Press ENTER to exit..."
	console.readLine()
} else {
	// no interactive console (e.g. Docker container or background service): keep watching until the process is stopped
	log.info "Watching $args (stop the process to exit)"
	while (true) {
		sleep(Long.MAX_VALUE)
	}
}
