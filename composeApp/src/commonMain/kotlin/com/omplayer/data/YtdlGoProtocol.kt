package com.omplayer.data

/** One JSON-line response from the bundled ytdl-go bridge. */
internal sealed interface YtdlGoMessage {
    data class Search(val tracks: List<OnlineTrack>) : YtdlGoMessage
    data class Resolve(
        val url: String?,
        val contentLength: Long?,
        val consentCookie: String?,
        val mimeType: String?,
        val itag: Int?,
    ) : YtdlGoMessage
    data class Progress(val value: Float) : YtdlGoMessage
    data class Done(val path: String) : YtdlGoMessage
    data class Error(val message: String) : YtdlGoMessage
}

/** Parses the bridge protocol without trusting fields supplied by the remote search response. */
internal fun parseYtdlGoMessage(json: String): YtdlGoMessage? {
    val root = runCatching { MiniJson.parse(json) }.getOrNull() as? Map<*, *> ?: return null
    return when (root["type"] as? String) {
        "search" -> YtdlGoMessage.Search((root["tracks"] as? List<*>).orEmpty().mapNotNull(::parseTrack))
        "resolve" -> YtdlGoMessage.Resolve(
            url = (root["url"] as? String)?.takeIf(::isYtdlHttpsUrl),
            contentLength = (root["content_length"] as? Number)?.toLong()?.takeIf { it > 0L },
            consentCookie = (root["consent_cookie"] as? String)
                ?.takeIf { CONSENT_COOKIE.matches(it) },
            mimeType = (root["mime_type"] as? String)?.takeIf(::isSupportedStreamMime),
            itag = (root["itag"] as? Number)?.toInt()?.takeIf { it > 0 },
        )
        "progress" -> {
            val value = (root["progress"] as? Number)?.toFloat() ?: return null
            YtdlGoMessage.Progress(value.coerceIn(0f, 1f))
        }
        "done" -> (root["path"] as? String)?.takeIf(String::isNotBlank)?.let(YtdlGoMessage::Done)
        "error" -> YtdlGoMessage.Error((root["error"] as? String)?.trim().orEmpty().ifBlank { "The source request failed." })
        else -> null
    }
}

private fun parseTrack(value: Any?): OnlineTrack? {
    val track = value as? Map<*, *> ?: return null
    val id = (track["id"] as? String)?.takeIf(String::isNotBlank) ?: return null
    val pageUrl = (track["page_url"] as? String)?.takeIf(::isYtdlHttpUrl) ?: return null
    val streamUrl = (track["stream_url"] as? String)?.takeIf(::isYtdlHttpUrl) ?: pageUrl
    val artworkUrl = (track["artwork_url"] as? String)?.takeIf(::isYtdlHttpUrl).orEmpty()
    return OnlineTrack(
        id = id,
        title = (track["title"] as? String)?.trim().orEmpty().ifBlank { "Untitled" },
        artist = (track["artist"] as? String).orEmpty(),
        durationMs = ((track["duration_ms"] as? Number)?.toLong() ?: 0L).coerceAtLeast(0L),
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        pageUrl = pageUrl,
        canDownload = track["can_download"] as? Boolean ?: false,
        // Keep existing favourites, queue, and download records under their stable source key.
        sourceId = "ytdlp",
        needsResolve = track["needs_resolve"] as? Boolean ?: true,
    )
}

internal fun isYtdlHttpUrl(value: String): Boolean = value.startsWith("https://") || value.startsWith("http://")

private fun isYtdlHttpsUrl(value: String): Boolean = value.startsWith("https://")

private fun isSupportedStreamMime(value: String): Boolean =
    value.substringBefore(';').trim().lowercase() in setOf("audio/mp4", "audio/webm", "audio/ogg", "video/mp4")

private val CONSENT_COOKIE = Regex("CONSENT=YES\\+cb\\.20210328-17-p0\\.en\\+FX\\+\\d{3}")
