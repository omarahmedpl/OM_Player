package com.omplayer.data

import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Audius (audius.co): independent artists sharing music openly. Uses its public API with an app name and no account.
 * Only tracks Audius marks as streamable and not gated behind payment or ownership are listed.
 */
class AudiusSource : OnlineMusicSource {
    override val name = "Audius"
    override val attribution = "Powered by Audius. Tracks are shared by independent artists on audius.co."

    private val base = "https://api.audius.co/v1"
    private val app = "OMPlayer"

    override suspend fun trending() = load("$base/tracks/trending?limit=30&app_name=$app")

    override suspend fun search(query: String) =
        load("$base/tracks/search?query=${URLEncoder.encode(query.trim(), "UTF-8")}&limit=30&app_name=$app")

    private suspend fun load(url: String): OnlineResult = withContext(Dispatchers.IO) {
        try {
            val root = Http.getText(url)?.let { MiniJson.parse(it) } as? Map<*, *>
            val data = root?.get("data") as? List<*> ?: return@withContext OnlineResult.Tracks(emptyList())
            OnlineResult.Tracks(data.mapNotNull { toTrack(it) })
        } catch (e: java.io.IOException) {
            OnlineResult.Failed("Couldn't reach Audius (${e.message}). Try again in a moment.")
        } catch (e: Exception) {
            OnlineResult.Failed("Couldn't read Audius' answer (${e::class.simpleName}).")
        }
    }

    private fun toTrack(node: Any?): OnlineTrack? {
        val m = node as? Map<*, *> ?: return null
        val id = m["id"] as? String ?: return null
        if (m["is_streamable"] == false || m["stream_conditions"] != null) return null
        if ((m["access"] as? Map<*, *>)?.get("stream") == false) return null
        val artwork = (m["artwork"] as? Map<*, *>)?.let { (it["480x480"] ?: it["150x150"]) as? String }.orEmpty()
        return OnlineTrack(
            id = id,
            title = (m["title"] as? String).orEmpty().ifBlank { "Untitled" },
            artist = ((m["user"] as? Map<*, *>)?.get("name") as? String).orEmpty(),
            durationMs = ((m["duration"] as? Double) ?: 0.0).toLong() * 1000,
            artworkUrl = artwork,
            streamUrl = "$base/tracks/$id/stream?app_name=$app",
            pageUrl = (m["permalink"] as? String)?.let { "https://audius.co$it" }.orEmpty(),
        )
    }
}
