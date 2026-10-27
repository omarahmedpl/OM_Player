package com.omplayer.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Same semantics as [InMemoryProgressStore], persisted as a small JSON file. */
class JsonFileProgressStore(
    private val file: File = File(AppDirs.config, "progress.json"),
) : ProgressStore {
    private data class Entry(val pos: Long, val dur: Long, val at: Long)
    private val map = LinkedHashMap<String, Entry>()
    private var clock = 0L

    init { load() }

    @Synchronized override fun positionMs(id: String): Long? = map[id]?.pos

    @Synchronized override fun save(id: String, positionMs: Long, durationMs: Long) {
        val changed = if (durationMs > 0 && positionMs >= durationMs - maxOf(5_000, durationMs / 50)) map.remove(id) != null
        else if (positionMs > 3_000) { map[id] = Entry(positionMs, durationMs, ++clock); true } else false
        if (changed) persist()
    }

    @Synchronized override fun recentIds(limit: Int): List<String> =
        map.entries.sortedByDescending { it.value.at }.take(limit).map { it.key }

    private val entryRe = Regex("""\{"id":"((?:[^"\\]|\\.)*)","pos":(\d+),"dur":(\d+),"at":(\d+)\}""")

    private fun load() {
        runCatching {
            if (!file.exists()) return
            entryRe.findAll(file.readText()).sortedBy { it.groupValues[4].toLong() }.forEach { m ->
                map[unescape(m.groupValues[1])] = Entry(m.groupValues[2].toLong(), m.groupValues[3].toLong(), ++clock)
            }
        }
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            val body = map.entries.joinToString(",") { (k, v) ->
                """{"id":"${escape(k)}","pos":${v.pos},"dur":${v.dur},"at":${v.at}}"""
            }
            val tmp = File(file.path + ".tmp")
            tmp.writeText("""{"entries":[$body]}""")
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
    private fun unescape(s: String) = Regex("""\\(.)""").replace(s) {
        when (val c = it.groupValues[1]) { "n" -> "\n"; "r" -> "\r"; else -> c }
    }
}
