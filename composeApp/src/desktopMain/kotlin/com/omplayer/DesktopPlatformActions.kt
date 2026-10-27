package com.omplayer

import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import java.awt.FileDialog
import java.awt.Frame

class DesktopPlatformActions(private val windowState: WindowState) : PlatformActions {
    override val formFactor = FormFactor.Desktop

    override fun setFullscreen(enabled: Boolean) {
        windowState.placement = if (enabled) WindowPlacement.Fullscreen else WindowPlacement.Floating
    }

    override fun pickMediaFile(onResult: (uri: String?) -> Unit) {
        val dialog = FileDialog(null as Frame?, "Open media", FileDialog.LOAD)
        dialog.isVisible = true
        val f = dialog.file
        onResult(if (f == null) null else java.io.File(dialog.directory, f).absolutePath)
    }
}
