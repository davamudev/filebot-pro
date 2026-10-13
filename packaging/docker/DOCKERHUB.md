# FileBot Pro

Organize and rename movies, TV shows and anime with data from TheMovieDB, TheTVDB, TVmaze and AniDB, and fetch
subtitles and artwork. Free and open source (GPL-3.0).

- **Documentation**: https://filebotpro.davamu.dev/getting-started/docker/
- **Source code and issues**: https://github.com/davamudev/filebot-pro
- **Release notes**: https://filebotpro.davamu.dev/reference/releases/

## Tags

| Tag | Contents |
| --- | --- |
| `latest`, `<version>` (e.g. `1.1.0`) | Command line. By default it watches `/input` and moves every new file into `/output` with the Plex naming scheme |
| `gui`, `<version>-gui` | The full desktop app in the browser at `http://<host>:5800` |

Architectures: `linux/amd64` and `linux/arm64`. Java, MediaInfo, fpcalc, mkvpropedit, AtomicParsley and 7-Zip are
included. The same images are published to `ghcr.io/davamudev/filebot-pro`.

## Quick start

```yaml
services:
  filebot-pro:
    image: davamudev/filebot-pro:latest
    container_name: filebot-pro
    restart: unless-stopped
    environment:
      - PUID=1000
      - PGID=1000
    volumes:
      - ./data:/data
      - /volume1/downloads/complete:/input
      - /volume1/media:/output
```

| Folder | Contents |
| --- | --- |
| `/input` | Folder that is watched, e.g. the completed downloads |
| `/output` | Media library the renamed files are moved into |
| `/data` | Settings, caches, rename history and logs (keep it on a volume) |

| Variable | Default | Meaning |
| --- | --- | --- |
| `PUID`, `PGID` | `1000` | Owner of the new files and folders |
| `UMASK` | `022` | Permissions of new files and folders |
| `FILEBOT_PRO_APIKEY_<NAME>` | | Your own API keys, e.g. `FILEBOT_PRO_APIKEY_THEMOVIEDB` |

Any other command runs after the image name:

```bash
# versions, native tools and data folder
docker run --rm -v ./data:/data davamudev/filebot-pro -script fn:sysinfo

# process a folder once with fn:amc
docker run --rm -e PUID=1000 -e PGID=1000 -v ./data:/data -v /volume1/downloads:/input -v /volume1/media:/output \
  davamudev/filebot-pro -script fn:amc /input --output /output --action move -non-strict
```

## Desktop app in the browser

```yaml
services:
  filebot-pro-gui:
    image: davamudev/filebot-pro:gui
    container_name: filebot-pro-gui
    restart: unless-stopped
    ports:
      - "5800:5800"
    environment:
      - PUID=1000
      - PGID=1000
      - VNC_PASSWORD=change-me   # required, asked by the browser
    volumes:
      - ./data:/data
      - /volume1/media:/media
```

## Updating

```bash
docker compose pull && docker compose up -d
```

When a new version is out, the container log says so. More options, NAS guides (Synology, QNAP, Unraid, TrueNAS) and
troubleshooting are in the [documentation](https://filebotpro.davamu.dev/getting-started/docker/).
