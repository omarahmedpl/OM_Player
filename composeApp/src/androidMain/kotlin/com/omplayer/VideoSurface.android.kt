package com.omplayer

import android.graphics.Color
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.omplayer.player.Media3PlayerEngine
import com.omplayer.player.PlayerEngine
import com.omplayer.player.ScaleMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(UnstableApi::class)
@Composable
actual fun VideoSurface(engine: PlayerEngine, scaleMode: ScaleMode, zoom: Float, modifier: Modifier, transformed: Boolean) {
    val exo = (engine as? Media3PlayerEngine)?.player
    if (exo == null) {
        Box(modifier.background(androidx.compose.ui.graphics.Color.Black))
        return
    }
    val subtitleStyle by remember(engine) {
        engine.state.map { it.subtitleScale to it.subtitleBackground }.distinctUntilChanged()
    }.collectAsState(initial = engine.state.value.let { it.subtitleScale to it.subtitleBackground })
    // Keep ordinary playback on the native compositor; TextureView is needed for manual transforms.
    val texture = transformed || zoom != 1f
    key(texture) {
        AndroidView(
            modifier = modifier.background(androidx.compose.ui.graphics.Color.Black).clipToBounds()
                .graphicsLayer { scaleX = zoom; scaleY = zoom },
            factory = { ctx ->
                (if (texture) LayoutInflater.from(ctx).inflate(R.layout.player_view_texture, null, false) as PlayerView
                else PlayerView(ctx)).apply {
                    useController = false
                    setShutterBackgroundColor(Color.BLACK)
                    setBackgroundColor(Color.BLACK)
                    setKeepContentOnPlayerReset(true)
                    player = exo
                }
            },
            update = { v ->
                if (v.player !== exo) v.player = exo
                v.subtitleView?.apply {
                    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitleStyle.first)
                    setStyle(
                        CaptionStyleCompat(
                            Color.WHITE,
                            if (subtitleStyle.second) 0x99000000.toInt() else Color.TRANSPARENT,
                            Color.TRANSPARENT,
                            if (subtitleStyle.second) CaptionStyleCompat.EDGE_TYPE_NONE else CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                            Color.BLACK,
                            null,
                        ),
                    )
                }
                v.resizeMode = when (scaleMode) {
                    ScaleMode.Fit, ScaleMode.Original -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    ScaleMode.Fill -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    ScaleMode.Crop -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            onRelease = { it.player = null },
        )
    }
}
