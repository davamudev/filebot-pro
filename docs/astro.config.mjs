// @ts-check
import { defineConfig } from 'astro/config'
import starlight from '@astrojs/starlight'
import { sidebar } from './src/sidebar.mjs'
import { wrapTables } from './src/rehype/wrap-tables.mjs'
import { imagePlates } from './src/rehype/image-plates.mjs'
import { languageLabel } from './src/ec/language-label.mjs'

export default defineConfig({
  // The sitemap, canonical URLs and hreflang links are built from this.
  site: process.env.SITE_URL ?? 'https://filebotpro.davamu.dev',
  server: { host: true, port: 4321 },
  markdown: {
    // Every table is wrapped in its own scroll container: without it the table
    // is both the bordered box and the scrollable area, and rows narrower than
    // the container left the border and header cut off halfway.
    // Images with a transparent background also get a plate underneath: a
    // logo's ink does not change with the theme, so it is the background that
    // has to stay put. See src/rehype/image-plates.mjs.
    rehypePlugins: [wrapTables, imagePlates],
  },
  integrations: [
    starlight({
      title: 'FileBot Pro',
      description:
        'Documentation for FileBot Pro, the desktop app that organizes and renames movies, TV shows and anime and fetches subtitles and artwork.',
      expressiveCode: {
        plugins: [languageLabel()],
      },
      head: [
        { tag: 'link', attrs: { rel: 'icon', type: 'image/png', sizes: '192x192', href: '/favicon.png' } },
        { tag: 'link', attrs: { rel: 'apple-touch-icon', href: '/apple-touch-icon.png' } },
      ],
      // English at the root, Spanish under /es/. An untranslated page is served
      // in English with a notice, so no /es/ path returns a 404.
      defaultLocale: 'root',
      locales: {
        root: { label: 'English', lang: 'en' },
        es: { label: 'Español', lang: 'es' },
      },
      logo: { src: './src/assets/logo.png', replacesTitle: false },
      favicon: '/favicon.svg',
      social: [
        { icon: 'github', label: 'GitHub', href: 'https://github.com/davamudev/filebot-pro' },
        { icon: 'reddit', label: 'Reddit', href: 'https://www.reddit.com/r/filebotpro/' },
        { icon: 'email', label: 'Email', href: 'mailto:filebotpro@davamu.dev' },
      ],
      editLink: undefined,
      pagination: true,
      tableOfContents: { minHeadingLevel: 2, maxHeadingLevel: 3 },
      customCss: [
        '@fontsource-variable/roboto/index.css',
        '@fontsource-variable/jetbrains-mono/index.css',
        './src/styles/theme.css',
      ],
      components: {
        Sidebar: './src/components/Sidebar.astro',
        PageTitle: './src/components/PageTitle.astro',
        Footer: './src/components/Footer.astro',
        ThemeSelect: './src/components/ThemeSelect.astro',
        LanguageSelect: './src/components/LanguageSelect.astro',
        Search: './src/components/Search.astro',
      },
      sidebar,
    }),
  ],
})
