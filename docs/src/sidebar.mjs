/**
 * Docs structure: the single source of truth for navigation, in both
 * languages.
 *
 * Every text is written as `{ es, en }`. Paths are written without a language
 * prefix (`/tools/rename/`): English lives at the root and Starlight prepends
 * `/es` on the Spanish pages.
 *
 * `sections` carries our own metadata (`icon`, `kicker`, `summary`) that
 * Starlight's schema does not accept, so two things are derived from it:
 *   · `sidebar`     → what astro.config.mjs consumes (valid fields only).
 *   · `sectionMeta` → a map by label (in either language) that Sidebar.astro
 *                     and the card grids read to look up the icon, kicker and
 *                     summary.
 */
export const sections = [
	{
		label: { es: 'Empezar', en: 'Getting started' },
		icon: 'rocket',
		kicker: { es: 'Primeros pasos', en: 'First steps' },
		summary: {
			es: 'Instala FileBot Pro, renombra tu primera serie en un minuto y conoce la interfaz.',
			en: 'Install FileBot Pro, rename your first show in a minute and get to know the interface.',
		},
		items: [
			{ label: { es: 'Instalación', en: 'Installation' }, link: '/getting-started/installation/' },
			{ label: { es: 'Docker y NAS', en: 'Docker and NAS' }, link: '/getting-started/docker/' },
			{ label: { es: 'Tu primer renombrado', en: 'Your first rename' }, link: '/getting-started/first-rename/' },
			{ label: { es: 'La interfaz', en: 'The interface' }, link: '/getting-started/interface/' },
		],
	},
	{
		label: { es: 'Herramientas', en: 'Tools' },
		icon: 'film',
		kicker: { es: 'Paneles', en: 'Panels' },
		summary: {
			es: 'Renombrar, listas de episodios, subtítulos, sumas de verificación, filtros y listas numeradas.',
			en: 'Renaming, episode lists, subtitles, checksums, filter tools and numbered lists.',
		},
		items: [
			{ label: { es: 'Renombrar', en: 'Rename' }, link: '/tools/rename/' },
			{ label: { es: 'Episodios', en: 'Episodes' }, link: '/tools/episodes/' },
			{ label: { es: 'Subtítulos', en: 'Subtitles' }, link: '/tools/subtitles/' },
			{ label: { es: 'SFV', en: 'SFV' }, link: '/tools/checksums/' },
			{ label: { es: 'Filtro', en: 'Filter' }, link: '/tools/filter/' },
			{ label: { es: 'Lista', en: 'List' }, link: '/tools/list/' },
		],
	},
	{
		label: { es: 'Formatos', en: 'Formats' },
		icon: 'code',
		kicker: { es: 'Expresiones', en: 'Expressions' },
		summary: {
			es: 'Cómo se construye cada nombre: sintaxis, todas las variables y ejemplos listos para copiar.',
			en: 'How every new name is built: syntax, every binding and ready-to-copy examples.',
		},
		items: [
			{ label: { es: 'Sintaxis', en: 'Syntax' }, link: '/formats/syntax/' },
			{ label: { es: 'Variables', en: 'Bindings' }, link: '/formats/bindings/' },
			{ label: { es: 'Ejemplos', en: 'Examples' }, link: '/formats/examples/' },
		],
	},
	{
		label: { es: 'Referencia', en: 'Reference' },
		icon: 'book',
		kicker: { es: 'Consulta', en: 'Lookup' },
		summary: {
			es: 'Línea de comandos, claves API opcionales, herramientas nativas, carpetas de datos y atajos de teclado.',
			en: 'Command line, optional API keys, native tools, data folders and keyboard shortcuts.',
		},
		items: [
			{ label: { es: 'Línea de comandos', en: 'Command line' }, link: '/reference/command-line/' },
			{ label: { es: 'Configuración', en: 'Configuration' }, link: '/reference/configuration/' },
			{ label: { es: 'Atajos de teclado', en: 'Keyboard shortcuts' }, link: '/reference/shortcuts/' },
		],
	},
	{
		label: { es: 'Ayuda', en: 'Help' },
		icon: 'lifebuoy',
		kicker: { es: 'Soporte', en: 'Support' },
		summary: {
			es: 'Preguntas frecuentes y qué revisar cuando algo no se detecta o no se renombra.',
			en: 'Frequently asked questions and what to check when something is not detected or renamed.',
		},
		items: [
			{ label: { es: 'Preguntas frecuentes', en: 'FAQ' }, link: '/help/faq/' },
			{ label: { es: 'Diagnóstico', en: 'Troubleshooting' }, link: '/help/troubleshooting/' },
		],
	},
	{
		label: { es: 'Desarrollo', en: 'Development' },
		icon: 'cube',
		kicker: { es: 'Código fuente', en: 'Source code' },
		summary: {
			es: 'Para quien quiere compilar FileBot Pro: instaladores, claves API incluidas y esta documentación.',
			en: 'For people who compile FileBot Pro: installers, embedded API keys and this documentation.',
		},
		items: [
			{ label: { es: 'Compilar desde el código', en: 'Building from source' }, link: '/development/building/' },
		],
	},
]

/** Language of a Starlight locale: the root is English. */
export const langOf = (locale) => (locale === 'es' ? 'es' : 'en')

/** URL prefix for the language: '' for English, '/es' for Spanish. */
export const prefixOf = (locale) => (langOf(locale) === 'es' ? '/es' : '')

/** Path without the language prefix, to compare it with the `link`s above. */
export const stripLocale = (pathname) => pathname.replace(/^\/es(?=\/|$)/, '') || '/'

/** What Starlight understands: `label`, `translations`, `items`, `link`. */
export const sidebar = sections.map(({ label, items }) => ({
	label: label.en,
	translations: { es: label.es },
	items: items.map((item) => ({
		label: item.label.en,
		translations: { es: item.label.es },
		link: item.link,
	})),
}))

/** Our own metadata, keyed by the group label in both languages. */
export const sectionMeta = Object.fromEntries(
	sections.flatMap((section) => [
		[section.label.es, section],
		[section.label.en, section],
	])
)
