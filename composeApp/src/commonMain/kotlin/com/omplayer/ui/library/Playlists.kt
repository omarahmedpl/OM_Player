package com.omplayer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.CompositionLocalProvider
import com.omplayer.SharedPlaylist
import com.omplayer.ShareFormat
import com.omplayer.theme.OmRadius
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omplayer.LocalAppDeps
import com.omplayer.PlatformBackHandler
import com.omplayer.data.Playlist
import com.omplayer.data.PlaylistExport
import com.omplayer.model.MediaItem
import com.omplayer.theme.OmSpace
import com.omplayer.theme.OmTheme
import com.omplayer.ui.components.OmButton
import com.omplayer.ui.components.OmChip
import com.omplayer.ui.components.OmIconButton
import com.omplayer.ui.components.OmText
import com.omplayer.ui.components.OmTextField
import com.omplayer.ui.components.pressable

/** Rows to add [itemIds] to an existing playlist, or to start a new one. Shared by the library dialog and the player sheet. */
@Composable
internal fun PlaylistPicker(playlists: List<Playlist>, containing: Set<String>, onPick: (Playlist) -> Unit, onCreate: (String) -> Unit) {
    val c = OmTheme.colors
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = OmSpace.s4), verticalArrangement = Arrangement.spacedBy(OmSpace.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OmSpace.s2)) {
            OmTextField(name, { name = it }, Modifier.weight(1f), placeholder = "New playlist", description = "New playlist name", onSubmit = { if (name.isNotBlank()) { onCreate(name); name = "" } })
            OmIconButton(Icons.Rounded.Add, "Create playlist", { onCreate(name); name = "" })
        }
        if (playlists.isEmpty()) OmText("No playlists yet. Name one above to create it.", color = c.text3, size = 13.sp, maxLines = 2)
        playlists.forEach { p ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).pressable({ onPick(p) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlaylistPlay, null, tint = c.text2, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f).padding(horizontal = OmSpace.s3)) {
                    OmText(p.name, weight = FontWeight.Medium)
                    OmText("${p.itemIds.size} ${if (p.itemIds.size == 1) "track" else "tracks"}", color = c.text3, size = 12.sp, tnum = true)
                }
                if (p.id in containing) Icon(Icons.Rounded.Check, "Already in this playlist", tint = c.primary)
            }
        }
    }
}

@Composable
internal fun PlaylistPickerDialog(items: List<MediaItem>, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val c = OmTheme.colors
    val lib = LocalAppDeps.current.userData
    val playlists by lib.playlists.collectAsState()
    val ids = items.map { it.id }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface2,
        title = { OmText("Add to playlist", weight = FontWeight.SemiBold, size = 17.sp) },
        text = {
            PlaylistPicker(
                playlists, playlists.filter { p -> ids.all { it in p.itemIds } }.map { it.id }.toSet(),
                onPick = { p -> lib.addToPlaylist(p.id, ids); onDone("Added to ${p.name}") },
                onCreate = { n -> val p = lib.createPlaylist(n, ids); onDone("Added to ${p.name}") },
            )
        },
        confirmButton = { OmButton("Close", onDismiss, primary = false) },
    )
}

/** Music destination: Songs, Favourites and Playlists. */
@Composable
internal fun MusicHome(all: List<MediaItem>, prefs: ListPrefs, actions: ItemActions) {
    val deps = LocalAppDeps.current
    val lib = deps.userData
    val favorites by lib.favorites.collectAsState()
    val playlists by lib.playlists.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var openId by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var shareTarget by remember { mutableStateOf<List<Playlist>?>(null) }
    var deleteTarget by remember { mutableStateOf<List<Playlist>?>(null) }
    val listState = rememberLazyListState()
    val audio = remember(all) { all.filter { it.isAudio } }
    val byId = remember(audio) { audio.associateBy { it.id } }

    PlatformBackHandler(enabled = openId != null) { openId = null }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = contentPad()).padding(top = OmSpace.s1), horizontalArrangement = Arrangement.spacedBy(OmSpace.s2)) {
            listOf("Songs", "Favourites", "Playlists").forEachIndexed { i, label ->
                OmChip(label, tab == i, { tab = i; openId = null; selected = emptySet() })
            }
        }
        Spacer(Modifier.size(OmSpace.s1))
        Column(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalScrollKey provides "music/$tab") {
                when (tab) {
                    0 -> MusicContent(all, prefs, actions)
                    1 -> {
                        val favs = audio.filter { it.id in favorites }
                        if (favs.isEmpty()) EmptyState(Icons.Rounded.Favorite, "No favourites yet", "Tap the heart in the player to keep a track here.")
                        else ItemList(favs, prefs, actions, Modifier.fillMaxSize())
                    }
                    else -> {
                        val open = playlists.firstOrNull { it.id == openId }
                        if (open == null) PlaylistList(
                            playlists, selected, listState,
                            onOpen = { openId = it.id },
                            onToggle = { p -> selected = if (p.id in selected) selected - p.id else selected + p.id },
                            onSelectAll = { selected = playlists.map { it.id }.toSet() },
                            onClear = { selected = emptySet() },
                            onImport = {
                                deps.platform.pickTextFile { text ->
                                    val lists = text?.let { PlaylistExport.parse(it) }
                                    if (lists == null) { if (text != null) actions.notice("That file isn't an OM Player playlist") }
                                    else {
                                        val r = lib.importPlaylists(lists, audio)
                                        actions.notice(
                                            if (r.playlists == 0) "None of those songs are on this device"
                                            else "Imported ${r.playlists} ${if (r.playlists == 1) "playlist" else "playlists"} · ${r.matched} of ${r.total} songs found",
                                        )
                                    }
                                }
                            },
                            onShare = { shareTarget = playlists.filter { it.id in selected } },
                            onDelete = { deleteTarget = playlists.filter { it.id in selected } },
                        ) else PlaylistDetail(
                            open, byId, actions,
                            onShare = { shareTarget = listOf(open) },
                            onDelete = { deleteTarget = listOf(open) },
                        )
                    }
                }
            }
        }
    }

    shareTarget?.let { target ->
        ShareFormatDialog(
            count = target.size,
            onPick = { format ->
                shareTarget = null
                val shared = target.map { p -> SharedPlaylist(p.name, p.itemIds.mapNotNull { byId[it]?.title }) }
                if (deps.platform.sharePlaylists(shared, format)) selected = emptySet() else actions.notice("Sharing isn't available here")
            },
            onDismiss = { shareTarget = null },
        )
    }
    deleteTarget?.let { target ->
        ConfirmDialog(
            title = if (target.size == 1) "Delete \"${target[0].name}\"?" else "Delete ${target.size} playlists?",
            body = "Only the playlist is removed. The songs stay on your device.",
            confirmLabel = "Delete",
            onConfirm = {
                target.forEach { lib.deletePlaylist(it.id) }
                if (openId in target.map { it.id }) openId = null
                selected = emptySet()
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun ShareFormatDialog(count: Int, onPick: (ShareFormat) -> Unit, onDismiss: () -> Unit) {
    val c = OmTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface2,
        title = { OmText(if (count == 1) "Share playlist as" else "Share $count playlists as", weight = FontWeight.SemiBold, size = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(OmSpace.s2)) {
                OmText("Both include the playlist name and song titles.", color = c.text2, size = 13.sp, maxLines = 2)
                OmButton("Image (PNG)", { onPick(ShareFormat.Image) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Image)
                OmButton("JSON file", { onPick(ShareFormat.Json) }, Modifier.fillMaxWidth(), primary = false, icon = Icons.Rounded.DataObject)
            }
        },
        confirmButton = { OmButton("Cancel", onDismiss, primary = false) },
    )
}

@Composable
private fun PlaylistList(
    playlists: List<Playlist>,
    selected: Set<String>,
    listState: LazyListState,
    onOpen: (Playlist) -> Unit,
    onToggle: (Playlist) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onImport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = OmTheme.colors
    val lib = LocalAppDeps.current.userData
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val selecting = selected.isNotEmpty()
    PlatformBackHandler(enabled = selecting) { onClear() }
    Column(Modifier.fillMaxSize()) {
        if (selecting) Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).background(c.surface1).padding(horizontal = OmSpace.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OmIconButton(Icons.Rounded.Close, "Cancel selection", onClear)
            OmText("${selected.size} selected", size = 17.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = OmSpace.s2))
            OmIconButton(Icons.Rounded.SelectAll, "Select all playlists", onSelectAll)
            OmIconButton(Icons.Rounded.Share, "Share selected playlists", onShare)
            OmIconButton(Icons.Rounded.Delete, "Delete selected playlists", onDelete, tint = c.danger)
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = contentPad()), state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = OmSpace.s6),
        ) {
            item {
                if (creating) Row(Modifier.padding(vertical = OmSpace.s2), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OmSpace.s2)) {
                    OmTextField(name, { name = it }, Modifier.weight(1f), placeholder = "Playlist name", description = "Playlist name",
                        onSubmit = { lib.createPlaylist(name); name = ""; creating = false })
                    OmButton("Create", { lib.createPlaylist(name); name = ""; creating = false })
                } else if (!selecting) Row(Modifier.padding(vertical = OmSpace.s2), horizontalArrangement = Arrangement.spacedBy(OmSpace.s2)) {
                    OmButton("New playlist", { creating = true }, primary = false, icon = Icons.Rounded.Add)
                    OmButton("Import", onImport, primary = false, icon = Icons.Rounded.FileOpen)
                }
            }
            if (playlists.isEmpty()) item {
                OmText("Playlists you create show up here.", color = c.text3, size = 13.sp, modifier = Modifier.padding(vertical = OmSpace.s3))
            }
            items(playlists, key = { it.id }) { p ->
                val on = p.id in selected
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp)
                        .then(if (on) Modifier.background(c.primarySoft, RoundedCornerShape(OmRadius.md)) else Modifier)
                        .pressable({ if (selecting) onToggle(p) else onOpen(p) }, onLongClick = { onToggle(p) })
                        .padding(horizontal = if (on) OmSpace.s2 else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.PlaylistPlay, null, tint = c.primary, modifier = Modifier.size(28.dp))
                    Column(Modifier.weight(1f).padding(horizontal = OmSpace.s3)) {
                        OmText(p.name, weight = FontWeight.Medium)
                        OmText("${p.itemIds.size} ${if (p.itemIds.size == 1) "track" else "tracks"}", color = c.text3, size = 12.sp, tnum = true)
                    }
                    if (selecting) SelectBadge(on)
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetail(playlist: Playlist, byId: Map<String, MediaItem>, actions: ItemActions, onShare: () -> Unit, onDelete: () -> Unit) {
    val c = OmTheme.colors
    val deps = LocalAppDeps.current
    val lib = deps.userData
    val tracks = playlist.itemIds.mapNotNull { byId[it] }
    val rowActions = remember(actions, playlist.id) {
        ItemActions(
            actions.play, actions.info, actions.notice, actions.selected, actions.selectable, actions.toggle,
            onRemove = { lib.removeFromPlaylist(playlist.id, it.id) },
        )
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = contentPad(), vertical = OmSpace.s2), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OmSpace.s1)) {
            Column(Modifier.weight(1f)) {
                OmText(playlist.name, size = 18.sp, weight = FontWeight.SemiBold)
                OmText("${tracks.size} ${if (tracks.size == 1) "track" else "tracks"}", color = c.text3, size = 12.sp, tnum = true)
            }
            OmIconButton(Icons.Rounded.Share, "Share playlist", onShare)
            OmIconButton(Icons.Rounded.Delete, "Delete playlist", onDelete, tint = c.danger)
        }
        if (tracks.isEmpty()) {
            EmptyState(Icons.Rounded.PlaylistPlay, "Empty playlist", "Long-press songs in the Songs tab, then use Add to playlist.")
            return
        }
        Row(Modifier.padding(horizontal = contentPad()), horizontalArrangement = Arrangement.spacedBy(OmSpace.s2)) {
            OmButton("Play", { deps.engine.setShuffle(false); actions.play(tracks.first(), tracks) }, icon = Icons.Rounded.PlayArrow)
            OmButton("Shuffle", { deps.engine.setShuffle(true); actions.play(tracks.random(), tracks) }, primary = false, icon = Icons.Rounded.Shuffle)
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = contentPad()), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = OmSpace.s2)) {
            items(tracks, key = { it.id }) { t -> MediaCell(t, tracks, false, rowActions) }
        }
    }
}
