package com.omplayer.data

import com.omplayer.SharedPlaylist

object PlaylistExport {
    /**
     * Reads what [toJson] writes: `{"playlists":[{"name","tracks":[...]}]}`. Also accepts one bare playlist object or an array
     * of them, and tracks given as `{"title": ...}`. Returns null when the text isn't a playlist file.
     */
    fun parse(json: String): List<SharedPlaylist>? = runCatching {
        val root = MiniJson.parse(json)
        val entries: List<*> = when {
            root is Map<*, *> && root["playlists"] is List<*> -> root["playlists"] as List<*>
            root is Map<*, *> && root["tracks"] is List<*> -> listOf(root)
            root is List<*> -> root
            else -> return null
        }
        entries.mapNotNull { e ->
            val m = e as? Map<*, *> ?: return@mapNotNull null
            val tracks = (m["tracks"] as? List<*>)?.mapNotNull { t ->
                when (t) { is String -> t; is Map<*, *> -> t["title"] as? String; else -> null }
            } ?: return@mapNotNull null
            SharedPlaylist((m["name"] as? String)?.trim().orEmpty().ifEmpty { "Imported playlist" }, tracks)
        }
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    /** `{"app":"OM Player","playlists":[{"name":"..","tracks":["..",..]}]}`, pretty-printed, UTF-8. */
    fun toJson(lists: List<SharedPlaylist>): String = buildString {
        append("{\n  \"app\": \"OM Player\",\n  \"playlists\": [")
        lists.forEachIndexed { i, p ->
            append(if (i == 0) "\n" else ",\n")
            append("    {\n      \"name\": ").append(quote(p.name)).append(",\n      \"tracks\": [")
            p.tracks.forEachIndexed { j, t -> append(if (j == 0) "\n        " else ",\n        ").append(quote(t)) }
            append(if (p.tracks.isEmpty()) "]\n    }" else "\n      ]\n    }")
        }
        append(if (lists.isEmpty()) "]\n}" else "\n  ]\n}")
    }

    internal fun quote(s: String): String = buildString {
        append('"')
        for (ch in s) when {
            ch == '"' -> append("\\\"")
            ch == '\\' -> append("\\\\")
            ch == '\n' -> append("\\n")
            ch == '\r' -> append("\\r")
            ch == '\t' -> append("\\t")
            ch < ' ' -> append("\\u").append(ch.code.toString(16).padStart(4, '0'))
            else -> append(ch)
        }
        append('"')
    }
}

/** Minimal JSON reader: objects, arrays, strings, numbers, booleans and null. Throws on malformed input. */
internal object MiniJson {
    fun parse(text: String): Any? {
        val r = Reader(text)
        r.ws()
        val v = r.value(0)
        r.ws()
        require(r.i == text.length) { "trailing data" }
        return v
    }

    private class Reader(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(depth: Int): Any? {
            require(depth < 32) { "too deep" }
            ws()
            return when (val c = s.getOrNull(i)) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c != null && (c == '-' || c.isDigit())) num() else throw IllegalArgumentException("bad value at $i")
            }
        }
        fun lit(w: String, v: Any?): Any? { require(s.startsWith(w, i)); i += w.length; return v }
        fun num(): Double { val st = i; while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++; return s.substring(st, i).toDouble() }
        fun obj(depth: Int): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>(); i++; ws()
            if (s.getOrNull(i) == '}') { i++; return m }
            while (true) {
                ws(); val k = str(); ws(); require(s.getOrNull(i) == ':'); i++
                m[k] = value(depth + 1); ws()
                when (s.getOrNull(i++)) { ',' -> continue; '}' -> return m; else -> throw IllegalArgumentException("bad object") }
            }
        }
        fun arr(depth: Int): List<Any?> {
            val l = ArrayList<Any?>(); i++; ws()
            if (s.getOrNull(i) == ']') { i++; return l }
            while (true) {
                l += value(depth + 1); ws()
                when (s.getOrNull(i++)) { ',' -> continue; ']' -> return l; else -> throw IllegalArgumentException("bad array") }
            }
        }
        fun str(): String {
            require(s.getOrNull(i) == '"'); i++
            val b = StringBuilder()
            while (true) {
                val c = s.getOrNull(i++) ?: throw IllegalArgumentException("unterminated string")
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> when (val e = s.getOrNull(i++)) {
                        'n' -> b.append('\n'); 'r' -> b.append('\r'); 't' -> b.append('\t'); 'b' -> b.append('\b')
                        'f' -> b.append('\u000C'); '/', '"', '\\' -> b.append(e)
                        'u' -> { b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> throw IllegalArgumentException("bad escape")
                    }
                    else -> b.append(c)
                }
            }
        }
    }
}
