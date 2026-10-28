#!/usr/bin/env bash
# Builds the OM Player .deb and fixes what jpackage can't express through Compose's DSL:
#   - menu name "OM Player" (jpackage uses the package name "om-player")
#   - "Open with" for video/audio files, and StartupWMClass so the dock shows the right icon
#   - a dependency on libVLC (playback engine)
# Output: dist/om-player_<version>_amd64.deb
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"

./gradlew :composeApp:packageDeb --console=plain -q

src="$(ls composeApp/build/compose/binaries/main/deb/om-player_*_amd64.deb | head -1)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

dpkg-deb -R "$src" "$work/pkg"

desktop="$work/pkg/opt/om-player/lib/om-player-om-player.desktop"
sed -i \
  -e 's/^Name=.*/Name=OM Player/' \
  -e 's/^Comment=.*/Comment=Ad-free video and music player/' \
  -e 's|^Exec=.*|Exec=/opt/om-player/bin/om-player %f|' \
  -e 's/^Categories=.*/Categories=AudioVideo;Video;Player;/' \
  -e 's|^MimeType=.*|MimeType=video/mp4;video/x-matroska;video/webm;video/quicktime;video/x-msvideo;video/mpeg;video/x-flv;video/3gpp;audio/mpeg;audio/flac;audio/ogg;audio/mp4;audio/x-wav;|' \
  "$desktop"
grep -q '^StartupWMClass=' "$desktop" || echo 'StartupWMClass=com-omplayer-MainKt' >> "$desktop"
grep -q '^Keywords=' "$desktop" || echo 'Keywords=video;movie;player;subtitles;mkv;mp4;' >> "$desktop"

control="$work/pkg/DEBIAN/control"
sed -i -E 's/^(Depends: .*)$/\1, libvlc5, vlc-plugin-base/' "$control"
sed -i -E 's/^Description: .*/Description: OM Player - an ad-free video and music player/' "$control"
grep -q '^Section:' "$control" || echo 'Section: video' >> "$control"
grep -q '^Homepage:' "$control" || true

mkdir -p dist
out="dist/$(basename "$src")"
fakeroot dpkg-deb --build --root-owner-group "$work/pkg" "$out" >/dev/null
echo "$out"
