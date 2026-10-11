#!/usr/bin/env filebot-pro -script
// Modified for FileBot Pro on 2026-10-07, licensed under the GPL-3.0, see README.md


// sanity checks
if (args.size() == 0) {
	die "Illegal usage: no input"
}


def episodes = []
def shows = [] as LinkedHashSet

args.getFiles().each{ f ->
	if (f.isVideo()) {
		def episode = f.metadata
		def seriesInfo = any{ episode.seriesInfo }{ null }

		if (episode instanceof Episode && seriesInfo instanceof SeriesInfo) {
			log.finest "$seriesInfo | $episode | $f"

			shows += seriesInfo

			if (episode instanceof MultiEpisode) {
				episodes += episode.episodes as List
			} else {
				episodes += episode
			}
		}
	}
}


if (episodes.size() == 0) {
	die "No xattr tagged files"
}


def episodeList = shows.collectMany{
	def db = getService(it.database)
	def el = db.getEpisodeList(it.id, it.order as SortOrder, it.language as Locale)
	// ignore special episodes
	return el.findAll{ it.regular }
} as LinkedHashSet


// print missing episodes
def missingEpisodes = episodeList - episodes


if (missingEpisodes.size() == 0) {
	log.finest "No missing episodes"
}


missingEpisodes.each{
	println it
}
