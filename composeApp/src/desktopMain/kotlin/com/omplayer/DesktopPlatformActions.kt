package com.omplayer

import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import java.awt.FileDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.Frame

class DesktopPlatformActions(private val windowState: WindowState) : PlatformActions {
    override val formFactor = FormFactor.Desktop

    private val fullscreenFlow = MutableStateFlow(false)
    override val windowFullscreen: StateFlow<Boolean> = fullscreenFlow.asStateFlow()

    private var savedSize: androidx.compose.ui.unit.DpSize? = null
    private var savedPosition: androidx.compose.ui.window.WindowPosition? = null

    /** Set from main.kt once the window exists. */
    var window: java.awt.Window? = null

    /** The Linux fullscreen host is hidden before restoring [window], avoiding simultaneous Skia GL contexts. */
    var fullscreenWindow: java.awt.Window? = null

    /**
     * Linux: fullscreen is OM Player's own borderless, screen-sized window ([FullscreenHost]); the system fullscreen
     * state is never entered, because on GNOME/XWayland + OpenJDK it can't be left (AWT re-applies it; reproduced with
     * a plain JFrame). Windows/macOS keep native fullscreen.
     */
    val ownFullscreenWindow: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("linux")

    /** Bounds of the screen the main window is on (logical units, = dp). */
    fun screenBounds(): java.awt.Rectangle =
        window?.graphicsConfiguration?.bounds
            ?: java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds

    override fun setFullscreen(enabled: Boolean) {
        if (ownFullscreenWindow) {
            // Player cleanup also requests false when fullscreen was never entered. Showing the main window for that
            // no-op can resize its Compose scene after the window has already been disposed during application exit.
            if (fullscreenFlow.value == enabled) return
            // Swap native visibility before changing Compose state: showing both windows at once makes Skia switch GL
            // contexts on every frame on Linux, which can block the UI thread for seconds.
            if (!enabled) {
                fullscreenWindow?.isVisible = false
                window?.isVisible = true
            }
            fullscreenFlow.value = enabled
            lastSwitchMs = System.currentTimeMillis()
            return
        }
        val w = window
        if (enabled) {
            // Compose overwrites WindowState.size with the screen size while fullscreen; remember the windowed bounds.
            if (windowState.placement == WindowPlacement.Floating) {
                savedSize = windowState.size
                savedPosition = windowState.position
            }
            windowState.placement = WindowPlacement.Fullscreen
        } else {
            windowState.placement = WindowPlacement.Floating
            savedSize?.let { windowState.size = it }
            savedPosition?.let { windowState.position = it }
        }
        fullscreenFlow.value = enabled
        lastSwitchMs = System.currentTimeMillis()
        // Switching placement re-creates the native frame and can drop keyboard focus (seen on Linux/X11). Take it back.
        if (w != null) javax.swing.Timer(150) { w.toFront(); w.requestFocus() }.apply { isRepeats = false; start() }
    }

    /** Window-level Esc: leaves fullscreen even when no player control has keyboard focus. */
    private var lastSwitchMs = 0L

    fun exitFullscreenIfActive(): Boolean {
        if (!fullscreenFlow.value) return false
        // Same re-delivered-key guard as the player's toggle.
        if (System.currentTimeMillis() - lastSwitchMs < 500) return true
        setFullscreen(false)
        return true
    }

    override fun pickTextFile(onResult: (String?) -> Unit) {
        val dialog = FileDialog(null as Frame?, "Import playlist", FileDialog.LOAD)
        dialog.isVisible = true
        val f = dialog.file
        onResult(if (f == null) null else runCatching { java.io.File(dialog.directory, f).takeIf { it.length() <= 2_000_000 }?.readText() }.getOrNull())
    }

    override fun saveCoverImage(bytes: ByteArray): String? = runCatching {
        val src = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes)) ?: return@runCatching null
        val scale = minOf(1.0, 640.0 / maxOf(src.width, src.height))
        val w = maxOf(1, (src.width * scale).toInt()); val h = maxOf(1, (src.height * scale).toInt())
        val out = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply { setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR); drawImage(src, 0, 0, w, h, null); dispose() }
        val file = java.io.File(com.omplayer.data.AppDirs.config, "covers").apply { mkdirs() }.let { java.io.File(it, "cover_${System.currentTimeMillis()}.jpg") }
        javax.imageio.ImageIO.write(out, "jpg", file)
        file.absolutePath
    }.getOrNull()

    override fun pickImage(onResult: (String?) -> Unit) {
        val dialog = FileDialog(null as Frame?, "Choose a cover image", FileDialog.LOAD)
        dialog.isVisible = true
        val f = dialog.file
        onResult(
            if (f == null) null else runCatching {
                val src = javax.imageio.ImageIO.read(java.io.File(dialog.directory, f)) ?: return@runCatching null
                val scale = minOf(1.0, 640.0 / maxOf(src.width, src.height))
                val w = maxOf(1, (src.width * scale).toInt()); val h = maxOf(1, (src.height * scale).toInt())
                val out = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
                out.createGraphics().apply { setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR); drawImage(src, 0, 0, w, h, null); dispose() }
                val file = java.io.File(com.omplayer.data.AppDirs.config, "covers").apply { mkdirs() }.let { java.io.File(it, "cover_${System.currentTimeMillis()}.jpg") }
                javax.imageio.ImageIO.write(out, "jpg", file)
                file.absolutePath
            }.getOrNull(),
        )
    }

    override fun pickMediaFile(onResult: (uri: String?) -> Unit) {
        val dialog = FileDialog(null as Frame?, "Open media", FileDialog.LOAD)
        dialog.isVisible = true
        val f = dialog.file
        onResult(if (f == null) null else java.io.File(dialog.directory, f).absolutePath)
    }
}
