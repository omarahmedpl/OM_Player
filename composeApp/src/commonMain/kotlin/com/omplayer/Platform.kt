package com.omplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.omplayer.data.MediaRepository
import com.omplayer.data.ProgressStore
import com.omplayer.player.PlayerEngine
import com.omplayer.player.ScaleMode

enum class FormFactor { Phone, Desktop }

/** Platform hooks the shared UI may call. Every method is safe to call on any platform (no-op where unsupported). */
interface PlatformActions {
    val formFactor: FormFactor
    /** Window brightness for the player, 0f..1f, or null to restore the system value. */
    fun setPlayerBrightness(value: Float?) {}
    fun enterPictureInPicture() {}
    fun setFullscreen(enabled: Boolean) {}
    /** Lock rotation to landscape (true) or let it follow the sensor/system (false). */
    fun setLandscape(enabled: Boolean) {}
    fun keepScreenOn(enabled: Boolean) {}
    /** Open a file picker for a single media file; result arrives via the callback (null = cancelled). */
    fun pickMediaFile(onResult: (uri: String?) -> Unit) { onResult(null) }
}

/** Everything the shared app needs from a platform, built once in each entry point. */
class AppDeps(
    val repository: MediaRepository,
    val progress: ProgressStore,
    val engine: PlayerEngine,
    val platform: PlatformActions,
)

val LocalAppDeps = staticCompositionLocalOf<AppDeps> { error("AppDeps not provided") }

/** Draws the engine's video. Android: Media3 surface. Desktop: vlcj frames drawn into Compose. */
@Composable
expect fun VideoSurface(engine: PlayerEngine, scaleMode: ScaleMode, zoom: Float, modifier: Modifier)
