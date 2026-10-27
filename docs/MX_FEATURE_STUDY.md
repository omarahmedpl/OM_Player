# MX Player: Clean-Room Feature Study for OM Player

> Status: research input for OM Player (Android + PC, Compose Multiplatform; Media3/ExoPlayer on
> Android, vlcj/libVLC on desktop). Compiled 2026-10-27 from public sources only (Play Store
> listing, official MX Player help center and archived FAQ, Wikipedia/press, Play reviews, Reddit).
> No APK was downloaded or decompiled. Everything is summarized in our own words. Behaviour
> marked *(reported)* comes from user reports or reviews, not official docs, so check it on a
> device before relying on it.

---

## 1. Overview

- **What it is:** MX Player started in July 2011 as a local video player for Android (J2 Interactive).
  It became known for broad codec support, hardware-accelerated playback, gesture controls and
  very complete subtitle support. Ownership moved from J2/Tencent to Times Internet (2018), and
  Amazon bought it in June 2024. It is now branded **Amazon MX Player** [S1][S6][S7].
- **Platforms:** Android app (1B+ installs, about 4.4 stars, last updated Oct 2026) [S1]. The help
  center says the iOS app, MX Player TV app and the web sites are being discontinued as part of
  the merger into Prime Video. The Android app stays, with its "Videos" (online) tab, "Fatafat"
  short-form content and the Local Video Player [S10].
- **Business model:**
  - The free app is **ad-supported**: Play lists "Contains ads" and in-app purchases [S1].
    Officially the ads are rated "Everyone" [S14].
  - **OTT pivot (Feb 2019):** relaunched as a streaming service with originals and licensed
    shows. The local player became a secondary surface inside a content app [S6].
  - **Ad removal:** in India this used to come from the MX Local / MX Gold subscriptions ("ad-lite",
    plus extras such as a music player, video downloader and cutter). Elsewhere it comes from the
    separate paid **MX Player Pro** app, which has no OTT, games or ads [S8]. MX Gold/Silver were
    discontinued in 2026 with pro-rata refunds [S11].
  - **Account changes:** from July 1 (2026), Android sign-in is Amazon-only. Google, Facebook and
    email sign-in were removed [S12].
  - **AI features:** paid AI subtitle generation (MX Silver, 600 min/month). It does not work for
    EAC3 audio or DRM content [S13][S15].

---

## 2. Feature inventory (observable behaviour)

| Area | Feature | How it behaves (observable) |
|---|---|---|
| Library | Folder/video list | Lists media by folder after scanning storage. The first scan can be slow on large storage [S9]. Dot-prefixed folders are excluded by default [S9]. |
| Library | Status tags | Badges next to items. **NEW** means a file copied or modified within the last 7 days and not yet watched. Others show progress or finished state [S2]. |
| Library | File ops | Rename/delete from the list. Can fail on SD card or external storage because of Android permission limits [S9]. |
| Library | Default-player hand-off | Registers for video intents. Browser links to media open straight into playback, and the FAQ explains how to download instead [S9]. |
| Library | Music | Can play audio files. A dedicated music player was a paid-tier extra in India [S8][S9]. |
| Decoders | HW | Uses the platform media framework (MediaCodec) [S2]. |
| Decoders | HW+ | MX's own pipeline over the hardware decoder. Plays more formats than HW. Needed for 200% volume boost and for background play in older versions. Hidden on unsupported devices [S2]. |
| Decoders | SW | FFmpeg-based CPU decoding with multi-core support (Play listing claims up to 70% faster on multi-core) [S1][S2]. Separate defaults for local and network sources [S2]. |
| Decoders | Per-file switch | The user can switch HW / HW+ / SW during playback or force one per codec in settings [S2][S9]. |
| Decoders | Custom codec | The user can point Settings > Decoder > Custom codec at a folder of self-built FFmpeg `.so` libraries. Used to restore codecs MX does not ship [S2]. |
| Decoders | Codec packs | Architecture-specific add-on packs (e.g. NEON variants). The app prompts only when a pack is needed. An extra pack does not improve speed [S9][S16]. |
| Decoders | AC3/EAC3/DTS | Not bundled. MX tries the device's built-in audio decoder, so support depends on the device. "EAC3 not supported" is a long-running complaint [S2][S5][S18]. |
| Gestures | Vertical swipe, left half | Brightness. Has its own brightness level, separate from the system setting [S9]. Some devices may need system auto-brightness off [S21]. |
| Gestures | Vertical swipe, right half | Volume. Can go above 100% (boost) when HW+/SW audio is used [S2][S21]. |
| Gestures | Horizontal swipe | Seek scrubbing with a time-delta overlay *(reported)*. |
| Gestures | Double tap | Off by default since v1.7.16 and can be re-enabled under Controls > Gestures. Commonly configured as left/right seek or center play/pause *(reported)* [S2]. |
| Gestures | Single tap | Toggles the controls UI. "Pause/resume" can be bound as the touch action instead [S2]. |
| Gestures | Pinch | Pinch to zoom, drag to pan. Zoom/pan also available from the menu [S1]. |
| Gestures | Subtitle gestures | On the subtitle text: swipe left/right jumps to the previous/next subtitle line, swipe up/down moves the text vertically, pinch resizes it [S1]. |
| Subtitles | Formats | Embedded DVD/DVB/SSA/ASS tracks, SSA/ASS with full styling, SAMI (Ruby), SRT, MicroDVD, VobSub (.sub/.idx), SubViewer 2, MPL2, TMPlayer, Teletext, PJS, WebVTT [S1]. |
| Subtitles | Styling | Font, colour, size, border, layout margins. Option to ignore broken fonts embedded in SSA. SSA font cache is built on load [S9]. |
| Subtitles | Sync | Subtitle panel with +/- buttons in 0.1 s steps [S17]. Users report sync drifting after seeking [S5]. |
| Subtitles | Sources | Auto-loads sidecar files. Can load subtitles for remote video. Online subtitle search/download exists (error "Can't save downloaded subtitles" appears in FAQ) [S9]. Paid AI generation and translation [S13]. |
| Audio | Track switch | Picks among embedded audio tracks or loads an external audio file *(reported)*. |
| Audio | Volume boost | Up to 200% via HW+/SW, using a constant gain factor with clipping. The FAQ admits the gain is small on already-loud sources [S2]. |
| Audio | Equalizer | Built-in EQ with presets *(reported; not in official docs reviewed)*. |
| Audio | A/V sync | Adjustable audio delay, plus troubleshooting for desync [S9]. |
| Network | Network stream | "Network stream" dialog for direct URLs (http, mms, etc.). Does not parse web pages. No DLNA browsing, though it can play DLNA URLs handed over by other apps [S2]. |
| Network | HTTP options | Configurable User-Agent. Extra HTTP headers can be passed via intent. Password-protected directory handling [S2][S9]. |
| Controls | Speed | Variable playback speed. Two-finger vertical swipe, or Menu > Play > Speed. Hardware decoding needs Android 6.0+; older devices need HW+ or SW [S21]. |
| Controls | Repeat | Loop one / loop all from Menu > Play, or long-press Next [S2]. A-B repeat is listed under playback controls [S21]. |
| Controls | Sleep timer | Stop-after timer *(reported)*. |
| Controls | Aspect/zoom | Fit, Stretch, Crop, 100% and custom zoom. Crop cannot enlarge beyond screen size in some cases [S9]. |
| Controls | Screen lock | Lock button blocks touch, and in older versions also Home/Power. Uses a "display system-level alerts" permission. Alternative lock modes exist for devices where the default breaks [S2][S9]. |
| Controls | Kids lock | A lock-mode setting (Player > Controls > Lock mode > Kids Lock), activated with the lock icon during playback. Stops kids leaving the video or touching other apps [S1][S3]. |
| Controls | PiP | Picture-in-picture with custom controls. Requests "Display over other apps" [S1]. |
| Controls | Background play | Audio keeps playing after leaving the player, with notification controls. A reviewed complaint says it fails to resume after Bluetooth output changes [S1][S5]. |
| Controls | Resume | Remembers position per file. Settings can reset per user/profile [S9]. |
| Controls | Multi-window | Samsung multi-window supported (with orientation quirks) [S9]. |
| Sharing | File transfer | Offline device-to-device transfer over Wi-Fi Direct/hotspot with QR pairing. Needs Camera, Location/Nearby devices and Modify System Settings. These are optional for the rest of the app [S1]. |
| Privacy | Privacy folder | Hides chosen videos in a private folder inside the app [S1]. |
| Settings | Scope | Very deep tree: Decoder, Player > Controls/Gestures/Lock mode, Subtitle, Audio, Display/Screen, List, Network (User-Agent), Dark mode [S2][S9]. |
| Online | OTT tab | Streaming videos, downloads, playlists, watch history and "continue watching", all mixed into the same app [S9]. |

---

## 3. User pain points

| # | Pain point | Evidence |
|---|---|---|
| 1 | **Intrusive / unskippable ads.** Reviewers describe ads after every pause or minimize. Users ask for a one-time payment to remove ads. | Play review [S5]; Reddit "fed up of cringe MX player ads" [S19] |
| 2 | **Ad removal is fragmented.** Pro is a separate app (installed alongside the free one, so users open the wrong app), India uses subscriptions instead, and those subscriptions were then discontinued. | [S8][S11][S2] |
| 3 | **Bloat from online content.** The local player sits inside an OTT app with video feed, Fatafat shorts, games and an account system. Sign-in is now Amazon-only. | [S6][S10][S12] |
| 4 | **Heavy permissions.** All Files Access is needed for media discovery. File transfer asks for Camera, Location, Nearby devices and Modify System Settings. Older versions requested "discover known accounts" and system alerts. | [S1][S4][S2] |
| 5 | **Missing AC3/EAC3/DTS audio.** These are not bundled, so playback depends on the device. Workarounds involve codec packs or self-built FFmpeg libraries, and the issue keeps reappearing after updates. | [S2][S5][S18][S20] |
| 6 | **Codec-pack confusion.** Architecture-specific packs and NEON variants, plus FAQ entries about packs that won't install or can't be found. | [S9][S16] |
| 7 | **Subtitle sync drift after seeking.** | Play review [S5] |
| 8 | **Unwanted autoplay of the next file.** Exiting a video starts another one. Playback breaks after an audio route change (AirPods to stereo). | Play review [S5] |
| 9 | **Too many updates / regressions.** | Play review [S5] |
| 10 | **Settings complexity.** Decoder, lock and gesture options are buried (for example, double tap is off by default). Many FAQ entries are workarounds for settings. | [S2][S9] |
| 11 | **Can't play during a phone call.** A user cites this as a reason VLC is better. | Reddit [S19] |
| 12 | **Platform shrinking.** iOS, TV and web apps are being discontinued, and there is no desktop player. | [S10] |

---

## 4. OM Player improvements (prioritized)

Legend: **M1** = Milestone 1 (core player + library). Priorities: P0 = must, P1 = should, P2 = later.

### P0
1. **M1: No ads, no account, no online feed.** Offline-first local player, with no sign-in and no telemetry by default. This addresses pain points 1–3.
2. **M1: Minimal permissions.** Use `READ_MEDIA_VIDEO`/`READ_MEDIA_AUDIO` on Android 13+ and honour partial "selected media" access on 14+. Add folders through the SAF folder picker as an option. All Files Access is never required, and there is a clear explanation screen. Addresses pain point 4.
3. **M1: Library.** MediaStore-backed folder and all-videos views. Thumbnails, duration and resolution badges, NEW/in-progress/watched state, sort/filter/search, and fast incremental rescans.
4. **M1: Resume and history.** Per-file position with a "resume from X?" prompt, a recently played list, and a continue-watching row (local files only).
5. **M1: Broad audio codec support out of the box.** Ship the Media3 FFmpeg audio decoder with AC3/EAC3/DTS/TrueHD/Opus/FLAC. No separate codec pack and no "custom codec" folder. Show per-file decoder info. Addresses pain points 5–6. *(Check licensing/patents for each codec before shipping.)*
6. **M1: Clear decoder model.** Use "Auto (hardware first, software fallback)" and switch automatically on decoder errors. Keep a single advanced toggle for "Prefer software" instead of HW/HW+/SW jargon.
7. **M1: Gestures matching MX muscle memory.** Left-half vertical swipe for brightness, right-half for volume, horizontal swipe to seek with a preview time, double-tap left/right to seek ±10 s (on by default, configurable), center double-tap or single tap to play/pause or show controls, pinch to zoom, long-press for 2x speed. Every gesture can be turned off individually.
8. **M1: Controls.** Speed 0.25–4x, aspect modes (Fit, Fill, Crop, 16:9, 4:3, Original), rotation lock, screen lock, prev/next within the folder, and a playlist/queue. Auto-play-next defaults **off** with a clear toggle (pain point 8).
9. **M1: Subtitles.** Embedded and sidecar SRT/ASS/SSA/VTT/PGS, auto-load by filename, track picker, sync slider with ±50 ms steps and "sync to current line". The offset is kept after seeking and stored per file (pain point 7). Basic styling: size, colour, outline, background, position.
10. **M1: Audio.** Track picker, external audio file, audio delay, and a volume boost up to 200% using a loudness limiter instead of hard clipping.
11. **M1: Background play and PiP.** MediaSession with notification and lock-screen controls. Auto-enter PiP on Home. Pause when headphones disconnect, handle audio focus, and resume correctly after Bluetooth route changes (pain point 8).
12. **M1: Desktop core (PC).** Open files and folders, drag and drop onto the window, standard keyboard shortcuts (below), remember window size, and a fullscreen toggle.

### P1
- **Full ASS/SSA rendering** via libass (karaoke, positioning, embedded fonts).
- **Online subtitle search** using OpenSubtitles with the user's own API key or login, matched by hash and filename. Optional, and off until configured.
- **10-band equalizer** with presets, plus bass boost and night mode (dynamic range compression).
- **A-B repeat**, loop one/all, and a **sleep timer** (minutes, or end of current file).
- **Kids lock:** lock touch and back navigation and pin the screen (screen pinning), unlocked by a long-press or PIN.
- **Network streams:** URL dialog (HTTP/HLS/DASH/RTSP) with User-Agent and header options, plus history.
- **Playback during calls** as an option (pain point 11).
- **Desktop:** mini-player (always-on-top compact window), media keys and OS integration (SMTC on Windows, MPRIS on Linux), recent files menu, and a file-association installer.
- Per-file memory of audio/subtitle track, zoom and speed.
- Screenshot / frame capture.

### P2
- **Privacy folder:** a vault in app-private storage, locked with biometrics or a PIN, with optional encryption.
- **LAN sharing:** browse and play SMB/DLNA (UPnP) shares and WebDAV. Optional "send to device" over the local network using QR pairing, without Modify-System-Settings or location permissions.
- **Desktop:** multiple player windows side by side, a synced "watch party" between OM instances on the LAN, playlist export.
- Chromecast / DLNA renderer output.
- Video filters (deinterlace, brightness/contrast), frame-step, and chapter navigation UI.
- Android TV / D-pad layout.

### PC keyboard shortcuts (P0 for M1 desktop)
| Key | Action | Key | Action |
|---|---|---|---|
| Space / K | Play/pause | F / double-click | Fullscreen toggle |
| ← / → | Seek ∓5 s (Shift: ∓30 s) | Esc | Exit fullscreen |
| ↑ / ↓ / wheel | Volume ± | M | Mute |
| [ / ] | Speed − / + (Backspace resets) | S / Shift+S | Next subtitle track / subtitle off |
| A | Next audio track | G / H | Subtitle delay −/+ 50 ms |
| N / P | Next / previous file | , / . | Frame step (when paused) |
| Ctrl+O | Open file | Ctrl+L | Open URL (P1) |
| Z | Cycle aspect/zoom | Ctrl+M | Mini-player (P1) |

---

## 5. Technical mapping (P0 features)

| P0 feature | Android (Media3) | Desktop (vlcj) | Shared / notes |
|---|---|---|---|
| No ads/account | — | — | Architecture decision. No ad SDKs. Only optional crash reporting, which the user opts into. |
| Minimal permissions | `READ_MEDIA_VIDEO/AUDIO`, `READ_MEDIA_VISUAL_USER_SELECTED` (API 34), `ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission` | JVM file access + `FileDialog`/`JFileChooser` | Permission rationale screen in `commonMain` Compose. |
| Library | `MediaStore.Video.Media` queries via `ContentResolver`, `ContentObserver` for changes, `loadThumbnail()` (API 29+) or `MediaMetadataRetriever` | `java.nio.file` walk + `WatchService`. Metadata/thumbnails via libVLC media parsing or a vlcj snapshot | Room KMP / SQLDelight as the shared index DB. Coil 3 for thumbnails. |
| Resume/history | `Player.currentPosition` saved on pause/stop | `MediaPlayer.status().time()` | Shared repository in DB, keyed by path/URI + size + mtime. |
| Codec coverage | Media3 `ExoPlayer` + `media3-decoder-ffmpeg` (**audio only**: AC3/EAC3/DTS/TrueHD/etc.), `DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER`. Video stays on `MediaCodec`. Optional `decoder-av1` (libdav1d/libgav1) and `decoder-vp9` | libVLC bundles FFmpeg/libavcodec, so practically all codecs | The FFmpeg extension must be built from source with the decoders we enable. Check codec licensing. |
| Auto HW→SW fallback | `MediaCodecSelector` + `setEnableDecoderFallback(true)`. Catch `PlaybackException.ERROR_CODE_DECODER_*` and retry on SW. For unsupported video codecs, fall back to a libVLC-Android engine (P1 decision) | libVLC `--avcodec-hw=any` vs `none` media option | Common `PlayerEngine` interface (expect/actual) so the UI does not depend on the engine. |
| Gestures | Compose `pointerInput` + `detectVerticalDragGestures`/`detectHorizontalDragGestures`/`detectTapGestures(onDoubleTap)`, `detectTransformGestures` for pinch. Brightness via `WindowManager.LayoutParams.screenBrightness`, volume via `AudioManager` | Mouse: wheel = volume, double-click = fullscreen, drag = seek (optional) | Gesture zone logic and overlays shared in `commonMain`. |
| Controls (speed, aspect, lock, queue) | `setPlaybackParameters(PlaybackParameters(speed))`, `AspectRatioFrameLayout` resize modes / `PlayerSurface` + `ContentScale`, `setMediaItems` playlist, `REPEAT_MODE_*` | `controls().setRate()`, `video().setAspectRatio()/setScale()`, vlcj `MediaListPlayer` | Lock overlay is a Compose layer that consumes input. |
| Subtitles | Media3 text renderers (SubRip, SSA (basic styling), WebVTT, TTML, PGS, DVB, CEA-608/708). `MediaItem.SubtitleConfiguration` for sidecars, `TrackSelectionParameters` for picking, `SubtitleView`/custom Compose cue renderer. Offset via a custom cue time shift. libass binding later for full ASS | libVLC native subtitles (libass included), `subpictures().setDelay()`, `subpictures().setSubTitleFile()` | Sidecar discovery (same basename, `.lang.srt`) shared. |
| Audio track/delay/boost | `TrackSelectionParameters` for tracks, `MergingMediaSource` for external audio, custom `AudioProcessor` for delay/gain or `android.media.audiofx.LoudnessEnhancer` on the audio session id | `audio().setTrack()`, `audio().setDelay()`, volume up to 200 via `audio().setVolume()` | Limiter to avoid clipping. |
| Background play | Media3 `MediaSessionService` + `MediaSession`, default media notification, `AudioAttributes` with `handleAudioFocus=true`, `setHandleAudioBecomingNoisy(true)` | N/A (desktop keeps playing). P1: SMTC/MPRIS | Foreground service type `mediaPlayback` (Android 14 requirement). |
| PiP | `PictureInPictureParams.Builder().setAspectRatio().setAutoEnterEnabled(true)` (API 31+), `RemoteAction`s, `onPictureInPictureModeChanged` | Mini-player (P1) = undecorated `Window(alwaysOnTop = true)` | Hide Compose controls in PiP. |
| Desktop core | — | vlcj 4.x `CallbackMediaPlayerComponent` / `CallbackVideoSurface` → Compose `ImageBitmap` (or `SwingPanel` + `EmbeddedMediaPlayerComponent`). Compose Desktop `Modifier.dragAndDropTarget`, `onPreviewKeyEvent`, `WindowState` for fullscreen and saved size | Bundle libVLC per OS, or detect a system VLC with `NativeDiscovery`. |

---

## 6. Sources

- [S1] Google Play: MX Player listing (features, subtitle formats, permissions, ratings) — https://play.google.com/store/apps/details?id=com.mxtech.videoplayer.ad&hl=en
- [S2] MX Player FAQ (archived, official Google Sites) — https://web.archive.org/web/2019/https://sites.google.com/site/mxvpen/faq
- [S3] Help center: How to enable Kids Lock — https://support.mxplayer.in/support/solutions/articles/43000608806-how-can-i-enable-activate-kids-lock-
- [S4] Help center: Why All Files Access is required — https://support.mxplayer.in/support/solutions/articles/43000652175-why-does-mx-player-now-require-all-files-access-or-allow-access-to-manage-all-files-permission-
- [S5] Google Play user reviews shown on the listing (EAC3, subtitle sync, unskippable ads, autoplay, Bluetooth) — https://play.google.com/store/apps/details?id=com.mxtech.videoplayer.ad&hl=en
- [S6] Wikipedia: Amazon MX Player (history, OTT relaunch, ownership) — https://en.wikipedia.org/wiki/MX_Player
- [S7] TechCrunch: Amazon buys MX Player (June 2024) — https://techcrunch.com/2024/06/06/amazon-buys-indian-video-streaming-service-mx-player/
- [S8] Help center: How can I get rid of advertisements? — https://support.mxplayer.in/support/solutions/articles/43000608796-how-can-i-get-rid-of-advertisements-
- [S9] Help center knowledge base (MX Video Player sections: Run, Playing, Subtitles, UI) — https://support.mxplayer.in/support/home
- [S10] Help center: Will Amazon MX Player be discontinued? — https://support.mxplayer.in/support/solutions/articles/43000788232-will-amazon-mx-player-be-discontinued-where-can-i-watch-amazon-mx-player-content-do-i-need-to-insta
- [S11] Help center: Important update on membership plans — https://support.mxplayer.in/support/solutions/articles/43000785961-important-update-on-mx-player-membership-plans
- [S12] Help center: What is changing in my MX account? — https://support.mxplayer.in/support/solutions/articles/43000786479-what-is-changing-in-my-mx-account-
- [S13] Help center: AI Subtitle Generation / quota — https://support.mxplayer.in/support/solutions/articles/43000783098-how-can-i-use-ai-subtitle-generation- and https://support.mxplayer.in/support/solutions/articles/43000783099-how-many-subtitles-can-i-generate-
- [S14] Help center: Maturity rating of advertisements — https://support.mxplayer.in/support/solutions/articles/43000608920-what-is-maturity-rate-of-advertisement-from-mx-player-
- [S15] Help center: AI subtitle supported formats (EAC3 excluded) — https://support.mxplayer.in/support/solutions/articles/43000783106-what-video-and-audio-formats-are-supported-
- [S16] Help center: App takes too much space (NEON codec) / codec package usage — https://support.mxplayer.in/support/solutions/articles/43000608804-app-takes-up-too-much-space-how-can-i-reduce-occupied-space- and https://support.mxplayer.in/support/solutions/articles/43000608793-i-downloaded-a-codec-package-but-cannot-find-how-to-use-
- [S17] Help center: How to adjust subtitle sync — https://support.mxplayer.in/support/solutions/articles/43000608872-how-to-adjust-subtitle-sync-
- [S18] Reddit r/AndroidTV: "EAC3 Audio not supported MX Player" (thread title; body not retrievable) — https://www.reddit.com/r/AndroidTV/comments/n9zzuq/eac3_audio_not_supported_mx_player/ and https://www.reddit.com/r/AndroidTV/comments/18v7fbg/eac3/
- [S19] Reddit r/androidapps: "MX player ad free alternatives" — https://www.reddit.com/r/androidapps/comments/10mhdqw/mx_player_ad_free_alternatives/
- [S20] Reddit r/fossdroid: "MX player like ad free alternatives" (thread title) — https://www.reddit.com/r/fossdroid/comments/10mhe7l/mx_player_like_ad_free_alternatives/
- [S21] MX Player features page, archived official Google Sites (same site as S2; gesture zones, speed, A-B repeat) — https://www.sites.google.com/site/mxvpen/features

**Research limits:** Reddit threads S18 and S20 could not be fetched from our tools, so they remain
cited by title only. The equalizer and sleep timer remain *(reported)*: the official
features page [S21] does not document them, and third-party APK changelogs only mention a
"Timer" shortcut without describing it. Confirm both hands-on with the public app before
finalizing the UX spec.
