package com.omplayer

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.omplayer.player.PlayerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidPlatformActions(
    private val activity: Activity,
    private val engine: PlayerEngine,
    private val launchPicker: (onResult: (String?) -> Unit) -> Unit,
) : PlatformActions {
    override val formFactor = FormFactor.Phone

    private val pip = MutableStateFlow(false)
    override val inPictureInPicture: StateFlow<Boolean> = pip.asStateFlow()
    fun onPictureInPictureChanged(active: Boolean) { pip.value = active }

    private val controller get() = WindowCompat.getInsetsController(activity.window, activity.window.decorView)

    override fun setPlayerBrightness(value: Float?) {
        val lp = activity.window.attributes
        lp.screenBrightness = value?.coerceIn(0.01f, 1f) ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = lp
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
