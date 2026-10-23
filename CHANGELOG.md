# Changelog

All notable changes to FileBot Pro.

## [Unreleased]

### Added

- Package downloads of each release have a build provenance attestation (`gh attestation verify`).

### Changed

- Scripts and format expressions run on Groovy 6.

### Fixed

- The desktop app saves each renamed file to the history right away, and the command line skips files that disappeared instead of failing.
- Docker: the desktop image restarts the app with a growing delay after crashes, and the images report their health.
- Docker: the desktop image no longer leaves root-owned cache files in `/data`.
- Synology: a data folder with spaces in its name works.
- The `.rpm` package declares the graphics and audio libraries it needs.
- The Filter panel tells you when MediaInfo is missing.
- The Windows shell copy and move no longer freeze the window.
- The help of `--action` and `--mode` lists all values.
- Invalid `--action`, `--conflict` and `--order` values show a usage message instead of a stack trace.
- A history file that cannot be read is no longer overwritten when no backup could be saved.
- `--conflict override` keeps the old file until its replacement is in place.
- Remote scripts with the same name from different servers no longer share a cached copy.
- Oversized server responses are rejected without reserving their size in memory.
- Updating with the MSI installer keeps the install folder and the options chosen in the setup.
- Redirected command line output keeps non-Latin characters on Windows.
- Setting `FILEBOT_PRO_OPTS` in Docker no longer moves the data out of `/data`.
- The Docker image starts after changing `PUID` or `PGID` on an existing data folder.
- The command line returns `100` when everything was skipped and an error when `-exec` fails.
- Extended attribute metadata with escaped type names is ignored.
- Renaming refuses destinations that end up outside of the `--output` folder.
- Names from online databases can no longer add `..` folders to a rename format.
- `fn:amc` refreshes the Plex library again.
- Extracting archives no longer overwrites existing files unless `--conflict override` is set.
- The rename history is saved right away, so moves can be reverted even if the process is killed (e.g. `fn:watcher` in Docker).
- The `.rpm` package installs on Fedora.

## [1.3.0] - 2026-10-22

### Changed

- The MediaInfo table in the Filter panel remembers the column widths of each view.

### Fixed

- Matching no longer fails now and then with "Response data is null".
- Renaming skips files that were moved or renamed outside FileBot Pro instead of failing.

## [1.2.2] - 2026-10-21

### Fixed

- Chinese and Japanese episode numbers like 第01集 or 第1話 are detected.
- Cached series, episode and artwork data is reused instead of downloaded again on every run.

## [1.2.1] - 2026-10-15

### Fixed

- The new release dialog adapts to its window size.
- Chinese, Japanese and Korean file names display correctly on macOS.
- Series with one- or two-character Chinese, Japanese or Korean titles (e.g. 三体) are detected.

## [1.2.0] - 2026-10-15

### Added

- APT repository for Ubuntu and Debian: install and update with `apt` (see the installation guide).
- `filebot-pro` command on the `PATH` on Windows (installer option) and macOS (Help → Install 'filebot-pro' command…).
- "Open in FileBot Pro" and "Send to → FileBot Pro" in the Windows Explorer right-click menu (installer option).

### Changed

- OpenSubtitles: **Spanish** also finds subtitles from Spain, and **Latin American Spanish** (formerly "Mexican Spanish") the Latin American ones.
- The language selector separates recently used languages from the full list.
- Opening several files from Explorer while FileBot Pro is running loads all of them and brings the window to the front.
- The Windows installer lets you choose the desktop and Start menu shortcuts.

### Fixed

- Movie and series selection dialogs show a poster for every option, not only for online search results.
- The `.deb` package installs on Ubuntu 22.04 and Debian 12.
- The `.deb` and `.rpm` packages install on servers and containers without a desktop.

## [1.1.0] - 2026-10-13

### Added

- New release notice: the desktop app shows a dialog at startup with the release notes of the newer versions (Download, Skip this version, Later), the command line a notice on stderr in interactive consoles and Docker a line in the container log, at most once a day. It never updates by itself; turn it off in Help → Check for updates at startup or with `-Dapplication.update=skip`.
- Releases page in the documentation: version numbers, how to see which version you have and the release notes of every version.
- Introduction video (English and Spanish) on the home page of the documentation.
- Links to GitHub and the r/filebotpro subreddit in the documentation header and footer; the footer no longer shows the last updated date.
- Docker images are also published to Docker Hub as `davamudev/filebot-pro` (same tags as GHCR: `latest`, `gui` and one per version).

### Changed

- Export user data now also includes the MediaInfo views of the Filter panel and the Groovy Pad script.

## [1.0.0] - 2026-10-11

Initial release.
