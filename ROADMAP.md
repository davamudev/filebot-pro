<h1 align="center">🗺️ Roadmap</h1>

<p align="center">
  <b>Where FileBot Pro is heading next.</b><br>
  No dates and no promises: priorities move with feedback. Ideas and pull requests are welcome.
</p>

<p align="center">
  <img alt="Next" src="https://img.shields.io/badge/next-APT%20%26%20Explorer-F97316?style=flat-square">
  <img alt="Later" src="https://img.shields.io/badge/later-integrations-4B5563?style=flat-square">
</p>

**Status:** ✅ done · 🚧 in progress · 📋 planned · 💡 idea

## 📋 Next

| Status | Item | Details |
|:---:|---|---|
| ✅ | **Windows installer shortcuts** | Let the user choose the desktop and Start menu shortcuts during setup (`--win-shortcut-prompt`), and stop creating shortcuts for the `filebot-pro` console launcher, which is only meant for the command line |
| ✅ | **`filebot-pro` on the PATH everywhere** | Run `filebot-pro` from any terminal, as the `.deb` / `.rpm` packages already allow with `/usr/bin/filebot-pro`. Windows: an "Add to PATH" checkbox in the installer, checked by default (jpackage has no option for it, so it needs a custom WiX template). macOS `.pkg`: a `postinstall` script that links `/usr/local/bin/filebot-pro`. macOS `.dmg`: an "Install `filebot-pro` command" action in the app that creates the same link after asking for the administrator password |
| 📋 | **APT repository** | Own repository: add it once, then `sudo apt install filebot-pro` and updates through `apt upgrade` |
| 📋 | **Windows Explorer integration** | "Send to" entry and a context menu action to open files and folders in FileBot Pro |
| 📋 | **Spanish subtitle variants** | Separate Spain (`sp`) and Latin American (`ea`) Spanish when searching OpenSubtitles, instead of always sending `es`; needs a real search comparing `es`, `sp` and `ea` first. Also add a separator and headings to the language selector so the "preferred" and "all languages" sections are not mistaken for duplicates, and clarify the "Mexican Spanish" label |

## 🔭 Later

| Status | Item | Details |
|:---:|---|---|
| 📋 | **Background service** | Folder watcher as a Windows service / scheduled task, systemd unit or launchd agent |
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
