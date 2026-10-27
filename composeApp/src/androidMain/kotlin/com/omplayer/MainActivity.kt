package com.omplayer

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import android.os.Bundle
import com.omplayer.data.MediaStoreRepository
import com.omplayer.data.PersistentProgressStore
import com.omplayer.model.MediaItem
import com.omplayer.player.Media3PlayerEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var engine: Media3PlayerEngine
    private lateinit var repository: MediaStoreRepository
    private lateinit var actions: AndroidPlatformActions

    /** ACTION_VIEW items; replay=1 so an item emitted before the UI starts collecting isn't lost. */
    private val openRequests = MutableSharedFlow<MediaItem>(replay = 1, extraBufferCapacity = 1)

    private var pickCallback: ((String?) -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        repository.onPermissionResult()
    }
    private val pickLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}
        pickCallback?.invoke(uri?.toString()); pickCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        engine = Media3PlayerEngine(this)
        repository = MediaStoreRepository(this).also { r ->
            r.permissionRequester = { permissionLauncher.launch(r.permissionsToRequest()) }
        }
        actions = AndroidPlatformActions(this, engine) { cb ->
            pickCallback = cb
            pickLauncher.launch(arrayOf("video/*", "audio/*"))
        }
        val deps = AppDeps(repository, PersistentProgressStore(this), engine, actions)

        // Keep PiP params current; on API 31+ the system enters PiP itself when playing.
        lifecycleScope.launch {
            var last: Triple<Boolean, Int, Int>? = null
            engine.state.collect { s ->
                val key = Triple(s.isPlaying, s.videoWidth, s.videoHeight)
                if (key != last) {
                    last = key
                    try { setPictureInPictureParams(actions.pipParams(s.isPlaying && s.item != null)) } catch (_: Exception) {}
                }
            }
        }
        handleIntent(intent)
        setContent { App(deps, openRequests) }
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
        if (Build.VERSION.SDK_INT < 31 && engine.state.value.let { it.isPlaying && it.item != null }) actions.enterPictureInPicture()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        actions.onPictureInPictureChanged(isInPictureInPictureMode)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) engine.release()
    }
}
