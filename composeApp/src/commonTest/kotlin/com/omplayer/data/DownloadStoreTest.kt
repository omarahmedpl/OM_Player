package com.omplayer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadStoreTest {
    private fun record(id: String, title: String = "Song", at: Long = 1_000) = DownloadRecord(
        trackId = id, title = title, artist = "Artist", album = "Album",
        filePath = "/data/downloads/$id.webm", coverPath = "https://img/$id.jpg",
        durationMs = 210_000, sizeBytes = 6_142_976, sourceId = "ytdlp",
        pageUrl = "https://example.test/$id", downloadedAtMs = at,
    )

    @Test fun roundTripsEveryFieldThroughStorage() {
        val storage = MemoryTextStorage()
        val written = record("ytdlp:abc")
        DownloadStore(storage).add(written)

        val back = DownloadStore(storage).all().single()
        assertEquals(written, back)
    }

    @Test fun separateStoreInstancesDoNotOverwriteRecordsAddedSinceTheirFirstRead() {
        val storage = MemoryTextStorage()
        val first = DownloadStore(storage)
        val second = DownloadStore(storage)

        assertTrue(first.all().isEmpty())
        assertTrue(second.all().isEmpty())
        second.add(record("track-b"))
        first.add(record("track-a"))

        assertEquals(setOf("track-a", "track-b"), DownloadStore(storage).all().map { it.trackId }.toSet())
    }

    @Test fun addSurfacesStorageWriteFailures() {
        val storage = object : TextStorage {
            override fun read(): String? = null
            override fun write(text: String) { throw IllegalStateException("storage unavailable") }
        }

        assertFailsWith<IllegalStateException> { DownloadStore(storage).add(record("track-a")) }
    }

    @Test fun keepsAwkwardCharactersIntact() {
        val storage = MemoryTextStorage()
        val tricky = record("ytdlp:x").copy(title = "Tab\there, 100% \"quoted\"\nnewline")
        DownloadStore(storage).add(tricky)

        assertEquals(tricky.title, DownloadStore(storage).all().single().title)
    }

    @Test fun addingTheSameTrackTwiceReplacesInsteadOfDuplicating() {
        val storage = MemoryTextStorage()
        val store = DownloadStore(storage)
        store.add(record("ytdlp:abc", title = "First"))
        store.add(record("ytdlp:abc", title = "Second"))

        assertEquals(1, store.all().size)
        assertEquals("Second", store.all().single().title)
    }

    @Test fun removeReturnsTheRecordAndForgetsIt() {
        val storage = MemoryTextStorage()
        val store = DownloadStore(storage)
        store.add(record("a"))
        store.add(record("b"))

        assertEquals("a", store.remove("a")?.trackId)
        assertNull(store.remove("a"))
        assertEquals(listOf("b"), DownloadStore(storage).all().map { it.trackId })
    }

    @Test fun clearEmptiesTheListAndSurvivesAReload() {
        val storage = MemoryTextStorage()
        val store = DownloadStore(storage)
        store.add(record("a"))
        store.add(record("b"))

        assertEquals(2, store.clear().size)
        assertTrue(DownloadStore(storage).all().isEmpty())
    }

    @Test fun totalBytesAddsUpTheFiles() {
        val store = DownloadStore(MemoryTextStorage())
        store.add(record("a").copy(sizeBytes = 100))
        store.add(record("b").copy(sizeBytes = 250))

        assertEquals(350, store.totalBytes())
    }

    @Test fun ignoresBrokenAndUnknownLines() {
        val storage = MemoryTextStorage()
        storage.write("garbage\nD\ttoo\tshort\n\n")

        assertTrue(DownloadStore(storage).all().isEmpty())
    }

    @Test fun aNewestFirstListPutsTheLatestDownloadOnTop() {
        val store = DownloadStore(MemoryTextStorage())
        store.add(record("old", at = 100))
        store.add(record("new", at = 900))

        assertEquals(listOf("new", "old"), store.all().map { it.trackId })
    }

    @Test fun anEmptyStoreReadsAsAnEmptyListNotNull() {
        assertNotNull(DownloadStore(MemoryTextStorage()).all())
    }
}
