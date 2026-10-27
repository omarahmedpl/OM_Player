package com.omplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.omplayer.AppSettings
import com.omplayer.LocalAppDeps
import com.omplayer.VideoSurface
import com.omplayer.model.MediaItem
import com.omplayer.player.ScaleMode

/**
 * Full-screen player. CONTRACT (called from App.kt): signature must stay as-is.
 * Placeholder body; replaced by the player agent (controls, gestures, sheets, keyboard shortcuts).
 */
@Composable
fun PlayerScreen(
    item: MediaItem,
    queue: List<MediaItem>,
    settings: AppSettings,
    onItemChange: (MediaItem) -> Unit,
    onClose: () -> Unit,
) {
    val deps = LocalAppDeps.current
    val engine = deps.engine
    LaunchedEffect(item.id) {
        engine.open(item, if (settings.resumePlayback) deps.progress.positionMs(item.id) ?: 0 else 0)
    }
    DisposableEffect(Unit) {
        onDispose {
            val s = engine.state.value
            s.item?.let { deps.progress.save(it.id, s.positionMs, s.durationMs) }
            engine.stop()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable { onClose() }) {
        VideoSurface(engine, ScaleMode.Fit, 1f, Modifier.fillMaxSize())
    }
}
