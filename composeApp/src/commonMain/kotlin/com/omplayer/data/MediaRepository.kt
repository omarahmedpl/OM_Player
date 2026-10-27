package com.omplayer.data

import com.omplayer.model.MediaFolder
import kotlinx.coroutines.flow.StateFlow

sealed interface LibraryState {
    data object Loading : LibraryState
    /** The platform needs a permission or a folder grant before it can scan. */
    data object NeedsPermission : LibraryState
    data class Ready(val folders: List<MediaFolder>) : LibraryState
    data class Failed(val message: String) : LibraryState
}

/** Finds local media. Android: MediaStore. Desktop: scans user video/music folders. */
interface MediaRepository {
    val state: StateFlow<LibraryState>
    suspend fun refresh()
    /** Ask for whatever access the platform needs (runtime permission, folder picker). */
    fun requestAccess()
}

/** Remembers where playback stopped, per item id. */
interface ProgressStore {
    fun positionMs(id: String): Long?
    fun save(id: String, positionMs: Long, durationMs: Long)
    /** Most recently watched first, unfinished only. */
    fun recentIds(limit: Int = 12): List<String>
}

class InMemoryProgressStore : ProgressStore {
    private data class Entry(val pos: Long, val dur: Long, val at: Long)
    private val map = LinkedHashMap<String, Entry>()
    private var clock = 0L

    override fun positionMs(id: String): Long? = map[id]?.pos

    override fun save(id: String, positionMs: Long, durationMs: Long) {
        // Within the last 2% (or 5 s) counts as finished: forget the position.
        if (durationMs > 0 && positionMs >= durationMs - maxOf(5_000, durationMs / 50)) map.remove(id)
        else if (positionMs > 3_000) map[id] = Entry(positionMs, durationMs, ++clock)
    }

    override fun recentIds(limit: Int): List<String> =
        map.entries.sortedByDescending { it.value.at }.take(limit).map { it.key }
}
