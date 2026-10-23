#!/bin/sh
# Desktop app in the browser: virtual display (Xvfb), window manager (openbox), VNC server (x11vnc, only reachable inside
# the container) and the noVNC web client on port 5800. FileBot Pro itself runs as PUID / PGID (see entrypoint.sh).
set -e

export DISPLAY=:0

# the X processes run as root: keep their caches out of the /data volume
HOME=/tmp

Xvfb :0 -screen 0 "${DISPLAY_WIDTH:-1600}x${DISPLAY_HEIGHT:-900}x24" -nolisten tcp &

# wait for the X server
i=0
while [ ! -e /tmp/.X11-unix/X0 ] && [ $i -lt 100 ]; do
	sleep 0.1
	i=$((i + 1))
done

openbox &

# the web client controls a desktop that renames and moves files: a password is required unless explicitly disabled
if [ -z "$VNC_PASSWORD" ] && [ "$VNC_ALLOW_NO_PASSWORD" != "true" ]; then
	echo "VNC_PASSWORD is not set. Set VNC_PASSWORD, or VNC_ALLOW_NO_PASSWORD=true to serve the desktop without authentication (only on a trusted network or behind a reverse proxy with authentication)." >&2
	exit 1
fi

if [ -n "$VNC_PASSWORD" ]; then
	# the password is read from a file, not from the command line (visible to other processes). VNC only uses the first 8 characters
	(umask 077; printf '%s
' "$VNC_PASSWORD" > /tmp/vncpasswd)
	x11vnc -display :0 -forever -shared -quiet -localhost -rfbport 5900 -passwdfile /tmp/vncpasswd &
else
	x11vnc -display :0 -forever -shared -quiet -localhost -rfbport 5900 -nopw &
fi

websockify --web /usr/share/novnc 5800 localhost:5900 > /dev/null 2>&1 &

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
