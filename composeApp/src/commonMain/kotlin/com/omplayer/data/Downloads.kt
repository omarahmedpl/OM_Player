package com.omplayer.data

import com.omplayer.model.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One finished download: a real file in app storage that plays with no network.
 *
 * [trackId] is the online track's id, namespaced by its source (`ytdlp:abc123`), so a download can be
 * matched back to the catalogue entry it came from.
 */
data class DownloadRecord(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val filePath: String,
    val coverPath: String = "",
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val sourceId: String = "",
    val pageUrl: String = "",
    val downloadedAtMs: Long = 0,
)

/** Downloaded items are ordinary local files: no network, so the player treats them like library songs. */
fun DownloadRecord.toMediaItem() = MediaItem(
    id = "download:$trackId",
    uri = "file://$filePath",
    title = title,
    folderId = "downloads",
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    isAudio = true,
    artist = artist,
    album = album,
    coverPath = coverPath,
)

/** The Downloaded list, in the order the store keeps it (newest first). */
fun List<DownloadRecord>.toMediaItems(): List<MediaItem> = map { it.toMediaItem() }

/**
 * Remembers finished downloads. One record per line, tab separated, `%`-escaped so titles may contain
 * tabs, commas or newlines. Unknown or short lines are skipped rather than losing the whole list.
 */
class DownloadStore(private val storage: TextStorage = MemoryTextStorage()) {
    private fun esc(s: String) = s.replace("%", "%25").replace("\t", "%09").replace("\n", "%0A").replace("\r", "%0D").replace(",", "%2C")
    private fun unesc(s: String) = s.replace("%2C", ",").replace("%0D", "\r").replace("%0A", "\n").replace("%09", "\t").replace("%25", "%")

    private fun load(): MutableList<DownloadRecord> {
        val list = mutableListOf<DownloadRecord>()
        storage.read()?.lineSequence()?.forEach { line ->
            val p = line.split('\t')
            if (p.size < 12 || p[0] != "D") return@forEach
            runCatching {
                DownloadRecord(
                    trackId = unesc(p[1]), title = unesc(p[2]), artist = unesc(p[3]), album = unesc(p[4]),
                    filePath = unesc(p[5]), coverPath = unesc(p[6]), durationMs = p[7].toLong(),
                    sizeBytes = p[8].toLong(), sourceId = unesc(p[9]), pageUrl = unesc(p[10]),
                    downloadedAtMs = p[11].toLong(),
                )
            }.onSuccess(list::add)
        }
        return list
    }

    private fun save(list: List<DownloadRecord>) {
        val text = list.joinToString("\n") { r ->
            listOf(
                "D", esc(r.trackId), esc(r.title), esc(r.artist), esc(r.album), esc(r.filePath), esc(r.coverPath),
                r.durationMs.toString(), r.sizeBytes.toString(), esc(r.sourceId), esc(r.pageUrl), r.downloadedAtMs.toString(),
            ).joinToString("\t")
        }
        storage.write(text)
    }

    /** Every finished download, newest first. */
    fun all(): List<DownloadRecord> = load().asReversed()

    /** Adds [record], replacing any earlier download of the same track. */
    fun add(record: DownloadRecord) {
        val list = load()
        val at = list.indexOfFirst { it.trackId == record.trackId }
        if (at >= 0) list[at] = record else list.add(record)
        save(list)
    }

    fun remove(trackId: String): DownloadRecord? {
        val list = load()
        val at = list.indexOfFirst { it.trackId == trackId }
        if (at < 0) return null
        val removed = list.removeAt(at)
        save(list)
        return removed
    }

    /** Forgets every download and returns what was removed, so the caller can delete the files. */
    fun clear(): List<DownloadRecord> {
        val removed = load().toList()
        save(emptyList())
        return removed.asReversed()
    }

    fun totalBytes(): Long = load().sumOf { it.sizeBytes }
}

/** Where one download is. */
sealed interface DownloadState {
    data object Queued : DownloadState
    data class Running(val progress: Float) : DownloadState
    data class Done(val filePath: String) : DownloadState
    data class Failed(val message: String) : DownloadState
    data object Cancelled : DownloadState
}

/** A download in progress or finished this session. */
data class DownloadJob(
    val track: OnlineTrack,
    val state: DownloadState,
    val progress: Float = 0f,
    /** Plain words, shown to the user when [state] is [DownloadState.Failed]. */
    val message: String = "",
    val filePath: String = "",
)

/** Thrown by a source when the user cancelled; not an error. */
class DownloadCancelled : Exception("Cancelled")

/**
 * Runs downloads one at a time and remembers the finished ones in [store].
 *
 * The real work belongs to the track's [OnlineMusicSource]; this class only sequences it, publishes
 * progress and turns failures into sentences a person can act on.
 */
class DownloadManager(
    private val store: DownloadStore,
    private val sources: List<OnlineMusicSource>,
    private val dir: String,
    private val now: () -> Long = { 0L },
    /** File sizes aren't knowable from common code; the platform supplies this. */
    private val fileSize: (String) -> Long = { 0L },
) {
    /** Downloads outlive the composable that started them (for example, navigating away from Discover). */
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs

    /** The finished downloads, kept in step with [store] so the UI can recompose. */
    private val _library = MutableStateFlow(store.all())
    val library: StateFlow<List<DownloadRecord>> = _library

    /** Lets only one caller work the queue at a time. */
    private val runLock = Mutex()
    private val drainLock = Mutex()

    private val cancelled = MutableStateFlow<Set<String>>(emptySet())

    /** Queues [track]. False when it is already queued, running or finished. */
    fun enqueue(track: OnlineTrack): Boolean {
        val existing = _jobs.value.firstOrNull { it.track.id == track.id }
        if (existing != null && existing.state !is DownloadState.Failed && existing.state !is DownloadState.Cancelled) return false
        cancelled.update { it - track.id }
        _jobs.update { jobs ->
            jobs.filterNot { it.track.id == track.id } + DownloadJob(track, DownloadState.Queued)
        }
        return true
    }

    /** Puts a failed or cancelled download back in the queue. */
    fun retry(trackId: String) {
        cancelled.update { it - trackId }
        _jobs.update { jobs ->
            jobs.map {
                if (it.track.id == trackId) it.copy(state = DownloadState.Queued, progress = 0f, message = "") else it
            }
        }
    }

    /** Drains queued work in a manager-owned scope so screen navigation does not cancel a download. */
    fun startQueued() {
        workerScope.launch {
            drainLock.withLock {
                while (runNext()) { /* drain the queue */ }
            }
        }
    }

    /** Stops a queued or running download. */
    fun cancel(trackId: String) {
        cancelled.update { it + trackId }
        _jobs.update { jobs ->
            jobs.map {
                when {
                    it.track.id != trackId -> it
                    it.state is DownloadState.Queued -> it.copy(state = DownloadState.Cancelled)
                    else -> it
                }
            }
        }
    }

    /** Deletes the record for [trackId] so the caller can remove the file. */
    fun delete(trackId: String): DownloadRecord? {
        _jobs.update { jobs -> jobs.filterNot { it.track.id == trackId } }
        val removed = store.remove(trackId)
        _library.value = store.all()
        return removed
    }

    /** Forgets every download and returns what was removed, so the caller can delete the files. */
    fun clearAll(): List<DownloadRecord> {
        val removed = store.clear()
        _library.value = store.all()
        return removed
    }

    private fun update(trackId: String, state: DownloadState, progress: Float, message: String = "", filePath: String = "") {
        _jobs.update { jobs ->
            jobs.map {
                if (it.track.id == trackId) it.copy(state = state, progress = progress, message = message, filePath = filePath) else it
            }
        }
    }

    /** Downloads the first queued track. False when the queue is empty. */
    suspend fun runNext(): Boolean = runLock.withLock { runNextLocked() }

    private suspend fun runNextLocked(): Boolean {
        val job = _jobs.value.firstOrNull { it.state is DownloadState.Queued } ?: return false
        val track = job.track
        update(track.id, DownloadState.Running(0f), 0f)

        val source = sources.firstOrNull { it.id == track.sourceId }
        if (source == null) {
            update(track.id, DownloadState.Failed("Downloads aren't available for this source."), 0f, "Downloads aren't available for this source.")
            return true
        }

        try {
            val path = source.download(
                track, dir,
                onProgress = { p -> if (track.id !in cancelled.value) update(track.id, DownloadState.Running(p), p) },
                isCancelled = { track.id in cancelled.value },
            )
            if (track.id in cancelled.value) {
                update(track.id, DownloadState.Cancelled, 0f)
            } else {
                store.add(
                    DownloadRecord(
                        trackId = trackKey(track), title = track.title, artist = track.artist, filePath = path,
                        coverPath = track.artworkUrl, durationMs = track.durationMs, sizeBytes = fileSize(path),
                        sourceId = track.sourceId, pageUrl = track.pageUrl, downloadedAtMs = now(),
                    ),
                )
                _library.value = store.all()
                update(track.id, DownloadState.Done(path), 1f, filePath = path)
            }
        } catch (_: DownloadCancelled) {
            update(track.id, DownloadState.Cancelled, 0f)
        } catch (e: CancellationException) {
            update(track.id, DownloadState.Queued, 0f)
            throw e
        } catch (e: Throwable) {
            val message = downloadErrorMessage(e)
            update(track.id, DownloadState.Failed(message), 0f, message)
        } finally {
            cancelled.update { it - track.id }
        }
        return true
    }
}

/**
 * Turns a download failure into something a person can act on. Ordered from the most specific cause
 * to the most general: the HTTP codes come from the message, the connection cases from the type.
 */
fun downloadErrorMessage(t: Throwable): String {
    val text = (t.message ?: "").lowercase()
    return when {
        "429" in text -> "The source is busy (rate limit). Wait a minute, then retry."
        "503" in text -> "The source is temporarily unavailable (503). Try again later."
        "404" in text || "removed" in text || "private" in text || "unavailable" in text ->
            "That track was removed or is private."
        "403" in text || "blocked" in text || "sign in" in text || "bot" in text ->
            "The source blocked this request. Try another track, or update the extractor."
        "unknownhost" in (t::class.simpleName ?: "").lowercase() ||
            "connect" in (t::class.simpleName ?: "").lowercase() ||
            "socket" in (t::class.simpleName ?: "").lowercase() ->
            "You're offline or the connection dropped. Check the connection and retry."
        else -> "Download failed. Tap retry."
    }
}
