# OM Player

OM Player is an ad-free, local-first video and music player for Android and desktop (Linux and Windows). Lite plays media stored on your device. The private Android Plus edition adds optional online music discovery, streaming and downloads.

<p>
  <img src="packaging/flathub/screenshots/library.png" alt="OM Player media library" width="48%">
  <img src="packaging/flathub/screenshots/player.png" alt="OM Player video player" width="48%">
</p>

## Download

Get public builds from the [GitHub Releases page](https://github.com/omarahmedpl/OM_Player/releases). Each release lists its files and SHA-256 checksums. The published files can vary by release.

| Platform | Package | Install notes |
| --- | --- | --- |
| Android 8.0+ | Lite APK for your device ABI (`arm64-v8a`, `armeabi-v7a`, `x86` or `x86_64`) | These are standalone ABI-specific APKs, not one universal APK. Most current phones use `arm64-v8a`. |
| Debian or Ubuntu, x86-64 | `om-player_<version>_amd64.deb` | Install with `sudo apt install ./om-player_<version>_amd64.deb`. The package depends on VLC's `libvlc`. |
| Linux, x86-64 | `om-player-<version>-linux-x86_64.tar.gz` | Portable desktop package; requires libVLC 3.x. |
| Windows 10/11, x86-64 | `.msi` installer | Requires 64-bit VLC 3.x. The Windows MSI workflow builds it. |

Each release may include only some of these packages; check its asset list before downloading. The Plus APK is private and is not included in public releases. Desktop currently has no online music source.

To verify a downloaded file, run `sha256sum <file>` on Linux or `Get-FileHash <file>` in PowerShell and compare it with that release's `SHA256SUMS` file.

## Features

### Video

- Phone gestures: swipe the left half for brightness, the right half for volume, swipe sideways to seek with a preview, double-tap to skip, long-press for 2× speed, and pinch to zoom.
- Keyboard and mouse controls on PC, plus drag and drop. Press `?` in the desktop app for the shortcut list.
- Subtitle track selection, external subtitle files, size and background controls, and a sync offset that persists when seeking.
- Audio-track selection, 0.25×–4× speed, volume boost up to 200%, sleep timer, A–B repeat, aspect modes, and Android picture-in-picture.
- Remembers playback position.

### Music on Android

- Audio-first player with cover art, a seek bar, skip back 10 seconds / forward 30 seconds, shuffle, repeat, speed, sleep timer, favourites and a queue.
- Mini-player and background playback with notification, lock-screen and Bluetooth controls.
- Playlists: create, play, shuffle, select multiple tracks, share as an image or JSON, and import JSON.
- Browse Songs, Artists, Albums, Folders, Favourites and Playlists.
- Edit a song's title, artist and cover. Add lyrics by pasting them or looking them up online; synced lyrics follow playback.

### Library

- Browse folders, videos and music with search, sorting, grid and list views.
- Long-press to select items, then share, delete or add them to a playlist.
- Remembers your library scroll position when you return from a folder.

## Android editions

| Edition | Includes | Distribution |
| --- | --- | --- |
| **Lite** | Local media playback and library | Public, ABI-specific APKs |
| **Plus** | Lite features plus Discover search, streaming and downloads through the Android Go bridge based on [`lvcoi/ytdl-go`](https://github.com/lvcoi/ytdl-go) | Private APK; not attached to public releases |

Plus resolves an online track when playback starts and stores completed downloads in app-private storage. The separate search implementation returns public search results. Download only content you have permission to keep. Python and yt-dlp are not bundled. Desktop has no online source.

## Build from source

### Requirements

- JDK 21.
- Android SDK with API 36 for Android builds; set `sdk.dir` in an untracked `local.properties` file.
- Go 1.24.13 and Android NDK 28.2.13676358 for Plus builds. Set `GO_BINARY` or pass `-PgoBinary=/path/to/go` if Go is not on `PATH`.
- VLC 3.x installed to run the desktop app or use the Windows MSI.

Lite and desktop builds do not need Go or the Android NDK.

### Common commands

```bash
# Run the desktop app (Linux requires VLC 3.x)
./gradlew :composeApp:run

# Build Android development APKs
./gradlew :composeApp:assembleLiteDebug :composeApp:assemblePlusDebug

# Build signed Android release APKs for the supported ABIs
./gradlew :composeApp:assembleLiteRelease :composeApp:assemblePlusRelease

# Run shared desktop unit tests
./gradlew :composeApp:desktopTest

# Build a Debian package into dist/
packaging/linux/build-deb.sh

# Build a Windows MSI (run on Windows with WiX Toolset 3.x)
./gradlew :composeApp:packageMsi
```

Release signing reads `omplayer.release.*` from `local.properties`, which is not tracked. Without signing properties, Android release tasks produce unsigned APKs. Keep signing credentials and private Plus packages out of public source control and releases.

### Windows MSI on GitHub

The [Windows MSI workflow](.github/workflows/windows-msi.yml) supports two modes:

- **Manual run:** Actions → **Windows MSI** → **Run workflow**. Download the MSI and checksum from the run's artifacts.
- **Release build:** push a tag such as `v1.0.0`. The workflow builds the MSI and attaches it with its checksum to that GitHub release.

## Project structure

- `composeApp/src/commonMain`: shared UI, media-player contracts, library, playlists and online-source interfaces.
- `composeApp/src/androidMain`: Android Media3 playback, media session service, platform integrations and widgets.
- `composeApp/src/desktopMain`: desktop UI and vlcj/libVLC playback.
- `composeApp/src/jvmShared`: JVM code shared by Android and desktop.
- `packaging/`: Linux packaging and Flathub metadata.

The app uses Kotlin and Compose Multiplatform. Android playback uses Media3 ExoPlayer; desktop playback uses [vlcj](https://github.com/caprica/vlcj) and the system's libVLC installation.

## Contributing

Bug reports are most useful with the device or OS, steps to reproduce, expected result and actual result. For code changes, keep pull requests focused and include the relevant test or build command and its result.

## More documentation

- [Project handoff](docs/HANDOFF.md): modules, architecture, build details and current verification status.
- [Design principles](docs/DESIGN.md) and [feature study](docs/MX_FEATURE_STUDY.md).
- [Audio research](docs/AUDIO_RESEARCH.md).
- [Flathub packaging](packaging/flathub): metadata, screenshots and submission notes.

## License and credits

OM Player is licensed under [GPL-3.0-or-later](LICENSE). The desktop app uses vlcj (GPL-3.0) and libVLC from the VideoLAN project. OM Player is an original project; it does not use other players' code, artwork or branding.
