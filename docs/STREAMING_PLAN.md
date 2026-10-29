# Streaming integration plan (historical draft)

**Current implementation (2026-10-29):** Android Plus now uses the existing `YtDlpSource` for search, per-track stream resolution and downloads. The owner explicitly approved retaining it. This document preserves the earlier official-API research; its proposed implementation order does not describe the current app. See [HANDOFF.md section 0](HANDOFF.md#0-current-state-read-this-first-last-updated-2026-10-29) for current architecture and verification status. Lite and desktop do not enable this source, and Plus APKs remain private.

Drafted by Codex on 2026-10-29 (read-only planning pass), condensed here. It cites the services' official docs as of that date, but terms change, so re-check each link before building. Nothing here is implemented.

**Headline:** start with a SoundCloud feasibility check. Defer Spotify until its policy and distribution limits are settled. Use external-app links for Anghami and YouTube Music.

## 1. What each service officially permits

| Service | Playback and auth | Access, limits, branding |
|---|---|---|
| **SoundCloud** | Official APIs support custom-player streams, so Media3 playback is possible for eligible tracks. OAuth 2.1 with PKCE; the docs also require a client secret, so token exchange and refresh need a small backend. Public playback needs app authentication; private resources need user consent. Tracks can be preview-only, blocked or territory-limited. | Registration currently needs the developer's Artist Pro subscription. Check OM Player's use case against the API terms. Respect rate-limit headers and 429s. Show uploader credit, SoundCloud logo and a source link. [API guide](https://developers.soundcloud.com/docs/api/guide), [registration](https://developers.soundcloud.com/docs/api/getting-started), [limits](https://developers.soundcloud.com/docs/api/rate-limits), [terms](https://developers.soundcloud.com/docs/api/terms-of-use) |
| **Spotify** | Android App Remote controls the installed, signed-in Spotify app; it gives no audio URL for Media3. The Web API gives metadata and some playback commands (browser auth-code PKCE). Premium is needed for most playback control. | Development mode: five allowlisted users and a Premium app owner. Extended access needs an organization and about 250,000 monthly active users. Policy restricts products that combine Spotify with other services' streams, so get clarification first. Logo and backlink required. [SDK](https://developer.spotify.com/documentation/android/tutorials/getting-started), [access](https://developer.spotify.com/documentation/web-api/concepts/quota-modes), [policy](https://developer.spotify.com/policy), [branding](https://developer.spotify.com/documentation/design) |
| **Anghami** | No current public, self-service playback API or SDK could be verified. Don't assume playback or remote control. Open official links externally, with no account connection. | Would need partner documentation and written approval. [Terms](https://www.anghami.com/legal) |
| **YouTube Music** | No public YouTube Music catalogue or audio API. The YouTube Data API gives metadata only. Embedded playback must stay visible: no audio extraction, no hidden or background player. | External links need no OAuth. Optional YouTube browsing would need a Cloud project, OAuth and quota audits. [Policies](https://developers.google.com/youtube/terms/developer-policies-guide), [quotas](https://developers.google.com/youtube/v3/guides/quota_and_compliance_audits) |

OM Player may stay ad-free for its own content but must never suppress ads a provider requires. Store acceptance can't be guaranteed.

## 2. Recommended order

1. Validate SoundCloud credentials, terms and backend cost first. It's the clearest route to audio inside OM Player.
2. Android SoundCloud pilot: connect, browse/search, play eligible tracks, provider-only queue, disconnect.
3. External service links. Spotify remote control only after policy clarification and a workable distribution route.
4. Desktop SoundCloud later. Skip YouTube embedding, Anghami playback, cross-service queues and automatic playlist sync at first.

## 3. Architecture in this repo

- Add a `StreamingSource` interface (connection state, capabilities, paged catalogue, playback resolution) and a stable `TrackRef(provider, id)`. Resolution returns a stream descriptor, a remote target or an external link.
- SoundCloud tracks map to `MediaItem` with namespaced ids and `isAudio = true`. Keep signed URLs, tokens and expiry out of persisted items. Resolve each queue entry when Media3 opens it, not for the whole queue up front (`openQueue()` currently builds every item immediately, so URLs could expire).
- Spotify goes in a separate `RemotePlaybackController`, never pretend to be a `PlayerEngine`. A small coordinator in `App.kt` picks local or remote playback and prevents overlap.
- UI: a Music "Services" area with one page per provider. Reuse the mini-player with provider attribution and capability-based controls. Disable sharing, deleting, metadata editing, lyrics lookup, speed and boost for streamed tracks unless the provider allows them.
- Media3 streams reuse `PlaybackService`. Spotify keeps its own notification and audio focus.
- Local favourites and playlists stay as they are. Provider collections are separate and read-only at first.
- No offline downloads or persistent audio cache. Only metadata and artwork, within provider rules.

## 4. Auth and settings

- System browser or Custom Tabs, PKCE (S256), single-use `state`, exact redirect checks, minimal scopes.
- Client IDs come from ignored local config or CI secrets. Client secrets must never ship in an APK or desktop package: SoundCloud needs a small token-broker backend that holds them.
- Tokens in Android Keystore-backed encrypted storage, excluded from backups, never in `UserLibrary`.
- Settings gets "Connected services" with Connect, Reconnect and Disconnect. Disconnect revokes where supported and clears tokens and caches.

## 5. Phases

1. Access gate: get approvals and write down exactly what each service allows.
2. Contracts and auth: a `streaming/` package, `Platform.kt` hooks, manifest and config; the backend separately.
3. Playback: provider-aware resolution in `Media3PlayerEngine`, error handling, coordinator in `App.kt`.
4. UI and data: Music "Services", Settings, player and mini-player attribution.
5. Tests and checks: fakes for capabilities, mapping and playback switching; Android tests for callback validation, refresh races and expiry; manual runs with real accounts, background play, Bluetooth, process recreation and disconnect. Then review Play and Flathub rules and update `docs/HANDOFF.md`.

Main risks: API closure, quotas, policy changes, backend upkeep, unavailable tracks, store rejection.

## Decisions needed from the owner

- Personal pilot or public release?
- Will you register SoundCloud credentials (Artist Pro) and fund a small backend?
- Can you get Spotify clarification and Anghami partner access?
- Which countries and test accounts?
- Who owns the HTTPS callback domain and privacy-policy page?
