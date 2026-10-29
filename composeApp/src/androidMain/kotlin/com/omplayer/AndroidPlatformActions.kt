package com.omplayer

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.omplayer.player.PlayerEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidPlatformActions(
    private val activity: Activity,
    private val engine: PlayerEngine,
    private val launchPicker: (onResult: (String?) -> Unit) -> Unit,
    private val launchSender: (IntentSender, (Boolean) -> Unit) -> Unit,
    private val requestNotifications: () -> Unit,
    private val launchTextPicker: (onResult: (String?) -> Unit) -> Unit,
) : PlatformActions {
    override fun pickTextFile(onResult: (String?) -> Unit) = launchTextPicker(onResult)

    private var askedNotifications = false

    override fun onAudioPlaybackStarted() {
        if (Build.VERSION.SDK_INT >= 33 && !askedNotifications &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedNotifications = true
            requestNotifications()
        }
    }

    override fun shareMedia(uris: List<String>, audioOnly: Boolean, videoOnly: Boolean): Boolean {
        val list = uris.map(Uri::parse)
        if (list.isEmpty()) return false
        val intent = if (list.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, list[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(list))
        intent.type = if (audioOnly) "audio/*" else if (videoOnly) "video/*" else "*/*"
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri("", list[0]).also { c -> list.drop(1).forEach { c.addItem(ClipData.Item(it)) } }
        return try { activity.startActivity(Intent.createChooser(intent, null)); true } catch (_: Exception) { false }
    }

    override fun sharePlaylists(lists: List<SharedPlaylist>, format: ShareFormat): Boolean {
        if (lists.isEmpty()) return false
        return try {
            val dir = java.io.File(activity.cacheDir, "shared").apply { deleteRecursively(); mkdirs() }
            val base = (if (lists.size == 1) lists[0].name else "playlists").replace(Regex("[^\\p{L}\\p{N}_ -]"), "").trim().ifEmpty { "playlists" }
            val (file, mime) = when (format) {
                ShareFormat.Json -> java.io.File(dir, "$base.json").also { it.writeText(com.omplayer.data.PlaylistExport.toJson(lists)) } to "application/json"
                ShareFormat.Image -> java.io.File(dir, "$base.png").also { f ->
                    f.outputStream().use { PlaylistImage.render(lists).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                } to "image/png"
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
            val intent = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newRawUri("", uri)
            activity.startActivity(Intent.createChooser(intent, null))
            true
        } catch (_: Exception) { false }
    }

    override fun deleteMedia(uris: List<String>, onResult: (deleted: Int) -> Unit) {
        val list = uris.map(Uri::parse)
        val resolver = activity.contentResolver
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                launchSender(MediaStore.createDeleteRequest(resolver, list).intentSender) { ok -> onResult(if (ok) list.size else 0) }
            } catch (_: Exception) { onResult(0) }
        } else {
            // API 26-29: only files this app may modify can be removed without a system prompt.
            var n = 0
            list.forEach { u -> try { n += resolver.delete(u, null, null) } catch (_: Exception) {} }
            onResult(n)
        }
    }

    override val formFactor = FormFactor.Phone

    private val pip = MutableStateFlow(false)
    override val inPictureInPicture: StateFlow<Boolean> = pip.asStateFlow()
    private val pipDismissed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val pictureInPictureDismissed: Flow<Unit> = pipDismissed.asSharedFlow()

    /** [dismissed] = PiP ended while the Activity is stopped (user closed the window), as opposed to expanding it. */
    fun onPictureInPictureChanged(active: Boolean, dismissed: Boolean = false) {
        pip.value = active
        if (!active && dismissed) pipDismissed.tryEmit(Unit)
    }

    private val controller get() = WindowCompat.getInsetsController(activity.window, activity.window.decorView)

    override fun setPlayerBrightness(value: Float?) {
        val lp = activity.window.attributes
        lp.screenBrightness = value?.coerceIn(0.01f, 1f) ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = lp
    }

    override fun setScreenOrientation(orientation: ScreenOrientation) {
        activity.requestedOrientation = when (orientation) {
            ScreenOrientation.Auto -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ScreenOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ScreenOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    override fun setLandscape(enabled: Boolean) {
        activity.requestedOrientation =
            if (enabled) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        setFullscreen(enabled)
    }

    override fun setFullscreen(enabled: Boolean) {
        val c = controller
        if (enabled) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun keepScreenOn(enabled: Boolean) {
        if (enabled) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun pipParams(autoEnter: Boolean): PictureInPictureParams {
        val s = engine.state.value
        val b = PictureInPictureParams.Builder()
        if (s.videoWidth > 0 && s.videoHeight > 0) {
            val r = (s.videoWidth.toFloat() / s.videoHeight).coerceIn(0.5f, 2.3f)
            b.setAspectRatio(Rational((r * 1000).toInt(), 1000))
        }
        if (Build.VERSION.SDK_INT >= 31) { b.setAutoEnterEnabled(autoEnter); b.setSeamlessResizeEnabled(true) }
        return b.build()
    }

    override fun enterPictureInPicture() {
        if (Build.VERSION.SDK_INT < 26 || !activity.packageManager.hasSystemFeature("android.software.picture_in_picture")) return
        try { activity.enterPictureInPictureMode(pipParams(false)) } catch (_: Exception) {}
    }

    override fun pickMediaFile(onResult: (String?) -> Unit) = launchPicker(onResult)
}
