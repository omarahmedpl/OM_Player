package com.omplayer.player

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem as XItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Exposed for the Android VideoSurface. */
    val player: ExoPlayer = ExoPlayer.Builder(
        appContext,
        DefaultRenderersFactory(appContext)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER),
    ).build()

    private val session: MediaSession = MediaSession.Builder(appContext, player).setId("om_player").build()

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state

    private var current: MediaItem? = null
    private val extraSubs = mutableListOf<XItem.SubtitleConfiguration>()
    private var error: String? = null
    private var volume = 1f
    private var subtitleDelayMs = 0L
    private var subtitleScale = 1f
    private var subtitleBackground = true
    private var enhancer: LoudnessEnhancer? = null
    private var ticker: Job? = null
    private var released = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_AUDIO_SESSION_ID)) applyVolume()
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) || events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) updateTicker()
            sync()
        }

        override fun onPlayerError(e: PlaybackException) {
            error = friendly(e)
            sync()
        }
    }

    init { player.addListener(listener) }

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
        .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(item.title).build())
        .setSubtitleConfigurations(extraSubs.toList())
        .build()

    override fun open(item: MediaItem, startMs: Long) {
        current = item
        extraSubs.clear()
        error = null
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverrides().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        player.setMediaItem(build(item), startMs.coerceAtLeast(0))
        player.prepare()
        player.playWhenReady = true
        sync()
    }

    override fun play() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.playbackState == Player.STATE_IDLE && current != null) player.prepare()
        player.play()
    }

    override fun pause() = player.pause()
    override fun seekTo(positionMs: Long) { player.seekTo(positionMs.coerceAtLeast(0)); sync() }
    override fun setSpeed(speed: Float) { player.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 4f)) }

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

    /** TODO: the value is stored (and survives seeks/track changes) but not yet applied to cue timing.
     *  Needs a custom SubtitleParser.Factory/CuesResolver that shifts cue start times by this delay. */
    override fun setSubtitleDelay(delayMs: Long) { subtitleDelayMs = delayMs; sync() }
    override fun setSubtitleStyle(scale: Float, background: Boolean) { subtitleScale = scale; subtitleBackground = background; sync() }

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
        player.setMediaItem(build(cur), pos)
        player.prepare()
        player.playWhenReady = wasPlaying
    }

    override fun stop() {
        player.stop(); player.clearMediaItems()
        current = null; extraSubs.clear(); error = null
        _state.value = PlayerState(volume = volume)
    }

    override fun release() {
        if (released) return
        released = true
        ticker?.cancel()
        scope.cancel()
        releaseEnhancer()
        session.release()
        player.removeListener(listener)
        player.release()
    }
}
