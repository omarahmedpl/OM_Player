package com.omplayer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineSelectionTest {
    private fun record(id: String, path: String = "/data/downloads/$id.webm") = DownloadRecord(
        trackId = "ytdlp:$id", title = "Title $id", artist = "Artist", album = "",
        filePath = path, coverPath = "/data/downloads/$id.jpg", durationMs = 90_000,
        sizeBytes = 1_024, sourceId = "ytdlp", pageUrl = "https://example.test/$id", downloadedAtMs = 7,
    )

    @Test fun downloadedTracksBecomeLocalFilesNotStreams() {
        val item = listOf(record("a")).toMediaItems().single()

        assertEquals("file:///data/downloads/a.webm", item.uri)
        assertFalse(item.isStreamed)
        assertTrue(item.isAudio)
    }

    @Test fun theTitleArtistArtworkAndLengthSurvive() {
        val item = listOf(record("a")).toMediaItems().single()

        assertEquals("Title a", item.title)
        assertEquals("Artist", item.artist)
        assertEquals("/data/downloads/a.jpg", item.coverPath)
        assertEquals(90_000, item.durationMs)
    }

    @Test fun downloadedItemsGetTheirOwnStableId() {
        val first = listOf(record("a")).toMediaItems().single()
        val again = listOf(record("a")).toMediaItems().single()

        assertEquals("download:ytdlp:a", first.id)
        assertEquals(first.id, again.id)
    }

    @Test fun theOrderOfTheStoreIsKept() {
        val items = listOf(record("new", "/n.webm"), record("old", "/o.webm")).toMediaItems()

        assertEquals(listOf("download:ytdlp:new", "download:ytdlp:old"), items.map { it.id })
    }

    @Test fun anEmptyDownloadListMakesNoItems() {
        assertTrue(emptyList<DownloadRecord>().toMediaItems().isEmpty())
    }

    @Test fun errorsAreExplainedInPlainWords() {
        assertTrue(downloadErrorMessage(java.io.IOException("HTTP 429")).contains("busy"))
        assertTrue(downloadErrorMessage(java.io.IOException("HTTP 503")).contains("temporarily unavailable"))
        assertTrue(downloadErrorMessage(java.io.IOException("HTTP 404")).contains("removed"))
        assertTrue(downloadErrorMessage(java.io.IOException("Sign in to confirm you're not a bot")).contains("blocked"))
        assertTrue(downloadErrorMessage(java.net.UnknownHostException("no dns")).contains("offline"))
        assertTrue(downloadErrorMessage(IllegalStateException("weird")).contains("retry"))
    }
}
