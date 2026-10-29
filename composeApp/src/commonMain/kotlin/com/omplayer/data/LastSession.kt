package com.omplayer.data

/** What was playing last: the queue (item ids), which one, and where it stopped. */
class SavedSession(val ids: List<String>, val index: Int, val positionMs: Long)

/** Remembers the last audio session so the app can offer it again after a restart. Format: `index<TAB>position<TAB>id,id,...`. */
class SessionStore(private val storage: TextStorage = MemoryTextStorage()) {
    private fun esc(s: String) = s.replace("%", "%25").replace(",", "%2C").replace("\t", "%09").replace("\n", "%0A")
    private fun unesc(s: String) = s.replace("%2C", ",").replace("%09", "\t").replace("%0A", "\n").replace("%25", "%")

    fun load(): SavedSession? = runCatching {
        val p = storage.read()?.split('\t') ?: return null
        if (p.size < 3) return null
        val ids = p[2].split(',').filter { it.isNotEmpty() }.map(::unesc)
        val index = p[0].toInt()
        if (ids.isEmpty() || index !in ids.indices) null else SavedSession(ids, index, p[1].toLong().coerceAtLeast(0))
    }.getOrNull()

    fun save(s: SavedSession) {
        runCatching { storage.write("${s.index}\t${s.positionMs}\t${s.ids.joinToString(",") { esc(it) }}") }
    }

    fun clear() { runCatching { storage.write("") } }
}
