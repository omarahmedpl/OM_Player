package com.omplayer.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadManagerTest {
    private val track = OnlineTrack(
        id = "abc", title = "Song", artist = "Artist", durationMs = 210_000,
        artworkUrl = "https://img/abc.jpg", streamUrl = "https://example.test/abc",
        pageUrl = "https://example.test/abc", canDownload = true, sourceId = "fake",
    )

    /** A source whose download behaviour the test scripts. */
    private class FakeSource(
        private val behaviour: suspend (OnlineTrack, (Float) -> Unit, () -> Boolean) -> String,
    ) : OnlineMusicSource {
        override val id = "fake"
        override val name = "Fake"
        override val attribution = "Fake source"
        override val capabilities = setOf(SourceCapability.Stream, SourceCapability.Download)
        override suspend fun trending() = OnlineResult.Tracks(emptyList())
        override suspend fun search(query: String) = OnlineResult.Tracks(emptyList())
        override suspend fun download(track: OnlineTrack, dir: String, onProgress: (Float) -> Unit, isCancelled: () -> Boolean): String =
            behaviour(track, onProgress, isCancelled)
    }

    private fun manager(source: OnlineMusicSource, store: DownloadStore = DownloadStore(MemoryTextStorage())) =
        DownloadManager(store, listOf(source), dir = "/data/downloads", now = { 5_000 })

    @Test
    fun aQueuedDownloadFinishesAndIsRemembered() {
        runBlocking {
            val store = DownloadStore(MemoryTextStorage())
            val m = manager(FakeSource { t, p, _ -> p(1f); "/data/downloads/${t.id}.webm" }, store)
            m.enqueue(track)

            assertTrue(m.runNext())

            val job = m.jobs.value.single()
            assertIs<DownloadState.Done>(job.state)
            assertEquals("/data/downloads/abc.webm", job.filePath)
            val saved = store.all().single()
            assertEquals("fake:abc", saved.trackId)
            assertEquals("Song", saved.title)
            assertEquals("/data/downloads/abc.webm", saved.filePath)
            assertEquals(5_000, saved.downloadedAtMs)
        }
    }

    @Test
    fun progressIsPublishedWhileItRuns() {
        runBlocking {
            lateinit var m: DownloadManager
            var seen = -1f
            m = manager(FakeSource { _, p, _ ->
                p(0.5f)
                seen = m.jobs.value.single().progress
                "/f.webm"
            })
            m.enqueue(track)

            m.runNext()

            assertEquals(0.5f, seen)
            assertEquals(1f, m.jobs.value.single().progress)
        }
    }

    @Test
    fun aFailureIsReportedInPlainWordsAndNothingIsStored() {
        runBlocking {
            val store = DownloadStore(MemoryTextStorage())
            val m = manager(FakeSource { _, _, _ -> throw java.io.IOException("HTTP 503") }, store)
            m.enqueue(track)

            m.runNext()

            val job = m.jobs.value.single()
            assertIs<DownloadState.Failed>(job.state)
            assertTrue(job.message.contains("temporarily unavailable"), job.message)
            assertTrue(store.all().isEmpty())
        }
    }

    @Test
    fun retryRunsAFailedDownloadAgain() {
        runBlocking {
            var attempts = 0
            val m = manager(FakeSource { _, _, _ ->
                attempts++
                if (attempts == 1) throw java.io.IOException("HTTP 503") else "/f.webm"
            })
            m.enqueue(track)
            m.runNext()
            assertIs<DownloadState.Failed>(m.jobs.value.single().state)

            m.retry(track.id)
            assertTrue(m.runNext())

            assertIs<DownloadState.Done>(m.jobs.value.single().state)
            assertEquals(2, attempts)
        }
    }

    @Test
    fun cancellingMidDownloadStopsItAndStoresNothing() {
        runBlocking {
            val store = DownloadStore(MemoryTextStorage())
            lateinit var m: DownloadManager
            m = manager(FakeSource { _, p, isCancelled ->
                p(0.3f)
                m.cancel(track.id)
                if (isCancelled()) throw DownloadCancelled() else "/f.webm"
            }, store)
            m.enqueue(track)

            m.runNext()

            assertIs<DownloadState.Cancelled>(m.jobs.value.single().state)
            assertTrue(store.all().isEmpty())
        }
    }

    @Test
    fun cancellationFromUiThreadIsVisibleToTheIoDownloadWorker() {
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val store = DownloadStore(MemoryTextStorage())
            val m = manager(FakeSource { _, _, isCancelled ->
                started.complete(Unit)
                withTimeout(2_000) { while (!isCancelled()) delay(5) }
                throw DownloadCancelled()
            }, store)
            m.enqueue(track)
            val running = launch(Dispatchers.IO) { m.runNext() }

            withTimeout(2_000) { started.await() }
            m.cancel(track.id)
            withTimeout(2_000) { running.join() }

            assertIs<DownloadState.Cancelled>(m.jobs.value.single().state)
            assertTrue(store.all().isEmpty())
        }
    }

    @Test
    fun aCancelledDownloadCanBeQueuedAgain() {
        runBlocking {
            var attempt = 0
            val m = manager(FakeSource { _, _, _ -> attempt++; if (attempt == 1) throw DownloadCancelled() else "/f.webm" })
            m.enqueue(track)
            m.runNext()
            assertIs<DownloadState.Cancelled>(m.jobs.value.single().state)

            m.retry(track.id)
            m.runNext()

            assertIs<DownloadState.Done>(m.jobs.value.single().state)
        }
    }

    @Test
    fun queueingTheSameTrackTwiceKeepsOneJob() {
        val m = manager(FakeSource { _, _, _ -> "/f.webm" })
        assertTrue(m.enqueue(track))
        assertFalse(m.enqueue(track))

        assertEquals(1, m.jobs.value.size)
    }

    @Test
    fun runNextReturnsFalseWhenThereIsNothingQueued() {
        runBlocking {
            assertFalse(manager(FakeSource { _, _, _ -> "/f.webm" }).runNext())
        }
    }

    @Test
    fun managerOwnedWorkerDrainsQueuedDownloads() {
        runBlocking {
            val m = manager(FakeSource { t, _, _ -> "/data/downloads/${t.id}.webm" })
            m.enqueue(track)

            m.startQueued()

            withTimeout(2_000) {
                while (m.jobs.value.singleOrNull()?.state !is DownloadState.Done) delay(10)
            }
            assertEquals("/data/downloads/abc.webm", m.library.value.single().filePath)
        }
    }

    @Test
    fun cancellationReturnsTheCurrentDownloadToQueuedInsteadOfFailed() {
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val m = manager(FakeSource { _, _, _ -> started.complete(Unit); awaitCancellation() })
            m.enqueue(track)
            val running = launch { m.runNext() }

            withTimeout(2_000) { started.await() }
            running.cancelAndJoin()

            assertIs<DownloadState.Queued>(m.jobs.value.single().state)
        }
    }

    @Test
    fun aStorageWriteFailureIsNotPublishedAsADoneDownload() {
        runBlocking {
            val storage = object : TextStorage {
                override fun read(): String? = null
                override fun write(text: String) { throw IllegalStateException("storage unavailable") }
            }
            val m = manager(FakeSource { _, _, _ -> "/data/downloads/abc.webm" }, DownloadStore(storage))
            m.enqueue(track)

            m.runNext()

            assertIs<DownloadState.Failed>(m.jobs.value.single().state)
            assertTrue(m.library.value.isEmpty())
        }
    }

    @Test
    fun aTrackWithoutADownloadableSourceFailsInPlainWords() {
        runBlocking {
            val m = DownloadManager(DownloadStore(MemoryTextStorage()), emptyList(), "/d", now = { 0 })
            m.enqueue(track)

            m.runNext()

            val job = m.jobs.value.single()
            assertIs<DownloadState.Failed>(job.state)
            assertTrue(job.message.isNotBlank())
        }
    }

    @Test
    fun deletingAFinishedDownloadForgetsIt() {
        val store = DownloadStore(MemoryTextStorage())
        val m = DownloadManager(store, emptyList(), "/d", now = { 0 })
        store.add(
            DownloadRecord(
                trackId = "fake:abc", title = "Song", artist = "", album = "", filePath = "/f.webm",
                coverPath = "", durationMs = 0, sizeBytes = 10, sourceId = "fake", pageUrl = "", downloadedAtMs = 1,
            ),
        )

        assertNotNull(m.delete("fake:abc"))
        assertNull(m.jobs.value.firstOrNull { it.track.id == "fake:abc" })
        assertTrue(store.all().isEmpty())
    }
}
