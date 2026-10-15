<h1 align="center">🗺️ Roadmap</h1>

<p align="center">
  <b>Where FileBot Pro is heading next.</b><br>
  No dates and no promises: priorities move with feedback. Ideas and pull requests are welcome.
</p>

<p align="center">
  <img alt="Next" src="https://img.shields.io/badge/next-subtitles-F97316?style=flat-square">
  <img alt="Later" src="https://img.shields.io/badge/later-integrations-4B5563?style=flat-square">
</p>

**Status:** ✅ done · 🚧 in progress · 📋 planned · 💡 idea

## 📋 Next

| Status | Item | Details |
|:---:|---|---|
| ✅ | **Windows installer shortcuts** | Let the user choose the desktop and Start menu shortcuts during setup (`--win-shortcut-prompt`), and stop creating shortcuts for the `filebot-pro` console launcher, which is only meant for the command line |
| ✅ | **`filebot-pro` on the PATH everywhere** | Run `filebot-pro` from any terminal, as the `.deb` / `.rpm` packages already allow with `/usr/bin/filebot-pro`. Windows: an "Add to PATH" checkbox in the installer, checked by default (jpackage has no option for it, so it needs a custom WiX template). macOS `.pkg`: a `postinstall` script that links `/usr/local/bin/filebot-pro`. macOS `.dmg`: an "Install `filebot-pro` command" action in the app that creates the same link after asking for the administrator password |
| ✅ | **APT repository** | Own repository: add it once, then `sudo apt install filebot-pro` and updates through `apt upgrade` |
| ✅ | **Windows Explorer integration** | "Open in FileBot Pro" in the right-click menu of folders, drives, video and subtitle files, and a "Send to" entry, both from an installer checkbox |
| ✅ | **Spanish subtitle variants** | OpenSubtitles tags Spanish subtitles `es` (most), `sp` (Spain) or `ea` (Latin America), and a search for one code never returns the others: Spanish now searches `es,sp` and Latin American Spanish (formerly "Mexican Spanish") `ea,es`, regional variant first. The language selector has "Recently used" and "Languages" headings |

## 🔭 Later

| Status | Item | Details |
|:---:|---|---|
| 📋 | **Background service** | Folder watcher as a Windows service / scheduled task, systemd unit or launchd agent |
| 📋 | **Simpler APT setup** | Shorten the repository setup to a few steps. A one-line install script (`curl … \| sh`) that adds the repository and installs the package. For a manual setup without a script, embed the public key in `filebot-pro.sources` (`Signed-By:` with the key block, apt 2.3.10 or later) so the separate `.asc` download goes away. Update the installation guide (EN + ES) |
| 📋 | **More package managers** | winget, Scoop, Homebrew, Flatpak (Flathub), Snap, AUR |

## 💡 Ideas

| Status | Item | Details |
|:---:|---|---|
| 💡 | **Web interface** | Queue renames and check results from the browser on a headless install |
| 💡 | **REST API** | Let other tools (Sonarr, Radarr, home automation) trigger renames |

---

<p align="center">
  Missing something? Open an issue or a pull request · <a href="CHANGELOG.md">Changelog</a> · <a href="https://filebotpro.davamu.dev">Documentation</a>
</p>
