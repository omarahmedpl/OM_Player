package com.omplayer.data

/** Lyrics found online: [synced] is LRC text with `[mm:ss.xx]` stamps, [plain] has none. Either may be null. */
class FoundLyrics(val plain: String?, val synced: String?) {
    val best: String? get() = synced?.takeIf { it.isNotBlank() } ?: plain?.takeIf { it.isNotBlank() }
}

sealed interface LyricsResult {
    data class Found(val lyrics: FoundLyrics) : LyricsResult
    data object NotFound : LyricsResult
    data class Failed(val message: String) : LyricsResult
}

interface LyricsProvider {
    suspend fun find(title: String, artist: String, album: String, durationMs: Long): LyricsResult
}

object NoLyrics : LyricsProvider {
    override suspend fun find(title: String, artist: String, album: String, durationMs: Long) =
        LyricsResult.Failed("Lyrics lookup isn't available here")
}

class LyricLine(val timeMs: Long?, val text: String)

object LyricsParser {
    private val stamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    /** Lines with their time when the text has LRC stamps (one or several per line), otherwise plain lines with null times. */
    fun parse(text: String): List<LyricLine> {
        val rows = text.lines()
        val synced = rows.any { stamp.containsMatchIn(it) }
        if (!synced) return rows.map { LyricLine(null, it.trimEnd()) }
        val out = ArrayList<LyricLine>()
        for (row in rows) {
            val stamps = stamp.findAll(row).toList()
            if (stamps.isEmpty()) continue // tags like [ar:..] and blank lines
            val lyric = stamp.replace(row, "").trim()
            for (m in stamps) {
                val frac = m.groupValues[3]
                val ms = if (frac.isEmpty()) 0L else (frac.padEnd(3, '0').take(3)).toLong()
                out += LyricLine(m.groupValues[1].toLong() * 60_000 + m.groupValues[2].toLong() * 1000 + ms, lyric)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    /** Index of the line being sung at [positionMs], or -1 before the first line / for unsynced text. */
    fun currentIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty() || lines[0].timeMs == null) return -1
        var lo = 0; var hi = lines.lastIndex; var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if ((lines[mid].timeMs ?: Long.MAX_VALUE) <= positionMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}
