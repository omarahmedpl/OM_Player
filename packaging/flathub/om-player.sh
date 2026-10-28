#!/bin/sh
# Flatpak launcher: libVLC and its plugins are bundled under /app/lib.
export VLC_PLUGIN_PATH=/app/lib/vlc/plugins
exec /app/om-player/bin/om-player "$@"
