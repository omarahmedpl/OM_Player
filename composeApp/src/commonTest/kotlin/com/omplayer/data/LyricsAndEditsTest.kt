package com.omplayer.data

import com.omplayer.model.MediaItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricsAndEditsTest {
    @Test fun parsesSyncedLyricsAndFindsCurrentLine() {
        val lines = LyricsParser.parse("[ar:Someone]\n[00:02.00]one\n[00:06.50]two\n[01:00]three\n[00:02.00][00:30.25]repeat")
        assertEquals(listOf(2000L, 2000L, 6500L, 30250L, 60000L), lines.map { it.timeMs })
        assertEquals(-1, LyricsParser.currentIndex(lines, 1_000))
        assertEquals("two", lines[LyricsParser.currentIndex(lines, 7_000)].text)
        assertEquals("three", lines[LyricsParser.currentIndex(lines, 90_000)].text)
    }

    @Test fun plainLyricsHaveNoTimes() {
        val lines = LyricsParser.parse("first\nsecond")
        assertEquals(listOf<Long?>(null, null), lines.map { it.timeMs })
        assertEquals(-1, LyricsParser.currentIndex(lines, 5_000))
    }

    @Test fun editsAndLyricsPersistAndApply() {
        val storage = MemoryTextStorage()
        val lib = UserLibrary(storage)
        lib.setEdit("a1", SongEdit(title = "New title", artist = "", coverPath = "/c/x.jpg"))
        lib.setLyrics("a1", "[00:01.00]hi\nline\twith tab")
        val again = UserLibrary(storage)
        val item = MediaItem("a1", "u", "Old", "f", isAudio = true, artist = "Orig artist").withEdit(again.edits.value["a1"])
        assertEquals("New title", item.title)
        assertEquals("Orig artist", item.artist)
        assertEquals("/c/x.jpg", item.coverPath)
        assertEquals("[00:01.00]hi\nline\twith tab", again.lyrics.value["a1"])
        again.setEdit("a1", SongEdit())
        again.setLyrics("a1", " ")
        assertNull(UserLibrary(storage).edits.value["a1"])
        assertNull(UserLibrary(storage).lyrics.value["a1"])
    }
}
