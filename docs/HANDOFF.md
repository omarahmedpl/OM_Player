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
- `expect fun VideoSurface(engine, scaleMode, zoom, modifier)`: the Android actual draws a Media3 surface, the desktop actual draws vlcj frames into Compose.
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
- [x] Desktop volume slider (0–200%, boost shown in warning colour) next to mute in `BottomBar`; compiles, not yet hand-tested
- [ ] Verify hover/cursor on desktop by hand (can't be screenshotted here; no xdotool)
- [ ] Codex review of Milestone 1 code → fix findings
- [ ] Desktop: subtitle background box (libVLC freetype is global), `bufferedMs` always 0, duration/resolution unknown at scan (parse with vlcj)
- [ ] Android: subtitle delay doesn't shift cues yet (stored only); Original scale = Fit; no background-play service; FFmpeg audio extension (AC3/EAC3/DTS) not added
- [ ] Persistence of settings (theme, gestures…) on both platforms; progress already persists (SharedPreferences / ~/.config/om-player/progress.json)
- [ ] Real-device test on Android (AVD `azkar` exists; no phone attached)

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

## 6. Continuing with Codex
If the Claude session stops, continue in Codex from the project folder:
```bash
cd /home/omarahmed/Downloads/OM_Player
codex "Read AGENTS.md and docs/HANDOFF.md, check git status, build both targets, then continue Milestone 1 from the status checklist."
```
Codex reads `AGENTS.md` automatically.

## 7. Next steps
1. Confirm `assembleDebug`, then dispatch the four Milestone 1 agents with the contracts above.
2. Integrate, then run Codex review → Sonnet fixes → Haiku checks.
3. Run the desktop app with a real file (`./gradlew :composeApp:run`); install the APK on a phone or emulator (`~/Android/Sdk/emulator`).
4. Add persistence (DataStore on Android, a JSON file on desktop) and the Media3 FFmpeg audio extension.
5. Package: `./gradlew :composeApp:packageDeb` (Linux), MSI on Windows, a signed APK/AAB for Android.
