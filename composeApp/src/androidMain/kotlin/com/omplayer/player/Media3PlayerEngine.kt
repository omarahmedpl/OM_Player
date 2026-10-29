package com.omplayer.player

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import android.content.pm.ApplicationInfo
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem as XItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import android.os.Looper
import androidx.media3.session.MediaSession
import com.omplayer.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/** Media3 ExoPlayer implementation of [PlayerEngine]. All calls on the main thread. */
@OptIn(UnstableApi::class)
class Media3PlayerEngine(context: Context) : PlayerEngine {
    private val appContext = context.applicationContext

    companion object {
        @Volatile private var instance: Media3PlayerEngine? = null

        /** One engine per process: it outlives the Activity so background audio keeps playing. */
        fun get(context: Context): Media3PlayerEngine =
            instance ?: synchronized(this) { instance ?: Media3PlayerEngine(context).also { instance = it } }

        fun existing(): Media3PlayerEngine? = instance
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * ExoPlayer + MediaSession are built on first use (or by [prewarm] after the first frame) so app startup
     * doesn't wait ~150 ms for them on the main thread. Exposed for the Android VideoSurface.
     */
    private val playerLazy = lazy { buildPlayer() }
    val player: ExoPlayer by playerLazy
    private var session: MediaSession? = null

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state

    private var current: MediaItem? = null
    private var queueItems: List<MediaItem> = emptyList()
    private val prefs = appContext.getSharedPreferences("om_player_prefs", Context.MODE_PRIVATE)
    private var sleepEndsAt = 0L
    private var sleepAtEnd = false
    private var sleepJob: Job? = null
    private var delayJob: Job? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    /** The session the background service publishes (lock screen, notification, Bluetooth buttons). */
    val mediaSession: MediaSession? get() { player; return session }
    private val extraSubs = mutableListOf<XItem.SubtitleConfiguration>()
    private var error: String? = null
    private var volume = 1f
    private var subtitleDelayMs = 0L
    private var subtitleScale = 1f
    private var subtitleBackground = true
    private var enhancer: LoudnessEnhancer? = null
    private var ticker: Job? = null
    private var released = false
    private var openedAtMs = 0L

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_AUDIO_SESSION_ID)) applyVolume()
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) || events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) updateTicker()
            sync()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && sleepAtEnd) {
                sleepAtEnd = false
                player.pauseAtEndOfMediaItems = false
            }
        }

        override fun onPlayerError(e: PlaybackException) {
            error = friendly(e)
            sync()
        }
    }

    private fun buildPlayer(): ExoPlayer {
        val player = ExoPlayer.Builder(
            appContext,
            DelayedRenderers(appContext)
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER),
        ).setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(listener)
        if (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            player.addAnalyticsListener(object : AnalyticsListener {
                override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime, videoSize: VideoSize) {
                    Log.d("OMVideo", "decoded=${videoSize.width}x${videoSize.height} pixelRatio=${videoSize.pixelWidthHeightRatio} metadataRotation=${player.videoFormat?.rotationDegrees}")
                }

                override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
                    Log.d("OMVideo", "firstFrame elapsedSinceOpenMs=${SystemClock.elapsedRealtime() - openedAtMs}")
                }

                override fun onVideoDisabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
                    Log.d("OMVideo", "rendered=${decoderCounters.renderedOutputBufferCount} dropped=${decoderCounters.droppedBufferCount} skipped=${decoderCounters.skippedOutputBufferCount}")
                }
            })
        }
        val builder = MediaSession.Builder(appContext, player).setId("om_player")
        appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)?.let {
            builder.setSessionActivity(PendingIntent.getActivity(appContext, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        session = builder.build()
        return player
    }

    /** Build the player now (call after the first frame) so the first open doesn't pay for it. */
    fun prewarm() { if (!released) player }

    private fun friendly(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "File not found. It may have been moved or deleted."
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "OM Player doesn't have permission to read this file."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Couldn't reach the server. Check your connection."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "This file looks damaged and can't be read."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "This file format isn't supported."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "This device can't decode this video."
        else -> "Playback failed. (${e.errorCodeName})"
    }

    private fun sync() {
        if (released) return
        current = queueItems.getOrNull(player.currentMediaItemIndex) ?: current
        val tracks = player.currentTracks
        val audio = mutableListOf<Track>()
        val subs = mutableListOf<Track>()
        val textDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        tracks.groups.forEachIndexed { gi, g ->
            if (!g.isSupported) return@forEachIndexed
            val f = g.getTrackFormat(0)
            val lang = f.language?.takeIf { it != "und" }
            val langName = lang?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()).ifBlank { it } }
            when (g.type) {
                C.TRACK_TYPE_AUDIO -> {
                    val ch = when (f.channelCount) { 1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> null }
                    val label = listOfNotNull(f.label ?: langName, ch).joinToString(" · ").ifBlank { "Track ${audio.size + 1}" }
                    audio += Track("$gi", label, lang, g.isSelected)
                }
                C.TRACK_TYPE_TEXT -> {
                    val label = (f.label ?: langName ?: "Subtitle ${subs.size + 1}")
                    subs += Track("$gi", label, lang, g.isSelected && !textDisabled)
                }
            }
        }
        val dur = player.duration.takeIf { it != C.TIME_UNSET && it >= 0 } ?: current?.durationMs ?: 0L
        val vs = player.videoSize
        val vw = if (vs.height > 0) (vs.width * vs.pixelWidthHeightRatio).toInt() else 0
        val st = player.playbackState
        _state.value = PlayerState(
            item = current,
            isPlaying = player.playWhenReady && (st == Player.STATE_READY || st == Player.STATE_BUFFERING),
            isBuffering = st == Player.STATE_BUFFERING,
            ended = st == Player.STATE_ENDED,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = dur,
            bufferedMs = player.bufferedPosition.coerceAtLeast(0),
            speed = player.playbackParameters.speed,
            volume = volume,
            audioTracks = audio,
            subtitleTracks = subs,
            subtitleDelayMs = subtitleDelayMs,
            subtitleScale = subtitleScale,
            subtitleBackground = subtitleBackground,
            videoWidth = vw,
            videoHeight = vs.height,
            error = error,
            repeat = when (player.repeatMode) { Player.REPEAT_MODE_ALL -> RepeatMode.All; Player.REPEAT_MODE_ONE -> RepeatMode.One; else -> RepeatMode.Off },
            shuffle = player.shuffleModeEnabled,
            hasNext = player.hasNextMediaItem(),
            queue = queueItems,
            queueIndex = if (queueItems.isEmpty()) -1 else player.currentMediaItemIndex,
            sleepEndsAtMs = sleepEndsAt,
            sleepAtTrackEnd = sleepAtEnd,
        )
    }

    private fun updateTicker() {
        if (player.isPlaying) {
            if (ticker?.isActive == true) return
            ticker = scope.launch { while (isActive) { sync(); delay(250) } }
        } else {
            ticker?.cancel(); ticker = null
        }
    }

    private fun toUri(s: String): Uri = if (s.startsWith("/")) Uri.fromFile(File(s)) else Uri.parse(s)

    private fun subMime(uri: Uri): String {
        val name = (uri.lastPathSegment ?: "").lowercase()
        return when {
            name.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            name.endsWith(".ass") || name.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    private fun build(item: MediaItem): XItem = XItem.Builder()
        .setUri(toUri(item.uri))
        .setMediaId(item.id)
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder().setTitle(item.title)
                .apply { if (item.artist.isNotBlank()) setArtist(item.artist) }.build(),
        )
        .setSubtitleConfigurations(if (item.id == current?.id) extraSubs.toList() else emptyList())
        .build()

    override fun open(item: MediaItem, startMs: Long) = openQueue(listOf(item), 0, startMs)

    override fun openQueue(items: List<MediaItem>, startIndex: Int, startMs: Long) {
        val first = items.getOrNull(startIndex) ?: return
        openedAtMs = SystemClock.elapsedRealtime()
        queueItems = items
        current = first
        extraSubs.clear()
        error = null
        setSleepTimer(null)
        player.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(if (first.isAudio) C.AUDIO_CONTENT_TYPE_MUSIC else C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            true,
        )
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverrides().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        if (first.isAudio) {
            player.playbackParameters = PlaybackParameters(prefs.getFloat("audio_speed", 1f))
            player.repeatMode = prefs.getInt("audio_repeat", Player.REPEAT_MODE_OFF)
            player.shuffleModeEnabled = prefs.getBoolean("audio_shuffle", false)
            connectService()
        } else {
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.shuffleModeEnabled = false
        }
        player.setMediaItems(items.map(::build), startIndex, startMs.coerceAtLeast(0))
        player.prepare()
        player.playWhenReady = true
        sync()
    }

    override fun setRepeatMode(mode: RepeatMode) {
        val m = when (mode) { RepeatMode.All -> Player.REPEAT_MODE_ALL; RepeatMode.One -> Player.REPEAT_MODE_ONE; RepeatMode.Off -> Player.REPEAT_MODE_OFF }
        player.repeatMode = m
        if (current?.isAudio != false) prefs.edit().putInt("audio_repeat", m).apply()
        sync()
    }

    override fun setShuffle(enabled: Boolean) {
        player.shuffleModeEnabled = enabled
        if (current?.isAudio != false) prefs.edit().putBoolean("audio_shuffle", enabled).apply()
        sync()
    }

    override fun skipNext() { player.seekToNext(); sync() }
    override fun skipPrevious() { player.seekToPrevious(); sync() }
    override fun skipTo(index: Int) {
        if (index in queueItems.indices) { player.seekToDefaultPosition(index); player.play(); sync() }
    }

    override fun setSleepTimer(minutes: Int?, atTrackEnd: Boolean) {
        sleepJob?.cancel(); sleepJob = null
        sleepEndsAt = 0L; sleepAtEnd = false
        if (playerLazy.isInitialized()) player.pauseAtEndOfMediaItems = false
        if (atTrackEnd) {
            sleepAtEnd = true
            player.pauseAtEndOfMediaItems = true
        } else if (minutes != null && minutes > 0) {
            sleepEndsAt = System.currentTimeMillis() + minutes * 60_000L
            sleepJob = scope.launch {
                delay(minutes * 60_000L)
                player.pause()
                sleepEndsAt = 0L
                sync()
            }
        }
        if (playerLazy.isInitialized()) sync()
    }

    /** Binding a controller starts [PlaybackService], which owns the notification and foreground state. */
    private fun connectService() {
        if (controllerFuture != null) return
        controllerFuture = MediaController.Builder(
            appContext, SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
        ).buildAsync()
    }

    override fun play() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.playbackState == Player.STATE_IDLE && current != null) player.prepare()
        player.play()
    }

    override fun pause() = player.pause()
    override fun seekTo(positionMs: Long) { player.seekTo(positionMs.coerceAtLeast(0)); sync() }
    override fun setSpeed(speed: Float) {
        val v = speed.coerceIn(0.25f, 4f)
        player.playbackParameters = PlaybackParameters(v)
        if (current?.isAudio == true) prefs.edit().putFloat("audio_speed", v).apply()
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 2f)
        applyVolume()
        sync()
    }

    private fun applyVolume() {
        player.volume = minOf(volume, 1f)
        if (volume > 1f) {
            val id = player.audioSessionId
            if (id != C.AUDIO_SESSION_ID_UNSET) {
                try {
                    val e = enhancer ?: LoudnessEnhancer(id).also { enhancer = it }
                    e.setTargetGain(((volume - 1f) * 1200).toInt()) // 0..2 -> up to +12 dB
                    e.enabled = true
                } catch (_: Exception) { releaseEnhancer() }
            }
        } else enhancer?.let { try { it.enabled = false } catch (_: Exception) {} }
    }

    private fun releaseEnhancer() {
        try { enhancer?.release() } catch (_: Exception) {}
        enhancer = null
    }

    private fun select(type: Int, id: String?) {
        val params = player.trackSelectionParameters.buildUpon()
        if (id == null) {
            params.setTrackTypeDisabled(type, true)
        } else {
            val g = player.currentTracks.groups.getOrNull(id.toIntOrNull() ?: return) ?: return
            if (g.type != type) return
            val supported = (0 until g.length).filter { g.isTrackSupported(it) }
            params.setTrackTypeDisabled(type, false)
                .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, supported.ifEmpty { listOf(0) }))
        }
        player.trackSelectionParameters = params.build()
    }

    override fun selectAudioTrack(id: String) = select(C.TRACK_TYPE_AUDIO, id)
    override fun selectSubtitleTrack(id: String?) = select(C.TRACK_TYPE_TEXT, id)

    /**
     * Applied live by [DelayedTextRenderer], so it holds across seeks. Renderers drop cues they've already passed, so a
     * same-position seek (debounced) makes them re-read at the new offset.
     */
    override fun setSubtitleDelay(delayMs: Long) {
        subtitleDelayMs = delayMs
        sync()
        delayJob?.cancel()
        delayJob = scope.launch {
            delay(350)
            if (queueItems.isNotEmpty()) player.seekTo(player.currentPosition)
        }
    }

    /** Shifts the subtitle renderer's clock by [subtitleDelayMs], so cues move whatever way they were parsed. */
    private inner class DelayedRenderers(context: Context) : DefaultRenderersFactory(context) {
        override fun buildTextRenderers(
            context: Context, output: TextOutput, outputLooper: Looper, extensionRendererMode: Int, out: ArrayList<Renderer>,
        ) {
            val start = out.size
            super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, out)
            for (i in start until out.size) if (out[i] is TextRenderer) out[i] = DelayedTextRenderer(out[i])
        }
    }

    private inner class DelayedTextRenderer(renderer: Renderer) : ForwardingRenderer(renderer) {
        override fun render(positionUs: Long, elapsedRealtimeUs: Long) = super.render(positionUs - subtitleDelayMs * 1000, elapsedRealtimeUs)
    }

    override fun addSubtitle(uri: String) {
        val cur = current ?: return
        val u = toUri(uri)
        extraSubs += XItem.SubtitleConfiguration.Builder(u)
            .setMimeType(subMime(u))
            .setLanguage("und")
            .setLabel(u.lastPathSegment?.substringAfterLast('/') ?: "External")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val pos = player.currentPosition
        val wasPlaying = player.playWhenReady
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        player.setMediaItems(queueItems.map(::build), player.currentMediaItemIndex, pos)
        player.prepare()
        player.playWhenReady = wasPlaying
    }

    override fun stop() {
        sleepJob?.cancel(); sleepJob = null; sleepEndsAt = 0L; sleepAtEnd = false
        if (playerLazy.isInitialized()) { player.pauseAtEndOfMediaItems = false; player.stop(); player.clearMediaItems() }
        current = null; queueItems = emptyList(); extraSubs.clear(); error = null
        _state.value = PlayerState(volume = volume)
    }

    override fun release() {
        if (released) return
        released = true
        if (instance === this) instance = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        ticker?.cancel()
        scope.cancel()
        releaseEnhancer()
        if (!playerLazy.isInitialized()) return
        session?.release()
        player.removeListener(listener)
        player.release()
    }
}
