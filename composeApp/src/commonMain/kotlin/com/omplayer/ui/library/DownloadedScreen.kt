package com.omplayer.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omplayer.LocalAppDeps
import com.omplayer.data.DownloadJob
import com.omplayer.data.DownloadState
import com.omplayer.data.toMediaItems
import com.omplayer.theme.OmRadius
import com.omplayer.theme.OmSpace
import com.omplayer.theme.OmTheme
import com.omplayer.ui.components.OmButton
import com.omplayer.ui.components.OmIconButton
import com.omplayer.ui.components.OmText
import com.omplayer.ui.components.ThumbnailPlaceholder
import com.omplayer.ui.components.formatDuration
import com.omplayer.ui.components.pressable

/** Downloaded: everything kept on the device, playable with no network at all. */
@Composable
internal fun DownloadedScreen(actions: ItemActions) {
    val c = OmTheme.colors
    val deps = LocalAppDeps.current
    val manager = deps.downloads ?: return
    val records by manager.library.collectAsState()
    val jobs by manager.jobs.collectAsState()
    var notice by remember { mutableStateOf<String?>(null) }
    val items = remember(records) { records.toMediaItems() }

    val active = jobs.filter { it.state is DownloadState.Queued || it.state is DownloadState.Running || it.state is DownloadState.Failed }

    Column(Modifier.fillMaxSize().padding(horizontal = contentPad())) {
        Row(Modifier.fillMaxWidth().padding(top = OmSpace.s2), verticalAlignment = Alignment.CenterVertically) {
            OmText(
                "${records.size} ${if (records.size == 1) "download" else "downloads"} · ${formatSize(manager.library.value.sumOf { it.sizeBytes })}",
                color = c.text3, size = 12.sp, tnum = true, modifier = Modifier.weight(1f),
            )
            if (records.isNotEmpty()) {
                OmButton("Delete all", {
                    val removed = manager.clearAll()
                    removed.forEach { deps.platform.deleteFile(it.filePath) }
                    notice = "Deleted ${removed.size} ${if (removed.size == 1) "download" else "downloads"}"
                }, primary = false)
            }
        }
        notice?.let { OmText(it, color = c.text2, size = 12.sp, maxLines = 2, modifier = Modifier.padding(top = OmSpace.s2)) }

        active.forEach { job ->
            DownloadRow(job, onCancel = { manager.cancel(job.track.id) }, onRetry = {
                manager.retry(job.track.id)
                manager.startQueued()
            })
        }

        if (items.isEmpty() && active.isEmpty()) {
            EmptyState(
                Icons.Rounded.DownloadDone, "No downloads yet",
                "Tap the download button on a track in Discover to keep it on the device.",
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = OmSpace.s6)) {
                items(items, key = { it.id }) { item ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).pressable({ actions.play(item, items) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ThumbnailPlaceholder(item.id, Modifier.size(52.dp).clip(RoundedCornerShape(OmRadius.sm)), isAudio = true, coverPath = item.coverPath)
                        Column(Modifier.weight(1f).padding(horizontal = OmSpace.s3)) {
                            OmText(item.title, weight = FontWeight.Medium, maxLines = 2)
                            OmText(
                                listOfNotNull(item.artist.ifBlank { null }, item.durationMs.takeIf { it > 0 }?.let(::formatDuration))
                                    .joinToString(" · "),
                                color = c.text3, size = 12.sp, tnum = true,
                            )
                        }
                        OmIconButton(Icons.Rounded.Delete, "Delete ${item.title}", {
                            manager.delete(item.id.removePrefix("download:"))?.let { deps.platform.deleteFile(it.filePath) }
                            notice = "Deleted “${item.title}”"
                        }, tint = c.text3)
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(job: DownloadJob, onCancel: () -> Unit, onRetry: () -> Unit) {
    val c = OmTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = OmSpace.s2)) {
            OmText(job.track.title, weight = FontWeight.Medium, maxLines = 1)
            val line = when (val s = job.state) {
                is DownloadState.Queued -> "Waiting to download"
                is DownloadState.Running -> "Downloading ${(s.progress * 100).toInt()}%"
                is DownloadState.Failed -> s.message
                is DownloadState.Done -> "Downloaded"
                DownloadState.Cancelled -> "Cancelled"
            }
            OmText(line, color = if (job.state is DownloadState.Failed) c.text2 else c.text3, size = 12.sp, maxLines = 2)
        }
        when (job.state) {
            is DownloadState.Failed -> OmButton("Retry", onRetry, primary = false)
            is DownloadState.Running, is DownloadState.Queued -> OmIconButton(Icons.Rounded.Close, "Cancel download", onCancel, tint = c.text3)
            else -> Unit
        }
    }
}

/** Storage sizes, kept short enough for a one-line header. */
internal fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "0 MB"
    bytes < 1024 * 1024 -> "${(bytes / 1024).coerceAtLeast(1)} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${(bytes / (1024.0 * 1024 * 1024) * 10).toLong() / 10.0} GB"
}
