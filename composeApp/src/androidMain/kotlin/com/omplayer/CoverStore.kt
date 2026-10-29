package com.omplayer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Keeps cover images small (at most 640 px, JPEG) in app storage. */
internal object CoverStore {
    fun save(dir: File, bytes: ByteArray): String? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 640) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        bmp?.let { b ->
            val scale = 640f / maxOf(b.width, b.height)
            val out = if (scale < 1f) Bitmap.createScaledBitmap(b, (b.width * scale).toInt(), (b.height * scale).toInt(), true) else b
            val covers = File(dir, "covers").apply { mkdirs() }
            val file = File(covers, "cover_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            file.absolutePath
        }
    } catch (_: Exception) { null }
}
