package com.omplayer

import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.util.LinkedHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.omplayer.player.PlayerEngine
import com.omplayer.player.ScaleMode
import com.omplayer.player.VlcjPlayerEngine
import com.omplayer.player.VlcDiscovery
import uk.co.caprica.vlcj.media.Media
import uk.co.caprica.vlcj.media.MediaParsedStatus
import uk.co.caprica.vlcj.media.Meta
import uk.co.caprica.vlcj.media.ParseFlag
import uk.co.caprica.vlcj.media.MediaEventAdapter
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter

@Composable
actual fun VideoSurface(engine: PlayerEngine, scaleMode: ScaleMode, zoom: Float, modifier: Modifier, transformed: Boolean) {
    val sink = (engine as? VlcjPlayerEngine)?.frames
    val tick = sink?.tick?.collectAsState()?.value ?: 0
    Canvas(modifier.fillMaxSize()) {
        tick // read so the canvas redraws on every frame
        val img = sink?.image ?: return@Canvas
        val cw = size.width
        val ch = size.height
        val iw = img.width.toFloat()
        val ih = img.height.toFloat()
        if (cw <= 0f || ch <= 0f) return@Canvas
        val (dw, dh) = when (scaleMode) {
            ScaleMode.Fit -> minOf(cw / iw, ch / ih).let { iw * it to ih * it }
            ScaleMode.Crop -> maxOf(cw / iw, ch / ih).let { iw * it to ih * it }
            ScaleMode.Fill -> cw to ch
            ScaleMode.Original -> iw to ih
        }
        val z = zoom.coerceAtLeast(0.1f)
        val w = dw * z
        val h = dh * z
        val l = (cw - w) / 2f
        val t = (ch - h) / 2f
        clipRect {
            drawIntoCanvas {
                it.nativeCanvas.drawImageRect(
                    img, Rect.makeWH(iw, ih), Rect.makeXYWH(l, t, w, h),
                    FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE), null, true,
                )
            }
        }
    }
}

private object DesktopThumbnailCache {
    private val images = object : LinkedHashMap<String, ImageBitmap>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 48
    }
    private val misses = object : LinkedHashMap<String, Unit>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > 256
    }
    private val artworkImages = object : LinkedHashMap<String, ImageBitmap>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 48
    }
    private val missingArtwork = object : LinkedHashMap<String, Unit>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > 256
    }
    private val extractionLock = Mutex()
    private val extractor by lazy {
        if (!VlcDiscovery.discover()) null
        else MediaPlayerFactory("--no-video-title-show", "--quiet", "--no-osd", "--no-audio")
            .let { it to it.mediaPlayers().newEmbeddedMediaPlayer() }
    }

    @Synchronized fun get(uri: String) = images[uri]
    @Synchronized private fun wasMissing(uri: String) = uri in misses
    @Synchronized private fun save(uri: String, image: ImageBitmap?) {
        if (image == null) misses[uri] = Unit
        else { misses.remove(uri); images[uri] = image }
    }
    @Synchronized fun getArtwork(uri: String) = artworkImages[uri]
    @Synchronized private fun artworkWasMissing(uri: String) = uri in missingArtwork
    @Synchronized private fun saveArtwork(uri: String, image: ImageBitmap?) {
        if (image == null) missingArtwork[uri] = Unit
        else { missingArtwork.remove(uri); artworkImages[uri] = image }
    }

    suspend fun load(uri: String): ImageBitmap? = withContext(Dispatchers.IO) {
        get(uri) ?: if (wasMissing(uri)) null else extractionLock.withLock {
            get(uri) ?: snapshotFrame(uri).also { save(uri, it) }
        }
    }

    suspend fun loadArtwork(uri: String): ImageBitmap? = withContext(Dispatchers.IO) {
        getArtwork(uri) ?: if (artworkWasMissing(uri)) null else extractionLock.withLock {
            getArtwork(uri) ?: embeddedArtwork(uri).also { saveArtwork(uri, it) }
        }
    }

    /**
     * Decodes one frame headlessly through a vlcj callback surface (RV32 into memory), like the main player.
     * The previous version used `snapshots().get()`, which needs libVLC's own native video window; that window isn't
     * available on Wayland/XWayland, so every thumbnail silently came back null.
     */
    private fun snapshotFrame(uri: String): ImageBitmap? = runCatching {
        val (factory, player) = extractor ?: return null
        val done = CountDownLatch(1)
        var frame: BufferedImage? = null
        var seen = 0
        val format = object : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat = RV32BufferFormat(sourceWidth, sourceHeight)
            override fun allocatedBuffers(buffers: Array<out java.nio.ByteBuffer>) {}
        }
        val render = RenderCallback { _, buffers, fmt ->
            // Skip the first couple of frames, which decoders often emit black/grey after a seek.
            if (done.count == 0L || ++seen < 3) return@RenderCallback
            val w = fmt.width; val h = fmt.height
            val pixels = IntArray(w * h)
            buffers[0].duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(pixels, 0, minOf(pixels.size, buffers[0].capacity() / 4))
            frame = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).apply { setRGB(0, 0, w, h, pixels, 0, w) }
            done.countDown()
        }
        val ended = object : MediaPlayerEventAdapter() {
            override fun error(mp: MediaPlayer) { done.countDown() }
            override fun finished(mp: MediaPlayer) { done.countDown() }
        }
        player.videoSurface().set(factory.videoSurfaces().newVideoSurface(format, render, true))
        player.events().addMediaPlayerEventListener(ended)
        try {
            // A frame a little way in is more representative than the (often black) first one; short clips stay near 0.
            val lengthMs = factory.media().newMedia(toMrl(uri))?.let { m ->
                try { if (m.parsing().parse(800, ParseFlag.PARSE_LOCAL)) { Thread.sleep(150) }; m.info().duration() } finally { m.release() }
            } ?: 0L
            val startSec = if (lengthMs > 0) minOf(3.0, lengthMs / 10_000.0) else 0.0
            player.media().play(toMrl(uri), ":no-audio", ":start-time=$startSec")
            if (!done.await(4, TimeUnit.SECONDS)) return null
            val raw = frame ?: return null
            // libVLC pads decoded buffers (e.g. 720 → 736 lines); crop to the real picture before scaling.
            val dim = player.video().videoDimension()
            val src = if (dim != null && dim.width in 1..raw.width && dim.height in 1..raw.height &&
                (dim.width < raw.width || dim.height < raw.height)) raw.getSubimage(0, 0, dim.width, dim.height) else raw
            val tw = 320; val th = maxOf(1, src.height * tw / maxOf(1, src.width))
            val scaled = BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB)
            scaled.createGraphics().apply {
                setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                drawImage(src, 0, 0, tw, th, null); dispose()
            }
            scaled.toComposeImageBitmap()
        } finally {
            player.events().removeMediaPlayerEventListener(ended)
            runCatching { player.controls().stop() }
        }
    }.getOrNull()

    private fun embeddedArtwork(uri: String): ImageBitmap? = runCatching {
        val factory = extractor?.first ?: return null
        val media = factory.media().newMedia(toMrl(uri)) ?: return null
        val parsed = CountDownLatch(1)
        val listener = object : MediaEventAdapter() {
            override fun mediaParsedChanged(media: Media, newStatus: MediaParsedStatus) {
                parsed.countDown()
            }
        }
        media.events().addMediaEventListener(listener)
        try {
            if (!media.parsing().parse(1_500, ParseFlag.PARSE_LOCAL)) return null
            if (!parsed.await(2, TimeUnit.SECONDS) && media.parsing().status() != MediaParsedStatus.DONE) return null
            val artworkUrl = media.meta().get(Meta.ARTWORK_URL) ?: return null
            val artworkFile = localArtworkFile(artworkUrl) ?: return null
            decodeArtwork(artworkFile)
        } finally {
            media.events().removeMediaEventListener(listener)
            media.release()
        }
    }.getOrNull()

    private fun localArtworkFile(value: String): File? = runCatching {
        val parsed = runCatching { URI(value) }.getOrNull()
        when (parsed?.scheme?.lowercase()) {
            "file" -> File(parsed)
            null -> File(value).takeIf { it.isAbsolute }
            else -> null
        }?.takeIf { it.isFile && it.length() in 1..(8L * 1024 * 1024) }
    }.getOrNull()

    private fun decodeArtwork(file: File): ImageBitmap? = runCatching {
        val input = ImageIO.createImageInputStream(file) ?: return null
        input.use {
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) return null
            val reader = readers.next()
            try {
                reader.input = input
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                if (width < 1 || height < 1 || width > 20_000 || height > 20_000 || width.toLong() * height > 100_000_000L) return null
                val sample = maxOf(1, (maxOf(width, height) + 511) / 512)
                val params = reader.defaultReadParam
                params.setSourceSubsampling(sample, sample, 0, 0)
                val decoded: BufferedImage = reader.read(0, params) ?: return null
                ByteArrayOutputStream().use { bytes ->
                    if (!ImageIO.write(decoded, "png", bytes)) return null
                    SkiaImage.makeFromEncoded(bytes.toByteArray()).toComposeImageBitmap()
                }
            } finally {
                reader.dispose()
            }
        }
    }.getOrNull()

    // libVLC needs "file:///path"; File.toURI() gives "file:/path", which libVLC rejects (every thumbnail failed).
    private fun toMrl(uri: String): String =
        if ("://" in uri) uri else File(uri).toURI().toASCIIString().replaceFirst(Regex("^file:/(?!/)"), "file:///")
}

@Composable
actual fun VideoThumbnail(uri: String, modifier: Modifier) {
    val image by produceState(DesktopThumbnailCache.get(uri), uri) {
        if (value == null) value = DesktopThumbnailCache.load(uri)
    }
    image?.let { Image(it, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop) }
}

@Composable
actual fun AudioArtwork(uri: String, modifier: Modifier) {
    val image by produceState(DesktopThumbnailCache.getArtwork(uri), uri) {
        if (value == null) value = DesktopThumbnailCache.loadArtwork(uri)
    }
    image?.let { Image(it, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop) }
}
