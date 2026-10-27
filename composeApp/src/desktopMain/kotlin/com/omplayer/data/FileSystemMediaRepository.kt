package com.omplayer.data

import com.omplayer.model.MediaFolder
import com.omplayer.model.MediaItem
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class FileSystemMediaRepository(
    roots: List<File> = listOf("Videos", "Movies", "Downloads", "Music").map { File(System.getProperty("user.home"), it) },
    private val maxDepth: Int = 4,
) : MediaRepository {
    private val defaultRoots = roots
    private val extraFile = File(AppDirs.config, "folders.txt")
    private val extraRoots = LinkedHashSet<File>().apply {
        runCatching { if (extraFile.exists()) extraFile.readLines().filter { it.isNotBlank() }.forEach { add(File(it)) } }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    override val state: StateFlow<LibraryState> = _state

    init { scope.launch { refresh() } }

    override suspend fun refresh() = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val roots = (defaultRoots + extraRoots).filter { it.isDirectory }.distinct()
                val seen = HashSet<String>()
                val items = ArrayList<MediaItem>()
                roots.forEach { scan(it, 0, seen, items) }
                val folders = items.groupBy { it.folderId }.map { (dir, list) ->
                    MediaFolder(dir, File(dir).name.ifEmpty { dir }, dir, list.sortedBy { it.title.lowercase() })
                }.sortedBy { it.name.lowercase() }
                _state.value = LibraryState.Ready(folders)
            } catch (e: Exception) {
                _state.value = LibraryState.Failed(e.message ?: "Scan failed")
            }
        }
    }

    private fun scan(dir: File, depth: Int, seen: MutableSet<String>, out: MutableList<MediaItem>) {
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                if (depth < maxDepth) scan(f, depth + 1, seen, out)
            } else {
                val ext = f.extension.lowercase()
                val audio = ext in AUDIO
                if (!audio && ext !in VIDEO) continue
                val path = f.absolutePath
                if (!seen.add(path)) continue
                val attrs = runCatching { Files.readAttributes(f.toPath(), BasicFileAttributes::class.java) }.getOrNull()
                out += MediaItem(
                    id = path, uri = path, title = f.nameWithoutExtension, folderId = f.parentFile?.absolutePath ?: "",
                    sizeBytes = attrs?.size() ?: f.length(),
                    dateAddedMs = attrs?.creationTime()?.toMillis()?.takeIf { it > 0 } ?: f.lastModified(),
                    isAudio = audio,
                )
            }
        }
    }

    override fun requestAccess() {
        SwingUtilities.invokeLater {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "Add a media folder"
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                extraRoots += chooser.selectedFile
                runCatching { extraFile.writeText(extraRoots.joinToString("\n") { it.absolutePath }) }
                scope.launch { refresh() }
            }
        }
    }

    companion object {
        val VIDEO = setOf("mkv", "mp4", "avi", "mov", "webm", "m4v", "ts", "flv", "wmv", "mpg", "mpeg", "3gp")
        val AUDIO = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav")
        val ALL get() = VIDEO + AUDIO
    }
}
