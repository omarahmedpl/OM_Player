package com.omplayer

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val localImages = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

@Composable
actual fun LocalImage(path: String, modifier: Modifier) {
    val image by produceState(synchronized(localImages) { localImages.get(path) }, path) {
        if (value == null) value = withContext(Dispatchers.IO) {
            runCatching {
                // Streamed tracks carry an artwork address instead of a file.
                if (path.startsWith("http")) java.net.URL(path).openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                else BitmapFactory.decodeFile(path)?.asImageBitmap()
            }.getOrNull()
                ?.also { synchronized(localImages) { localImages.put(path, it) } }
        }
    }
    image?.let { Image(it, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop) }
}
