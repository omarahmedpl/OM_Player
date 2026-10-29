# Audio player: competitor scan, ideas, critique (2026-10-29)

Sources: my knowledge of the apps (no live web research this pass; pricing and share figures are not verified), plus a read of OM Player's source.

## 1. Competitor scan (audio on Android)

| App | Strength | Weakness | Lesson for OM Player |
|---|---|---|---|
| Spotify | Persistent mini-player, large artwork, queue, sleep timer, lock-screen controls | Streaming and account first, ads on free tier, no local-file focus | Mini-player + full-screen "Now Playing" with queue is the baseline users expect |
| Apple Music | Artwork-led full player, clean typography, restrained motion | Local files are second-class on Android | Square artwork, title/artist hierarchy, one primary control |
| Pocket Casts | Skip back 10 s / forward 30 s, variable speed, sleep timer with "end of episode", resume position | Podcast-only | Spoken-word controls: skip buttons, speed, per-file resume |
| Poweramp | Local library, equalizer, gapless, deep settings | Dense UI, paid | Local-first audio is a real niche; keep settings shallow |
| VLC | Plays everything, background play | Utilitarian UI, video-shaped player | Our edge is polish on top of the same format coverage |
| MX Player | Video-first, ads, Amazon account | Audio is an afterthought | OM Player's pitch: no ads, no account, real audio UX |

**Gap OM Player can own:** ad-free, account-free, local-first audio with Spotify-grade player polish and a shared video/audio library.

## 2. Ideas (PM / Designer / Engineer), top 5

1. **Audio-first Now Playing screen** (Designer). Square artwork, title, folder/artist, slim seek bar with elapsed and remaining time, big play/pause, previous/next, skip ±10/30 s. No video chrome (no rotate, fullscreen, scale mode).
2. **One shared playback session + mini-player** (Engineer). A single `MediaSessionService` holds the one ExoPlayer; mini-player and full player both read it, so navigation never recreates the player and playback continues in the background.
3. **Queue, speed, sleep timer** (PM). Reorder-free queue view with tap-to-jump, speed 0.5-3x, sleep timer (15/30/60 min, end of track).
4. **Multi-select with share/delete** (PM). Long-press selects, action bar shows count, Share and Delete; delete goes through the system confirmation on Android 11+.
5. **Subtitle delay that works** (Engineer). Apply the stored offset to cue timing instead of only displaying it.

Assumptions to validate: users mostly play local music/podcasts in the background; skip-forward 30 s matters for spoken audio; delete needs the system consent dialog on API 30+.

## 3. Critique of today's audio UX

- **First impression:** opening a song shows the video player (black stage, scale-mode and rotate buttons, fullscreen). The first impression is "video app that happens to play audio".
- **Usability (critical):** no mini-player and no background-play service. `AppSettings.backgroundPlay` exists but nothing implements it; leaving the app pauses nothing, but there is no notification or lock-screen control, and navigating away closes the player (`PlayerScreen` calls `engine.stop()` on dispose).
- **Usability (moderate):** "Play as audio" menu item is a stub. Folder queue ordering for music uses all items in the list, but there is no queue view.
- **Hierarchy:** video chrome (aspect, rotate, lock) competes with the controls that matter for audio. Artwork is only a 28 dp icon placeholder in the library and not shown at all while playing.
- **Consistency:** audio reuses `PlayerScreen`, so gesture HUDs (brightness/volume swipes) fire on a screen with nothing to look at.
- **Accessibility:** controls have content descriptions and 44 dp targets. Good. Seek bar exposes a state description. Good.
- **What works:** library design tokens, press/hover feedback, resume store, PiP handling, Media3 engine and MediaSession already exist.

Root cause of the video-player feel: one `PlayerScreen` serves both media types, the engine is owned by the Activity (dies with it, no `MediaSessionService`), and the UI has no audio branch.

## 4. Scope decision for this pass

Implement: audio Now Playing + mini-player (UI branch), multi-select share/delete (phone), subtitle delay (Android). A true `MediaSessionService` is the larger change and is tracked below.
