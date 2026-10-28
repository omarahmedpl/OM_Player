package com.omplayer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omplayer.LocalAppDeps
import com.omplayer.model.MediaItem
import com.omplayer.model.SortKey
import com.omplayer.theme.OmRadius
import com.omplayer.theme.OmSpace
import com.omplayer.theme.OmTheme
import com.omplayer.ui.components.MetaChip
import com.omplayer.ui.components.OmButton
import com.omplayer.ui.components.OmIconButton
import com.omplayer.ui.components.OmText
import com.omplayer.ui.components.ProgressBar
import com.omplayer.ui.components.ThumbnailPlaceholder
import com.omplayer.ui.components.formatDuration
import com.omplayer.ui.components.formatSize
import com.omplayer.ui.components.pressable
import com.omplayer.ui.components.staggerIn

internal data class ListPrefs(val sort: SortKey = SortKey.Name, val asc: Boolean = true, val grid: Boolean = false)

internal class ItemActions(
    val play: (MediaItem, List<MediaItem>) -> Unit,
    val info: (MediaItem) -> Unit,
    val notice: (String) -> Unit,
)

internal fun sortItems(items: List<MediaItem>, p: ListPrefs): List<MediaItem> {
    val sorted = when (p.sort) {
        SortKey.Name -> items.sortedBy { it.title.lowercase() }
        SortKey.Date -> items.sortedBy { it.dateAddedMs }
        SortKey.Size -> items.sortedBy { it.sizeBytes }
        SortKey.Duration -> items.sortedBy { it.durationMs }
    }
    return if (p.asc) sorted else sorted.asReversed()
}

internal fun sortLabel(k: SortKey) = when (k) {
    SortKey.Name -> "Name"; SortKey.Date -> "Date added"; SortKey.Size -> "Size"; SortKey.Duration -> "Duration"
}

/** Sort menu + grid/list toggle. */
@Composable
internal fun ViewControls(prefs: ListPrefs, onPrefs: (ListPrefs) -> Unit) {
    val c = OmTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        OmIconButton(Icons.Rounded.SwapVert, "Sort", { open = true })
        DropdownMenu(open, { open = false }, modifier = Modifier.background(c.surface2)) {
            SortKey.entries.forEach { k ->
                val on = prefs.sort == k
                DropdownMenuItem(
                    text = { OmText(sortLabel(k), color = if (on) c.primary else c.text) },
                    trailingIcon = if (on) ({
                        Icon(if (prefs.asc) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, null, tint = c.primary, modifier = Modifier.size(18.dp))
                    }) else null,
                    onClick = {
                        onPrefs(if (on) prefs.copy(asc = !prefs.asc) else prefs.copy(sort = k, asc = k == SortKey.Name))
                        open = false
                    },
                )
            }
        }
    }
    OmIconButton(
        if (prefs.grid) Icons.Rounded.ViewList else Icons.Rounded.GridView,
        if (prefs.grid) "Switch to list view" else "Switch to grid view",
        { onPrefs(prefs.copy(grid = !prefs.grid)) },
    )
}

@Composable
internal fun contentPad(): Dp = if (LocalAppDeps.current.platform.formFactor == com.omplayer.FormFactor.Desktop) OmSpace.s6 else OmSpace.s4

internal fun gridCells(grid: Boolean): GridCells = if (grid) GridCells.Adaptive(170.dp) else GridCells.Fixed(1)

internal fun LazyGridScope.fullSpan(key: Any? = null, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

/** A sortable list/grid of items; tapping plays with `queue` as the playlist. */
@Composable
internal fun ItemList(
    items: List<MediaItem>,
    prefs: ListPrefs,
    actions: ItemActions,
    modifier: Modifier = Modifier,
    header: (LazyGridScope.() -> Unit)? = null,
) {
    val sorted = remember(items, prefs.sort, prefs.asc) { sortItems(items, prefs) }
    val pad = contentPad()
    LazyVerticalGrid(
        columns = gridCells(prefs.grid),
        modifier = modifier,
        contentPadding = PaddingValues(start = pad, end = pad, top = OmSpace.s2, bottom = OmSpace.s6),
        horizontalArrangement = Arrangement.spacedBy(OmSpace.s3),
        verticalArrangement = Arrangement.spacedBy(if (prefs.grid) OmSpace.s4 else OmSpace.s1),
    ) {
        header?.invoke(this)
        itemsIndexed(sorted, key = { _, it -> it.id }) { i, item ->
            MediaCell(item, sorted, prefs.grid, actions, Modifier.staggerIn(i))
        }
    }
}

@Composable
internal fun MediaCell(item: MediaItem, queue: List<MediaItem>, grid: Boolean, actions: ItemActions, modifier: Modifier = Modifier) {
    val deps = LocalAppDeps.current
    val c = OmTheme.colors
    val pos = deps.progress.positionMs(item.id)
    val frac = if (pos != null && item.durationMs > 0) pos.toFloat() / item.durationMs else 0f
    val meta = listOfNotNull(
        item.resolutionLabel.takeIf { it.isNotEmpty() },
        item.sizeBytes.takeIf { it > 0 }?.let(::formatSize),
    ).joinToString(" · ")

    @Composable
    fun thumb(m: Modifier) = ThumbnailPlaceholder(item.id, m.clip(RoundedCornerShape(OmRadius.sm)), item.isAudio, uri = item.uri) {
        if (item.durationMs > 0) MetaChip(formatDuration(item.durationMs), Modifier.align(Alignment.BottomEnd).padding(4.dp))
        if (frac > 0f) ProgressBar(frac, Modifier.align(Alignment.BottomStart))
    }

    if (grid) {
        Column(modifier.fillMaxWidth().pressable({ actions.play(item, queue) }, role = androidx.compose.ui.semantics.Role.Button)) {
            thumb(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Spacer(Modifier.height(OmSpace.s2))
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    OmText(item.title, weight = FontWeight.Medium, maxLines = 2)
                    if (meta.isNotEmpty()) OmText(meta, color = c.text3, size = 12.sp, tnum = true)
                }
                ItemMenu(item, queue, actions)
            }
        }
    } else {
        Row(
            modifier.fillMaxWidth().heightIn(min = 72.dp).pressable({ actions.play(item, queue) }).padding(vertical = OmSpace.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            thumb(Modifier.width(120.dp).aspectRatio(16f / 9f))
            Spacer(Modifier.width(OmSpace.s3))
            Column(Modifier.weight(1f)) {
                OmText(item.title, weight = FontWeight.Medium, maxLines = 2)
                if (meta.isNotEmpty()) OmText(meta, color = c.text3, size = 12.sp, tnum = true)
            }
            ItemMenu(item, queue, actions)
        }
    }
}


@Composable
internal fun ItemMenu(item: MediaItem, queue: List<MediaItem>, actions: ItemActions) {
    val c = OmTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        OmIconButton(Icons.Rounded.MoreVert, "More options for ${item.title}", { open = true })
        DropdownMenu(open, { open = false }, modifier = Modifier.background(c.surface2)) {
            DropdownMenuItem(
                text = { OmText("Play") },
                leadingIcon = { Icon(Icons.Rounded.PlayArrow, null, tint = c.text2) },
                onClick = { open = false; actions.play(item, queue) },
            )
            DropdownMenuItem(
                text = { OmText("Play as audio") },
                leadingIcon = { Icon(Icons.Rounded.Headphones, null, tint = c.text2) },
                onClick = { open = false; actions.notice("Audio-only playback is coming soon") },
            )
            DropdownMenuItem(
                text = { OmText("Info") },
                leadingIcon = { Icon(Icons.Rounded.Info, null, tint = c.text2) },
                onClick = { open = false; actions.info(item) },
            )
        }
    }
}

@Composable
internal fun InfoDialog(item: MediaItem, folderPath: String?, onDismiss: () -> Unit) {
    val c = OmTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface2,
        title = { OmText(item.title, maxLines = 3, weight = FontWeight.SemiBold, size = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(OmSpace.s2)) {
                val rows = listOf(
                    "Folder" to folderPath,
                    "Duration" to item.durationMs.takeIf { it > 0 }?.let(::formatDuration),
                    "Resolution" to item.resolutionLabel.takeIf { it.isNotEmpty() }?.let { r -> "$r (${item.width}×${item.height})" },
                    "Size" to item.sizeBytes.takeIf { it > 0 }?.let(::formatSize),
                    "Location" to item.uri,
                )
                rows.forEach { (k, v) ->
                    if (!v.isNullOrBlank()) Column {
                        OmText(k, color = c.text3, size = 12.sp)
                        OmText(v, tnum = true, maxLines = 3)
                    }
                }
            }
        },
        confirmButton = { OmButton("Close", onDismiss, primary = false) },
    )
}
