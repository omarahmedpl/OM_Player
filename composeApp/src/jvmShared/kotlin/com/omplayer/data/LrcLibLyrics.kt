package com.omplayer.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Looks lyrics up on LRCLIB (lrclib.net), a free, open lyrics database. Sends only the title, artist, album and length. */
class LrcLibLyrics : LyricsProvider {
    override suspend fun find(title: String, artist: String, album: String, durationMs: Long): LyricsResult = withContext(Dispatchers.IO) {
        try {
            fun q(s: String) = URLEncoder.encode(s, "UTF-8")
            val exact = buildString {
                append("https://lrclib.net/api/get?track_name=").append(q(title))
                if (artist.isNotBlank()) append("&artist_name=").append(q(artist))
                if (album.isNotBlank()) append("&album_name=").append(q(album))
                if (durationMs > 0) append("&duration=").append(durationMs / 1000)
            }
            val hit = get(exact)?.let { parse(MiniJson.parse(it)) }
                ?: get("https://lrclib.net/api/search?q=" + q(listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")))
                    ?.let { (MiniJson.parse(it) as? List<*>)?.firstNotNullOfOrNull { e -> parse(e) } }
            if (hit == null) LyricsResult.NotFound else LyricsResult.Found(hit)
        } catch (e: java.io.IOException) {
            LyricsResult.Failed("The lyrics service isn't answering right now (${e.message}). Try again in a moment, or paste the lyrics yourself.")
        } catch (e: Exception) {
            LyricsResult.Failed("Couldn't read the lyrics response (${e::class.simpleName}: ${e.message}).")
        }
    }

    private fun parse(node: Any?): FoundLyrics? {
        val m = node as? Map<*, *> ?: return null
        val plain = (m["plainLyrics"] as? String)?.takeIf { it.isNotBlank() }
        val synced = (m["syncedLyrics"] as? String)?.takeIf { it.isNotBlank() }
        return if (plain == null && synced == null) null else FoundLyrics(plain, synced)
    }

    /** Body of a 200 response; null for 404 (not found). Other statuses throw. */
    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8_000; c.readTimeout = 10_000
            c.setRequestProperty("User-Agent", "OM Player (https://github.com/omarahmedpl/OM_Player)")
            return when (val code = c.responseCode) {
                200 -> c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                404 -> null
                else -> throw java.io.IOException("HTTP $code")
            }
        } finally { c.disconnect() }
    }
}
