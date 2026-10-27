package com.omplayer.player

import com.omplayer.model.MediaItem
import kotlinx.coroutines.flow.StateFlow

data class Track(
    val id: String,
    val label: String,
    val language: String? = null,
    val selected: Boolean = false,
)

enum class ScaleMode { Fit, Fill, Crop, Original }

enum class Decoder { HW, SW }

data class PlayerState(
    val item: MediaItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val ended: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val speed: Float = 1f,
    /** 0f..2f. Above 1f is software volume boost. */
    val volume: Float = 1f,
    val audioTracks: List<Track> = emptyList(),
    val subtitleTracks: List<Track> = emptyList(),
    val subtitleDelayMs: Long = 0,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val error: String? = null,
)

/**
 * The playback engine behind the shared UI.
 * Android: Media3 ExoPlayer (+ FFmpeg audio extension later). Desktop: vlcj / libVLC.
 * All calls are made from the UI thread; implementations publish state through [state].
 */
interface PlayerEngine {
    val state: StateFlow<PlayerState>

    fun open(item: MediaItem, startMs: Long = 0)
    fun play()
    fun pause()
    fun togglePlay() = if (state.value.isPlaying) pause() else play()
    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long) = seekTo((state.value.positionMs + deltaMs).coerceIn(0, maxOf(0, state.value.durationMs)))
    fun setSpeed(speed: Float)
    fun setVolume(volume: Float)
    fun selectAudioTrack(id: String)
    /** null turns subtitles off. */
    fun selectSubtitleTrack(id: String?)
    /** Positive delays subtitles. Survives seeks (an MX Player pain point). */
    fun setSubtitleDelay(delayMs: Long)
    /** Load an external subtitle file (path or URI) for the current item. */
    fun addSubtitle(uri: String) {}
    fun stop()
    fun release()
}
