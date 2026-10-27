package com.omplayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.omplayer.player.PlayerEngine
import com.omplayer.player.ScaleMode
import com.omplayer.player.VlcjPlayerEngine
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect

@Composable
actual fun VideoSurface(engine: PlayerEngine, scaleMode: ScaleMode, zoom: Float, modifier: Modifier) {
    val sink = (engine as? VlcjPlayerEngine)?.frames
    val tick = sink?.tick?.collectAsState()?.value ?: 0
    Canvas(modifier.fillMaxSize()) {
        tick // read so the canvas redraws on every frame
        val img = sink?.image ?: return@Canvas
        val cw = size.width
        val ch = size.height
        val iw = img.width.toFloat()
        val ih = img.height.toFloat()
        if (cw <= 0f || ch <= 0f) return@Canvas
        val (dw, dh) = when (scaleMode) {
            ScaleMode.Fit -> minOf(cw / iw, ch / ih).let { iw * it to ih * it }
            ScaleMode.Crop -> maxOf(cw / iw, ch / ih).let { iw * it to ih * it }
            ScaleMode.Fill -> cw to ch
            ScaleMode.Original -> iw to ih
        }
        val z = zoom.coerceAtLeast(0.1f)
        val w = dw * z
        val h = dh * z
        val l = (cw - w) / 2f
        val t = (ch - h) / 2f
        clipRect {
            drawIntoCanvas {
                it.nativeCanvas.drawImageRect(
                    img, Rect.makeWH(iw, ih), Rect.makeXYWH(l, t, w, h),
                    FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE), null, true,
                )
            }
        }
    }
}
