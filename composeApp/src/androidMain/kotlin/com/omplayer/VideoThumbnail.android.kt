package com.omplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.util.LruCache
import android.util.Size
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private object VideoThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    @Synchronized fun get(uri: String): Bitmap? = cache.get(uri)
    @Synchronized fun put(uri: String, bitmap: Bitmap) { cache.put(uri, bitmap) }
}

private object AudioArtworkCache {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val misses = object : LinkedHashMap<String, Unit>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > 256
    }

    @Synchronized fun get(uri: String): Bitmap? = cache.get(uri)
    @Synchronized fun wasMissing(uri: String): Boolean = uri in misses
    @Synchronized fun put(uri: String, bitmap: Bitmap) { misses.remove(uri); cache.put(uri, bitmap) }
    @Synchronized fun markMissing(uri: String) { misses[uri] = Unit }
}

private val audioArtworkLoads = Semaphore(2)

/** Shares the audio artwork cache and bounded embedded-art loader with the home-screen widget. */
internal suspend fun loadWidgetArtwork(context: Context, address: String, embeddedMedia: Boolean): Bitmap? =
    audioArtworkLoads.withPermit {
        withContext(Dispatchers.IO) {
            AudioArtworkCache.get(address) ?: if (AudioArtworkCache.wasMissing(address)) null else {
                val artwork = when {
                    address.startsWith("http://") || address.startsWith("https://") -> loadRemoteArtwork(address)
                    embeddedMedia || address.startsWith("content://") -> loadAudioArtwork(context, Uri.parse(address))
                    else -> loadLocalArtwork(address)
                }
                if (artwork != null) AudioArtworkCache.put(address, artwork) else AudioArtworkCache.markMissing(address)
                artwork
            }
        }
    }

private fun loadLocalArtwork(address: String): Bitmap? = runCatching {
    val path = if (address.startsWith("file://")) Uri.parse(address).path else address
    val file = path?.let(::File)?.takeIf(File::isFile) ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
    BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

private fun loadRemoteArtwork(address: String): Bitmap? {
    val connection = (URL(address).openConnection() as? HttpURLConnection) ?: return null
    return try {
        connection.connectTimeout = 3_500
        connection.readTimeout = 3_500
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "image/*")
        val length = connection.contentLengthLong
        if (length > 5L * 1024 * 1024) return null
        val bytes = connection.inputStream.use { input ->
            val out = ByteArrayOutputStream(length.takeIf { it > 0 }?.toInt() ?: 32 * 1024)
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > 5 * 1024 * 1024) return null
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

@Composable
actual fun VideoThumbnail(uri: String, modifier: Modifier) {
    val context = LocalContext.current
    val initial = VideoThumbnailCache.get(uri)
    val bitmap by produceState(initial, uri) {
        value = initial
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                VideoThumbnailCache.get(uri) ?: loadThumbnail(context, Uri.parse(uri))?.also {
                    VideoThumbnailCache.put(uri, it)
                }
            }
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop) }
}

@Composable
actual fun AudioArtwork(uri: String, modifier: Modifier) {
    val context = LocalContext.current
    val initial = AudioArtworkCache.get(uri)
    val bitmap by produceState(initial, uri) {
        value = initial
        if (value == null && !AudioArtworkCache.wasMissing(uri)) {
            value = audioArtworkLoads.withPermit {
                withContext(Dispatchers.IO) {
                    AudioArtworkCache.get(uri) ?: if (AudioArtworkCache.wasMissing(uri)) {
                        null
                    } else {
                        loadAudioArtwork(context, Uri.parse(uri)).also { artwork ->
                            if (artwork != null) AudioArtworkCache.put(uri, artwork)
                            else AudioArtworkCache.markMissing(uri)
                        }
                    }
                }
            }
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop) }
}

private fun loadThumbnail(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.loadThumbnail(uri, Size(480, 270), CancellationSignal())
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
                val scale = minOf(1f, 480f / frame.width, 270f / frame.height)
                if (scale >= 1f) frame else Bitmap.createScaledBitmap(
                    frame, (frame.width * scale).toInt().coerceAtLeast(1), (frame.height * scale).toInt().coerceAtLeast(1), true,
                ).also { if (it !== frame) frame.recycle() }
            } finally {
                retriever.release()
            }
        }
    } catch (_: Exception) {
        null
    }
}

private fun loadAudioArtwork(context: Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val bytes = retriever.embeddedPicture ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (_: Exception) {
        null
    } finally {
        retriever.release()
    }
}
