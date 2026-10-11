<div align="center">

<img src="assets/filebot-pro-logo.png" alt="" width="96" />

# FileBot Pro documentation

User guide and reference of the app, in English and Spanish —
written in Markdown, published as static HTML at
[filebotpro.davamu.dev](https://filebotpro.davamu.dev).

[![Astro](https://img.shields.io/badge/Astro-5.14-BC52EE?logo=astro&logoColor=white)](https://astro.build)
[![Starlight](https://img.shields.io/badge/Starlight-0.36-f97316?logo=astro&logoColor=white)](https://starlight.astro.build)

</div>

---

## What it is

A static site built with **Astro + Starlight**. No backend, no database: the content is MDX files,
`npm run build` generates the HTML in `dist/` and any web server can serve it.

| | |
| --- | --- |
| **Content** | MDX. English at the root (`/`, default language), Spanish under `/es/` |
| **Navigation** | A single file, `src/sidebar.mjs`, for both languages |
| **Search** | Pagefind, one index per language, generated at build time |
| **Themes** | Light and dark, with a custom switcher |

## Sections

| Section | Contents |
| --- | --- |
| **Getting started** | Installation, first rename and the interface |
| **Tools** | Rename, Episodes, Subtitles, SFV, Filter and List panels |
| **Formats** | Format expression syntax, every binding and examples |
| **Reference** | Command line, configuration (API keys, native tools, data folders, start options) and shortcuts |
| **Help** | FAQ and troubleshooting |

The app links here: the help menu, `F1` and the welcome screen open pages of this site (see `link.*` in
`src/main/resources/dev/davamu/filebotpro/Settings.properties`). Keep those paths stable.

## Getting started

Requires Node 20 or newer.

```bash
npm install
npm run dev        # http://localhost:4321
```

| Command | What it does |
| --- | --- |
| `npm run dev` | Development server with hot reload (search does not work here) |
| `npm run build` | Generates the site, the search index and the sitemap in `dist/` |
| `npm run preview` | Serves `dist/` on `:4321` |
| `npm run check` | Astro type check |

## Structure

```
src/
├── content/docs/      English pages, one folder per section
│   └── es/            The same pages, in Spanish
├── content/i18n/      Texts of the custom UI (es.json, en.json)
├── components/        Starlight overrides + custom components
├── styles/theme.css   Full design system
├── sidebar.mjs        Navigation for both languages: single source of truth
├── rehype/            Markdown plugins (tables, image plates)
└── ec/                Expressive Code plugin
assets/                Original high-resolution logo
public/                Favicon, robots.txt and screenshots (public/img/)
```

## Adding a page

1. Create `src/content/docs/<section>/<slug>.mdx` (English) with `title` and `description`.
2. Create its translation in `src/content/docs/es/<section>/<slug>.mdx`, **with the same path**. Internal links
   in the Spanish version carry the `/es/` prefix.
3. Register it in `src/sidebar.mjs` with the label in both languages and the path without prefix, ending in a
   slash.
4. `npm run build` to check that it builds.

A file whose name starts with `_` is left out of the build. An untranslated page is served under `/es/` with the
English text and a notice.

## Screenshots of the app

The images in `public/img/` are screenshots of FileBot Pro itself (light and dark theme). Replace them with the
same file names when the UI changes; pages reference them as `/img/<name>.png`, and clicking one opens it
enlarged.

## Languages

- English at the root (`locales.root`) and Spanish under `/es/`. A page without a Spanish twin is served at
  `/es/...` with the English text and a notice.
- `src/sidebar.mjs` exports `sidebar` (English labels + `translations.es`), `sectionMeta` (indexed by the label
  in both languages) and the `langOf`, `prefixOf` and `stripLocale` helpers used by the components.
- Texts needed by a client-side script travel in `data-*` attributes (see `Search.astro` and `Lightbox.astro`).
- Pagefind indexes each language separately, based on the `lang` of `<html>`.
- The app UI is in English, so button and menu names stay in English in both languages, in bold.

## Design

Inherited from `wiki.davamu.dev` and adapted to the app's orange accent:

- `--w-accent-50…900` ramp around `#f97316`. `--sl-color-accent` is `#fb923c` in dark mode and `#ea580c` in
  light mode (so it reads on white); a test checks it.
- Accent fills use `--w-accent-solid` and its ink `--w-on-accent`, never a hand-written `#fff`.
- `:root` is the dark theme and `:root[data-theme='light']` the light one. 4 px grid (`--w-space-*`),
  `--w-radius-pill` for actions and `--w-radius-lg` for surfaces. Breakpoint `50rem`.
- The document never scrolls horizontally: wide content scrolls inside its own box.

## Known pitfalls

- **`.header` is a repeated class** (the `<header>` and an inner `<div>`): rules use `header.header`.
- **A table cannot be both frame and scroll area**: scrolling lives in the `.w-table` added by
  `src/rehype/wrap-tables.mjs`.
- **`display` in a class rule beats `[hidden]`**: `[hidden] { display: none }` has to be added by hand.
- **Expressive Code's `.header::before`** is also the terminal traffic lights (`line-height: 0`); the language
  label resets it with `all: unset`. The bar sits at `z-index: 1` and the copy button above it.
- **Theme and language switchers render twice**: in the header and at the bottom of the mobile menu
  (`.mobile-preferences`).
- **`@astrojs/starlight/constants` is not exported**: `PAGE_TITLE_ID` is the literal `'_top'`.
- **The `Could not render /404` build warning** is harmless.
- **The home page sets its own `<title>`** through `head` in the frontmatter to avoid "FileBot Pro | FileBot Pro".
- **In tests**, locate by structure rather than translated text (`starlight-menu-button button`), and wait for
  the custom element to be registered before clicking it.

## Deployment

The build output is static HTML, CSS and JS:

```bash
SITE_URL=https://filebotpro.davamu.dev npm run build
```

`SITE_URL` (default `https://filebotpro.davamu.dev`) sets the sitemap, canonical URLs and the `hreflang` links
between languages. The site **is indexable**: `robots.txt` allows everything and points to the sitemap.

Serving it only needs a web server pointing at `dist/`. Example with Caddy:

```txt
filebotpro.davamu.dev {
	root * /usr/share/caddy/filebotpro.davamu.dev
	file_server
	encode gzip zstd
	try_files {path} {path}/ {path}.html
	handle_errors {
		rewrite * /404.html
		file_server
	}
}
```
