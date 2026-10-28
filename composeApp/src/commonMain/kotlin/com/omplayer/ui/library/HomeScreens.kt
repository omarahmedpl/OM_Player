package com.omplayer.ui.library

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omplayer.LocalAppDeps
import com.omplayer.model.MediaFolder
import com.omplayer.model.MediaItem
import com.omplayer.theme.OmMotion
import com.omplayer.theme.OmRadius
import com.omplayer.theme.OmSpace
import com.omplayer.theme.OmTheme
import com.omplayer.ui.components.MetaChip
import com.omplayer.ui.components.OmButton
import com.omplayer.ui.components.OmText
import com.omplayer.ui.components.ProgressBar
import com.omplayer.ui.components.SectionHeader
import com.omplayer.ui.components.ThumbnailPlaceholder
import com.omplayer.ui.components.formatDuration
import com.omplayer.ui.components.formatSize
import com.omplayer.ui.components.pressable
import com.omplayer.ui.components.staggerIn

/** Videos in a folder (audio belongs to the Music tab). */
internal fun MediaFolder.videos(): List<MediaItem> = items.filter { !it.isAudio }

// ---------- states ----------

@Composable
internal fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = OmTheme.colors
    Column(
        modifier.fillMaxSize().padding(OmSpace.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(OmRadius.lg)).background(c.surface2), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.text3, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(OmSpace.s4))
        OmText(title, size = 18.sp, weight = FontWeight.SemiBold, maxLines = 2)
        Spacer(Modifier.height(OmSpace.s1))
        androidx.compose.material3.Text(body, color = c.text2, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = OmSpace.s4))
        if (action != null) {
            Spacer(Modifier.height(OmSpace.s5))
            action()
        }
    }
}

@Composable
internal fun LoadingSkeleton() {
    val c = OmTheme.colors
    val a by rememberInfiniteTransition(label = "skeleton").animateFloat(
        0.45f, 1f, infiniteRepeatable(tween(900, easing = OmMotion.EaseOut), RepeatMode.Reverse), label = "a",
    )
    val pad = contentPad()
    Column(Modifier.fillMaxSize().padding(horizontal = pad, vertical = OmSpace.s4).graphicsLayer { alpha = a }, verticalArrangement = Arrangement.spacedBy(OmSpace.s3)) {
        Box(Modifier.width(140.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(c.surface2))
        Row(horizontalArrangement = Arrangement.spacedBy(OmSpace.s3)) {
            repeat(2) { Box(Modifier.width(200.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(OmRadius.md)).background(c.surface2)) }
        }
        Spacer(Modifier.height(OmSpace.s2))
        repeat(5) {
            Row(horizontalArrangement = Arrangement.spacedBy(OmSpace.s3), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(OmRadius.sm)).background(c.surface2))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(180.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(c.surface2))
                    Box(Modifier.width(100.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(c.surface2))
                }
            }
        }
    }
}

@Composable
internal fun PermissionState(onAllow: () -> Unit) {
    EmptyState(
        Icons.Rounded.Lock, "Allow access to your videos",
        "OM Player only reads video and audio files on your device. Nothing leaves your device and there are no ads or accounts.",
        action = { OmButton("Allow access", onAllow) },
    )
}

@Composable
internal fun FailedState(message: String, onRetry: () -> Unit) {
    EmptyState(
        Icons.Rounded.ErrorOutline, "Couldn't load your library", message,
        action = { OmButton("Try again", onRetry, icon = Icons.Rounded.Refresh) },
    )
}

// ---------- home ----------

@Composable
internal fun HomeContent(
    folders: List<MediaFolder>,
    prefs: ListPrefs,
    showContinue: Boolean,
    actions: ItemActions,
    onOpenFolder: (MediaFolder) -> Unit,
    onRefresh: () -> Unit,
) {
    val deps = LocalAppDeps.current
    val videoFolders = remember(folders) { folders.filter { folder -> folder.items.any { !it.isAudio } } }
    if (videoFolders.isEmpty()) {
        EmptyState(
            Icons.Rounded.VideoLibrary, "No videos yet", "Add video files to your device, then refresh.",
            action = { OmButton("Refresh", onRefresh, primary = false, icon = Icons.Rounded.Refresh) },
        )
        return
    }
    val all = remember(videoFolders) { videoFolders.flatMap { folder -> folder.items.filter { !it.isAudio } } }
    val byId = remember(all) { all.associateBy { it.id } }
    val recent = if (showContinue) {
        deps.progress.recentIds().mapNotNull { byId[it] }
    } else emptyList()
    val pad = contentPad()
    LazyVerticalGrid(
        columns = gridCells(prefs.grid),
        contentPadding = PaddingValues(start = pad, end = pad, top = OmSpace.s2, bottom = OmSpace.s6),
        horizontalArrangement = Arrangement.spacedBy(OmSpace.s3),
        verticalArrangement = Arrangement.spacedBy(if (prefs.grid) OmSpace.s3 else OmSpace.s1),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (recent.isNotEmpty()) {
            fullSpan("continue-header") { SectionHeader("Continue watching", Modifier.padding(top = OmSpace.s2)) }
            fullSpan("continue-row") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(OmSpace.s3)) {
                    items(recent.size, key = { recent[it].id }) { i ->
                        ContinueCard(recent[i], all, actions, Modifier.staggerIn(i))
                    }
                }
            }
        }
        fullSpan("folders-header") { SectionHeader("Folders", trailing = "${videoFolders.size}") }
        items(videoFolders.size, key = { videoFolders[it].id }) { i ->
            FolderCell(videoFolders[i], prefs.grid, { onOpenFolder(videoFolders[i]) }, Modifier.staggerIn(i))
        }
    }
}

@Composable
private fun ContinueCard(item: MediaItem, queue: List<MediaItem>, actions: ItemActions, modifier: Modifier) {
    val c = OmTheme.colors
    val pos = LocalAppDeps.current.progress.positionMs(item.id) ?: 0L
    val frac = if (item.durationMs > 0) pos.toFloat() / item.durationMs else 0f
    val left = ((item.durationMs - pos).coerceAtLeast(0) + 59_999) / 60_000
    Column(modifier.width(220.dp).pressable({ actions.play(item, queue) })) {
        ThumbnailPlaceholder(item.id, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(OmRadius.md)), uri = item.uri) {
            if (item.durationMs > 0) MetaChip(formatDuration(item.durationMs), Modifier.align(Alignment.BottomEnd).padding(6.dp))
            ProgressBar(frac, Modifier.align(Alignment.BottomStart), height = 4.dp)
        }
        Spacer(Modifier.height(OmSpace.s2))
        OmText(item.title, weight = FontWeight.Medium)
        OmText("${maxOf(left, 1)} min left", color = c.text3, size = 12.sp, tnum = true)
    }
}

@Composable
private fun FolderCell(folder: MediaFolder, grid: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = OmTheme.colors
    val vids = folder.videos()
    val sub = "${vids.size} ${if (vids.size == 1) "video" else "videos"} · ${formatSize(vids.sumOf { it.sizeBytes })}"
    val icon = @Composable {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(OmRadius.sm)).background(c.primarySoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Folder, null, tint = c.primary, modifier = Modifier.size(24.dp))
        }
    }
    val body = @Composable { mod: Modifier ->
        Column(mod) {
            OmText(folder.name, weight = FontWeight.Medium)
            OmText(sub, color = c.text3, size = 12.sp, tnum = true)
        }
    }
    if (grid) {
        Column(
            modifier.fillMaxWidth().clip(RoundedCornerShape(OmRadius.md)).background(c.surface1)
                .pressable(onClick).padding(OmSpace.s3),
            verticalArrangement = Arrangement.spacedBy(OmSpace.s3),
        ) { icon(); body(Modifier) }
    } else {
        Row(
            modifier.fillMaxWidth().heightIn(min = 60.dp).pressable(onClick).padding(vertical = OmSpace.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) { icon(); Spacer(Modifier.width(OmSpace.s3)); body(Modifier.weight(1f)) }
    }
}

// ---------- folder / search / music ----------

@Composable
internal fun FolderContent(folder: MediaFolder, prefs: ListPrefs, actions: ItemActions) {
    ItemList(folder.videos(), prefs, actions, Modifier.fillMaxSize())
}

@Composable
internal fun SearchResults(query: String, all: List<MediaItem>, prefs: ListPrefs, actions: ItemActions) {
    val hits = all.filter { it.title.contains(query.trim(), ignoreCase = true) }
    if (hits.isEmpty()) {
        EmptyState(Icons.Rounded.VideoLibrary, "No results", "Nothing matches \"${query.trim()}\".")
    } else {
        ItemList(hits, prefs, actions, Modifier.fillMaxSize(), header = {
            fullSpan("search-count") { OmText("${hits.size} ${if (hits.size == 1) "result" else "results"}", color = OmTheme.colors.text3, size = 12.sp, tnum = true, modifier = Modifier.padding(vertical = OmSpace.s2)) }
        })
    }
}

@Composable
internal fun MusicContent(all: List<MediaItem>, prefs: ListPrefs, actions: ItemActions) {
    val audio = all.filter { it.isAudio }
    if (audio.isEmpty()) {
        EmptyState(Icons.Rounded.MusicNote, "No music found", "Audio files on your device show up here.")
    } else {
        ItemList(audio, prefs, actions, Modifier.fillMaxSize())
    }
}
