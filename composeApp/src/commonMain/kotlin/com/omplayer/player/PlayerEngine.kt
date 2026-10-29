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

enum class RepeatMode { Off, All, One }

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
    /** Subtitle text size multiplier (1f = platform default). */
    val subtitleScale: Float = 1f,
    /** Draw a translucent box behind subtitle text. */
    val subtitleBackground: Boolean = true,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val error: String? = null,
    /** Playlist loaded with [PlayerEngine.openQueue]; [item] is `queue[queueIndex]`. Empty for single-item playback. */
    val queue: List<MediaItem> = emptyList(),
    val queueIndex: Int = -1,
    /** Sleep timer: wall-clock end (0 = none) or pause at the end of the current track. */
    val sleepEndsAtMs: Long = 0,
    val sleepAtTrackEnd: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val shuffle: Boolean = false,
    /** True when [PlayerEngine.skipNext] has somewhere to go (respects repeat and shuffle). */
    val hasNext: Boolean = false,
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
    /** Subtitle size multiplier and background box. Implementations publish them in [state]. */
    fun setSubtitleStyle(scale: Float, background: Boolean) {}
    /** Load an external subtitle file (path or URI) for the current item. */
    fun addSubtitle(uri: String) {}
    /**
     * Play [items] starting at [startIndex]. The engine advances through them (also in the background).
     * With [autoPlay] false the item is loaded and left paused, waiting for the user.
     */
    fun openQueue(items: List<MediaItem>, startIndex: Int, startMs: Long = 0, autoPlay: Boolean = true) {
        items.getOrNull(startIndex)?.let { open(it, startMs) }
    }
    fun setRepeatMode(mode: RepeatMode) {}
    fun setShuffle(enabled: Boolean) {}
    /** The shown metadata of a queued item changed (title, artist, cover). Engines refresh what they display. */
    fun updateItem(item: MediaItem) {}
    fun skipNext() {}
    fun skipPrevious() {}
    fun skipTo(index: Int) {}
    /** Pause after [minutes], or at the end of the current track. Null/0 cancels. Engines without support ignore it. */
    fun setSleepTimer(minutes: Int?, atTrackEnd: Boolean = false) {}
    fun stop()
    fun release()
}
