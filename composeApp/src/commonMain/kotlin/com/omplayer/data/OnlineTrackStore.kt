package com.omplayer.data

/**
 * Remembers the catalogue details of online tracks the user played or favourited, so they can be shown
 * and played again without searching for them. One record per line, `%`-escaped.
 */
class OnlineTrackStore(private val storage: TextStorage = MemoryTextStorage()) {
    private var cache: MutableList<OnlineTrack>? = null

    private fun esc(s: String) = s.replace("%", "%25").replace("\t", "%09").replace("\n", "%0A").replace("\r", "%0D").replace(",", "%2C")
    private fun unesc(s: String) = s.replace("%2C", ",").replace("%0D", "\r").replace("%0A", "\n").replace("%09", "\t").replace("%25", "%")

    private fun load(): MutableList<OnlineTrack> {
        cache?.let { return it }
        val list = mutableListOf<OnlineTrack>()
        storage.read()?.lineSequence()?.forEach { line ->
            val p = line.split('\t')
            if (p.size < 9 || p[0] != "T") return@forEach
            runCatching {
                OnlineTrack(
                    id = unesc(p[1]), title = unesc(p[2]), artist = unesc(p[3]),
                    durationMs = p[4].toLong(), artworkUrl = unesc(p[5]),
                    streamUrl = unesc(p[6]), pageUrl = unesc(p[7]), sourceId = unesc(p[8]),
                    canDownload = p.size > 9 && p[9] == "1",
                    needsResolve = p.size > 10 && p[10] == "1",
                )
            }.onSuccess(list::add)
        }
        cache = list
        return list
    }

    private fun save(list: List<OnlineTrack>) {
        cache = list.toMutableList()
        val text = list.joinToString("\n") { t ->
            listOf(
                "T", esc(t.id), esc(t.title), esc(t.artist), t.durationMs.toString(), esc(t.artworkUrl),
                esc(t.streamUrl), esc(t.pageUrl), esc(t.sourceId),
                if (t.canDownload) "1" else "0", if (t.needsResolve) "1" else "0",
            ).joinToString("\t")
        }
        runCatching { storage.write(text) }
    }

    fun all(): List<OnlineTrack> = load().toList()

    /** The remembered track with this namespaced id (`ytdlp:abc`), or null. */
    fun byId(key: String): OnlineTrack? = load().firstOrNull { trackKey(it) == key }

    /** Keeps [track] for later, replacing any earlier copy of the same one. */
    fun remember(track: OnlineTrack) {
        val list = load()
        val at = list.indexOfFirst { trackKey(it) == trackKey(track) }
        if (at >= 0) list[at] = track else list.add(track)
        save(list)
    }

    /** Keeps several at once (a whole Discover queue), in one write. */
    fun rememberAll(tracks: List<OnlineTrack>) {
        if (tracks.isEmpty()) return
        val list = load()
        tracks.forEach { track ->
            val at = list.indexOfFirst { trackKey(it) == trackKey(track) }
            if (at >= 0) list[at] = track else list.add(track)
        }
        save(list)
    }

    fun forget(key: String) {
        val list = load()
        if (list.removeAll { trackKey(it) == key }) save(list)
    }
}
