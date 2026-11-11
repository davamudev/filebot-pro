#!/bin/sh
# Desktop app in the browser: virtual display (Xvfb), window manager (openbox), VNC server (x11vnc, only reachable inside
# the container) and the noVNC web client on port 5800. FileBot Pro itself runs as PUID / PGID (see entrypoint.sh).
set -e

export DISPLAY=:0

# keep the caches of the X processes out of the /data volume
HOME=/tmp

# the display, window manager, VNC server and web client run as PUID / PGID too, not as root
if [ "$(id -u)" = "0" ]; then
	PUID="${PUID:-1000}"
	PGID="${PGID:-1000}"
	mkdir -p -m 1777 /tmp/.X11-unix
	drop() { setpriv --reuid="$PUID" --regid="$PGID" --clear-groups "$@"; }
else
	drop() { "$@"; }
fi

# a stopped and restarted container keeps /tmp: the lock and socket of the previous X server would stop Xvfb from starting
rm -f /tmp/.X0-lock /tmp/.X11-unix/X0 || true

drop Xvfb :0 -screen 0 "${DISPLAY_WIDTH:-1600}x${DISPLAY_HEIGHT:-900}x24" -nolisten tcp &
xvfb=$!

# wait for the X server
i=0
while [ ! -e /tmp/.X11-unix/X0 ] && [ $i -lt 100 ]; do
	if ! kill -0 "$xvfb" 2> /dev/null; then
		echo "Xvfb could not start the display :0" >&2
		exit 1
	fi
	sleep 0.1
	i=$((i + 1))
done

drop openbox &

# the web client controls a desktop that renames and moves files: a password is required unless explicitly disabled
if [ -z "$VNC_PASSWORD" ] && [ "$VNC_ALLOW_NO_PASSWORD" != "true" ]; then
	echo "VNC_PASSWORD is not set. Set VNC_PASSWORD, or VNC_ALLOW_NO_PASSWORD=true to serve the desktop without authentication (only on a trusted network or behind a reverse proxy with authentication)." >&2
	exit 1
fi

# the example passwords of older docs are known to everyone
case "$VNC_PASSWORD" in
	change-me | changeme | cambiame | password)
		echo "VNC_PASSWORD is still the example password of the documentation. Set your own password." >&2
		exit 1
		;;
esac

if [ -n "$VNC_PASSWORD" ]; then
	# the password is read from a file, not from the command line (visible to other processes). VNC only uses the first 8 characters
	(umask 077; printf '%s
' "$VNC_PASSWORD" > /tmp/vncpasswd)
	[ "$(id -u)" != "0" ] || chown "$PUID:$PGID" /tmp/vncpasswd
	drop x11vnc -display :0 -forever -shared -quiet -localhost -rfbport 5900 -passwdfile /tmp/vncpasswd &
else
	drop x11vnc -display :0 -forever -shared -quiet -localhost -rfbport 5900 -nopw &
fi

drop websockify --web /usr/share/novnc 5800 localhost:5900 > /dev/null 2>&1 &

# stop the app with the container (wait returns as soon as a trapped signal arrives)
trap 'kill "$pid" 2> /dev/null; exit 0' TERM INT

# restart the app when its window is closed, so the browser tab never ends up on an empty desktop. A crash loop backs off
# up to 30 seconds, an app that ran for a while restarts right away
delay=1
while true; do
	started=$(date +%s)
	HOME=/data /usr/local/bin/entrypoint.sh "$@" &
	pid=$!
	wait "$pid" || true
	if [ $(($(date +%s) - started)) -ge 30 ]; then
		delay=1
	else
		delay=$((delay * 2))
		[ "$delay" -le 30 ] || delay=30
	fi
	sleep "$delay"
done
