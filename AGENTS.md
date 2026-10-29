# AGENTS.md: instructions for Codex (and any coding agent)

You are continuing **OM Player**, an original, ad-free video player for Android and PC built with Compose Multiplatform. A Claude Code session started it. Pick up exactly where it stopped.

## Start here
1. Read `docs/HANDOFF.md`. It covers decisions, module layout, contracts, status checklist and next steps. It is the source of truth; update it whenever you finish a step.
2. Run `git log --oneline` and `git status` to see what was committed and what is in progress (uncommitted files are agent work not yet integrated).
3. Build both targets before changing anything:
   ```bash
   ./gradlew :composeApp:compileKotlinDesktop :composeApp:assembleDebug --console=plain
   ```
   Fix compile errors first.

## Rules
- **Read `docs/HANDOFF.md` section 0 first.** Two Android editions exist (lite and plus); build both and keep the plus APK private.
- **Online sources:** on 2026-10-29 the owner approved replacing the Android Plus yt-dlp integration with an Android bridge using the Go engine behind `lvcoi/ytdl-go`, including a separate search implementation. Preserve the `ytdlp` storage key for saved-track/download compatibility. Lite and desktop do not enable this source. Keep Plus APKs private. Do not add DRM circumvention; keep credentials out of the repo and honour applicable source terms, licences and attribution.
- **Clean room:** never decompile, copy or reference MX Player code, assets, names or branding. Public-feature parity only.
- **Contracts are stable** (see HANDOFF §4): `App(deps, openRequests)`, `LibraryRoot(...)`, `PlayerScreen(...)`, `PlayerEngine`, `expect VideoSurface(...)`, `PlatformActions`. Change them only with a matching update in HANDOFF.md.
- **Design:** colours only from `OmTheme.colors`; spacing `OmSpace`, radius `OmRadius`, motion `OmMotion` (ease-out, ≤300 ms, sheets ≤380 ms, press scale 0.97, never animate keyboard-triggered actions). The player stays dark (`OmScheme(DarkColors)`). 44 dp touch targets on phone. Content descriptions on icon buttons. The UX reference is `prototype/mobile.html` and `prototype/desktop.html`.
- **Permissions (Android):** read-media only. No All Files Access, camera, location or overlay.
- Commit in small steps on `main` with clear messages. Don't push anywhere unless the owner asks.
- Don't modify files outside this repo. The sample video in `~/Downloads/*.mp4` is the owner's: read only.

## Environment
Linux, JDK 21, Gradle wrapper 8.14.3, Android SDK at `~/Android/Sdk` (API 36), VLC/libVLC installed (`/usr/lib/x86_64-linux-gnu/libvlc.so.5`). The owner's phone (SM-S721B, serial R5CX90H99EP) may be attached; target devices explicitly with `adb -s`.
