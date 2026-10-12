export type Lang = 'en' | 'es';

export type Strings = {
	chaos: {title: string; subtitle: string};
	logo: {tagline: string};
	free: {words: [string, string, string]; badges: string[]};
	demo: {title: string; steps: [string, string, string]; matching: string; done: string};
	databases: {title: string; subtitle: string};
	examples: {title: string; movies: string; tv: string; anime: string; before: string; after: string};
	format: {title: string; subtitle: string; presets: string; preview: string};
	features: {title: string; items: {title: string; text: string}[]};
	cli: {title: string; subtitle: string; tags: string[]};
	platforms: {title: string; subtitle: string};
	cta: {title: string; subtitle: string; star: string; footer: string};
};

// The app itself is in English, so the recreated windows keep their English labels in both versions
const en: Strings = {
	chaos: {
		title: 'Your media library is a mess.',
		subtitle: 'Cryptic names. Random folders. Missing episodes.',
	},
	logo: {
		tagline: 'Rename and organize your movies, TV shows and anime. Automatically.',
	},
	free: {
		words: ['Free.', 'Open source.', 'For everyone.'],
		badges: ['GPL-3.0', 'No ads', 'No subscriptions', 'No account required'],
	},
	demo: {
		title: 'From chaos to a clean library in three clicks',
		steps: ['Drop your files', 'Match', 'Rename'],
		matching: 'Matching…',
		done: '5 files renamed',
	},
	databases: {
		title: 'Powered by the best online databases',
		subtitle: 'Titles, episodes, years, artwork and subtitles, matched for you.',
	},
	examples: {
		title: 'Every file, exactly where it belongs',
		movies: 'Movies',
		tv: 'TV shows',
		anime: 'Anime',
		before: 'Before',
		after: 'After',
	},
	format: {
		title: 'Your names, your rules',
		subtitle: 'Write your own naming scheme with format expressions and see the result as you type.',
		presets: 'Or use a ready-made layout for your media server:',
		preview: 'Preview',
	},
	features: {
		title: 'And there is much more',
		items: [
			{title: 'Subtitles', text: 'Search and download them from OpenSubtitles, by file hash or by name.'},
			{title: 'Checksums', text: 'Create and verify SFV, MD5, SHA-1 and SHA-256 files.'},
			{title: 'Filter tools', text: 'Inspect media info, extract archives, split and tidy up folders.'},
			{title: 'Episode lists', text: 'Browse the full episode guide of any show and export it.'},
			{title: 'Artwork', text: 'Posters and fanart for Plex, Jellyfin, Emby and Kodi.'},
			{title: 'Light & dark', text: 'A modern, animated interface that follows your system theme.'},
		],
	},
	cli: {
		title: 'Set it and forget it',
		subtitle: 'Command line and scripts like fn:amc sort every new download into your library on their own.',
		tags: ['Torrent clients', 'Docker', 'Synology NAS', 'Groovy scripts'],
	},
	platforms: {
		title: 'Runs everywhere',
		subtitle: 'Java runtime included. Nothing else to install.',
	},
	cta: {
		title: 'Download it free',
		subtitle: 'Take back control of your library today.',
		star: 'Give it a star on GitHub',
		footer: 'Free and open source · GPL-3.0',
	},
};

const es: Strings = {
	chaos: {
		title: 'Tu biblioteca multimedia es un caos.',
		subtitle: 'Nombres crípticos. Carpetas sin orden. Episodios perdidos.',
	},
	logo: {
		tagline: 'Renombra y organiza tus películas, series y anime. Automáticamente.',
	},
	free: {
		words: ['Gratis.', 'Open source.', 'Para todos.'],
		badges: ['GPL-3.0', 'Sin anuncios', 'Sin suscripciones', 'Sin registro'],
	},
	demo: {
		title: 'Del caos a una biblioteca impecable en tres clics',
		steps: ['Arrastra tus archivos', 'Busca coincidencias', 'Renombra'],
		matching: 'Buscando…',
		done: '5 archivos renombrados',
	},
	databases: {
		title: 'Con las mejores bases de datos online',
		subtitle: 'Títulos, episodios, años, carátulas y subtítulos, identificados por ti.',
	},
	examples: {
		title: 'Cada archivo, justo donde debe estar',
		movies: 'Películas',
		tv: 'Series',
		anime: 'Anime',
		before: 'Antes',
		after: 'Después',
	},
	format: {
		title: 'Tus nombres, tus reglas',
		subtitle: 'Crea tu propio esquema de nombres con expresiones de formato y ve el resultado mientras escribes.',
		presets: 'O usa una estructura lista para tu servidor multimedia:',
		preview: 'Vista previa',
	},
	features: {
		title: 'Y mucho más',
		items: [
			{title: 'Subtítulos', text: 'Búscalos y descárgalos de OpenSubtitles, por hash del archivo o por nombre.'},
			{title: 'Checksums', text: 'Crea y verifica archivos SFV, MD5, SHA-1 y SHA-256.'},
			{title: 'Herramientas', text: 'Analiza la información multimedia, extrae archivos y ordena carpetas.'},
			{title: 'Listas de episodios', text: 'Consulta la guía completa de episodios de cualquier serie y expórtala.'},
			{title: 'Carátulas', text: 'Pósters y fanart para Plex, Jellyfin, Emby y Kodi.'},
			{title: 'Claro y oscuro', text: 'Una interfaz moderna y animada que sigue el tema de tu sistema.'},
		],
	},
	cli: {
		title: 'Configúralo y olvídate',
		subtitle: 'La línea de comandos y scripts como fn:amc colocan cada nueva descarga en tu biblioteca sin que hagas nada.',
		tags: ['Clientes torrent', 'Docker', 'NAS Synology', 'Scripts Groovy'],
	},
	platforms: {
		title: 'Funciona en todas partes',
		subtitle: 'Incluye su propio Java. No hace falta instalar nada más.',
	},
	cta: {
		title: 'Descárgalo gratis',
		subtitle: 'Recupera el control de tu biblioteca hoy mismo.',
		star: 'Dale una estrella en GitHub',
		footer: 'Gratis y open source · GPL-3.0',
	},
};

export const STRINGS: Record<Lang, Strings> = {en, es};
