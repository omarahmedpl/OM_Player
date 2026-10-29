package com.omplayer.data

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a plain HTTP file to [target] with progress, writing `<target>.part` first and renaming on
 * success so a half-finished file never looks like a finished one.
 */
internal object FileDownload {
    fun get(url: String, target: File, onProgress: (Float) -> Unit = {}, isCancelled: () -> Boolean = { false }) {
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", Http.USER_AGENT)
            val code = c.responseCode
            if (code != 200) throw IOException("HTTP $code")
            val total = c.contentLengthLong
            var done = 0L
            c.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (isCancelled()) throw DownloadCancelled()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (!part.renameTo(target)) {
                target.delete()
                if (!part.renameTo(target)) throw IOException("Couldn't finish the file")
            }
            onProgress(1f)
        } finally {
            c.disconnect()
            if (part.exists() && !target.exists()) part.delete()
        }
    }
}
