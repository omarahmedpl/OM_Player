package com.omplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.omplayer.BuildConfig
import com.omplayer.MainActivity
import com.omplayer.R
import com.omplayer.model.MediaItem
import com.omplayer.player.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Actions carried from the home-screen widget into the app when its player is not alive. */
object WidgetIntents {
    const val EXTRA_ACTION = "com.omplayer.widget.action"
    const val OPEN_DISCOVER = "com.omplayer.widget.DISCOVER"
    const val TOGGLE = "com.omplayer.widget.TOGGLE"
    const val NEXT = "com.omplayer.widget.NEXT"
    const val PREVIOUS = "com.omplayer.widget.PREVIOUS"
}

/** Current process snapshot. Artwork decoding happens once per cover change on an IO thread. */
object OmWidgetState {
    @Volatile var title: String = ""
        private set
    @Volatile var artist: String = ""
        private set
    @Volatile var coverPath: String? = null
        private set
    @Volatile var cover: Bitmap? = null
        private set
    @Volatile var playing: Boolean = false
        private set
    @Volatile var hasItem: Boolean = false
        private set
    @Volatile var positionMs: Long = 0L
        private set
    @Volatile var durationMs: Long = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastRefreshKey: List<Any?>? = null

    fun update(context: Context, item: MediaItem?, isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        val embeddedMedia = item?.coverPath.isNullOrBlank()
        val nextPath = item?.let { it.coverPath.ifBlank { it.uri } }
        val metadataChanged: Boolean
        val artworkChanged: Boolean
        val shouldRefresh: Boolean
        synchronized(this) {
            metadataChanged = title != item?.title.orEmpty() || artist != item?.artist.orEmpty() || hasItem != (item != null)
            artworkChanged = coverPath != nextPath
            title = item?.title.orEmpty()
            artist = item?.artist.orEmpty()
            coverPath = nextPath
            playing = isPlaying
            hasItem = item != null
            this.positionMs = positionMs.coerceAtLeast(0L)
            this.durationMs = durationMs.coerceAtLeast(0L)
            val progressSecond = if (durationMs > 0) positionMs / 1000 else 0
            val key = listOf(title, artist, nextPath, isPlaying, durationMs > 0, progressSecond)
            shouldRefresh = key != lastRefreshKey
            lastRefreshKey = key
            if (artworkChanged) cover = null
        }
        if (artworkChanged && nextPath != null) {
            scope.launch {
                val decoded = com.omplayer.loadWidgetArtwork(context.applicationContext, nextPath, embeddedMedia)
                synchronized(this@OmWidgetState) {
                    if (coverPath != nextPath) return@synchronized
                    cover = decoded
                }
                refreshOmWidgets(context)
            }
        }
        if (shouldRefresh || metadataChanged) refreshOmWidgets(context)
    }

}

class OmWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        render(context, manager, appWidgetId)
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int) {
        val options = manager.getAppWidgetOptions(id)
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val layout = when {
            width >= 250 && height >= 110 -> R.layout.widget_wide
            width >= 250 -> R.layout.widget_compact
            else -> R.layout.widget_small
        }
        val views = RemoteViews(context.packageName, layout)
        val item = OmWidgetState.hasItem
        views.setTextViewText(R.id.title, if (item) OmWidgetState.title else context.getString(R.string.widget_idle_title))
        views.setTextViewText(R.id.artist, if (item) OmWidgetState.artist else context.getString(R.string.widget_idle_subtitle))
        views.setContentDescription(R.id.widget_root, if (item) context.getString(R.string.widget_open_track, OmWidgetState.title) else context.getString(R.string.widget_open))
        views.setViewVisibility(R.id.play, if (item) View.VISIBLE else View.GONE)
        views.setImageViewResource(R.id.play, if (OmWidgetState.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
        views.setContentDescription(R.id.play, context.getString(if (OmWidgetState.playing) R.string.widget_pause else R.string.widget_play))
        if (layout != R.layout.widget_small) {
            val artId = R.id.art
            OmWidgetState.cover?.let { views.setImageViewBitmap(artId, it) }
                ?: views.setImageViewResource(artId, R.drawable.ic_w_note)
            views.setViewVisibility(artId, View.VISIBLE)
            if (layout == R.layout.widget_wide) {
                OmWidgetState.cover?.let { views.setImageViewBitmap(R.id.cover, it) }
                views.setViewVisibility(R.id.cover, if (item && OmWidgetState.cover != null) View.VISIBLE else View.GONE)
                val pct = if (OmWidgetState.durationMs > 0L) {
                    (OmWidgetState.positionMs * 100 / OmWidgetState.durationMs).toInt().coerceIn(0, 100)
                } else 0
                views.setProgressBar(R.id.progress, 100, pct, false)
                val showDiscover = BuildConfig.DISCOVER
                views.setViewVisibility(R.id.discover, if (showDiscover) View.VISIBLE else View.GONE)
                if (showDiscover) views.setOnClickPendingIntent(R.id.discover, discover(context))
            }
        } else {
            if (item) OmWidgetState.cover?.let { views.setImageViewBitmap(R.id.cover, it) }
            val hasCover = item && OmWidgetState.cover != null
            views.setViewVisibility(R.id.cover, if (hasCover) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.empty_note, if (hasCover) View.GONE else View.VISIBLE)
        }
        views.setInt(R.id.play, "setBackgroundResource", if (OmWidgetState.playing) R.drawable.widget_play_bg else R.drawable.widget_play_bg_neutral)
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        if (item) {
            views.setOnClickPendingIntent(R.id.play, action(context, WidgetIntents.TOGGLE, id))
            if (layout == R.layout.widget_wide) {
                views.setContentDescription(R.id.prev, context.getString(R.string.widget_previous))
                views.setContentDescription(R.id.next, context.getString(R.string.widget_next))
                views.setOnClickPendingIntent(R.id.prev, action(context, WidgetIntents.PREVIOUS, id))
                views.setOnClickPendingIntent(R.id.next, action(context, WidgetIntents.NEXT, id))
            }
        }
        manager.updateAppWidget(id, views)
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun discover(context: Context) = PendingIntent.getActivity(
        context, 1, Intent(context, MainActivity::class.java).putExtra(WidgetIntents.EXTRA_ACTION, WidgetIntents.OPEN_DISCOVER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(context: Context, name: String, widgetId: Int) = PendingIntent.getService(
        context, name.hashCode() xor widgetId,
        Intent(context, PlaybackService::class.java).putExtra(WidgetIntents.EXTRA_ACTION, name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

fun refreshOmWidgets(context: Context) {
    val manager = AppWidgetManager.getInstance(context.applicationContext)
    val ids = manager.getAppWidgetIds(ComponentName(context, OmWidgetProvider::class.java))
    if (ids.isNotEmpty()) {
        context.sendBroadcast(Intent(context, OmWidgetProvider::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
    }
}
