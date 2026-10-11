/**
 * Rehype plugin: puts a colored plate behind images with a transparent background.
 *
 * Why it is needed. A PNG logo with a transparent background carries single-color ink, meant
 * for the background of the site it came from. The Linux one is black: it vanished on the dark
 * theme. The Pterodactyl one is almost white, so it got lost on the light theme instead. The
 * site has both themes, so neither image can depend on the page background.
 *
 * The fix is not letting the background change: the image always sits on the surface it was
 * drawn for, light or dark, in both themes.
 *
 * It is marked in the Markdown with a URL fragment:
 *
 *     ![Linux](/img/linux/linux.png#plate-light)
 *     ![Pterodactyl](/img/pterodactyl/pterodactyl.png#plate-dark)
 *
 * The fragment accepts several comma-separated words, for an icon that also should not take up
 * the whole column:
 *
 *     ![Keyboard](/img/keyboards/keyboard.webp#plate-light,small)
 *
 * The fragment goes in the URL rather than in a class or an attribute so the Markdown stays
 * plain Markdown: browsers ignore a `#` in an image `src`, so if this plugin breaks the image
 * still loads the same.
 *
 * The tree is walked by hand, as in wrap-tables.mjs, to avoid depending on `unist-util-visit`,
 * which is only in the tree as a transitive dependency.
 */

const PLATES = {
	'plate-light': 'w-img-plate--light',
	'plate-dark': 'w-img-plate--dark',
}

/** A square icon stretched to the full column says nothing more: it just weighs more. */
const SIZES = {
	small: 'w-img--small',
}

export function imagePlates() {
	return (tree) => walk(tree)
}

function walk(node) {
	if (!Array.isArray(node.children)) return

	for (const child of node.children) {
		if (child.type !== 'element') continue
		if (child.tagName === 'img') applyPlate(child)
		walk(child)
	}
}

function applyPlate(img) {
	const src = img.properties?.src
	if (typeof src !== 'string') return

	const cut = src.lastIndexOf('#')
	if (cut === -1) return

	const words = src.slice(cut + 1).split(',')
	const plate = words.map((w) => PLATES[w]).find(Boolean)
	const size = words.map((w) => SIZES[w]).find(Boolean)
	if (!plate && !size) return

	// Strip the fragment from `src`: it has said what it had to say, and leaving it only
	// clutters the HTML.
	img.properties.src = src.slice(0, cut)

	const classes = img.properties.className ?? []
	img.properties.className = [
		...(Array.isArray(classes) ? classes : [classes]),
		...(plate ? ['w-img-plate', plate] : []),
		...(size ? [size] : []),
	]
}
