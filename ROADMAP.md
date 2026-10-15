<h1 align="center">🗺️ Roadmap</h1>

<p align="center">
  <b>Where FileBot Pro is heading next.</b><br>
  No dates and no promises: priorities move with feedback. Ideas and pull requests are welcome.
</p>

<p align="center">
  <img alt="Later" src="https://img.shields.io/badge/later-integrations-4B5563?style=flat-square">
</p>

**Status:** ✅ done · 🚧 in progress · 📋 planned · 💡 idea

## 📋 Next

Nothing pending right now: the next items will come from the [Later](#-later) list and from feedback.

## 🔭 Later

| Status | Item | Details |
|:---:|---|---|
| 📋 | **Auto-updated detection data** | Scheduled GitHub Actions workflow in this repository that runs `updateData` (e.g. weekly) and publishes the `.xz` indexes as assets of a fixed `data` pre-release. The app downloads them about once a week into the `data` cache and falls back to the bundled copy when offline or on first start, so movie, series, anime and disc data stay current between releases. Option to turn it off; mention the weekly request in the docs (EN + ES) |
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
