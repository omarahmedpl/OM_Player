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

@Composable
actual fun VideoThumbnail(uri: String, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState(VideoThumbnailCache.get(uri), uri) {
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
    val bitmap by produceState(AudioArtworkCache.get(uri), uri) {
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
