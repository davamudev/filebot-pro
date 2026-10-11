/**
 * Expressive Code plugin: writes the block's language where CSS can read it.
 *
 * The language only lives on `<pre data-language="…">`, while the label is painted on
 * `<figcaption class="header">`, its *previous* sibling. CSS cannot read a sibling's attribute,
 * and `attr()` only reads attributes of the element the pseudo-element belongs to, so the value
 * is copied to both places: the `<figure class="frame">` and the header itself.
 */

/** Readable names. Anything missing here is shown exactly as Shiki calls it. */
const NAMES = {
	bash: 'Terminal',
	sh: 'Terminal',
	shell: 'Terminal',
	zsh: 'Terminal',
	powershell: 'PowerShell',
	ps1: 'PowerShell',
	yaml: 'YAML',
	yml: 'YAML',
	json: 'JSON',
	ini: 'INI',
	toml: 'TOML',
	html: 'HTML',
	css: 'CSS',
	js: 'JavaScript',
	javascript: 'JavaScript',
	ts: 'TypeScript',
	typescript: 'TypeScript',
	py: 'Python',
	python: 'Python',
	java: 'Java',
	groovy: 'Groovy',
	properties: 'Properties',
	sql: 'SQL',
	diff: 'Diff',
	txt: 'Text',
	text: 'Text',
	plaintext: 'Text',
}

export function languageLabel() {
	return {
		name: 'frame-language-label',
		hooks: {
			postprocessRenderedBlock: ({ codeBlock, renderData }) => {
				// A fence without a language (bare ```) arrives here without `language`, but
				// Expressive Code still draws its frame: label it as text instead of leaving the
				// bar empty.
				const language = codeBlock.language || 'plaintext'
				const frame = renderData.blockAst
				if (!frame?.properties) return

				const name = NAMES[language] ?? language
				frame.properties['data-code-lang'] = name

				const header = (frame.children ?? []).find(
					(child) => child.type === 'element' && child.tagName === 'figcaption'
				)
				if (header?.properties) header.properties['data-code-lang'] = name
			},
		},
	}
}
