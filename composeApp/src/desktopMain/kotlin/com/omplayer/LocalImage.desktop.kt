package com.omplayer

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun LocalImage(path: String, modifier: Modifier) {
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { runCatching { ImageIO.read(File(path))?.toComposeImageBitmap() }.getOrNull() }
    }
    image?.let { Image(it, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop) }
}
