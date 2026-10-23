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
	# a changed PUID / PGID must also take over what the previous user created (not the media a user may keep in the volume)
	for entry in /data/cache /data/.java /data/.cache /data/.filebot-pro; do
		if [ -e "$entry" ]; then
			chown -R "$PUID:$PGID" "$entry"
		fi
	done
	find /data -maxdepth 1 -type f -exec chown "$PUID:$PGID" {} +

	exec setpriv --reuid="$PUID" --regid="$PGID" --clear-groups /opt/filebot-pro/bin/filebot-pro "$@"
fi

exec /opt/filebot-pro/bin/filebot-pro "$@"
