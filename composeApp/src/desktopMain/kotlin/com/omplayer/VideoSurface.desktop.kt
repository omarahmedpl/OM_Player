package com.omplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.omplayer.player.PlayerEngine
import com.omplayer.player.ScaleMode

@Composable
actual fun VideoSurface(engine: PlayerEngine, scaleMode: ScaleMode, zoom: Float, modifier: Modifier) {
    // Placeholder until the vlcj engine lands.
    Box(modifier)
}
