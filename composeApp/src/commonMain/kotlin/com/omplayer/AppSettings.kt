package com.omplayer

import androidx.compose.runtime.Immutable
import com.omplayer.player.Decoder
import com.omplayer.theme.ThemeMode

/** User settings shared by library, player and settings screens. Held in App state; persistence comes later. */
@Immutable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.Dark,
    val decoder: Decoder = Decoder.HW,
    val resumePlayback: Boolean = true,
    val backgroundPlay: Boolean = false,
    // Gestures (phone)
    val gestureBrightness: Boolean = true,
    val gestureVolume: Boolean = true,
    val gestureSeek: Boolean = true,
    val gestureDoubleTap: Boolean = true,
    val gestureLongPressSpeed: Boolean = true,
    val gesturePinchZoom: Boolean = true,
    /** Seconds skipped by double-tap and J/L. */
    val seekStepSec: Int = 10,
    // Subtitles defaults
    val subtitleScale: Float = 1f,
    val subtitleBackground: Boolean = true,
)
