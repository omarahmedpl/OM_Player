package com.omplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState

@Composable
actual fun FullscreenHost(active: Boolean, onExit: () -> Unit, content: @Composable () -> Unit) {
    val platform = LocalAppDeps.current.platform as? DesktopPlatformActions
    if (!active || platform == null || !platform.ownFullscreenWindow) {
        content()
        return
    }
    // A dedicated borderless window covering the app's screen. It never uses Java/system fullscreen, which is what
    // gets stuck on GNOME/XWayland (even a throwaway window put into it was closed again by a spurious toggle).
    // Exiting simply closes this window.
    val screen = remember { platform.screenBounds() }
    val state = rememberWindowState(
        placement = WindowPlacement.Floating,
        position = WindowPosition(screen.x.dp, screen.y.dp),
        size = DpSize(screen.width.dp, screen.height.dp),
    )
    var windowVisible by remember { mutableStateOf(false) }
    Window(
        visible = windowVisible,
        onCloseRequest = onExit,
        title = "OM Player",
        undecorated = true,
        resizable = false,
        state = state,
        // Same bubbling Esc fallback as the main window, for when no player control has focus.
        onKeyEvent = { e -> e.type == KeyEventType.KeyDown && e.key == Key.Escape && platform.exitFullscreenIfActive() },
    ) {
        LaunchedEffect(Unit) {
            // GNOME first places new windows below its top bar (+35 px, clipping the bottom controls). Once mapped, move
            // it to the monitor origin: a borderless window exactly covering the monitor is then shown above the top bar.
            delay(60)
            window.setBounds(screen.x, screen.y, screen.width, screen.height)
            window.toFront(); window.requestFocus()
        }
        // Only one window may render at a time: with both visible, Skiko's Linux OpenGL redrawer switches GL contexts
        // every frame and glXMakeCurrent blocked the UI thread for up to ~3 s (thread dumps), delaying clicks, hover and
        // exit. Hide the main window while fullscreen. DesktopPlatformActions restores it before switching this host
        // out; Compose owns this window's hide/dispose lifecycle, so don't touch native visibility during onDispose.
        DisposableEffect(window) {
            platform.fullscreenWindow = window
            platform.window?.isVisible = false
            windowVisible = true
            onDispose {
                if (platform.fullscreenWindow === window) platform.fullscreenWindow = null
            }
        }
        content()
    }
}
