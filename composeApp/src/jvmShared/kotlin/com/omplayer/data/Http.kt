package com.omplayer.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Small HTTP helper for the lookups in this folder. Returns null on 404; other failures throw [IOException]. */
internal object Http {
    const val USER_AGENT = "OM Player/1.0 (https://github.com/omarahmedpl/OM_Player)"

    fun getBytes(url: String, maxBytes: Int = 4_000_000): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8_000; c.readTimeout = 12_000
            c.setRequestProperty("User-Agent", USER_AGENT)
            return when (val code = c.responseCode) {
                200 -> c.inputStream.use { s ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(16_384)
                    while (true) {
                        val n = s.read(buf); if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > maxBytes) throw IOException("response too large")
                    }
                    out.toByteArray()
                }
                404 -> null
                else -> throw IOException("HTTP $code")
            }
        } finally { c.disconnect() }
    }

    fun getText(url: String): String? = getBytes(url, 4_000_000)?.toString(Charsets.UTF_8)
}
