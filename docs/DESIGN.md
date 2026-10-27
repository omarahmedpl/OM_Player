# OM Player — Design Brief (prototype phase)

OM Player is an original, clean-room media player for Android and PC (later built in
Compose Multiplatform: Media3/ExoPlayer on Android, vlcj on desktop). The feature set
draws on what users expect from players like MX Player, but no MX code, assets, names
or branding are used.

## Principles
1. **Content first.** Chrome fades away during playback; the library shows big thumbnails.
2. **One-hand, no-look gestures** on mobile; **keyboard-first** on desktop.
3. **Calm, fast motion.** Use the token curves (`--ease-out`, `--ease-drawer`), keep durations under 300 ms (sheets ≤ 380 ms), and press feedback at `scale(0.97)`. Never animate from `scale(0)`. Keyboard-initiated actions get no animation.
4. **No ads, no clutter.** This is a key improvement over MX Player.
5. **Accessible.** 4.5:1 text contrast, 44 px targets, visible focus, `prefers-reduced-motion`, aria-labels on icon buttons.

## Tokens
All values come from `prototype/tokens.css`. Never hard-code a hex colour in a screen file.
Icons are inline SVG in the Lucide style (24 px viewBox, 1.75 stroke, round caps). No emoji.

## Screens (both platforms)
| # | Screen | Mobile | Desktop |
|---|--------|--------|---------|
| 1 | Library: Folders | grid/list toggle, sort, search | sidebar + content grid |
| 2 | Library: Videos in folder | thumbnails, duration, resume progress bar, "new" dot | same, with hover preview |
| 3 | Continue Watching / Recents row | top of library | top of home |
| 4 | Player | gestures (left-half vertical swipe = brightness, right = volume, horizontal = seek with preview, double-tap ±10 s, pinch = zoom/fill, long-press = 2× speed), lock, rotate, PiP | hover controls, keyboard shortcuts, mini player |
| 5 | Player sheets/menus | audio track, subtitles (track + style + sync offset + online search), speed (0.25–4×), aspect/zoom, sleep timer, A-B repeat, equalizer | same as popovers/side panel |
| 6 | Network stream | URL input + history | same |
| 7 | Settings | decoder (HW/HW+/SW), gestures, subtitles, theme, privacy folder (PIN) | same |

## Sample content
Use CSS gradient "thumbnails" with plausible fictional titles. No real film posters, no network images.
