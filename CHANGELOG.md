# Changelog

All notable changes to FileBot Pro.

## [1.0.0] - 2026-10-11

### Interface

- New **Help → About FileBot Pro** dialog with the version, the license and the attribution of the data sources
  (TMDB logo and notice).
- The About dialog shows the original logos of the data sources (TheMovieDB, TheTVDB, AniDB, OpenSubtitles), with a
  dark theme variant where needed; menus and lists keep a small icon, new for these four sources.
- Logos are downscaled with area averaging, so they look sharper on screens without scaling.
- Opening the **List** panel for the first time no longer freezes the window for about a second: the Groovy format
  engine and the expression editor are now initialized in the background at startup.
- **Filter → MediaInfo** has custom views: pick and order the columns you want (**Columns …**), save them under a name
  and switch between them with the **View** list. Views can be duplicated, renamed, deleted, and exported or imported
  as `.json` files. The **Default** view still shows every column.
- The buttons below the lists of the Rename panel have more room: a small gap between related buttons and a larger one
  between groups.
- The **Conflicts** dialog of the Rename panel explains each conflict in labeled rows (source, new name, folder, the file
  that would be overwritten, which duplicate is renamed and which is skipped, with file sizes) and wraps long file
  names to the window width instead of cutting them off.
- The **Match Mode** and **Episode Order** boxes in the Rename panel's **Preferences** dialog no longer show a second,
  misaligned border around the drop-down.
- New logo: an "FB" monogram with a "PRO" label on a dark tile. It replaces the old icon in the app windows, the About
  and Welcome dialogs (with a light and a dark version), the Windows, macOS and Linux icons, the Windows installer
  and the documentation site (logo and favicon).

### Platform and build

- The Windows installer (`.msi` / `.exe`) shows the FileBot Pro logo in its setup dialogs instead of the default WiX artwork.
- Builds only embed the API keys of services that allow one key for all users (TheMovieDB, Fanart.tv, AcoustID, AniDB,
  OpenSubtitles). TheTVDB, OMDb and Google Geocoding need your own key in `api-keys.properties` in the user data folder.
- Linux packages (`.deb`, `.rpm`) install the `filebot-pro` command in `/usr/bin`, so the command line works right
  after `sudo apt install ./filebot-pro_*.deb` or `sudo dnf install ./filebot-pro-*.rpm`. The Linux portable folder
  has `bin/filebot-pro` too.
- The `.deb` recommends MediaInfo and fpcalc (`libmediainfo0v5`, `libchromaprint-tools`) and the `.rpm` recommends
  `libmediainfo` and `chromaprint-tools`, so `apt` and `dnf` install them automatically.
- On Linux the window is matched with its menu entry, so docks and taskbars show the FileBot Pro icon instead of a
  generic Java one. The menu entry also opens files passed to it.
- Linux packages include the license, the website link and, if `app.maintainerEmail` is set, the maintainer.

### Compatibility

- TheTVDB uses API v4 (`api4.thetvdb.com`), so the `thetvdb` key must be a v4 project key; legacy v2/v3 keys no longer
  work. Series and episode titles fall back to English and then to the original language when no translation exists.
  API v4 has no ratings, so `{rating}` and `{votes}` are empty for TheTVDB data.

### Documentation

- The documentation site moved from `wiki/` to `docs/`.
- The installation guide covers Windows, Ubuntu / Debian, Fedora, other Linux distributions and macOS, with the
  location of the command line on each system, updating and uninstalling.
- New [ROADMAP.md](ROADMAP.md) with the planned work.
- The bindings reference lists every binding, including `ny`, `airdate`, `anime`, `series`, `languages`, `dim`,
  `mediaTitle`, `encodedDate`, `ffprobe`, `AnimeList`, `XEM`, `json`, `mediaPath`, `xattr`, `output`, `defines` and
  `label`.
- The syntax page explains the format editor, with a screenshot.
- The subtitles page mentions Shooter, used for Chinese subtitles.
- New screenshots with the new logo.
- The documentation is written for users: the online databases work without setup, and only TheTVDB, OMDb and
  photo locations ask for your own key. Building from source, the embedded API keys and the documentation site
  moved to a new **Development** section.

### Fixes

- MediaInfo found no streams on 64-bit systems, so the MediaInfo tab of the Filter panel stayed empty and stream based
  bindings (e.g. audio channels, `{audio}`, `{text}`) had no values.
- Switching between light and dark theme after opening the Episodes or Subtitles panel failed with
  "Failed to set look and feel" and left the window frozen.
- The **File Tree** of the Filter panel listed subfolders in reverse order (e.g. `S02` before `S01`).
- The **Login** button of the Subtitles panel opened an empty window without title bar or buttons that blocked the
  application. It now shows the OpenSubtitles login form (Enter confirms, Esc cancels).
- Drop-down lists kept the focus border after choosing an item, so they looked pressed. Clicking a drop-down no longer
  moves the keyboard focus to it; Tab still does.
- The focus border is a thin orange line instead of a thick red-orange ring that looked like an error (and showed red
  corners next to an open drop-down list). Clicking on an empty area of the window now takes the focus away from the
  text field, so it no longer looks selected.
- The search field of the Episodes and Subtitles panels didn't repaint its border when it gained or lost the focus:
  it stayed orange after leaving it, didn't turn orange when clicked, or showed only its corners in the other color.
  Its rounded corners were also covered by the square background of the text.
- The list of sources next to the search field (e.g. OpenSubtitles) showed the icons stuck to the left edge.
- Switching the theme froze the window for several seconds after choosing a suggestion in the search field of the
  Subtitles or Episodes panel: the suggestion list measured every entry of the search history. The search field also
  threw errors on every focus change after a theme switch, which could leave the window unresponsive.
- The suggestions of the search field and the drop-down lists of the subtitle matching dialog now look like all other
  drop-down lists (same border, padding and selection) instead of a black or gray square border. Matching text in the
  suggestions is highlighted in the accent color, so it's readable in the light theme.
