package com.omplayer

import android.content.Intent
import android.animation.ValueAnimator
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.animation.DecelerateInterpolator
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import android.os.Bundle
import com.omplayer.data.MediaStoreRepository
import com.omplayer.data.PersistentProgressStore
import com.omplayer.model.MediaItem
import com.omplayer.player.Media3PlayerEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/** Minimum time (since process start) the cold-start splash stays up, and how long it takes to fade out. */
private const val SPLASH_MIN_MS = 1500L
private const val SPLASH_FADE_MS = 450L

class MainActivity : ComponentActivity() {
    private lateinit var engine: Media3PlayerEngine
    private lateinit var repository: MediaStoreRepository
    private lateinit var actions: AndroidPlatformActions

    /** ACTION_VIEW items; replay=1 so an item emitted before the UI starts collecting isn't lost. */
    private val openRequests = MutableSharedFlow<MediaItem>(replay = 1, extraBufferCapacity = 1)

    private var pickCallback: ((String?) -> Unit)? = null
    private var senderCallback: ((Boolean) -> Unit)? = null
    private var textCallback: ((String?) -> Unit)? = null
    private var imageCallback: ((String?) -> Unit)? = null

    /** Copies the picked image, scaled to at most 640 px, into app storage and reports the file path. */
    private val imagePickLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) { imageCallback?.invoke(null); imageCallback = null; return@registerForActivityResult }
        lifecycleScope.launch {
            val path = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { saveCover(uri) }
            imageCallback?.invoke(path); imageCallback = null
        }
    }

    private fun saveCover(uri: Uri): String? = try {
        contentResolver.openInputStream(uri)?.use { it.readBytes() }?.takeIf { it.size <= 25_000_000 }?.let { CoverStore.save(filesDir, it) }
    } catch (_: Exception) { null }

    private val textPickLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val text = uri?.let {
            try {
                contentResolver.openInputStream(it)?.use { s -> s.readBytes().takeIf { b -> b.size <= 2_000_000 }?.toString(Charsets.UTF_8) }
            } catch (_: Exception) { null }
        }
        textCallback?.invoke(text); textCallback = null
    }

    private val senderLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        senderCallback?.invoke(r.resultCode == RESULT_OK); senderCallback = null
    }
    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        repository.onPermissionResult()
    }
    private val pickLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}
        pickCallback?.invoke(uri?.toString()); pickCallback = null
    }

    /** Debug-only startup marks, in ms since process start (tag OMStartup). */
    private fun mark(label: String) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.d("OMStartup", "$label=${SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis()}")
        }
    }

    private var prewarmScheduled = false

    /** Build the player on an idle main thread once startup visuals are done (never during the splash fade). */
    private fun schedulePrewarm() {
        if (prewarmScheduled) return
        prewarmScheduled = true
        android.os.Looper.myQueue().addIdleHandler {
            engine.prewarm()
            mark("prewarm")
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        mark("onCreate")
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 31) {
            // Cold start only: the splash view stays on screen until we remove it, so hold it until SPLASH_MIN_MS after
            // process start, then fade it out gently. Skipped when system animations are off (accessibility) and on warm starts.
            val holdUntil = if (savedInstanceState == null) android.os.Process.getStartUptimeMillis() + SPLASH_MIN_MS else 0L
            splashScreen.setOnExitAnimationListener { splash ->
                if (!ValueAnimator.areAnimatorsEnabled()) {
                    splash.remove()
                    schedulePrewarm()
                    return@setOnExitAnimationListener
                }
                // Fade only after Android signals that the first app frame is ready (and the hold has passed).
                val wait = (holdUntil - SystemClock.uptimeMillis()).coerceAtLeast(0L)
                val exitStart = SystemClock.uptimeMillis() + wait
                splash.postDelayed({
                  splash.animate().alpha(0f).setDuration(SPLASH_FADE_MS)
                    .setInterpolator(android.view.animation.PathInterpolator(0.23f, 1f, 0.32f, 1f))
                    .withEndAction {
                        splash.remove()
                        mark("splashRemoved")
                        schedulePrewarm()
                        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                            Log.d("OMSplash", "exitMs=${SystemClock.uptimeMillis() - exitStart}")
                        }
                    }.start()
                }, wait)
            }
        }
        mark("splashSetup")
        engine = Media3PlayerEngine.get(this)
        mark("engine")
        repository = MediaStoreRepository(this).also { r ->
            r.permissionRequester = { permissionLauncher.launch(r.permissionsToRequest()) }
        }
        actions = AndroidPlatformActions(
            this, engine,
            launchPicker = { cb ->
                pickCallback = cb
                pickLauncher.launch(arrayOf("video/*", "audio/*"))
            },
            launchSender = { sender, cb ->
                senderCallback = cb
                senderLauncher.launch(IntentSenderRequest.Builder(sender).build())
            },
            launchImagePicker = { cb ->
                imageCallback = cb
                imagePickLauncher.launch(arrayOf("image/*"))
            },
            launchTextPicker = { cb ->
                textCallback = cb
                textPickLauncher.launch(arrayOf("*/*"))
            },
            requestNotifications = {
                if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            },
        )
        val deps = AppDeps(repository, PersistentProgressStore(this), engine, actions, com.omplayer.data.UserLibrary(com.omplayer.data.PrefsTextStorage(this)), com.omplayer.data.LrcLibLyrics(), com.omplayer.data.MusicBrainzCovers(),
            if (BuildConfig.DISCOVER) com.omplayer.data.AudiusSource() else com.omplayer.data.NoOnlineMusic,
            com.omplayer.data.SessionStore(com.omplayer.data.PrefsTextStorage(this, "om_session")))
        mark("deps")

        // Keep PiP params current; on API 31+ the system enters PiP itself when playing.
        lifecycleScope.launch {
            var last: Triple<Boolean, Int, Int>? = null
            engine.state.collect { s ->
                val key = Triple(s.isPlaying, s.videoWidth, s.videoHeight)
                if (key != last) {
                    last = key
                    try { setPictureInPictureParams(actions.pipParams(s.isPlaying && s.item?.isAudio == false)) } catch (_: Exception) {}
                }
            }
        }
        handleIntent(intent)
        setContent { App(deps, openRequests) }
        mark("setContent")
        window.decorView.viewTreeObserver.addOnDrawListener(object : android.view.ViewTreeObserver.OnDrawListener {
            var done = false
            override fun onDraw() {
                if (done) return
                done = true
                mark("firstDraw")
                window.decorView.post {
                    window.decorView.viewTreeObserver.removeOnDrawListener(this)
                    // No animated splash exit before API 31 (or a warm start): prewarm once idle after the first frame.
                    if (Build.VERSION.SDK_INT < 31 || savedInstanceState != null) schedulePrewarm()
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        openRequests.tryEmit(MediaItem(
            id = "ext:$uri", uri = uri.toString(), title = displayName(uri), folderId = "external",
            isAudio = intent.type?.startsWith("audio") == true,
        ))
    }

    private fun displayName(uri: Uri): String {
        if (uri.scheme == "content") try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) return it.getString(0).substringBeforeLast('.')
            }
        } catch (_: Exception) {}
        return (uri.lastPathSegment ?: "Video").substringAfterLast('/').substringBeforeLast('.')
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // API 31+ auto-enters via PictureInPictureParams; this covers 26-30.
        if (Build.VERSION.SDK_INT < 31 && engine.state.value.let { it.isPlaying && it.item?.isAudio == false }) actions.enterPictureInPicture()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // Dismissing the PiP window stops the Activity before this callback; expanding it keeps it started.
        val dismissed = !isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (dismissed) engine.pause()
        actions.onPictureInPictureChanged(isInPictureInPictureMode, dismissed)
    }

    override fun onDestroy() {
        super.onDestroy()
        // The engine is process-wide so audio keeps playing through the service; only video (or idle) ends with the Activity.
        val s = engine.state.value
        if (!(s.isPlaying && s.item?.isAudio == true)) engine.release()
    }
}
