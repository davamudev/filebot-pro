# Changelog

All notable changes to FileBot Pro.

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
