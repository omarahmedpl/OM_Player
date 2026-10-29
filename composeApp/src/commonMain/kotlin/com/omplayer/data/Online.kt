package com.omplayer.data

import com.omplayer.model.MediaItem

/** A track from an online catalogue that allows streaming (not part of the device library). */
class OnlineTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val artworkUrl: String,
    val streamUrl: String,
    val pageUrl: String,
)

sealed interface OnlineResult {
    class Tracks(val tracks: List<OnlineTrack>) : OnlineResult
    class Failed(val message: String) : OnlineResult
}

/** A catalogue of freely streamable music, searched and played inside the app. */
interface OnlineMusicSource {
    val name: String
    /** Shown wherever its tracks are listed. */
    val attribution: String
    suspend fun trending(): OnlineResult
    suspend fun search(query: String): OnlineResult
}

object NoOnlineMusic : OnlineMusicSource {
    override val name = "Online music"
    override val attribution = ""
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

/** Streamed items keep their stream address as `uri` and their artwork address as `coverPath`. */
fun OnlineTrack.toMediaItem(source: String) = MediaItem(
    id = "$source:$id", uri = streamUrl, title = title, folderId = source, durationMs = durationMs,
    isAudio = true, artist = artist, coverPath = artworkUrl,
)

/** True for items that stream from the network instead of a file on the device. */
val MediaItem.isStreamed: Boolean get() = uri.startsWith("http://") || uri.startsWith("https://")
