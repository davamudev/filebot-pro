#!/usr/bin/env filebot-pro -script
// Modified for FileBot Pro on 2026-10-07, licensed under the GPL-3.0, see README.md


delete = 'DELETE'.equalsIgnoreCase(_args.action)
hardlink = 'HARDLINK'.equalsIgnoreCase(_args.action)
binary = 'BINARY'.equalsIgnoreCase(_args.mode)

// Binary Duplicates: Keep Input Argument Order
// Logical Duplicates: Order by Video Quality
order  = 'INPUT'  .equalsIgnoreCase(_args.order) ? 'INPUT'
       : 'QUALITY'.equalsIgnoreCase(_args.order) ? 'QUALITY'
       : 'SIZE'   .equalsIgnoreCase(_args.order) ? 'SIZE'
       : 'DATE'   .equalsIgnoreCase(_args.order) ? 'DATE'
       : 'TIME'   .equalsIgnoreCase(_args.order) ? 'TIME'
       : binary ? 'INPUT' : 'QUALITY'


// sanity checks
if (args.size() == 0) {
	die "Invalid usage: no input"
}

// only binary duplicates can be replaced with hard links
if (hardlink && !binary) {
	die "Invalid usage: --action hardlink requires --mode binary"
}


def group(files) {
	// Binary Duplicates: Group by File Size, then Fast MovieHash, then CRC32 via Xattr
	if (binary) {
		def groups = [:]

		// 0. Group by File Key (i.e. physical link duplicates are always binary duplicates)
		def links = files.groupBy{ f -> any{ f.key }{ f.canonicalFile }{ f } }.entrySet()

		// 1. Group by File Size 
		links.groupBy{ it.value[0].length() }.each{ size, size_fs ->
			if (size_fs.size() == 1) {
				groups.put(size_fs[0].key, size_fs[0].value)
				return
			}
			// 2. Group by MovieHash
			size_fs.groupBy{ it.value[0].hash('moviehash') }.each{ hash, hash_fs ->
				if (hash_fs.size() == 1) {
					groups.put(hash_fs[0].key, hash_fs[0].value)
					return
				}
				// 3. Group by CRC32 via Xattr
				hash_fs.groupBy{ it.value[0].CRC32 }.each{ crc, crc_fs ->
					groups.put([size, hash, crc], crc_fs.collectMany{ it.value })
				}
			}
		}
		return groups
	}

	// Logical Duplicates: Group by Xattr Metadata Object
	return files.findAll{ it.isVideo() }.groupBy{ f ->
		def m = f.metadata
		// the metadata comes from the file's xattr or .xattr folder, which can be copied from another file: before deleting anything,
		// only trust it if it agrees with the name of the file or its folders (equal ids alone do not make two files the same movie)
		if (m && delete && !matchesFileName(f, m)) {
			log.warning "[!] Ignore $f because its metadata [$m] does not match its name"
			return null
		}
		def v = m && _args.format ? getMediaInfo(f, _args.format) : null
		return m && v ? [m, v] : m
	}
}


def normalizeName(s) {
	return ' ' + s.toString().toLowerCase().replaceAll(/['’`]/, '').replaceAll(/[^\p{L}\p{N}]+/, ' ').trim() + ' '
}


def matchesFileName(f, m) {
	def path = normalizeName([f.nameWithoutExtension, f.dir?.name, f.dir?.dir?.name].findAll().join(' '))
	def name = { n -> n && path.contains(normalizeName(n)) }

	if (m instanceof Movie) {
		return ([m.name] + (m.aliasNames as List ?: [])).any(name) && (m.year <= 0 || path.contains(" ${m.year} "))
	}

	if (m instanceof Episode) {
		def series = ([m.seriesName] + (m.seriesInfo?.aliasNames as List ?: [])).any(name)
		def number = [
			m.season != null && m.episode != null ? /(?i)\bs0*${m.season}\s*e0*${m.episode}\b|\b0*${m.season}x0*${m.episode}\b/ : null,
			m.absolute != null ? /\b0*${m.absolute}\b/ : null,
			m.special != null ? /(?i)\bs0+\s*e0*${m.special}\b|\b0+x0*${m.special}\b/ : null
		].findAll().any{ f.name =~ it }
		return series && (number || name(m.title))
	}

	return true
}


def order(files) {
	switch(order) {
		case 'INPUT':
			return files
		case 'QUALITY':
			return files.toSorted(VideoQuality.DESCENDING_ORDER)
		case 'SIZE':
			return files.toSorted{ -(it.length()) }
		case 'DATE':
			return files.toSorted{ -(it.mediaCharacteristics?.creationTime?.toEpochMilli() ?: it.creationDate) }
		case 'TIME':
			return files.toSorted{ -(it.lastModified()) }
	}
}


// select video files (and preserve input argument order)
def files = args.files
def duplicates = []
def originals = [:]


group(files).each{ g, fs ->
	if (g && fs.size() > 1) {
		log.info "[*] $g"
		def ofs = order(fs).unique()
		ofs.eachWithIndex{ f, i ->
			if (i == 0) {
				log.finest "[+] 1. $f"
			} else if (binary && (delete || hardlink) && !f.isSameContent(ofs[0])) {
				// the CRC32 may come from a cached xattr or .xattr file that anyone can write: compare the content before deleting or replacing files
				log.warning "[!] ${i+1}. $f is not identical to ${ofs[0]}"
			} else {
				log.warning "[-] ${i+1}. $f"
				duplicates += f
				originals[f] = ofs[0]
			}
		}
	}
}


// no duplicates; return with NOOP
if (duplicates.size() == 0) {
	die "0 duplicates", ExitCode.NOOP
}


// continue with post-processing
log.fine "${duplicates.size()} duplicates"


// -mediainfo post-processing
if (_args.mediaInfo) {
	getMediaInfo(file: duplicates)
}


// -rename post-processing
if (_args.rename) {
	rename(file: duplicates, db: binary ? 'file' : 'xattr')
}


// delete duplicate files
if (delete) {
	duplicates.each{ f ->
		log.info "[DELETE] $f"
		f.trash()
	}
}


// replace duplicate files with hard links to the first file (same content, but stored only once)
if (hardlink) {
	duplicates.each{ f ->
		def original = originals[f]
		if (f.isSameFile(original)) {
			log.finest "[LINKED] $f"
		} else {
			log.info "[HARDLINK] $f => $original"
			f.hardlinkTo(original)
		}
	}
}
