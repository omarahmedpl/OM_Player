# Prompt: build the Plus edition the legal way

Copy everything below the line into Claude Code or Codex, started in `/home/omarahmed/Downloads/OM_Player`.

---

You are continuing OM Player, an ad-free Kotlin Compose Multiplatform player for Android and PC. Read `AGENTS.md`, then `docs/HANDOFF.md` section 0, then `docs/STREAMING_PLAN.md`. Inspect `git status` and `git log` before editing, and preserve any uncommitted work.

## Goal
Grow the **Plus** Android edition (applicationId `com.omplayer.plus`, `BuildConfig.DISCOVER = true`) so the owner can find music online and play it, including offline, using only **legal, official sources**. The **Lite** edition (`com.omplayer`) must keep working with no Discover tab and no online code paths in use. Both are for the owner's personal use; Plus is never published.

## Hard rules (do not break these)
- Use only official APIs, open feeds and open licences. **Never** add yt-dlp, youtube-dl, NewPipe-style extraction, page scraping, unofficial or reverse-engineered endpoints, or DRM circumvention. Never fetch audio from Spotify, YouTube, YouTube Music, Anghami, Apple Music, Deezer or any service whose terms don't allow it. If a request needs that, stop and say so.
- Offer a download only when the source allows it: Audius `access.download == true` and no `download_conditions`; podcast enclosures; Internet Archive items with a licence that permits copies (show the licence); Jamendo tracks whose Creative Commons terms allow it. Radio is stream-only. SoundCloud: no offline copies except tracks whose owner enabled downloads.
- Show each source's attribution and a link back (for example "Powered by Audius").
- Identify the app in `User-Agent`. Respect rate limits (MusicBrainz 1 request per second, 429 handling, back-off).
- No secrets in git. Keys and client ids go in git-ignored `local.properties` and reach the app through `BuildConfig` fields. A client secret must never be in the APK (SoundCloud needs a token broker).
- Keep the contracts in `HANDOFF.md` stable, or update that file in the same change. Colours only from `OmTheme.colors`. Don't add permissions beyond what exists.
- Don't commit or push unless the owner asks.

## Build in this order (each step ends with tests, an emulator check and a HANDOFF update)
1. **Source registry.** Replace the single `OnlineMusicSource` with a list of sources (id, name, attribution, capabilities: stream, download, search, browse). Discover shows one chip per source. Keep Audius working.
2. **Downloads.** A `DownloadStore` (persisted via `TextStorage`) and a downloader in `src/jvmShared` that writes to a `.part` file and renames on success, with progress, cancel, retry and delete. Files live in app-private storage. A "Downloaded" list plays fully offline (no network calls), keeps title, artist and cover, and survives restarts. Show storage used and a delete-all option. Downloaded items must not use favourites or playlists unless you extend those stores on purpose.
3. **Podcasts.** Search with the iTunes Search API, read each show's RSS feed (enclosure URLs), list episodes, stream, download, remember played position and played/unplayed. Parse feeds defensively (huge feeds, odd encodings, redirects).
4. **Internet Archive.** Search with `advancedsearch.php` (`mediatype:audio`), read item metadata for files and licence, pick a playable audio file (mp3/ogg/flac), stream and download.
5. **Internet radio.** Radio Browser (pick a server from its server list, use `url_resolved`, send the polite click count). Stream only, show station name and codec, handle dead stations.
6. **Jamendo.** Needs a free client id from the owner, read from `local.properties`; the tab stays hidden when the key is missing.
7. **SoundCloud, only after the owner supplies a client id and a token-broker URL.** Follow `docs/STREAMING_PLAN.md`: PKCE in the system browser, tokens in Keystore-backed storage, resolve stream URLs when a track starts (they expire), show the logo and link, no offline copies. Also write the small token-broker code (for example a Cloudflare Worker) but do not deploy it.

## Quality bar
- Unit tests with fakes for parsing (feeds, JSON), the download state machine and offline playback selection. `./gradlew :composeApp:desktopTest` must pass.
- Build and install both editions on the emulator (`adb -s emulator-5554`): confirm Lite has no Discover tab, Plus does, a stream plays, a download completes, and airplane mode still plays downloaded items. Use real behaviour, not screenshots alone: check `dumpsys media_session` for playback state.
- Handle errors in plain words (offline, 429/503, blocked, removed track).
- Final report: files changed, test results, what you verified on a device, and what you could not verify.
