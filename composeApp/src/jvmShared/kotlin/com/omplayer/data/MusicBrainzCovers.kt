package com.omplayer.data

import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds cover art with MusicBrainz (to identify the release) and the Cover Art Archive (to fetch the front cover).
 * One MusicBrainz request per lookup, with an identifying User-Agent, as their usage rules ask.
 * Covers are third-party artwork: they are kept on the device for display only.
 */
class MusicBrainzCovers : CoverProvider {
    override suspend fun find(title: String, artist: String, album: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            fun clean(s: String) = s.replace("\"", " ").replace("\\", " ").trim()
            val who = if (artist.isNotBlank()) " AND artist:\"${clean(artist)}\"" else ""
            val (kind, query) = if (album.isNotBlank()) "release" to "release:\"${clean(album)}\"$who"
            else "recording" to "recording:\"${clean(title)}\"$who"
            val url = "https://musicbrainz.org/ws/2/$kind/?query=${URLEncoder.encode(query, "UTF-8")}&fmt=json&limit=5"
            val root = Http.getText(url)?.let { MiniJson.parse(it) } as? Map<*, *> ?: return@withContext null
            val releaseIds = if (kind == "release") (root["releases"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.get("id") as? String }
            else (root["recordings"] as? List<*>).orEmpty().flatMap { r ->
                ((r as? Map<*, *>)?.get("releases") as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.get("id") as? String }
            }
            for (id in releaseIds.distinct().take(5)) {
                val bytes = try { Http.getBytes("https://coverartarchive.org/release/$id/front-500") } catch (_: java.io.IOException) { null }
                if (bytes != null) return@withContext bytes
            }
            null
        } catch (_: Exception) { null }
    }
}
