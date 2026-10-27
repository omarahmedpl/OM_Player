package com.omplayer.data

import android.content.Context

/** SharedPreferences-backed [ProgressStore]. Value format: "pos|dur|timestamp". */
class PersistentProgressStore(context: Context) : ProgressStore {
    private val prefs = context.applicationContext.getSharedPreferences("om_progress", Context.MODE_PRIVATE)

    private fun read(id: String): Triple<Long, Long, Long>? {
        val p = prefs.getString(id, null)?.split('|') ?: return null
        if (p.size != 3) return null
        return Triple(p[0].toLongOrNull() ?: return null, p[1].toLongOrNull() ?: 0, p[2].toLongOrNull() ?: 0)
    }

    override fun positionMs(id: String): Long? = read(id)?.first

    override fun save(id: String, positionMs: Long, durationMs: Long) {
        if (durationMs > 0 && positionMs >= durationMs - maxOf(5_000, durationMs / 50)) {
            prefs.edit().remove(id).apply()
        } else if (positionMs > 3_000) {
            prefs.edit().putString(id, "$positionMs|$durationMs|${System.currentTimeMillis()}").apply()
        }
    }

    override fun recentIds(limit: Int): List<String> =
        prefs.all.keys.mapNotNull { k -> read(k)?.let { k to it.third } }
            .sortedByDescending { it.second }.take(limit).map { it.first }
}
