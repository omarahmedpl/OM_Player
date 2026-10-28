# OM Player: Handoff

Last updated: 2026-10-28. Owner: Omar Ahmed (0omarahmed73@gmail.com).

OM Player is an original, ad-free video player for **Android and PC**. It's inspired by the feature set people expect from MX Player and fixes MX's main pain points. It is a **clean-room** project: no MX Player code, assets, names or branding were decompiled, copied or used. Research came from public sources only.

---

## 1. Where things are

| What | Where |
|---|---|
| Project root | `/home/omarahmed/Downloads/OM_Player` (git repo, branch `main`) |
| Feature study (MX Player, public sources, 21 citations) | [docs/MX_FEATURE_STUDY.md](MX_FEATURE_STUDY.md) |
| Design brief (principles, screens, motion rules) | [docs/DESIGN.md](DESIGN.md) |
| Interactive prototype (HTML) | `prototype/index.html` (switcher), `prototype/mobile.html`, `prototype/desktop.html`, `prototype/tokens.css` |
| Published prototype (private artifact) | https://claude.ai/artifact/1g23XUjnndhSMxeyoVKHDR |
| Logo design canvas (private artifact) | https://claude.ai/artifact/9hgGT5aydnErwF9mgnUuUT |
| Kotlin app (Milestone 1, in progress) | `composeApp/` |

Both artifacts are private. Share them from each page's Share menu before sending the links to anyone.

---

## 2. Decisions made (and why)

| Decision | Choice | Reason |
|---|---|---|
| Research method | Public feature research, no APK decompiling | Legal clean-room; MX Player is proprietary |
| Stack | **Compose Multiplatform** (Kotlin 2.2.20, CMP 1.9.0) | One shared UI for Android + PC; fits installed SDK/JDK/VLC |
| Android playback | **Media3 ExoPlayer 1.8.0** (+ FFmpeg audio extension later for AC3/EAC3/DTS) | Standard, hardware decoding, MediaSession, PiP |
| Desktop playback | **vlcj 4.8.3 / libVLC** (system VLC) | Plays nearly every codec, libass subtitles |
| First milestone | UI prototype first, then Milestone 1 (core player + library) | User choice |
| Logo | **Direction A, "Playhead"**: a resume ring (75% arc from 12 o'clock) plus a playhead dot | Replaced a YouTube-like play-triangle-on-red-tile mark; it shows OM's resume feature |
| Brand colour | Rose `#E11D48` (dark theme fills; white text = 4.7:1), `#BE123C` in light theme | WCAG AA verified for every token pair |
| Theme | Dark-first; light theme for library screens; **player always dark** | Media apps live in the dark |
| Permissions (Android) | READ_MEDIA_VIDEO/AUDIO only (+ READ_EXTERNAL_STORAGE ≤ API 32, INTERNET) | MX asks for All Files Access, camera, location, overlay |
| Motion | Emil Kowalski rules: ease-out curves, ≤300 ms (sheets ≤380 ms), press scale 0.97, no animation on keyboard actions, reduced-motion respected | Polish without sluggishness |

### OM Player's improvements over MX Player (from the study)
- **P0 (Milestone 1):**
  - No ads, no account, no online feed.
  - Minimal permissions.
  - Built-in AC3/EAC3/DTS (Media3 FFmpeg extension).
  - Automatic hardware-to-software decoder fallback.
  - Gesture set: left-half swipe for brightness, right-half for volume, horizontal swipe to seek with preview, double-tap ±10 s, long-press 2×, pinch zoom.
  - Subtitle offset that **survives seeking**.
  - Reliable background play and PiP.
  - Desktop: drag-and-drop and full keyboard shortcuts.
- **P1:**
  - libass ASS rendering.
  - Online subtitle search with the user's own OpenSubtitles key.
  - Equalizer, A-B repeat, sleep timer.
  - Kids lock via screen pinning.
  - Desktop mini-player and OS media keys.
- **P2:** PIN or biometric privacy vault; LAN sharing without heavy permissions.

---

## 3. Prototype (done)

A vanilla HTML/CSS/JS clickable prototype of every screen, built and reviewed by agents:

- **Mobile** (`mobile.html`, about 1,950 lines):
  - library with Continue watching, folders, search, sort and grid/list
  - folder view with item menu (Info, Rename, Hide to privacy folder with Undo)
  - landscape player with every gesture and a HUD
  - bottom sheets: audio, subtitles (style + sync + online search), speed, sleep timer, A-B repeat, 5-band EQ
  - network stream screen
  - settings, including a PIN privacy folder
  - Android Back handling: Back closes the top overlay first
- **Desktop** (`desktop.html`, about 1,800 lines):
  - window shell with Ctrl+K command palette
  - home with hover scrubbing and a right-click menu
  - sortable library list
  - player with auto-hiding controls, seek preview and a side panel (queue, subtitles, 10-band EQ, video filters)
  - full keyboard shortcuts and a `?` cheat sheet
  - draggable mini-player that snaps to corners
  - drag-and-drop for videos and subtitle files
  - network dialog and settings
- **Shared:**
  - `tokens.css` is the single source of design tokens.
  - `.scheme-dark` keeps the player dark under the light theme.
  - `.om-mark` is the theme-aware logo.

**Quality pipeline that ran:**
1. Build (Claude agents)
2. **Codex** review: 22 desktop + 20 mobile findings
3. **Sonnet 5.5** fixes: all applied except the hover colour-fade finding, deliberately kept
4. **Haiku 5.5** automated QA: syntax, aria-labels, banned motion values, ids, overflow at 360/390/1024/1280 px; all clean
5. Manual browser test

One bug was caught in the manual test and fixed. Mobile Back didn't close the player in a throttled tab, because WAAPI `finished` never resolved. The shared `anim()` helper now settles `finished` on a timeout.

**Not verified:** real-device behaviour of the 24 px swipe edge zones and of the safe-area insets in the rotated player stage.

---

## 4. Milestone 1: Kotlin app (in progress)

### Build
```bash
./gradlew :composeApp:run                 # desktop app (needs system VLC; libvlc is at /usr/lib/x86_64-linux-gnu)
./gradlew :composeApp:assembleDebug       # Android APK → composeApp/build/outputs/apk/debug/
./gradlew :composeApp:compileKotlinDesktop
```
Toolchain: Gradle 8.14.3 (wrapper), AGP 8.13.0, JDK 21 (bytecode target 17), Android SDK at `~/Android/Sdk` (compileSdk/targetSdk 36, minSdk 26). `local.properties` holds `sdk.dir` and is gitignored.

### Module layout (`composeApp/src/`)
```
commonMain/kotlin/com/omplayer/
  App.kt                  navigation: LibraryRoot + PlayerScreen overlay (NowPlaying)
  AppSettings.kt          shared settings (theme, decoder, gesture toggles, seek step, subtitle defaults)
  Platform.kt             AppDeps, PlatformActions, FormFactor, LocalAppDeps, expect VideoSurface(...)
  model/Media.kt          MediaItem, MediaFolder, SortKey
  data/MediaRepository.kt LibraryState, MediaRepository, ProgressStore, InMemoryProgressStore
  data/SampleMediaRepository.kt  fictional sample library (previews / pre-engine)
  player/PlayerEngine.kt  PlayerEngine interface, PlayerState, Track, ScaleMode, Decoder
  player/FakePlayerEngine.kt     clock-driven fake engine
  theme/Theme.kt          OmColors (ported 1:1 from tokens.css), OmMotion, OmSpace, OmRadius, OmTheme, OmScheme
  ui/OmMark.kt            Playhead logo (Canvas) + OmWordmark
  ui/library/LibraryRoot.kt      CONTRACT entry: library shell (placeholder body)
  ui/player/PlayerScreen.kt      CONTRACT entry: player (placeholder body)
androidMain/  MainActivity.kt (fakes for now), VideoSurface.android.kt (stub), AndroidManifest.xml
desktopMain/  main.kt (Window 1280×800, fakes for now), VideoSurface.desktop.kt (stub)
```

### Contracts (keep stable; agents build against these)
- `App(deps: AppDeps)`: each platform builds `AppDeps(repository, progress, engine, platform)` and calls it.
- `LibraryRoot(settings, onSettingsChange, onPlay(item, queue))`.
- `PlayerScreen(item, queue, settings, onItemChange, onClose)`.
- `PlayerEngine`: `state: StateFlow<PlayerState>`, `open/play/pause/togglePlay/seekTo/seekBy/setSpeed/setVolume(0..2)/selectAudioTrack/selectSubtitleTrack(null=off)/setSubtitleDelay/addSubtitle/stop/release`. UI-thread calls only.
- `expect fun VideoSurface(engine, scaleMode, zoom, modifier, transformed = false)`: Android selects a native SurfaceView for ordinary playback and a TextureView for a caller-applied manual rotation or zoom. The caller owns the manual transform; Media3 owns intrinsic orientation metadata. The desktop actual continues drawing vlcj frames into Compose.
- `PlatformActions`: `setPlayerBrightness`, `enterPictureInPicture`, `setFullscreen`, `setLandscape`, `keepScreenOn` and `pickMediaFile`. Each is a no-op where unsupported.
- Colours come **only** from `OmTheme.colors`. The player wraps itself in `OmScheme(DarkColors)`.

### Planned agent split (four writers, separate files)
| Agent | Model | Owns |
|---|---|---|
| Library & settings UI | Sonnet 5.5 | `commonMain/ui/library/**`, settings, network screen |
| Player UI | Sonnet 5.5 | `commonMain/ui/player/**`: controls, gestures, sheets, keyboard shortcuts |
| Android engine | Sonnet 5.5 | `androidMain/**`: Media3 engine, VideoSurface, MediaStore repo, permissions, PiP, brightness |
| Desktop engine | Sonnet 5.5 | `desktopMain/**`: vlcj engine, Compose frame renderer, folder scanner, window, file drop |

Then: Codex review, Sonnet fixes, Haiku checks, a build of both targets, and a desktop run.

### Status at handoff (2026-10-28)
- [x] Gradle scaffold, version catalog, wrapper, manifest; shared contracts, theme, logo, fake engine
- [x] Four agents done and integrated (commit `e6bbba6`): library & settings UI, player UI, Media3 engine, vlcj engine
- [x] Contracts added during integration: `PlatformBackHandler` (expect/actual), `PlatformActions.inPictureInPicture`,
      `PlayerEngine.setSubtitleStyle` + `PlayerState.subtitleScale/subtitleBackground`, `App(deps, openRequests)`
- [x] Both targets build (`compileKotlinDesktop` + `assembleDebug`)
- [x] **Desktop verified with a real video**: vlcj frames render inside Compose with controls overlaid; replay at end works
- [x] Desktop hover polish: hand cursor + hover tint on all `pressable`s (library `ui/components/Components.kt`, player `ui/player/PlayerUtil.kt`), panel rows, seek bar; library screens crossfade; player opens with fade + 0.98 scale
- [x] Desktop scanner skips `node_modules`, `build`, `dist`, etc. and TypeScript `.ts`/`.d.ts` files
- [x] Desktop volume slider (0–200%, boost shown in warning colour) next to mute in `BottomBar`
- [x] Desktop bottom controls: play between previous/next; volume slider revealed on hover or keyboard focus; bottom-bar clicks intercepted; hand cursor limited to bottom-bar buttons. Both targets build. Visual screenshot checked; click, cursor and audible volume behavior still need hands-on verification.
- [x] Player behavior fixes: video clicks and center double-taps no longer toggle playback; Space and explicit play/pause buttons do. Escape closes a panel or exits fullscreen, and never closes the player. Android player panels no longer have empty parent tap detectors that intercept their controls.
- [x] Android player no longer forces Activity landscape orientation. Only the video layer rotates; controls stay upright and use two rows on narrow portrait screens. Fullscreen hides system bars independently of orientation. SurfaceView handles ordinary playback; TextureView handles manual transforms. Real 1280×720 content, portrait 360×640, rotation metadata, all four manual rotations, and device rotation were checked on the AVD. Android library video cells load cached thumbnails from MediaStore, with a frame-retrieval fallback on API 26–28. Android launcher icon and system splash screen use the Playhead mark.
- [x] Performance: library folder flattening and video-folder lookup are memoized across recompositions; desktop VLC frame copies are limited to 60 Hz. Android renderer measurements and limitations are below. Physical-device and desktop performance remain unmeasured.
- [x] Android Media3 engine is released whenever its Activity is destroyed, so Activity recreation does not reuse an active fixed-ID MediaSession.
- [ ] Verify hover/cursor on desktop by hand (can't be screenshotted here; no xdotool)
- [ ] Codex review of Milestone 1 code → fix findings
- [ ] Desktop: subtitle background box (libVLC freetype is global), `bufferedMs` always 0, duration/resolution unknown at scan (parse with vlcj)
- [ ] Android: subtitle delay doesn't shift cues yet (stored only); Original scale = Fit; no background-play service; FFmpeg audio extension (AC3/EAC3/DTS) not added
- [ ] Persistence of settings (theme, gestures…) on both platforms; progress already persists (SharedPreferences / ~/.config/om-player/progress.json)
- [ ] Physical-device test (the connected phone was not used). Remaining Android emulator checks: library thumbnails and launcher interaction. Player panels, rotation, fullscreen, replay, speed, seeking, and native splash were checked on API 34.

### Session 2026-10-28 (Claude): rotation, PiP, startup, lag (uncommitted)

All changes below are uncommitted on top of the earlier dirty tree (nothing reset, committed or pushed).

**Re-verified on `emulator-5554` (API 34) with real footage** (`/sdcard/Download/recovery-real.mp4`, copied from the approved `Shop-Hanimex (2).mp4`; H.264 1280×720, 19.4 s, no rotation tag). The emulator's data had been reset, so the APK and file were reinstalled.
- Real content renders, with no colour bars: `OMVideo decoded=1280x720 metadataRotation=0`. Aspect ratio is kept and centred in portrait.
- The library video thumbnail shows a real frame with the 0:19 duration and 720p label. Audio-file artwork on Android is still untested, because there is no approved audio file.

**1. Rotate button = whole screen (owner decision; owner confirmed it works).** This replaces the previous video-only rotation.
- `Platform.kt` adds `PlatformActions.setScreenOrientation(ScreenOrientation.Auto|Landscape|Portrait)`. The Android implementation uses `SENSOR_LANDSCAPE` / `SENSOR_PORTRAIT` / `UNSPECIFIED`.
- `PlayerScreen.rotateScreen()` toggles based on the current window shape (`LocalWindowInfo.containerSize`). Closing the player restores Auto.
- The `rotationDegrees` video transform was removed. The button reads "Rotate screen" and is hidden on desktop.
- Verified:
  - portrait → `ROTATION_90` landscape, with landscape controls and the video fitted
  - landscape → portrait
  - closing → `requestedOrientation=UNSPECIFIED`
  - the first Back exits fullscreen and the second closes the player (existing design)

**2. PiP dismiss vs restore (bug fixed).**
- Before: any exit from PiP called `engine.stop()` and `onClose()`, so expanding PiP also closed the video.
- Now: `MainActivity.onPictureInPictureModeChanged` treats the exit as a dismissal only when the Activity is no longer STARTED (PiP closed). Only then does it pause the engine and emit `PlatformActions.pictureInPictureDismissed`, and only on that signal does `PlayerScreen` close the player.
- Verified:
  - Home → PiP opens automatically, with no controls inside it
  - restore → the player stays open and keeps playing
  - drag-to-dismiss → media session `state=NONE`, and reopening shows the library

**3. Startup (measured first, with debug-only `OMStartup` marks in ms from process start; 3 cold starts each).**
- Cause: `Media3PlayerEngine` built ExoPlayer + MediaSession on the main thread before the first frame. Engine construction took 140–180 ms; it now takes about 10 ms.
- Change: `player` is now `by lazy`, and `prewarm()` builds it on an idle main thread after the splash exit finishes. An earlier attempt ran right after the first draw, where it blocked for about 360 ms during the splash fade, so it was moved. `stop()` and `release()` don't create a player that was never used.
- Results:
  - `onCreate→firstDraw`: median 698 → 505 ms
  - `am start -W TotalTime`: median 1,594 → about 1,300–1,400 ms
  - Splash exit runs undisturbed: `OMSplash exitMs` 242–249 ms against the 220 ms target. No artificial delays were added.

**4. Lag: the main cause is the debug build.**
- New `benchmark` build type: release settings, signed with the debug key, not debuggable.
  - Build: `./gradlew :composeApp:assembleBenchmark`
  - Then install it and run `adb shell cmd package compile -f -m speed-profile com.omplayer`
- Workload: `pm clear`, cold launch, open folder, play, show controls, speed sheet open/close, exit fullscreen, close player, scroll. Three runs each on a settled emulator, read with `dumpsys gfxinfo`:

| | Debug | Benchmark (release-like) |
|---|---:|---:|
| Janky frames | 18.0–34.3% (median 20.4%) | 12.9–15.7% (median 13.2%) |
| p50 / p90 frame | 20–31 / 48–117 ms | 19–21 / 38–42 ms |
| Slow UI thread frames | 23–45 | 9–12 |
| Launch TotalTime | 608–1,675 ms | 409–610 ms |

- The benchmark build plays real footage with no crash. Judge smoothness on release-like builds, not debug ones.
- Remaining: about 13% janky frames on the emulator in release mode, cause not yet attributed.
- Next diagnostic: a Perfetto trace of the same workload, and a Baseline Profile / Macrobenchmark module for app code.

**5. Release-lint fix.** The release/benchmark build failed lint with a `ResourceCycle` error: `Theme.OmPlayer.App` was the dot-name child of `Theme.OmPlayer` and also its parent. The base was renamed to `OmPlayerBase` in `res/values/styles.xml` and `res/values-v31/styles.xml`. Debug builds never ran this check.

**6. Desktop fullscreen (owner reports: Esc didn't exit; F flickered; no hover or close button; slow to close). Fixed on Linux with OM Player's own fullscreen window (owner chose option a; **owner confirmed it works well**).**
- Environment: Ubuntu, GNOME Shell 42.9, Wayland session (XWayland), OpenJDK 21. Tested with real input from `java.awt.Robot` via `jshell`, plus `xprop`, `xwininfo` and `jstack`.
- **Root cause 1 (system fullscreen can't be left).** After leaving fullscreen, AWT re-applied `_NET_WM_STATE_FULLSCREEN` within about 100–200 ms.
  - Reproduced with a **plain `JFrame`**, so it isn't app code.
  - All of these failed:
    - Compose `WindowPlacement`
    - `GraphicsDevice.setFullScreenWindow`
    - direct EWMH messages, sent from inside and outside the app
    - hide + show
- **Design.** New `expect FullscreenHost(active, onExit, content)` in `Platform.kt`.
  - Android passes the content through. Windows and macOS keep native `WindowPlacement` (untested).
  - On Linux, `FullscreenHost.desktop.kt` puts **only the player UI `Box`** into a separate borderless window covering the screen. Player state, the open panel and the playback lifecycle stay in `PlayerContent`, so nothing reopens or stops.
  - The window is never put into system fullscreen; exiting closes it.
  - After mapping it is moved to the monitor origin, because GNOME first places it 35 px below the top bar, which clipped the controls.
  - `DesktopPlatformActions.ownFullscreenWindow` / `screenBounds()`; on Linux, `setFullscreen` only flips `windowFullscreen`.
  - A throwaway window in `WindowPlacement.Fullscreen` was also tried. It was closed again by a spurious toggle, so it was dropped.
- **Root cause 2 (lag, missing hover, slow close).** With two Compose windows visible, `jstack` showed the AWT event thread stuck in Skiko's `LinuxOpenGLRedrawer.makeCurrent` (5 of 6 dumps). Frame gaps of 1–3 s; native press → Compose press took 457 ms, and up to 1.4 s.
  - Fix: hide the main window while the fullscreen window is up, so only one GL window renders.
  - Because the fullscreen window belongs to the main window's composition, and that composition pauses while hidden, `setFullscreen(false)` shows the main window **first**.
  - After the fix: no frame gaps over 100 ms while fullscreen (max 74 ms at open). Native press → toggle is about 200 ms; the remaining ~200 ms from robot click to native press is Robot/X overhead.
- **Kept from earlier:**
  - window focus restore after a switch
  - 500 ms toggle cooldown
  - window-level bubbling Esc fallback, also on the fullscreen window
  - player mirrors `windowFullscreen`
- **UX added:** a **✕ "Exit full screen"** button in the player's top bar while desktop fullscreen (with hand cursor). Player hover tint raised from 14/22% to 20/28% white.
- **Verified on Linux:**
  - F opens 1920×1080 at 0,0, and the main window is unmapped
  - Esc, ✕ and the bottom-bar button each close it, and the main window returns at 1280×763
  - F/F toggles once per press
  - the ✕ hover circle is visible
  - **Caveat:** some automated runs were mixed with the owner's own clicks; the clean runs passed.
- **Not verified:** Windows/macOS native path; HiDPI scaling of `screenBounds()` (it assumes logical px = dp); multi-monitor.

**7. Desktop video thumbnails were blank (owner report). Fixed.**
- Cause, found by an env-gated self-test on 4 real files (since removed): `DesktopThumbnailCache.toMrl()` built `file:/path` (`File.toURI()`). libVLC rejects that form, so every extraction ended immediately with no frame.
- The old method also used `snapshots().get()`, which needs libVLC's own native video window (unreliable on Wayland/XWayland).
- Fix in `VideoSurface.desktop.kt`:
  - `toMrl()` now produces `file:///path`, like the engine's `mrl()`.
  - Extraction now uses a headless embedded player with a callback surface (RV32 into memory, like the main player).
  - It seeks to 10% of the length, at most 3 s, and skips the first 2 frames.
  - It crops libVLC's buffer padding using `videoDimension()` (720 → 736 lines), then scales to 320 px wide.
- Results: thumbnails of 320×180 (landscape) and 320×510 (portrait) in 373–1,371 ms each, built sequentially on IO. The saved frame was inspected and matches the video.
- Not verified by me in the library grid itself, to avoid injecting mouse input while the owner was testing Android. The library calls the same `VideoThumbnail`/`DesktopThumbnailCache.load` path. Embedded song artwork (`AudioArtwork`) shares the MRL fix but wasn't tested; there's no audio file with artwork.

**8. Mobile Back needed 2+ presses (owner report). Fixed.**
- Cause: the phone player starts with `fullscreen = true`, and `handleClose()` and the player's `PlatformBackHandler` exited fullscreen first. So the first Back/← press appeared to do nothing, and a third press was needed if a panel was open.
- Now one press closes the player; only an open panel (or the lock) is handled first.
- On desktop, ← while in the own fullscreen window calls `setFullscreen(false)` directly (bypassing the toggle cooldown) and then `onClose()`. The hidden main window must be shown before it can recompose.
- Verified on `emulator-5554`:
  - one system Back → folder view
  - one ← tap (with controls visible) → library
  - a ← tap while the controls are auto-hidden only reveals them (by design)

**9. Phone control row (owner request).**
- Play/pause is now shown on phones too, between previous and next.
- In portrait (`compact`), lock · previous · play/pause · next · time use `Arrangement.SpaceBetween` across the full width.
- Landscape and desktop keep actions on the right of the same row. Screenshot verified on the emulator.

**10. Release builds (2026-10-28).** Output is in `dist/` (git-ignored), with `SHA256SUMS`.
- **Android:** `dist/om-player-0.1.0.apk` (`./gradlew :composeApp:assembleRelease`), versionName 0.1.0, versionCode 1. Not minified.
  - Signed with a **new release key**: `~/.android/om-player-release.jks` (alias `omplayer`, RSA 4096, 10,000-day validity).
  - The key's password lives only in the git-ignored `local.properties` (`omplayer.release.*`), and `build.gradle.kts` reads it from there.
  - Cert SHA-256: `f8ddfb85e70a2e948bbc40b70a6651fd7ae25614bfbb44247c81bd1b2292dda1`.
  - **Back the key up: losing it means installed copies can never be updated.**
  - Verified with `apksigner verify`; installed on `emulator-5554` (API 34); launches in 631 ms; real footage plays; no FATAL.
- **Linux:** `dist/om-player_1.0.0_amd64.deb` (110 MB, with the full Java runtime bundled through `includeAllModules`). Build it with `packaging/linux/build-deb.sh`, which runs `packageDeb` and then patches the result:
  - menu name "OM Player"
  - video/audio MIME types and `Exec … %f`
  - `StartupWMClass`
  - `Depends: … libvlc5, vlc-plugin-base`
  - Section `video`
  - The icon is `composeApp/icons/om-player.png`, from `om-player.svg`.
  - Verified: the extracted package's `/opt/om-player/bin/om-player` plays a real video.
  - Not installed system-wide, since that needs sudo. Install with: `sudo apt install ./dist/om-player_1.0.0_amd64.deb`.
- The `.deb` version comes from `nativeDistributions.packageVersion` (1.0.0, which macOS packaging requires to be ≥ 1); the APK is 0.1.0. Align them before a public release.

**11. Flathub packaging (2026-10-28).** Everything is in `packaging/flathub/`; the submission steps are in `packaging/flathub/README.md`.
- App ID `io.github.omarahmedpl.OMPlayer` (GitHub account `omarahmedpl`); repo https://github.com/omarahmedpl/OM_Player (public, GPL-3.0 `LICENSE`).
- The license is GPL-3.0-or-later (required by vlcj); metadata is CC0.
- Runtime: `org.freedesktop.Platform` 25.08.
- Sandbox permissions: X11, PulseAudio, DRI, network, and read-only `xdg-videos`, `xdg-music`, `xdg-download`.
- libVLC is built from source (`libvlc.yml`, headless).
  - It uses **VLC 3.0.24**, sha256 verified against VideoLAN. 3.0.21 fails to compile against the SDK's newer FFmpeg (`channel_layout`, `AV_CODEC_ID_AYUV`).
  - `BUILDCC=/usr/bin/gcc -std=gnu99` is needed, or configure stops with "Cannot find native C99 compiler".
  - `vlc-cache-gen` runs in the app module, not libVLC's `post-install`, because Flatpak strips plugins after `post-install` and the cache then goes stale.
- `VlcDiscovery` checks `/app/lib` first. The launcher `om-player.sh` sets `VLC_PLUGIN_PATH`.
- **Local test (passed):**
  - Built with `flatpak run org.flatpak.Builder --user --install --force-clean --disable-rofiles-fuse --state-dir=~/.cache/om-player-flatpak/state ~/.cache/om-player-flatpak/build packaging/flathub/local-test.yml`.
  - Build and state dirs must be host-visible, because the Builder's own sandbox has a private `/tmp`. `--disable-rofiles-fuse` was needed here.
  - Installed size is 274 MB. `flatpak run io.github.omarahmedpl.OMPlayer "<video in ~/Downloads>"` plays real footage, and no libVLC errors remain.
- **Still to do before submitting:**
  - `v1.0.0` release tarball, whose sha256 goes into `io.github.omarahmedpl.OMPlayer.yml` (currently `TODO`)
  - screenshots
  - run `flatpak-builder-lint`
  - PR to `flathub/flathub` on the `new-pr` branch
  - The library scan and the "Open with" desktop entry were not tested inside the sandbox.

**Builds and checks**
- `compileKotlinDesktop`, `assembleDebug` and `assembleBenchmark` succeed.
- There are still no unit-test sources, so no tests count as passing.
- The debug APK is reinstalled on the emulator.
- **Desktop:** a real video plays with the controls overlaid and no rotate button. Hover, cursor and audible volume still need checking by hand; there's no input injection here.
- **Not verified this session:** physical device, Android below 34, Android audio artwork, desktop performance.

### Android recovery evidence (2026-10-28)

Recovery files: `PlayerScreen.kt`, `PlayerChrome.kt`, and `PlayerPanels.kt` for orientation, responsive controls, fullscreen and the sheet race; Android `VideoSurface.android.kt` for surface selection and filtered subtitle updates; `Platform.kt` and the desktop actual for the compatible surface argument; `Media3PlayerEngine.kt` for debug-only decoder diagnostics; `MainActivity.kt` and native splash resources for the exit transition, pacing and mask-safe icon. Other pre-existing dirty changes were retained.

**Baseline and causes.** The latest committed baseline is `216447a`; the earlier attempts are uncommitted changes. Their intermediate whole-stage rotation patch is not preserved as a separate Git commit, so its exact historical diff cannot be reconstructed safely. The current source and runtime confirmed:

- `PlayerScreen.kt` forced `setLandscape(true)` on entry. This rotated the Activity independently of the manual video transform. Removing that call preserves device orientation; the video-only layer swaps its pre-transform bounds for quarter turns. Media3 applies file metadata once; the manual adjustment starts at zero.
- The last `VideoSurface.android.kt` fix forced every video through TextureView. The original SurfaceView path renders ordinary video correctly and avoids feeding every decoded frame through the app's drawing pipeline. TextureView is now used only when needed for manual transforms.
- `VideoSurface.android.kt` also collected the entire 250-ms playback state and repeatedly called `SubtitleView.setStyle`. Inspection of Media3 1.8.0 confirmed that this setter always invalidates its output. The view now observes only distinct subtitle-style changes.
- The invisible Android panels were a pre-existing `PlayerPanels.kt` race: `opened` was set before a suspending entrance animation. Logs showed a measurement-key restart with `opened=true` and the offset still equal to the sheet height. Setting `opened` after the animation completes fixes the cancelled entrance. Speed, audio, subtitles, and More then opened and responded on the emulator.
- `testvid.mp4` really contains the color-bar pattern. A decoded source frame matches it; Android has no test-pattern renderer or fallback. The owner's explicitly approved H.264 recording renders real footage before and after recovery. The screenshot's ended/replay state is normal for the 12-second test clip.
- Native splash resources previously animated the arc for 620 ms and the dot for 240 ms. The shared launcher vector also clipped the dot inside Android's splash mask. A dedicated padded splash vector now keeps the mark intact; the arc/dot use 860/480 ms, and the Activity uses a 220-ms ease-out exit after the native first-frame-ready callback. There is no keep-on-screen timer or readiness delay; disabled system animations remove the splash immediately.

**Controlled performance comparison.** Three cold starts and three ten-second playback windows per variant, same AVD, landscape orientation, and approved 1280×720 recording. The comparison APK was built outside the repository with the previous always-TextureView path; final playback also filters redundant subtitle updates. Medians:

| Measurement | TextureView + repeated style updates | Final native path |
|---|---:|---:|
| App draw frames in ten seconds (not video frames) | 307 | 16 |
| Slow draw-command events | 166 | 2 |
| Decoder dropped buffers | 0 | 0 |
| Process PSS | 116,653 KB | 117,719 KB |
| Cold launch to first Activity frame | 1,510 ms | 1,823 ms |
| Open-to-first decoded frame | 570 ms | 603 ms |

Startup did **not** improve consistently (final range 1,479–2,408 ms). Steady-playback app-frame p95 was 16 ms versus 48 ms, but the frame populations differ because SurfaceView presents video outside the app drawing loop; this does not establish faster controls or higher video FPS. No claim of globally smooth playback or faster startup is made from these counters.

A separate equal-workload check opened and closed the speed sheet five times over paused video: both variants rendered 205 app frames, had 10.24% missed deadlines, and had 32-ms p95. This found no control-animation regression, and also does not establish universally smooth animation on this emulator.

**Verification actually run.** Desktop compilation and Android debug assembly succeeded. `desktopTest` and `testDebugUnitTest` reported `NO-SOURCE`; there are no existing unit tests to count as passing. Automated ADB assertions covered all four rotations without changing an explicitly paused seek position, horizontal seek/control bounds, seeking to 11 seconds, selecting 1.5× speed, audio/subtitle selection, More, fullscreen in/out, replay back to 1 second, portrait media, embedded rotation metadata, and an actual portrait/landscape device transition. Screenshots were inspected, including real footage. Debug-only Media3 analytics reported effective decoded dimensions and zero dropped buffers in the measured windows. Native splash was recorded before and after; its complete exit was visually inspected. Initial automation exposed the sheet race; one initial position check was repeated after an explicit paused seek, and one audio assertion was corrected to the actual title `Audio`.

Raw diagnostics are temporary under `/tmp/om-*`, including renderer JSON results, graphics dumps, UI screenshots, and splash recordings. The emulator process disappeared during the investigation and was cold-booted without wiping data or saving a snapshot; the controlled comparison was performed after that restart. Physical devices, Android versions below API 34, arbitrary codecs, and desktop runtime behavior were not tested in this recovery.

Final splash recording: `/tmp/om-splash-final.mp4` (native fade measured 232 ms for the 220-ms target). Final real-content screenshot: `/tmp/om-recovery-final.png`. Final debug APK is installed on `emulator-5554`; the approved recording is selected instead of the color-bar fixture.

How to check the desktop app visually here: run `./gradlew :composeApp:run --args="/path/video.mp4"`, then
`import -window "$(xwininfo -root -tree | grep -m1 '"OM Player"' | awk '{print $1}')" shot.png` (Wayland blocks root capture).

---

## 5. Environment notes (things that bit us)

1. **Subagent model mapping.** `~/.claude/settings.json` mapped `sonnet` and `haiku` to `cc/...` proxy IDs, which fail against api.anthropic.com. Fixed to `claude-sonnet-5-5` and `claude-haiku-5-5`; backup at `~/.claude/settings.json.bak-om`. `ANTHROPIC_DEFAULT_OPUS_MODEL` is still `cc/claude-opus-5` (left as is on purpose).
2. **Codex.** The `codex` on PATH used to be an unrelated npm package (`codex-cli@0.1.3`). It was replaced with the official `@openai/codex` 0.165.0, which is signed in through ChatGPT. Use it via the `codex:codex-rescue` agent.
3. **Artifact viewer limits.** Downloads (`<a download>`) are blocked, so the desktop screenshot toast only shows the frame. Hash routes don't reach the address bar.
4. **Throttled preview tabs** can stall WAAPI `finished` promises. Always add a timeout fallback for close animations.
5. Disk was about 89% full (26 GB free) when this was written.

---

## 6. Continuation briefing for Claude

Continue from the current working tree. Milestone 1's four implementation agents have already finished and their work was integrated in `e6bbba6`; do not dispatch that original build again. The latest committed baseline is `216447a`, but the Android recovery and earlier user-approved fixes are still **uncommitted**. Inspect `git status` and scoped diffs; preserve the dirty files and untracked Android resources. Do not reset the repository, discard existing work, commit, or push without an explicit owner request.

Read `AGENTS.md`, the status checklist, and **Android recovery evidence** above before editing. The owner's latest priorities are correct real-video rendering, video-only rotation with upright controls, functioning player buttons, measured performance, and a smooth native splash. Keep the Compose Multiplatform / Media3 / vlcj architecture. Graphify's current graph contains design documents only, so Kotlin changes still require direct source inspection.

Last verified runtime was the API 34 AVD `azkar`, addressed as `emulator-5554`. A physical phone was also connected and was not used. Target emulator commands explicitly with `adb -s emulator-5554` after checking the current device list. The owner approved copying only `~/Downloads/Shop-Hanimex (2).mp4` into the emulator as `/sdcard/Download/recovery-real.mp4` for these tests; the original file was not modified. Do not substitute the color-bar fixture for real-content verification.

The final debug APK is `composeApp/build/outputs/apk/debug/composeApp-debug.apk`. Both target builds succeeded. Gradle unit-test tasks have no test sources; the functional evidence came from ADB assertions, decoded-frame analytics, screenshots, and recordings. Logs use debug-only tags `OMVideo` and `OMSplash`. Temporary evidence and scripts live under `/tmp/om-*` and may disappear between sessions. Wait for the decoded first-frame event and inspect a fresh screenshot: an early startup capture can be blank even when the subsequent accessibility tree contains player controls.

If continuing in Codex instead, start from the same folder:
```bash
cd /home/omarahmed/Downloads/OM_Player
codex "Read AGENTS.md and docs/HANDOFF.md, preserve the existing dirty working tree, build both targets, then continue from the current recovery checklist."
```
Codex reads `AGENTS.md` automatically.

## 7. Next steps

1. **Profile remaining UI lag on release-like builds** (startup engine cost fixed; see session notes and the `benchmark` build type). Startup was measured, but did not improve consistently. Capture a cold-start trace to separate Media3/session initialization, first Compose layout, native surface creation, and emulator overhead. The reduced app draw count is not a video-FPS measurement. Use the same device, source, orientation, and workload for comparisons; preserve the ordinary SurfaceView path and filtered subtitle updates.
2. **(Done 2026-10-28, see session notes) Check PiP close versus restore.** `PlayerScreen.kt` currently stops playback whenever an entered PiP session returns to `inPip=false`. Verify both dismissing the popup and returning to the Activity; the source does not distinguish those paths. The owner wants dismissal to stop playback immediately. Do not mark restoring PiP correct without testing it separately.
3. **Verify remaining Android library behavior.** Test video thumbnails and embedded song artwork with real files, launcher interaction, and repeated player open/close. Rotation, panels, seeking, speed, replay, fullscreen and splash passed on API 34; physical devices and older Android versions remain untested. Android below API 31 retains the existing static launch-screen path.
4. **Perform desktop runtime checks.** Confirm hover volume, audible volume changes, cursor boundaries, Space/button-only pause, Escape/fullscreen exit, panel controls, video thumbnails and song artwork. Desktop compilation passed, but this recovery did not measure desktop performance or revalidate its runtime. Profile reported desktop lag before further optimization.
5. **Review the integrated Milestone 1 and recovery diff.** Pay particular attention to surface replacement during rotation/zoom, sheet cancellation, Activity/engine lifecycle, and PiP behavior. Add focused regression coverage using existing infrastructure where practical; do not count `NO-SOURCE` tasks as passing tests.
6. **Continue remaining milestone gaps after recovery review:** settings persistence, Android subtitle delay application, proper Original scale behavior, background-play service, FFmpeg audio support, and the desktop gaps listed above. Keep each change scoped and update this handoff with actual verification.
7. **Package only when requested:** `./gradlew :composeApp:packageDeb` for Linux; MSI on Windows; signed APK/AAB for Android. A desktop compile is not a rebuilt Linux distributable. No commit, push, or release was performed during recovery.
