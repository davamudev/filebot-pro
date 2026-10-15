# Changelog

All notable changes to FileBot Pro.

## [Unreleased]

### Added

- APT repository for Ubuntu and Debian: add it once and FileBot Pro installs with `sudo apt install filebot-pro` and updates with the rest of the system (see the installation guide).
- `filebot-pro` on the `PATH` on Windows and macOS. The Windows installer adds it with an "Add filebot-pro to the PATH" checkbox, checked by default (`ADD_TO_PATH=0` turns it off in silent installs), and removes it on uninstall. On macOS, Help → Install 'filebot-pro' command… links `/usr/local/bin/filebot-pro` after asking for the administrator password (and removes it again); the `.pkg` installer creates the same link.

### Changed

- The Windows installer lets you choose whether to create the desktop and Start menu shortcuts, and no longer creates shortcuts for `filebot-pro`, the command-line launcher.

### Fixed

- The movie and series selection dialogs show the poster of every option, including matches from the built-in index, nfo files or file metadata (before, only results of an online search had one, so some dialogs showed no posters at all).
- The `.deb` package installs on Ubuntu 22.04 and Debian 12: it depended on `libasound2t64`, which only exists on Ubuntu 24.04 and Debian 13 or later, and now accepts `libasound2` as well. Its Maintainer field also shows a contact address instead of `<Unknown>`.
- The `.deb` and `.rpm` packages install on servers and containers without a desktop: adding the menu entry failed when the system had no menu folders, which left the `.deb` half-configured and without the `filebot-pro` command.

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
