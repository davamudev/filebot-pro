/**
 * Remark plugin: replaces `{/* release-notes *\/}` in a page with the released versions of CHANGELOG.md
 * (the one at the root of the repository, also used for the GitHub releases and the update dialog of the app), so
 * the release notes page never goes out of sync with it.
 *
 * Every `## [1.2.3] - 2026-01-01` section becomes a `### 1.2.3` heading (id `v1-2-3`, so it is listed in the table of
 * contents under the page's own `##` heading) with its date and a link to the GitHub release, and its `### Added` /
 * `### Fixed` headings move one level down. `[Unreleased]` is left out: the docs only describe released versions.
 *
 * The changelog is parsed as plain Markdown, not MDX, so braces in format expressions (`{plex}`) stay text.
 */
import { readFileSync } from 'node:fs'
import { fromMarkdown } from 'mdast-util-from-markdown'

const CHANGELOG = new URL('../../../CHANGELOG.md', import.meta.url)
const RELEASES = 'https://github.com/davamudev/filebot-pro/releases/tag/v'
const MARKER = '/* release-notes */'

const labels = {
	en: { download: 'Downloads', date: (d) => `Released on ${d}` },
	es: { download: 'Descargas', date: (d) => `Publicada el ${d}` },
}

export function releaseNotes() {
	return (tree, file) => {
		const index = tree.children.findIndex((node) => node.type === 'mdxFlowExpression' && node.value.trim() === MARKER)
		if (index < 0) return

		const lang = /[\\/]es[\\/]/.test(file.path ?? '') ? 'es' : 'en'
		tree.children.splice(index, 1, ...changelogNodes(labels[lang]))
	}
}

function changelogNodes(label) {
	const sections = readFileSync(CHANGELOG, 'utf8').split(/^## /m).slice(1)
	const nodes = []

	for (const section of sections) {
		const [header, ...body] = section.split(/\r?\n/)
		const match = header.match(/^\[([^\]]+)\](?:\s*-\s*(\S+))?/)
		if (!match || match[1] === 'Unreleased') continue

		const [, version, date] = match
		nodes.push({
			type: 'heading',
			depth: 3,
			data: { hProperties: { id: `v${version.replace(/\W/g, '-')}` } },
			children: [{ type: 'text', value: version }],
		})

		const meta = [{ type: 'link', url: RELEASES + version, children: [{ type: 'text', value: label.download }] }]
		if (date) meta.unshift({ type: 'text', value: `${label.date(date)} · ` })
		nodes.push({ type: 'paragraph', children: meta })

		for (const node of fromMarkdown(body.join('\n')).children) {
			if (node.type === 'heading') node.depth = Math.min(node.depth + 1, 6)
			nodes.push(node)
		}
	}

	return nodes
}
