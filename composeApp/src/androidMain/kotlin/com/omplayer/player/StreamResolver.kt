package com.omplayer.player

/**
 * The one place the player can ask an online source for a playable address.
 *
 * Discover queues hold page addresses that expire, so each entry is turned into a media address only
 * when it starts playing. Set once at startup; null on editions without online sources.
 */
internal data class YtdlResolvedStream(
    val url: String,
    val contentLength: Long,
    /** Complete Cookie header value generated alongside [url]. */
    val consentCookie: String,
    val mimeType: String,
    val itag: Int,
)

object StreamResolver {
    @Volatile
    internal var resolve: ((String) -> YtdlResolvedStream?)? = null
}
