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

| Status | Item | Details |
|:---:|---|---|
| ✅ | **Auto-updated detection data** | The Update data workflow runs `updateData` every week and publishes the `.xz` indexes with a `manifest.json` (format version, dates, SHA-256) as assets of the fixed `data` pre-release. The app checks about once a week in the background, downloads only the indexes that changed into the `data` cache, validates them and uses them from the next start on, falling back to the bundled copy. Help menu option and `-Dapplication.data.update=skip` to turn it off; documented in the docs (EN + ES) |

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
