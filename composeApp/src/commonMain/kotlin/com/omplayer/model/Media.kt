package com.omplayer.model

/** One playable file. `uri` is a content:// URI on Android and an absolute path or URL on desktop. */
data class MediaItem(
    val id: String,
    val uri: String,
    val title: String,
    val folderId: String,
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val dateAddedMs: Long = 0,
    val isAudio: Boolean = false,
    /** Audio only: artist tag when the platform provides one. */
    val artist: String = "",
    val album: String = "",
    /** App-side cover image file chosen by the user (overrides embedded artwork); empty when none. */
    val coverPath: String = "",
) {
    /** "4K", "1080p", "720p"… or empty when unknown. */
    val resolutionLabel: String
        get() {
            val h = minOf(width, height).takeIf { it > 0 } ?: return ""
            return when {
                h >= 2160 -> "4K"
                h >= 1440 -> "1440p"
                h >= 1080 -> "1080p"
                h >= 720 -> "720p"
                h >= 480 -> "480p"
                else -> "${h}p"
            }
        }
}

data class MediaFolder(
    val id: String,
    val name: String,
    val path: String,
    val items: List<MediaItem>,
) {
    val totalBytes: Long get() = items.sumOf { it.sizeBytes }
}

enum class SortKey { Name, Date, Size, Duration }
