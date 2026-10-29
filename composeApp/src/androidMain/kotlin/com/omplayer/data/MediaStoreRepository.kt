package com.omplayer.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.omplayer.model.MediaFolder
import com.omplayer.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Library backed by MediaStore. [permissionRequester] is set by the Activity and launches the system dialog. */
class MediaStoreRepository(context: Context) : MediaRepository {
    private val ctx = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val handler = Handler(Looper.getMainLooper())

    var permissionRequester: (() -> Unit)? = null

    private val _state = MutableStateFlow<LibraryState>(if (hasAccess()) LibraryState.Loading else LibraryState.NeedsPermission)
    override val state: StateFlow<LibraryState> = _state

    private val refreshRunnable = Runnable { scope.launch { refresh() } }
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(refreshRunnable)
            handler.postDelayed(refreshRunnable, 800)
        }
    }

    init {
        ctx.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        ctx.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        if (hasAccess()) scope.launch { refresh() }
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    private fun hasVideo() = when {
        Build.VERSION.SDK_INT >= 34 -> granted(Manifest.permission.READ_MEDIA_VIDEO) || granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> granted(Manifest.permission.READ_MEDIA_VIDEO)
        else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun hasAudio() = if (Build.VERSION.SDK_INT >= 33) granted(Manifest.permission.READ_MEDIA_AUDIO)
    else granted(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun hasAccess() = hasVideo() || hasAudio()

    /** Permissions to request from the Activity for this API level. */
    fun permissionsToRequest(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Called by the Activity after the permission dialog closes. */
    fun onPermissionResult() { scope.launch { refresh() } }

    override fun requestAccess() { permissionRequester?.invoke() }

    override suspend fun refresh() {
        if (!hasAccess()) { _state.value = LibraryState.NeedsPermission; return }
        mutex.withLock {
            if (_state.value !is LibraryState.Ready) _state.value = LibraryState.Loading
            _state.value = try {
                val items = withContext(Dispatchers.IO) {
                    (if (hasVideo()) query(false) else emptyList()) + (if (hasAudio()) query(true) else emptyList())
                }
                LibraryState.Ready(group(items))
            } catch (e: SecurityException) {
                LibraryState.NeedsPermission
            } catch (e: Exception) {
                LibraryState.Failed(e.message ?: "Couldn't read your media library.")
            }
        }
    }

    private class Row(val folderId: String, val folderName: String, val path: String, val item: MediaItem)

    private fun group(rows: List<Row>): List<MediaFolder> =
        rows.groupBy { it.folderId }.map { (id, list) ->
            MediaFolder(id, list.first().folderName, list.first().path, list.map { it.item }.sortedBy { it.title.lowercase() })
        }.sortedBy { it.name.lowercase() }

    @Suppress("DEPRECATION")
    private fun query(audio: Boolean): List<Row> {
        val base: Uri = if (audio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val pathCol = if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        val cols = mutableListOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_ID, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION, MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED, pathCol,
        )
        if (!audio) { cols += MediaStore.MediaColumns.WIDTH; cols += MediaStore.MediaColumns.HEIGHT }
        else cols += MediaStore.Audio.AudioColumns.ARTIST
        val out = ArrayList<Row>()
        ctx.contentResolver.query(base, cols.toTypedArray(), null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val iBid = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val iBn = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val iDur = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iDate = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val iPath = c.getColumnIndexOrThrow(pathCol)
            val iW = if (audio) -1 else c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val iH = if (audio) -1 else c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val iArtist = if (audio) c.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST) else -1
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val uri = android.content.ContentUris.withAppendedId(base, id).toString()
                val name = c.getString(iName) ?: "Unknown"
                val bucket = c.getString(iBid) ?: "0"
                val raw = c.getString(iPath) ?: ""
                val path = if (Build.VERSION.SDK_INT >= 29) raw.trimEnd('/') else raw.substringBeforeLast('/', "")
                val item = MediaItem(
                    id = (if (audio) "a" else "v") + id,
                    uri = uri,
                    title = name.substringBeforeLast('.', name),
                    folderId = bucket,
                    durationMs = c.getLong(iDur),
                    sizeBytes = c.getLong(iSize),
                    width = if (iW >= 0) c.getInt(iW) else 0,
                    height = if (iH >= 0) c.getInt(iH) else 0,
                    dateAddedMs = c.getLong(iDate) * 1000,
                    isAudio = audio,
                    artist = if (iArtist >= 0) c.getString(iArtist)?.takeIf { it != "<unknown>" }.orEmpty() else "",
                )
                out += Row(bucket, c.getString(iBn) ?: "Unknown", path, item)
            }
        }
        return out
    }
}
