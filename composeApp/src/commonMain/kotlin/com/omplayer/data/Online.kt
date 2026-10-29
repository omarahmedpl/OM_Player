package com.omplayer.data

import com.omplayer.model.MediaItem

/** What a source can do. Downloads are only offered where the source's terms allow keeping a copy. */
enum class SourceCapability { Stream, Download }

/** A track from an online catalogue that allows streaming (not part of the device library). */
class OnlineTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val artworkUrl: String,
    /** A direct media address when the source hands one out, otherwise the page address. */
    val streamUrl: String,
    val pageUrl: String,
    /** True when this particular track may be kept on the device. */
    val canDownload: Boolean = false,
    /** The source's id; also the prefix of [MediaItem.id]. */
    val sourceId: String = "",
    /**
     * True when [pageUrl] has to be turned into a media address at the moment it plays, because the
     * source hands out links that expire.
     */
    val needsResolve: Boolean = false,
)

sealed interface OnlineResult {
    class Tracks(val tracks: List<OnlineTrack>) : OnlineResult
    class Failed(val message: String) : OnlineResult
}

/**
 * A catalogue of freely playable music, searched and played inside the app.
 *
 * Sources differ in what they allow, so the UI asks [capabilities] and each track's
 * [OnlineTrack.canDownload] before offering a download.
 */
interface OnlineMusicSource {
    /** Stable short id, also used to namespace track and download ids (`ytdlp`). */
    val id: String
    val name: String
    /** Shown wherever its tracks are listed. */
    val attribution: String
    val capabilities: Set<SourceCapability>
    suspend fun trending(): OnlineResult
    suspend fun search(query: String): OnlineResult

    /** A playable URL for [track], resolved now. Null when it can't be played right now. */
    suspend fun resolveStream(track: OnlineTrack): String? = track.streamUrl

    /** Download [track] into [dir] and return the finished file's path. Throws on failure. */
    suspend fun download(track: OnlineTrack, dir: String, onProgress: (Float) -> Unit, isCancelled: () -> Boolean): String =
        throw UnsupportedOperationException("This source doesn't support downloads")
}

object NoOnlineMusic : OnlineMusicSource {
    override val id = "none"
    override val name = "Online music"
    override val attribution = ""
    override val capabilities: Set<SourceCapability> = emptySet()
    override suspend fun trending() = OnlineResult.Failed("Online music isn't available here")
    override suspend fun search(query: String) = OnlineResult.Failed("Online music isn't available here")
}

/** Looks up cover art on open services. Returns the image bytes, or null when nothing was found. */
interface CoverProvider {
    suspend fun find(title: String, artist: String, album: String): ByteArray?
}

object NoCovers : CoverProvider {
    override suspend fun find(title: String, artist: String, album: String): ByteArray? = null
}

/** The store key for [track]: source id plus track id, so two sources can never collide. */
fun trackKey(track: OnlineTrack): String = if (track.sourceId.isBlank()) track.id else "${track.sourceId}:${track.id}"

/**
 * Addresses that stand in for a track the player must resolve when it starts. Keeping the page address
 * inside an ordinary https URL means the queue can hold expiring sources: each entry is resolved only
 * as it plays, so next and previous keep working without resolving thirty tracks up front.
 */
private const val RESOLVE_PREFIX = "https://om-player.invalid/ytdlp?u="

/** True when [uri] is a stand-in that the player resolves before opening it. */
fun isResolveUri(uri: String): Boolean = uri.startsWith(RESOLVE_PREFIX)

fun resolveUri(pageUrl: String): String = RESOLVE_PREFIX + encodeComponent(pageUrl)

/** The page address behind a stand-in, or null when [uri] is an ordinary address. */
fun pageUrlOf(uri: String): String? =
    if (uri.startsWith(RESOLVE_PREFIX)) decodeComponent(uri.removePrefix(RESOLVE_PREFIX)) else null

private fun encodeComponent(s: String): String = buildString {
    for (byte in s.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        val ch = c.toChar()
        if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
            append(ch)
        } else {
            append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
        }
    }
}

private fun decodeComponent(s: String): String {
    val bytes = ArrayList<Byte>(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '%' && i + 2 < s.length) {
            val v = s.substring(i + 1, i + 3).toIntOrNull(16)
            if (v != null) { bytes.add(v.toByte()); i += 3; continue }
        }
        bytes.add(c.code.toByte()); i++
    }
    return bytes.toByteArray().decodeToString()
}

private const val HEX = "0123456789ABCDEF"

/** The player item for [track]; expiring sources get a stand-in address resolved at play time. */
fun OnlineTrack.toMediaItem(resolvedUrl: String = if (needsResolve) resolveUri(pageUrl) else streamUrl) = MediaItem(
    id = trackKey(this),
    uri = resolvedUrl,
    title = title,
    folderId = sourceId.ifBlank { "online" },
    durationMs = durationMs,
    isAudio = true,
    artist = artist,
    coverPath = artworkUrl,
)

/** Rebuilds the catalogue entry behind a player item, so it can be favourited, remembered or resolved. */
fun MediaItem.toOnlineTrack() = OnlineTrack(
    id = id.substringAfter(':'),
    title = title,
    artist = artist,
    durationMs = durationMs,
    artworkUrl = coverPath,
    streamUrl = uri,
    pageUrl = pageUrlOf(uri) ?: uri,
    canDownload = false,
    sourceId = id.substringBefore(':'),
    needsResolve = isResolveUri(uri),
)

/** True for items that stream from the network instead of a file on the device. */
val MediaItem.isStreamed: Boolean get() = uri.startsWith("http://") || uri.startsWith("https://")
