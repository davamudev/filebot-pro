# FileBot Pro promo video

A ~70 second introduction video in English and Spanish, built with [Remotion](https://www.remotion.dev) (React).
It is shown on the home page of the docs (`src/components/PromoVideo.astro`). The windows in the video are
recreations of the app's dark theme with the same files and results as the screenshots in the docs.

```bash
npm install
npm run render       # music, then ../public/video/filebot-pro-{en,es}.mp4 (1080p) and their posters (.jpg)
npm run render:720p  # out/filebot-pro-promo-{en,es}-720p.mp4, under 10 MB for a GitHub README attachment
npm run studio       # live preview and editing in the browser
```

- `src/i18n.ts`: all texts (EN + ES). The app labels in the windows stay in English, like the app.
- `src/timeline.json`: length of each scene in frames (30 fps). `scripts/music.py` reads it too, so the music
  follows the cuts: run `npm run music` after changing it.
- `scripts/music.py`: soundtrack synthesized from scratch with numpy (no licensing issues).
- `scripts/stills.mjs`: renders single frames for a quick check, e.g. `node scripts/stills.mjs promo-es 600 1200`.
