/**
 * Rehype plugin: wraps every content `<table>` in a `<div class="w-table">`.
 *
 * Why it is needed. The obvious way to make a wide table scroll inside its box is giving it
 * `display: block; overflow-x: auto`, but that breaks the table: the rows end up in an anonymous
 * table box that shrinks to fit its content, while the border and radius follow the block's
 * width. Narrow tables showed the frame reaching the end of the container and the rows cut off
 * halfway through the box.
 *
 * With a dedicated container the table goes back to `display: table` and the scroll lives on
 * the `<div>`, so border, radius and rows always line up.
 *
 * The tree is walked by hand instead of using `unist-util-visit` to avoid depending on a package
 * that is only in the tree as a transitive dependency of Astro.
 */
export function wrapTables() {
	return (tree) => wrapChildren(tree)
}

function wrapChildren(node) {
	if (!Array.isArray(node.children)) return

	for (let i = 0; i < node.children.length; i += 1) {
		const child = node.children[i]

		// A node that is not an HTML element can still have children: an MDX component such as
		// `<Steps>` arrives here as `mdxJsxFlowElement`, with the tables inside it. Skipping it
		// left those tables unwrapped, so they overflowed their box instead of scrolling.
		if (child.type !== 'element') {
			wrapChildren(child)
			continue
		}

		// An already wrapped table is left alone: the plugin is idempotent.
		const alreadyWrapped =
			node.tagName === 'div' && !!node.properties?.className?.includes?.('w-table')

		if (child.tagName === 'table' && !alreadyWrapped) {
			// `tabindex` makes the box scrollable with the keyboard too; no `role="region"`, which
			// would require an accessible name per table.
			node.children[i] = {
				type: 'element',
				tagName: 'div',
				properties: { className: ['w-table'], tabindex: 0 },
				children: [child],
			}
			continue
		}

		wrapChildren(child)
	}
}
