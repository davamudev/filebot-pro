#!/usr/bin/env filebot-pro -script
// Modified for FileBot Pro on 2026-10-07, licensed under the GPL-3.0, see README.md


def fetchMovieArtwork(movie, language, movieFolder) {
	fetchArtwork(movie, 'posters', language, movieFolder / 'poster.jpg')
	fetchArtwork(movie, 'backdrops', null, movieFolder / 'backdrop.jpg')
}


def fetchSeriesArtwork(series, language, seriesFolder, seasonFolder, season = null) {
	if (series.database == 'TheTVDB') {
		fetchArtwork(series, 'poster', language, seriesFolder / 'poster.jpg')
		fetchArtwork(series, 'series', language, seriesFolder / 'series.jpg')
		fetchArtwork(series, 'fanart', language, seriesFolder / 'fanart.jpg')
		if (season != null && seasonFolder != seriesFolder) {
			// season artwork must belong to this season (tagged [category, season number, ...])
			def isSeason = { a -> a.tags.size() > 1 && a.tags[1] == "$season" }
			fetchArtwork(series, 'season', language, seasonFolder / 'season.jpg', isSeason)
			fetchArtwork(series, 'seasonwide', language, seasonFolder / 'seasonwide.jpg', isSeason)
		}
		return
	}

	if (series.database == 'TheMovieDB::TV') {
		fetchArtwork(series, 'posters', language, seriesFolder / 'poster.jpg')
		fetchArtwork(series, 'backdrops', null, seriesFolder / 'backdrop.jpg')
		if (season != null && seasonFolder != seriesFolder) {
			fetchArtwork(series, 'season/' + season, language, seasonFolder / 'season.jpg')
		}
		return
	}

	log.warning "Artwork not supported: $series"
	return
}


def fetchEpisodeArtwork(episode, episodeFile) {
	def thumbnailFile = episodeFile.dir / episodeFile.nameWithoutExtension + '.jpg'
	if (thumbnailFile.exists()) {
		log.finest "[SKIP] Artwork already exists: $thumbnailFile"
		return
	}

	def i = episode.info
	if (i == null) {
		log.warning "Artwork not supported: $episode.seriesInfo"
		return
	}
	if (i.image == null) {
		log.warning "Artwork not found: $episode [$thumbnailFile]"
		return
	}

	log.fine "Fetch $i.image [$thumbnailFile]"
	i.image.saveAs(thumbnailFile)
}


def fetchArtwork(object, category, language, file, filter = { true }) {
	if (file.exists()) {
		log.finest "[SKIP] Artwork already exists: $file"
		return
	}

	// artwork is sorted by preference: requested language, then no text, then everything else (by rating)
	def artwork = object.getArtwork(category, language)?.findAll(filter)
	if (artwork == null || artwork.empty) {
		log.warning "Artwork not found: $object [$file]"
		return
	}

	// try the next image if one fails to download
	for (a in artwork.take(3)) {
		try {
			log.fine "Fetch $a [$file]"
			return a.url.saveAs(file)
		} catch (e) {
			log.warning "Failed to fetch $a: $e.message"
		}
	}
}




args.getFiles{ it.video }.each{ f ->
	def m = f.metadata
	switch(m) {
		case Movie:
			log.finest "[MOVIE] $m [$f]"
			fetchMovieArtwork(m, m.language, f.dir)
			break
		case Episode:
			log.finest "[EPISODE] $m [$f]"
			fetchSeriesArtwork(m.seriesInfo, m.seriesInfo.language, f.dir.dir, f.dir, m.special ? 0 : m.season)
			fetchEpisodeArtwork(m, f)
			break;
		default:
			log.finest "[XATTR NOT FOUND] $f"
			break
	}
}
