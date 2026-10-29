package com.omplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.omplayer.data.MediaRepository
import com.omplayer.data.ProgressStore
import com.omplayer.player.PlayerEngine
import com.omplayer.player.ScaleMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FormFactor { Phone, Desktop }

enum class ShareFormat { Json, Image }

/** A playlist as shared with other apps: its name and the track titles in order. */
class SharedPlaylist(val name: String, val tracks: List<String>)

/** Requested screen orientation while the player is open. Auto follows the device/system setting. */
enum class ScreenOrientation { Auto, Landscape, Portrait }

/** Platform hooks the shared UI may call. Every method is safe to call on any platform (no-op where unsupported). */
interface PlatformActions {
    val formFactor: FormFactor
    /** Window brightness for the player, 0f..1f, or null to restore the system value. */
    fun setPlayerBrightness(value: Float?) {}
    fun enterPictureInPicture() {}
    fun setFullscreen(enabled: Boolean) {}
    /** Lock rotation to landscape (true) or let it follow the sensor/system (false). */
    fun setLandscape(enabled: Boolean) {}
    /** Rotate the whole screen (Android). Controls turn with it; no-op on desktop. */
    fun setScreenOrientation(orientation: ScreenOrientation) {}
    fun keepScreenOn(enabled: Boolean) {}
    /** Open a file picker for a single media file; result arrives via the callback (null = cancelled). */
    fun pickMediaFile(onResult: (uri: String?) -> Unit) { onResult(null) }
    /** Share media files with other apps. Returns false when the platform can't. */
    fun shareMedia(uris: List<String>, audioOnly: Boolean, videoOnly: Boolean): Boolean = false
    /** Delete media files after the system's own confirmation. [onResult] gets the number actually deleted. */
    fun deleteMedia(uris: List<String>, onResult: (deleted: Int) -> Unit) { onResult(0) }
    /** Let the user pick a small text file (an exported playlist). [onResult] gets its text, or null if cancelled or unreadable. */
    fun pickTextFile(onResult: (text: String?) -> Unit) { onResult(null) }
    /** Let the user pick an image; the platform copies it (scaled down) into app storage and returns that file's path, or null. */
    fun pickImage(onResult: (path: String?) -> Unit) { onResult(null) }
    /** Share playlists (names and song titles) as a JSON file or a PNG image. Returns false when the platform can't. */
    fun sharePlaylists(lists: List<SharedPlaylist>, format: ShareFormat): Boolean = false
    /** Called when audio playback starts so the platform can start its background service / ask for notifications. */
    fun onAudioPlaybackStarted() {}
    /** True while the app runs in a picture-in-picture window; the player hides its controls. */
    val inPictureInPicture: StateFlow<Boolean> get() = NotInPip
    /** Desktop: the window's real fullscreen state (it can also change outside the player, e.g. Esc at window level). */
    val windowFullscreen: StateFlow<Boolean> get() = NotInPip
    /** Emits when the user dismisses the PiP window (not when they expand it back). The player then stops and closes. */
    val pictureInPictureDismissed: Flow<Unit> get() = emptyFlow()
}

private val NotInPip: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

/** Everything the shared app needs from a platform, built once in each entry point. */
class AppDeps(
    val repository: MediaRepository,
    val progress: ProgressStore,
    val engine: PlayerEngine,
    val platform: PlatformActions,
    /** Favourites and playlists. */
    val userData: com.omplayer.data.UserLibrary = com.omplayer.data.UserLibrary(),
    /** Finds song lyrics online. */
    val lyrics: com.omplayer.data.LyricsProvider = com.omplayer.data.NoLyrics,
)

val LocalAppDeps = staticCompositionLocalOf<AppDeps> { error("AppDeps not provided") }

/** Draws the engine's video. [transformed] describes the caller's manual video-layer transform,
 * so Android can select a compatible surface. Media orientation metadata stays with the engine. */
@Composable
expect fun VideoSurface(engine: PlayerEngine, scaleMode: ScaleMode, zoom: Float, modifier: Modifier, transformed: Boolean = false)

/** Draws a bounded video preview when the platform can retrieve one; otherwise the caller's placeholder remains visible. */
@Composable
expect fun VideoThumbnail(uri: String, modifier: Modifier = Modifier)

/** Draws an image file from app storage (a cover the user picked); draws nothing if it can't be read. */
@Composable
expect fun LocalImage(path: String, modifier: Modifier = Modifier)

/** Draws embedded album artwork when available; the library keeps its music-note fallback otherwise. */
@Composable
expect fun AudioArtwork(uri: String, modifier: Modifier = Modifier)

/**
 * System Back (Android back button/gesture). The most recently composed enabled handler wins,
 * so inner UI (a player panel) registers after outer UI (the player, the library). No-op on desktop.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/**
 * Hosts the player UI while fullscreen. Android renders [content] in place. Desktop/Linux renders it in its own
 * borderless, screen-sized window: the system fullscreen state can't be left reliably there (GNOME/XWayland +
 * OpenJDK re-applies it), so OM Player never enters it. [onExit] is called when that window is closed.
 * Only the UI moves; the player's state and playback lifecycle stay with the caller.
 */
@Composable
expect fun FullscreenHost(active: Boolean, onExit: () -> Unit, content: @Composable () -> Unit)
