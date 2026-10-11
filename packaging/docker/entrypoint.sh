#!/bin/sh
# Run FileBot Pro as the user and group given by PUID and PGID (e.g. the owner of the media shares on the NAS), so new
# files and folders belong to that user and not to root.
set -e

umask "${UMASK:-022}"

if [ "$(id -u)" = "0" ]; then
	PUID="${PUID:-1000}"
	PGID="${PGID:-1000}"

	if ! getent group "$PGID" > /dev/null; then
		groupadd --gid "$PGID" filebot
	fi
	if ! getent passwd "$PUID" > /dev/null; then
		useradd --uid "$PUID" --gid "$PGID" --home-dir /data --no-create-home --shell /usr/sbin/nologin filebot
	fi

	# settings, caches and logs
	mkdir -p /data
	chown "$PUID:$PGID" /data

	exec setpriv --reuid="$PUID" --regid="$PGID" --clear-groups /opt/filebot-pro/bin/filebot-pro "$@"
fi

exec /opt/filebot-pro/bin/filebot-pro "$@"
