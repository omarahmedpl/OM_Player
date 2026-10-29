package com.omplayer.data

import android.content.Context
import android.os.SystemClock
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.omplayer.player.YtdlResolvedStream
import org.json.JSONObject

/** Android adapter for the bundled ytdl-go bridge. The persisted online source key remains `ytdlp`. */
class YtdlGoSource(private val context: Context) : OnlineMusicSource {
    override val id = "ytdlp"
    override val name = "YouTube"
    override val attribution = "Powered by ytdl-go. Download only content you have the right to keep."
    override val capabilities = setOf(SourceCapability.Stream, SourceCapability.Download)

    override suspend fun trending(): OnlineResult = OnlineResult.Tracks(emptyList())

    override suspend fun search(query: String): OnlineResult {
        if (query.isBlank()) return OnlineResult.Tracks(emptyList())
        return try {
            var tracks: List<OnlineTrack>? = null
            runBridge(listOf("search", query.trim()), SEARCH_TIMEOUT_MS) { line ->
                when (val message = parseYtdlGoMessage(line)) {
                    is YtdlGoMessage.Search -> tracks = message.tracks
                    is YtdlGoMessage.Error -> throw SourceFailure(message.message)
                    else -> throw SourceFailure("The search response was invalid.")
                }
            }
            OnlineResult.Tracks(tracks ?: throw SourceFailure("The search returned no response."))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            OnlineResult.Failed(searchError(error))
        }
    }

    override suspend fun resolveStream(track: OnlineTrack): String? = resolvePage(track.pageUrl)?.url

    /** Media3 calls this on its loading thread, so resolution blocks that thread and never the UI. */
    internal fun resolveBlocking(pageUrl: String): YtdlResolvedStream? = runBlocking { resolvePage(pageUrl) }

    private suspend fun resolvePage(pageUrl: String): YtdlResolvedStream? {
        if (!isYtdlHttpUrl(pageUrl)) return null
        return try {
            var resolved: YtdlResolvedStream? = null
            runBridge(listOf("resolve", pageUrl), RESOLVE_TIMEOUT_MS) { line ->
                when (val message = parseYtdlGoMessage(line)) {
                    is YtdlGoMessage.Resolve -> {
                        val url = message.url
                        val contentLength = message.contentLength
                        val consentCookie = message.consentCookie
                        val mimeType = message.mimeType
                        val itag = message.itag
                        if (url == null || contentLength == null || consentCookie == null || mimeType == null || itag == null) {
                            throw SourceFailure("The playback response was incomplete.")
                        }
                        resolved = YtdlResolvedStream(url, contentLength, consentCookie, mimeType, itag)
                    }
                    is YtdlGoMessage.Error -> throw SourceFailure(message.message)
                    else -> throw SourceFailure("The playback response was invalid.")
                }
            }
            resolved
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun download(
        track: OnlineTrack,
        dir: String,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): String = withContext(Dispatchers.IO) {
        if (!isYtdlHttpUrl(track.pageUrl)) throw SourceFailure("This track has an invalid page address.")
        val folder = File(dir)
        if (!folder.isDirectory && (!folder.mkdirs() || !folder.isDirectory)) {
            throw SourceFailure("Couldn't create the downloads folder.")
        }
        val outputBase = File(folder, safeName(track.id))
        if (isCancelled()) throw DownloadCancelled()
        val stream = resolvePage(track.pageUrl) ?: throw SourceFailure("Couldn't resolve this track for download.")
        val path = YtdlDownloadService.download(
            context = context,
            trackId = track.id,
            title = track.title,
            outputBase = outputBase.absolutePath,
            stream = stream,
            isCancelled = isCancelled,
            onProgress = onProgress,
        )
        val output = validateOutput(folder, outputBase, path)
        if (isCancelled()) {
            output.delete()
            throw DownloadCancelled()
        }
        output.absolutePath
    }

    /** Runs only inside [YtdlDownloadService], after the stream was resolved and handed off. */
    internal suspend fun downloadResolved(
        outputBase: String,
        stream: YtdlResolvedStream,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): String = withContext(Dispatchers.IO) {
        val base = File(outputBase).canonicalFile
        val folder = base.parentFile ?: throw SourceFailure("The download folder is invalid.")
        if (!folder.isDirectory && (!folder.mkdirs() || !folder.isDirectory)) {
            throw SourceFailure("Couldn't create the downloads folder.")
        }
        val tempFolder = File(base.absolutePath + ".tmp")
        var reportedPath: String? = null
        var succeeded = false
        try {
            if (isCancelled()) throw DownloadCancelled()
            val streamJson = JSONObject()
                .put("url", stream.url)
                .put("content_length", stream.contentLength)
                .put("consent_cookie", stream.consentCookie)
                .put("mime_type", stream.mimeType)
                .put("itag", stream.itag)
                .toString()
            runBridge(
                listOf("download-resolved", base.absolutePath),
                DOWNLOAD_TIMEOUT_MS,
                isCancelled = isCancelled,
                stdin = streamJson,
            ) { line ->
                when (val message = parseYtdlGoMessage(line)) {
                    is YtdlGoMessage.Progress -> onProgress(message.value)
                    is YtdlGoMessage.Done -> reportedPath = message.path
                    is YtdlGoMessage.Error -> throw SourceFailure(message.message)
                    else -> throw SourceFailure("The download response was invalid.")
                }
            }
            val output = reportedPath?.let { validateOutput(folder, base, it) }
                ?: throw SourceFailure("The download finished without a file.")
            if (isCancelled()) {
                output.delete()
                throw DownloadCancelled()
            }
            succeeded = true
            output.absolutePath
        } finally {
            if (!succeeded) reportedPath?.let { runCatching { validateOutput(folder, base, it).delete() } }
            tempFolder.deleteRecursively()
        }
    }

    private suspend fun runBridge(
        args: List<String>,
        timeoutMs: Long,
        isCancelled: () -> Boolean = { false },
        stdin: String? = null,
        onLine: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        if (isCancelled()) throw DownloadCancelled()
        val executable = File(context.applicationInfo.nativeLibraryDir, BRIDGE_NAME)
        if (!executable.isFile || !executable.canExecute()) {
            throw SourceFailure("The bundled online source couldn't start. Reinstall the Plus edition.")
        }

        val process = try {
            ProcessBuilder(listOf(executable.absolutePath) + args).apply {
                environment()["SSL_CERT_DIR"] = ANDROID_CERT_DIRS
            }.start()
        } catch (error: IOException) {
            throw SourceFailure("The bundled online source couldn't start.")
        }
        try {
            if (stdin == null) {
                process.outputStream.close()
            } else {
                OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(stdin)
                    writer.write('\n'.code)
                }
            }
        } catch (error: IOException) {
            process.destroyForcibly()
            throw SourceFailure("The bundled online source couldn't receive the resolved stream.")
        }
        val lines = ArrayBlockingQueue<BridgeLine>(MAX_QUEUED_LINES)
        val stderr = StringBuilder()
        val readerError = AtomicReference<Throwable?>(null)
        val readerFinished = AtomicBoolean(false)

        val stdoutReader = Thread({
            try {
                BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
                    while (true) {
                        val line = readBoundedLine(reader) ?: break
                        var added = false
                        while (!added && !Thread.currentThread().isInterrupted) {
                            added = lines.offer(BridgeLine(line), 100, TimeUnit.MILLISECONDS)
                        }
                    }
                }
            } catch (error: Throwable) {
                readerError.set(error)
                process.destroyForcibly()
            } finally {
                readerFinished.set(true)
            }
        }, "om-ytdl-stdout").apply { isDaemon = true; start() }

        val stderrReader = Thread({
            process.errorStream.use { input ->
                val buffer = ByteArray(1024)
                while (true) {
                    val count = runCatching { input.read(buffer) }.getOrDefault(-1)
                    if (count <= 0) break
                    synchronized(stderr) {
                        val remaining = MAX_STDERR_CHARS - stderr.length
                        if (remaining > 0) stderr.append(String(buffer, 0, minOf(count, remaining), StandardCharsets.UTF_8))
                    }
                }
            }
        }, "om-ytdl-stderr").apply { isDaemon = true; start() }

        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                if (isCancelled()) throw DownloadCancelled()
                if (SystemClock.elapsedRealtime() >= deadline) throw SourceFailure("The online source request timed out.")

                lines.poll(100, TimeUnit.MILLISECONDS)?.let { onLine(it.text) }
                val exitCode = runCatching { process.exitValue() }.getOrNull()
                if (readerFinished.get() && lines.isEmpty() && exitCode != null) {
                    val readFailure = readerError.get()
                    if (readFailure != null) throw SourceFailure("The online source response was too large or unreadable.")
                    if (exitCode != 0) {
                        val detail = synchronized(stderr) { stderr.toString().trim() }
                        throw SourceFailure(detail.take(MAX_STDERR_CHARS).ifBlank { "The online source request failed." })
                    }
                    break
                }
            }
        } finally {
            if (process.isAlive) process.destroyForcibly()
            runCatching { process.outputStream.close() }
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            stdoutReader.interrupt()
            stderrReader.interrupt()
        }
    }

    private fun validateOutput(folder: File, base: File, path: String): File {
        val output = File(path).canonicalFile
        val parent = folder.canonicalFile
        if (output.parentFile != parent || !output.name.startsWith(base.name + ".") || !output.isFile || output.length() == 0L) {
            throw SourceFailure("The download finished without a valid audio file.")
        }
        return output
    }

    private fun searchError(error: Exception): String {
        val message = error.message.orEmpty()
        val lower = message.lowercase()
        return when {
            "429" in lower -> "The source is busy (rate limit). Wait a minute, then search again."
            "403" in lower || "blocked" in lower || "sign in" in lower || "bot" in lower ->
                "The source blocked this search. Try again later."
            "unknownhost" in (error::class.simpleName ?: "").lowercase() ||
                "connect" in (error::class.simpleName ?: "").lowercase() ||
                "socket" in (error::class.simpleName ?: "").lowercase() ->
                "You're offline. Connect to the internet and search again."
            error is SourceFailure -> "Couldn't search. ${message.take(MAX_STDERR_CHARS)}"
            else -> "Couldn't search. Check the connection and try again."
        }
    }

    private fun safeName(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun readBoundedLine(reader: BufferedReader): String? {
        val line = StringBuilder()
        while (true) {
            val value = reader.read()
            if (value == -1) return line.takeIf { it.isNotEmpty() }?.toString()
            if (value == '\n'.code) return line.toString().removeSuffix("\r")
            if (line.length >= MAX_LINE_CHARS) throw IOException("The source response line exceeded its limit")
            line.append(value.toChar())
        }
    }

    private class SourceFailure(message: String) : Exception(message)
    private data class BridgeLine(val text: String)

    private companion object {
        const val BRIDGE_NAME = "libytdlbridge.so"
        const val ANDROID_CERT_DIRS = "/apex/com.android.conscrypt/cacerts:/system/etc/security/cacerts"
        const val MAX_LINE_CHARS = 1_000_000
        const val MAX_QUEUED_LINES = 32
        const val MAX_STDERR_CHARS = 8_192
        const val SEARCH_TIMEOUT_MS = 45_000L
        const val RESOLVE_TIMEOUT_MS = 30_000L
        const val DOWNLOAD_TIMEOUT_MS = 3 * 60 * 60 * 1_000L
    }
}
