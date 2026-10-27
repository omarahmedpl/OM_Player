package com.omplayer.player

import com.omplayer.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A clock-driven engine with no real decoding. Used for previews, tests, and before a platform engine exists. */
class FakePlayerEngine : PlayerEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state
    private var ticker: Job? = null

    override fun open(item: MediaItem, startMs: Long) {
        _state.value = PlayerState(
            item = item,
            durationMs = item.durationMs.takeIf { it > 0 } ?: 90 * 60_000L,
            positionMs = startMs,
            videoWidth = item.width.takeIf { it > 0 } ?: 1920,
            videoHeight = item.height.takeIf { it > 0 } ?: 1080,
            audioTracks = listOf(Track("a0", "English · Stereo", "en", true), Track("a1", "Español · 5.1", "es")),
            subtitleTracks = listOf(Track("s0", "English", "en", true), Track("s1", "Español", "es")),
        )
        play()
    }

    override fun play() {
        _state.update { it.copy(isPlaying = true, ended = false) }
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(250)
                _state.update { s ->
                    val next = s.positionMs + (250 * s.speed).toLong()
                    if (next >= s.durationMs) s.copy(positionMs = s.durationMs, isPlaying = false, ended = true)
                    else s.copy(positionMs = next, bufferedMs = minOf(s.durationMs, next + 30_000))
                }
                if (_state.value.ended) break
            }
        }
    }

    override fun pause() { ticker?.cancel(); _state.update { it.copy(isPlaying = false) } }
    override fun seekTo(positionMs: Long) = _state.update { it.copy(positionMs = positionMs.coerceIn(0, it.durationMs), ended = false) }
    override fun setSpeed(speed: Float) = _state.update { it.copy(speed = speed.coerceIn(0.25f, 4f)) }
    override fun setVolume(volume: Float) = _state.update { it.copy(volume = volume.coerceIn(0f, 2f)) }
    override fun selectAudioTrack(id: String) = _state.update { s -> s.copy(audioTracks = s.audioTracks.map { it.copy(selected = it.id == id) }) }
    override fun selectSubtitleTrack(id: String?) = _state.update { s -> s.copy(subtitleTracks = s.subtitleTracks.map { it.copy(selected = it.id == id) }) }
    override fun setSubtitleDelay(delayMs: Long) = _state.update { it.copy(subtitleDelayMs = delayMs) }
    override fun setSubtitleStyle(scale: Float, background: Boolean) = _state.update { it.copy(subtitleScale = scale, subtitleBackground = background) }
    override fun stop() { pause(); _state.value = PlayerState() }
    override fun release() { scope.cancel() }
}
