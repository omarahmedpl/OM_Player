package com.omplayer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnlineTrackStoreTest {
    private fun track(id: String, title: String = "Song") = OnlineTrack(
        id = id, title = title, artist = "Artist", durationMs = 200_000,
        artworkUrl = "https://img/$id.jpg", streamUrl = "https://example.test/$id",
        pageUrl = "https://example.test/$id", canDownload = true, sourceId = "ytdlp",
    )

    @Test fun aRememberedTrackCanBeRebuiltAfterARestart() {
        val storage = MemoryTextStorage()
        OnlineTrackStore(storage).remember(track("abc"))

        val back = OnlineTrackStore(storage).byId("ytdlp:abc")
        assertEquals("Song", back?.title)
        assertEquals("Artist", back?.artist)
        assertEquals(200_000, back?.durationMs)
        assertEquals("https://example.test/abc", back?.pageUrl)
        assertEquals("ytdlp", back?.sourceId)
    }

    @Test fun theKeyIsTheSourcePlusTheTrackId() {
        val store = OnlineTrackStore(MemoryTextStorage())
        store.remember(track("abc"))

        assertEquals(listOf("ytdlp:abc"), store.all().map { "${it.sourceId}:${it.id}" })
    }

    @Test fun rememberingTheSameTrackTwiceKeepsOneCopyWithTheLatestTitle() {
        val store = OnlineTrackStore(MemoryTextStorage())
        store.remember(track("abc", "Old"))
        store.remember(track("abc", "New"))

        assertEquals(1, store.all().size)
        assertEquals("New", store.byId("ytdlp:abc")?.title)
    }

    @Test fun forgettingRemovesItForGood() {
        val storage = MemoryTextStorage()
        val store = OnlineTrackStore(storage)
        store.remember(track("abc"))

        store.forget("ytdlp:abc")

        assertNull(store.byId("ytdlp:abc"))
        assertTrue(OnlineTrackStore(storage).all().isEmpty())
    }

    @Test fun awkwardTitlesSurviveTheRoundTrip() {
        val storage = MemoryTextStorage()
        val tricky = track("abc", "Tab\there, 100% \"quoted\"\nnewline")
        OnlineTrackStore(storage).remember(tricky)

        assertEquals(tricky.title, OnlineTrackStore(storage).byId("ytdlp:abc")?.title)
    }

    @Test fun brokenLinesAreIgnoredInsteadOfLosingEverything() {
        val storage = MemoryTextStorage()
        storage.write("garbage\nT\ttoo\tshort\n")

        assertTrue(OnlineTrackStore(storage).all().isEmpty())
    }

    @Test fun aMediaItemCanBeTurnedBackIntoTheTrackItCameFrom() {
        val original = OnlineTrack(
            id = "abc", title = "Song", artist = "Artist", durationMs = 200_000,
            artworkUrl = "https://img/abc.jpg", streamUrl = "https://example.test/abc",
            pageUrl = "https://example.test/abc", canDownload = true, sourceId = "ytdlp",
            needsResolve = true,
        )

        val back = original.toMediaItem().toOnlineTrack()

        assertEquals("abc", back.id)
        assertEquals("ytdlp", back.sourceId)
        assertEquals("https://example.test/abc", back.pageUrl)
        assertTrue(back.needsResolve)
    }

    @Test fun anExpiringTrackIsQueuedWithAStandInAddress() {
        val original = OnlineTrack(
            id = "abc", title = "Song", artist = "Artist", durationMs = 1,
            artworkUrl = "", streamUrl = "https://example.test/abc", pageUrl = "https://example.test/abc",
            sourceId = "ytdlp", needsResolve = true,
        )

        val item = original.toMediaItem()

        // The queue never holds the expiring media address itself, only the page it is resolved from.
        assertTrue(isResolveUri(item.uri))
        assertEquals("https://example.test/abc", pageUrlOf(item.uri))
    }
}
