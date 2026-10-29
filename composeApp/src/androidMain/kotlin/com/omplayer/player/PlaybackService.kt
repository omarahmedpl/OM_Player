package com.omplayer.player

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Publishes the engine's single MediaSession for background playback: notification, lock screen and Bluetooth buttons.
 * The player itself is owned by [Media3PlayerEngine] (one per process); this service never creates a second one.
 */
class PlaybackService : MediaSessionService() {
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        Media3PlayerEngine.existing()?.mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = Media3PlayerEngine.existing()?.player
        if (player == null || !player.playWhenReady) stopSelf()
    }
}
