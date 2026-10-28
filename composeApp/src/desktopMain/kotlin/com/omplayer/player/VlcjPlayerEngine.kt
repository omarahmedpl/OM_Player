package com.omplayer.player

import com.omplayer.model.MediaItem
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat

/** Receives RV32 frames from libVLC (native thread) and exposes the latest as a Skia Image to the UI. */
class FrameSink {
    private val lock = Any()
    private var buf = ByteArray(0)
    private var w = 0
    private var h = 0
    @Volatile var image: Image? = null
        private set
    @Volatile private var lastPublishNanos = 0L
    private val minPublishIntervalNanos = 16_000_000L
    /** Bumped for every frame; collect to redraw. */
    val tick = MutableStateFlow(0)

    internal fun configure(width: Int, height: Int) = synchronized(lock) {
        w = width; h = height
        val need = width * height * 4
        if (buf.size != need) buf = ByteArray(need)
    }

    internal fun push(src: ByteBuffer) {
        // The Compose surface cannot present faster than a 60 Hz display. Skip excess
        // decoder callbacks before copying and wrapping a full-resolution frame.
        val now = System.nanoTime()
        if (now - lastPublishNanos < minPublishIntervalNanos) return
        val img = synchronized(lock) {
            if (w == 0 || buf.isEmpty()) return
            src.rewind()
            src.get(buf, 0, minOf(buf.size, src.remaining()))
            Image.makeRaster(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), buf, w * 4)
        }
        lastPublishNanos = now
        image = img
        tick.update { it + 1 }
    }

    internal fun clear() { image = null; tick.update { it + 1 } }
}

class VlcjPlayerEngine(
    private val decoderProvider: () -> Decoder = { Decoder.HW },
) : PlayerEngine {
    private val factory: MediaPlayerFactory
    private val player: EmbeddedMediaPlayer
    /** Single thread for every call into libVLC; event callbacks only post work here. */
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "vlc-control").apply { isDaemon = true } }
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state
    val frames = FrameSink()
    @Volatile private var lastTimeEmit = 0L
    @Volatile private var released = false

    init {
        check(VlcDiscovery.discover()) { "libVLC not found" }
        factory = MediaPlayerFactory("--no-video-title-show", "--quiet", "--no-osd")
        player = factory.mediaPlayers().newEmbeddedMediaPlayer()
        attachSurface()
        player.events().addMediaPlayerEventListener(listener())
    }

    /** Hooks libVLC's frame callbacks to [frames] (RV32, source size). */
    private fun attachSurface() {
        val fmt = object : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                frames.configure(sourceWidth, sourceHeight)
                _state.update { it.copy(videoWidth = sourceWidth, videoHeight = sourceHeight) }
                return RV32BufferFormat(sourceWidth, sourceHeight)
            }
            override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {}
        }
        val render = RenderCallback { _, nativeBuffers, _ -> frames.push(nativeBuffers[0]) }
        player.videoSurface().set(factory.videoSurfaces().newVideoSurface(fmt, render, true))
    }

    private fun post(block: () -> Unit) {
        if (released) return
        try { exec.execute { if (!released) runCatching(block) } } catch (_: Exception) {}
    }

    private fun listener() = object : MediaPlayerEventAdapter() {
        override fun playing(mp: MediaPlayer) {
            _state.update { it.copy(isPlaying = true, isBuffering = false, ended = false, error = null) }
            post { reapply(); refreshTracks() }
        }
        override fun paused(mp: MediaPlayer) { _state.update { it.copy(isPlaying = false) } }
        override fun stopped(mp: MediaPlayer) { _state.update { it.copy(isPlaying = false) } }
        override fun finished(mp: MediaPlayer) {
            _state.update { it.copy(isPlaying = false, ended = true, positionMs = it.durationMs) }
        }
        override fun error(mp: MediaPlayer) {
            _state.update { it.copy(isPlaying = false, isBuffering = false, error = "Playback failed") }
        }
        override fun buffering(mp: MediaPlayer, newCache: Float) {
            _state.update { it.copy(isBuffering = newCache < 100f) }
        }
        override fun lengthChanged(mp: MediaPlayer, newLength: Long) {
            _state.update { it.copy(durationMs = newLength) }
        }
        override fun timeChanged(mp: MediaPlayer, newTime: Long) {
            val now = System.nanoTime()
            if (now - lastTimeEmit < 250_000_000L) return
            lastTimeEmit = now
            _state.update { it.copy(positionMs = newTime) }
        }
        override fun videoOutput(mp: MediaPlayer, newCount: Int) { post { refreshTracks() } }
        override fun mediaPlayerReady(mp: MediaPlayer) { post { refreshTracks() } }
    }

    /** Runs on [exec]. */
    private fun reapply() {
        val s = _state.value
        player.controls().setRate(s.speed)
        player.audio().setVolume((s.volume * 100).toInt().coerceIn(0, 200))
        if (s.subtitleDelayMs != 0L) player.subpictures().setDelay(s.subtitleDelayMs * 1000)
    }

    /** Runs on [exec]. */
    private fun refreshTracks() {
        val aSel = player.audio().track()
        val sSel = player.subpictures().track()
        val audio = player.audio().trackDescriptions().filter { it.id() >= 0 }
            .map { Track(it.id().toString(), it.description() ?: "Track ${it.id()}", null, it.id() == aSel) }
        val subs = player.subpictures().trackDescriptions().filter { it.id() >= 0 }
            .map { Track(it.id().toString(), it.description() ?: "Subtitle ${it.id()}", null, it.id() == sSel) }
        _state.update { it.copy(audioTracks = audio, subtitleTracks = subs) }
    }

    private fun mrl(uri: String): String =
        if ("://" in uri) uri else File(uri).toURI().toASCIIString().replaceFirst(Regex("^file:/(?!/)"), "file:///")

    override fun open(item: MediaItem, startMs: Long) {
        _state.update {
            PlayerState(
                item = item, positionMs = startMs, durationMs = item.durationMs, isBuffering = true,
                speed = it.speed, volume = it.volume, subtitleDelayMs = it.subtitleDelayMs,
                subtitleScale = it.subtitleScale, subtitleBackground = it.subtitleBackground,
                videoWidth = item.width, videoHeight = item.height,
            )
        }
        val hw = if (decoderProvider() == Decoder.SW) ":avcodec-hw=none" else ":avcodec-hw=any"
        val opts = mutableListOf(hw)
        if (startMs > 0) opts += ":start-time=${startMs / 1000.0}"
        // libVLC reads subtitle size per input; the background box is a global freetype setting (not applied yet).
        opts += ":sub-text-scale=${(_state.value.subtitleScale * 100).toInt()}"
        post { player.media().play(mrl(item.uri), *opts.toTypedArray()) }
    }

    override fun play() {
        val s = _state.value
        if (s.ended && s.item != null) open(s.item, 0) else post { player.controls().play() }
    }

    override fun pause() = post { player.controls().setPause(true) }

    override fun seekTo(positionMs: Long) {
        val p = positionMs.coerceAtLeast(0)
        _state.update { it.copy(positionMs = p, ended = false) }
        post { player.controls().setTime(p) }
    }

    override fun setSpeed(speed: Float) {
        _state.update { it.copy(speed = speed) }
        post { player.controls().setRate(speed) }
    }

    override fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 2f)
        _state.update { it.copy(volume = v) }
        post { player.audio().setVolume((v * 100).toInt()) }
    }

    override fun selectAudioTrack(id: String) {
        val n = id.toIntOrNull() ?: return
        post { player.audio().setTrack(n); refreshTracks() }
    }

    override fun selectSubtitleTrack(id: String?) {
        val n = id?.toIntOrNull() ?: -1
        post { player.subpictures().setTrack(n); refreshTracks() }
    }

    /** Size applies from the next open (libVLC reads it per input). */
    override fun setSubtitleStyle(scale: Float, background: Boolean) {
        _state.update { it.copy(subtitleScale = scale, subtitleBackground = background) }
    }

    override fun setSubtitleDelay(delayMs: Long) {
        _state.update { it.copy(subtitleDelayMs = delayMs) }
        post { player.subpictures().setDelay(delayMs * 1000) }
    }

    override fun addSubtitle(uri: String) {
        val path = if (uri.startsWith("file:")) runCatching { File(java.net.URI(uri)).path }.getOrDefault(uri) else uri
        post {
            player.subpictures().setSubTitleFile(path)
            refreshTracks()
        }
    }

    override fun stop() {
        post { player.controls().stop() }
        _state.value = _state.value.copy(isPlaying = false, positionMs = 0)
        frames.clear()
    }

    override fun release() {
        if (released) return
        try {
            exec.execute {
                runCatching { player.controls().stop() }
                released = true
                runCatching { player.release() }
                runCatching { factory.release() }
            }
            exec.shutdown()
        } catch (_: Exception) {}
    }
}
