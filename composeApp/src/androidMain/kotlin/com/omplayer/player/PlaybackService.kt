package com.omplayer.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.core.content.ContextCompat
import com.omplayer.data.LibraryState
import com.omplayer.data.MediaStoreRepository
import com.omplayer.data.PersistentProgressStore
import com.omplayer.data.PrefsTextStorage
import com.omplayer.data.SessionStore
import com.omplayer.data.UserLibrary
import com.omplayer.data.withEdit
import com.omplayer.model.MediaItem
import com.omplayer.widget.WidgetIntents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Publishes the engine's single MediaSession for background playback: notification, lock screen and Bluetooth buttons.
 * The player itself is owned by [Media3PlayerEngine] (one per process); this service never creates a second one.
 */
class PlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val widgetActionLock = Mutex()

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        Media3PlayerEngine.get(this).mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        intent?.getStringExtra(WidgetIntents.EXTRA_ACTION)?.let { action ->
            if (action in setOf(WidgetIntents.TOGGLE, WidgetIntents.NEXT, WidgetIntents.PREVIOUS)) {
                scope.launch {
                    widgetActionLock.withLock {
                        try {
                            handleWidgetAction(action)
                        } catch (e: Exception) {
                            android.util.Log.w("OMPlayback", "Widget playback action failed", e)
                        }
                        val state = Media3PlayerEngine.existing()?.state?.value
                        if (state?.item?.isAudio == true) {
                            com.omplayer.widget.OmWidgetState.update(
                                applicationContext, state.item, state.isPlaying, state.positionMs, state.durationMs,
                            )
                        }
                    }
                }
            }
        }
        return result
    }

    private suspend fun handleWidgetAction(action: String) {
        val engine = Media3PlayerEngine.get(this)
        if (engine.state.value.item?.isAudio != true && !restoreLocalSession(engine)) return
        when (action) {
            WidgetIntents.TOGGLE -> engine.togglePlay()
            WidgetIntents.NEXT -> engine.skipNext()
            WidgetIntents.PREVIOUS -> engine.skipPrevious()
        }
    }

    /** Restores only the already-authorized local audio library; never opens an Activity or permission prompt. */
    private suspend fun restoreLocalSession(engine: Media3PlayerEngine): Boolean {
        val saved = SessionStore(PrefsTextStorage(this, "om_session")).load() ?: return false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return false

        val repository = MediaStoreRepository(this)
        val ready = repository.state.first {
            it is LibraryState.Ready || it is LibraryState.NeedsPermission || it is LibraryState.Failed
        } as? LibraryState.Ready ?: return false
        val edits = UserLibrary(PrefsTextStorage(this)).edits.value
        val localAudio = ready.folders.flatMap { it.items }
            .filter { it.isAudio }
            .associate { item -> item.id to item.withEdit(edits[item.id]) }
        val queue = saved.ids.mapNotNull(localAudio::get)
        val currentId = saved.ids.getOrNull(saved.index) ?: return false
        val startIndex = queue.indexOfFirst { it.id == currentId }
        if (startIndex < 0) return false
        val position = saved.positionMs.takeIf { it > 0L }
            ?: PersistentProgressStore(this).positionMs(currentId)
            ?: 0L
        engine.openQueue(queue, startIndex, position, autoPlay = false)
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = Media3PlayerEngine.existing()?.player
        if (player == null || !player.playWhenReady) stopSelf()
    }
}
