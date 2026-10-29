package com.omplayer.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A playlist of online tracks, kept apart from the device playlists in [UserLibrary]. */
class OnlinePlaylist(val id: String, val name: String, val keys: List<String> = emptyList())

/**
 * Favourites and playlists for Discover tracks. Deliberately separate from [UserLibrary]: an online
 * song is not a file on the device, and mixing the two would put entries in the offline lists that
 * cannot be played from there.
 *
 * Tracks are held by key (`ytdlp:<id>`); the catalogue details live in [OnlineTrackStore].
 */
class OnlineLibrary(private val storage: TextStorage = MemoryTextStorage()) {
    private val _favorites = MutableStateFlow(readFavorites())
    val favorites: StateFlow<Set<String>> = _favorites

    private val _playlists = MutableStateFlow(readPlaylists())
    val playlists: StateFlow<List<OnlinePlaylist>> = _playlists

    fun isFavorite(key: String): Boolean = key in _favorites.value

    fun toggleFavorite(key: String) {
        val next = _favorites.value.toMutableSet().apply { if (!add(key)) remove(key) }
        _favorites.value = next
        save()
    }

    /** Creates a playlist and returns it, or returns the existing one when the name is taken. */
    fun createPlaylist(name: String): OnlinePlaylist {
        val clean = name.trim().ifBlank { "New playlist" }
        _playlists.value.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it }
        val playlist = OnlinePlaylist(id = "p${nextId()}", name = clean)
        _playlists.value = _playlists.value + playlist
        save()
        return playlist
    }

    /** Adds [keys] to a playlist, skipping any it already holds. */
    fun addToPlaylist(playlistId: String, keys: List<String>) {
        if (keys.isEmpty()) return
        _playlists.value = _playlists.value.map { p ->
            if (p.id != playlistId) p else OnlinePlaylist(p.id, p.name, (p.keys + keys).distinct())
        }
        save()
    }

    fun removeFromPlaylist(playlistId: String, key: String) {
        _playlists.value = _playlists.value.map { p ->
            if (p.id != playlistId) p else OnlinePlaylist(p.id, p.name, p.keys - key)
        }
        save()
    }

    fun renamePlaylist(playlistId: String, name: String) {
        val clean = name.trim().ifBlank { return }
        _playlists.value = _playlists.value.map { if (it.id == playlistId) OnlinePlaylist(it.id, clean, it.keys) else it }
        save()
    }

    fun deletePlaylist(playlistId: String) {
        _playlists.value = _playlists.value.filterNot { it.id == playlistId }
        save()
    }

    /** Forgets a track everywhere, so deleting it from the catalogue doesn't leave dangling keys. */
    fun forget(key: String) {
        _favorites.value = _favorites.value - key
        _playlists.value = _playlists.value.map { OnlinePlaylist(it.id, it.name, it.keys - key) }
        save()
    }

    private fun nextId(): Int = (_playlists.value.mapNotNull { it.id.removePrefix("p").toIntOrNull() }.maxOrNull() ?: 0) + 1

    private fun readFavorites(): Set<String> = storage.read().orEmpty().lineSequence()
        .filter { it.startsWith("F\t") }
        .map { unesc(it.removePrefix("F\t")) }
        .filter { it.isNotBlank() }
        .toSet()

    private fun readPlaylists(): List<OnlinePlaylist> = storage.read().orEmpty().lineSequence()
        .filter { it.startsWith("P\t") }
        .mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 3 || p[1].isBlank()) return@mapNotNull null
            OnlinePlaylist(
                id = p[1],
                name = unesc(p[2]).ifBlank { "Playlist" },
                keys = p.getOrNull(3).orEmpty().split(',').map(::unesc).filter { it.isNotBlank() },
            )
        }
        .toList()

    private fun save() {
        val text = buildString {
            _favorites.value.forEach { append("F\t").append(esc(it)).append('\n') }
            _playlists.value.forEach { p ->
                append("P\t").append(p.id).append('\t').append(esc(p.name)).append('\t')
                append(p.keys.joinToString(",") { esc(it) }).append('\n')
            }
        }
        runCatching { storage.write(text) }
    }

    private fun esc(s: String) = s.replace("%", "%25").replace("\t", "%09").replace("\n", "%0A").replace("\r", "%0D").replace(",", "%2C")
    private fun unesc(s: String) = s.replace("%2C", ",").replace("%0D", "\r").replace("%0A", "\n").replace("%09", "\t").replace("%25", "%")
}
