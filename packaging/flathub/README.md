# Publishing OM Player on Flathub

Flathub only distributes Flatpak packages, so this folder is the Flatpak package plus its store listing.

| File | What it is |
|---|---|
| `io.github.omarahmedpl.OMPlayer.yml` | Manifest for Flathub (downloads the app from a GitHub release) |
| `local-test.yml` | Same manifest, but uses this checkout's build, for testing on your machine |
| `libvlc.yml` | Builds libVLC 3.0.24 from VideoLAN's source (checksum matches VideoLAN's published one) |
| `io.github.omarahmedpl.OMPlayer.metainfo.xml` | Store listing: name, summary, description, license, screenshots, releases |
| `io.github.omarahmedpl.OMPlayer.desktop` | App-menu entry and "Open with" file types |
| `io.github.omarahmedpl.OMPlayer.png` / `.svg` | App icon |
| `om-player.sh` | Launcher inside the sandbox |

## Test locally

```bash
./gradlew :composeApp:createDistributable
flatpak run org.flatpak.Builder --user --install --force-clean build-dir packaging/flathub/local-test.yml
flatpak run io.github.omarahmedpl.OMPlayer
```

The first build compiles libVLC and takes a while; later builds reuse the cache.

## Before submitting

1. **Source on GitHub** under GPL-3.0-or-later (required: the desktop app uses vlcj, which is GPL-3.0): https://github.com/omarahmedpl/OM_Player, with `LICENSE`. Done.
2. **App ID** is `io.github.omarahmedpl.OMPlayer` (matches the GitHub account that owns the repo). Done.
3. **Make a release.** Pack the distributable and attach it to a GitHub release tagged `v1.0.0`:
   ```bash
   tar -C composeApp/build/compose/binaries/main/app -czf om-player-1.0.0-linux-x86_64.tar.gz om-player
   sha256sum om-player-1.0.0-linux-x86_64.tar.gz
   ```
   Put that SHA-256 into the `sha256: TODO` line of the Flathub manifest.
4. **Screenshots.** Save two PNGs (library and player, at least 1280×720) to `packaging/flathub/screenshots/` and push them, so the URLs in the metainfo work.
5. **Lint** with Flathub's own checker:
   ```bash
   flatpak install --user flathub org.flatpak.Builder
   flatpak run --command=flatpak-builder-lint org.flatpak.Builder manifest packaging/flathub/io.github.omarahmedpl.OMPlayer.yml
   flatpak run --command=flatpak-builder-lint org.flatpak.Builder repo repo
   ```

## Submit

1. Fork `https://github.com/flathub/flathub` and create a branch from `new-pr`.
2. Add the manifest, `libvlc.yml`, and the files it references (desktop file, metainfo, icons, launcher) at the root.
3. Open a pull request against `new-pr` titled "Add io.github.omarahmedpl.OMPlayer".
4. A Flathub bot builds it; reviewers comment. Common requests: narrower permissions, better metainfo, screenshots.
5. After approval Flathub creates `github.com/flathub/io.github.omarahmedpl.OMPlayer`; future updates are pull requests there.

## Notes for reviewers

- **Filesystem:** read-only `xdg-videos`, `xdg-music`, `xdg-download`, because the library scans those folders. No home access.
- **X11:** Compose Desktop renders through AWT, which needs X11 (XWayland on Wayland desktops).
- **Network:** network streams (http/https/rtsp) entered by the user.
- **Prebuilt app:** the app module repackages the upstream release archive (a Java runtime plus jars from the Gradle build). libVLC is built from source.
