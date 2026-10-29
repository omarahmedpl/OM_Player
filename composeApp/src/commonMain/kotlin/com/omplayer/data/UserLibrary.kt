package com.omplayer.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What an import did: playlists created, tracks found on this device, tracks listed in the file. */
data class ImportResult(val playlists: Int, val matched: Int, val total: Int)

data class Playlist(val id: String, val name: String, val itemIds: List<String>)

/** Where [UserLibrary] keeps its text. One small file or preference per platform. */
interface TextStorage {
    fun read(): String?
    fun write(text: String)
}

class MemoryTextStorage : TextStorage {
    private var text: String? = null
    override fun read() = text
    override fun write(text: String) { this.text = text }
}

/**
 * The user's favourites and playlists (item ids), persisted through [TextStorage].
 * Format, one record per line: `F<TAB>id` and `P<TAB>playlistId<TAB>name<TAB>id,id,...` with `%`, tab, newline and comma escaped.
 * All calls come from the UI thread.
 */
class UserLibrary(private val storage: TextStorage = MemoryTextStorage()) {
    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists

    init { load() }

    fun toggleFavorite(id: String) {
        _favorites.value = if (id in _favorites.value) _favorites.value - id else _favorites.value + id
        save()
    }

    fun createPlaylist(name: String, itemIds: List<String> = emptyList()): Playlist {
        val next = (_playlists.value.mapNotNull { it.id.removePrefix("pl").toIntOrNull() }.maxOrNull() ?: 0) + 1
        val p = Playlist("pl$next", name.trim().ifEmpty { "Playlist $next" }, itemIds.distinct())
        _playlists.value = _playlists.value + p
        save()
        return p
    }

    fun renamePlaylist(id: String, name: String) = update(id) { it.copy(name = name.trim().ifEmpty { it.name }) }
    fun deletePlaylist(id: String) { _playlists.value = _playlists.value.filter { it.id != id }; save() }
    fun addToPlaylist(id: String, itemIds: List<String>) = update(id) { it.copy(itemIds = (it.itemIds + itemIds).distinct()) }
    fun removeFromPlaylist(id: String, itemId: String) = update(id) { it.copy(itemIds = it.itemIds - itemId) }

    /**
     * Creates playlists from [lists] by matching track titles (case and spacing ignored) to [library]. Playlists with no
     * match are skipped; a name already in use gets a number added.
     */
    fun importPlaylists(lists: List<com.omplayer.SharedPlaylist>, library: List<com.omplayer.model.MediaItem>): ImportResult {
        fun norm(s: String) = s.lowercase().trim().replace(Regex("\\s+"), " ")
        val byTitle = library.groupBy { norm(it.title) }
        var made = 0; var matched = 0; var total = 0
        for (p in lists) {
            total += p.tracks.size
            val used = HashSet<String>()
            val ids = p.tracks.mapNotNull { t -> byTitle[norm(t)]?.firstOrNull { it.id !in used }?.also { used += it.id }?.id }
            matched += ids.size
            if (ids.isEmpty()) continue
            var name = p.name
            var n = 2
            while (_playlists.value.any { it.name == name }) name = "${p.name} (${n++})"
            createPlaylist(name, ids)
            made++
        }
        return ImportResult(made, matched, total)
    }

    private fun update(id: String, f: (Playlist) -> Playlist) {
        _playlists.value = _playlists.value.map { if (it.id == id) f(it) else it }
        save()
    }

    private fun esc(s: String) = s.replace("%", "%25").replace("\t", "%09").replace("\n", "%0A").replace("\r", "%0D").replace(",", "%2C")
    private fun unesc(s: String) = s.replace("%2C", ",").replace("%0D", "\r").replace("%0A", "\n").replace("%09", "\t").replace("%25", "%")

    private fun save() {
        val lines = _favorites.value.map { "F\t${esc(it)}" } +
            _playlists.value.map { "P\t${it.id}\t${esc(it.name)}\t${it.itemIds.joinToString(",") { id -> esc(id) }}" }
        runCatching { storage.write(lines.joinToString("\n")) }
    }

    private fun load() {
        val text = runCatching { storage.read() }.getOrNull() ?: return
        val fav = LinkedHashSet<String>()
        val lists = ArrayList<Playlist>()
        text.lineSequence().forEach { line ->
            val p = line.split('\t')
            when {
                p.size >= 2 && p[0] == "F" -> fav += unesc(p[1])
                p.size >= 3 && p[0] == "P" ->
                    lists += Playlist(p[1], unesc(p[2]), p.getOrNull(3).orEmpty().split(',').filter { it.isNotEmpty() }.map(::unesc))
            }
        }
        _favorites.value = fav
        _playlists.value = lists
    }
}
