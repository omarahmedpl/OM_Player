package com.omplayer

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.omplayer.data.InMemoryProgressStore
import com.omplayer.data.SampleMediaRepository
import com.omplayer.player.FakePlayerEngine

/** Scaffold entry point. The desktop agent replaces the fakes with vlcj + a filesystem scanner. */
fun main() = application {
    val deps = AppDeps(
        repository = SampleMediaRepository(),
        progress = InMemoryProgressStore(),
        engine = FakePlayerEngine(),
        platform = object : PlatformActions { override val formFactor = FormFactor.Desktop },
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "OM Player",
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
    ) {
        App(deps)
    }
}
