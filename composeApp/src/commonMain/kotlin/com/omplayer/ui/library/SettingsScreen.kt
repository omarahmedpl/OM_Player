package com.omplayer.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.omplayer.AppSettings
import com.omplayer.player.Decoder
import com.omplayer.theme.OmSpace
import com.omplayer.theme.ThemeMode
import com.omplayer.ui.components.ControlRow
import com.omplayer.ui.components.OmSegmented
import com.omplayer.ui.components.SectionHeader
import com.omplayer.ui.components.SwitchRow

@Composable
internal fun SettingsScreen(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val pad = contentPad()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pad).padding(bottom = OmSpace.s8)) {
        SectionHeader("Appearance")
        ControlRow("Theme") {
            OmSegmented(
                listOf(ThemeMode.Dark to "Dark", ThemeMode.Light to "Light", ThemeMode.System to "System"),
                settings.theme, { onChange(settings.copy(theme = it)) },
            )
        }

        SectionHeader("Playback")
        ControlRow("Decoder", subtitle = "Software decoding is used automatically if hardware fails.") {
            OmSegmented(
                listOf(Decoder.HW to "Hardware", Decoder.SW to "Software"),
                settings.decoder, { onChange(settings.copy(decoder = it)) },
            )
        }
        SwitchRow("Resume playback", settings.resumePlayback, { onChange(settings.copy(resumePlayback = it)) }, subtitle = "Continue where you left off")
        SwitchRow("Background play", settings.backgroundPlay, { onChange(settings.copy(backgroundPlay = it)) }, subtitle = "Keep audio playing when the app is hidden")
        ControlRow("Seek step", subtitle = "Double-tap and J / L keys") {
            OmSegmented(
                listOf(5, 10, 15, 30).map { it to "$it s" },
                settings.seekStepSec, { onChange(settings.copy(seekStepSec = it)) },
            )
        }

        SectionHeader("Gestures")
        SwitchRow("Brightness", settings.gestureBrightness, { onChange(settings.copy(gestureBrightness = it)) }, subtitle = "Swipe the left half up or down")
        SwitchRow("Volume", settings.gestureVolume, { onChange(settings.copy(gestureVolume = it)) }, subtitle = "Swipe the right half up or down")
        SwitchRow("Swipe to seek", settings.gestureSeek, { onChange(settings.copy(gestureSeek = it)) }, subtitle = "Swipe sideways with a preview")
        SwitchRow("Double-tap to skip", settings.gestureDoubleTap, { onChange(settings.copy(gestureDoubleTap = it)) })
        SwitchRow("Long-press for 2x", settings.gestureLongPressSpeed, { onChange(settings.copy(gestureLongPressSpeed = it)) })
        SwitchRow("Pinch to zoom", settings.gesturePinchZoom, { onChange(settings.copy(gesturePinchZoom = it)) })

        SectionHeader("Subtitles")
        ControlRow("Size") {
            OmSegmented(
                listOf(0.8f to "Small", 1f to "Normal", 1.25f to "Large", 1.5f to "Huge"),
                settings.subtitleScale, { onChange(settings.copy(subtitleScale = it)) },
            )
        }
        SwitchRow("Background box", settings.subtitleBackground, { onChange(settings.copy(subtitleBackground = it)) }, subtitle = "Improves legibility on bright scenes")
    }
}
