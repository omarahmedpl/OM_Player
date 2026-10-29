package com.omplayer.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omplayer.AppDeps
import com.omplayer.LocalAppDeps
import com.omplayer.data.OnlinePlaylist
import com.omplayer.data.OnlineResult
import com.omplayer.data.OnlineTrack
import com.omplayer.data.trackKey
import com.omplayer.data.toMediaItem
import com.omplayer.theme.OmRadius
import com.omplayer.theme.OmSpace
import com.omplayer.theme.OmTheme
import com.omplayer.ui.components.OmButton
import com.omplayer.ui.components.OmChip
import com.omplayer.ui.components.OmIconButton
import com.omplayer.ui.components.OmText
import com.omplayer.ui.components.OmTextField
import com.omplayer.ui.components.ThumbnailPlaceholder
import com.omplayer.ui.components.formatDuration
import com.omplayer.ui.components.pressable
import kotlinx.coroutines.delay

/**
 * Discover: its own destination, with its own favourites and playlists. Nothing here mixes with the
 * device library — an online song is not a file, and a device playlist is not a search result.
 */
@Composable
internal fun DiscoverHome(actions: ItemActions) {
    val deps = LocalAppDeps.current
    if (deps.sources.isEmpty()) return

    var tab by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<OnlinePlaylist?>(null) }
    var picker by remember { mutableStateOf<List<OnlineTrack>?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = contentPad())) {
        Row(Modifier.fillMaxWidth().padding(top = OmSpace.s2), horizontalArrangement = Arrangement.spacedBy(OmSpace.s2)) {
            OmChip("Search", tab == 0, { tab = 0; open = null })
            OmChip("Favourites", tab == 1, { tab = 1; open = null })
            OmChip("Playlists", tab == 2, { tab = 2 })
        }
        when (tab) {
            0 -> DiscoverSearch(actions, onAddToPlaylist = { picker = listOf(it) })
            1 -> OnlineFavourites(actions, onAddToPlaylist = { picker = it })
            else -> OnlinePlaylists(actions, open, { open = it }, onAddToPlaylist = { picker = it })
        }
    }

    picker?.let { tracks -> AddToPlaylistDialog(tracks, onDone = { picker = null }) }
}

/** Plays a whole Discover list: each entry keeps its page address and is resolved as it starts. */
private fun playOnline(deps: AppDeps, actions: ItemActions, tracks: List<OnlineTrack>, start: OnlineTrack) {
    if (tracks.isEmpty()) return
    // Kept so the queue can be rebuilt after a restart, when the search results are gone.
    deps.onlineTracks.rememberAll(tracks)
    val queue = tracks.map { it.toMediaItem() }
    actions.play(start.toMediaItem(), queue)
}

@Composable
private fun DiscoverSearch(actions: ItemActions, onAddToPlaylist: (OnlineTrack) -> Unit) {
    val c = OmTheme.colors
    val deps = LocalAppDeps.current
    val sources = deps.sources
    var sourceIndex by remember { mutableIntStateOf(0) }
    val source = sources[sourceIndex.coerceIn(0, sources.lastIndex)]
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<OnlineResult?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(source, query, retry) {
        result = null
        if (query.isNotBlank()) delay(450) // wait for a pause in typing
        result = if (query.isBlank()) source.trending() else source.search(query)
    }

    Column(Modifier.fillMaxSize()) {
        if (sources.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = OmSpace.s2), horizontalArrangement = Arrangement.spacedBy(OmSpace.s2)) {
                sources.forEachIndexed { i, s -> OmChip(s.name, i == sourceIndex, { sourceIndex = i; query = "" }) }
            }
        }
        OmTextField(
            query, { query = it }, Modifier.fillMaxWidth().padding(top = OmSpace.s2),
            placeholder = "Search ${source.name}", leading = Icons.Rounded.Search, description = "Search ${source.name}",
        )
        if (source.attribution.isNotBlank()) {
            OmText(source.attribution, color = c.text3, size = 12.sp, maxLines = 2, modifier = Modifier.padding(vertical = OmSpace.s2))
        }
        notice?.let { OmText(it, color = c.text2, size = 12.sp, maxLines = 3, modifier = Modifier.padding(bottom = OmSpace.s2)) }

        when (val r = result) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { OmText("Loading…", color = c.text3) }
            is OnlineResult.Failed -> FailedState(r.message) { retry++ }
            is OnlineResult.Tracks -> {
                val tracks = r.tracks
                if (tracks.isEmpty()) {
                    if (query.isBlank()) EmptyState(Icons.Rounded.MusicNote, "Find something to play", "Search for a song or an artist.")
                    else EmptyState(Icons.Rounded.MusicNote, "Nothing found", "Try another search.")
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = OmSpace.s6)) {
                        item {
                            OmText(
                                "${tracks.size} results", color = c.text3, size = 12.sp,
                                weight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = OmSpace.s1),
                            )
                        }
                        items(tracks, key = { trackKey(it) }) { t ->
                            OnlineTrackRow(
                                track = t,
                                onPlay = { playOnline(deps, actions, tracks, t) },
                                onAddToPlaylist = { onAddToPlaylist(t) },
                                onDownload = deps.downloads?.takeIf { t.canDownload }?.let { manager ->
                                    {
                                        val added = manager.enqueue(t)
                                        notice = if (added) "Downloading “${t.title}”" else "Already downloading or downloaded"
                                        if (added) manager.startQueued()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineFavourites(actions: ItemActions, onAddToPlaylist: (List<OnlineTrack>) -> Unit) {
    val deps = LocalAppDeps.current
    val favorites by deps.onlineLibrary.favorites.collectAsState()
    val tracks = remember(favorites) { favorites.mapNotNull { deps.onlineTracks.byId(it) } }

    if (tracks.isEmpty()) {
        EmptyState(Icons.Rounded.FavoriteBorder, "No favourites yet", "Tap the heart on a track to keep it here.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = OmSpace.s6)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = OmSpace.s2), verticalAlignment = Alignment.CenterVertically) {
                OmText("${tracks.size} favourites", color = OmTheme.colors.text3, size = 12.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                OmButton("Play all", { playOnline(deps, actions, tracks, tracks.first()) }, primary = false, icon = Icons.Rounded.PlayArrow)
                OmIconButton(Icons.Rounded.PlaylistAdd, "Add favourites to a playlist", { onAddToPlaylist(tracks) })
            }
        }
        items(tracks, key = { trackKey(it) }) { t ->
            OnlineTrackRow(
                track = t,
                onPlay = { playOnline(deps, actions, tracks, t) },
                onAddToPlaylist = { onAddToPlaylist(listOf(t)) },
            )
        }
    }
}

@Composable
private fun OnlinePlaylists(
    actions: ItemActions,
    open: OnlinePlaylist?,
    onOpen: (OnlinePlaylist?) -> Unit,
    onAddToPlaylist: (List<OnlineTrack>) -> Unit,
) {
    val c = OmTheme.colors
    val deps = LocalAppDeps.current
    val playlists by deps.onlineLibrary.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }

    if (open == null) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(vertical = OmSpace.s2), verticalAlignment = Alignment.CenterVertically) {
                OmText("Online playlists", color = c.text3, size = 12.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                OmButton("New playlist", { creating = true }, primary = false)
            }
            if (playlists.isEmpty()) {
                EmptyState(Icons.Rounded.PlaylistAdd, "No playlists yet", "Create one, then add songs from Search or Favourites.")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = OmSpace.s6)) {
                    items(playlists, key = { it.id }) { p ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 60.dp).pressable({ onOpen(p) }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OmText(p.name, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            OmText("${p.keys.size}", color = c.text3, size = 12.sp, tnum = true, modifier = Modifier.padding(end = OmSpace.s2))
                            OmIconButton(Icons.Rounded.Delete, "Delete ${p.name}", { deps.onlineLibrary.deletePlaylist(p.id) }, tint = c.text3)
                        }
                    }
                }
            }
        }
        if (creating) NewPlaylistDialog(onDone = { creating = false })
        return
    }

    // The list in the store is the truth: adding a track rebuilds this view from it.
    val current = playlists.firstOrNull { it.id == open.id } ?: open
    val tracks = remember(current, playlists) { current.keys.mapNotNull { deps.onlineTracks.byId(it) } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = OmSpace.s2), verticalAlignment = Alignment.CenterVertically) {
            OmIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back to playlists", { onOpen(null) })
            OmText(open.name, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (tracks.isNotEmpty()) {
                OmButton("Play all", { playOnline(deps, actions, tracks, tracks.first()) }, primary = false, icon = Icons.Rounded.PlayArrow)
            }
        }
        if (tracks.isEmpty()) {
            EmptyState(Icons.Rounded.MusicNote, "This playlist is empty", "Add songs from Search or Favourites.")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = OmSpace.s6)) {
                items(tracks, key = { trackKey(it) }) { t ->
                    OnlineTrackRow(
                        track = t,
                        onPlay = { playOnline(deps, actions, tracks, t) },
                        onAddToPlaylist = { onAddToPlaylist(listOf(t)) },
                        onRemove = { deps.onlineLibrary.removeFromPlaylist(open.id, trackKey(t)) },
                    )
                }
            }
        }
    }
}

/** One catalogue row: plays on tap, with heart, playlist and (where allowed) download actions. */
@Composable
private fun OnlineTrackRow(
    track: OnlineTrack,
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val c = OmTheme.colors
    val deps = LocalAppDeps.current
    val favorites by deps.onlineLibrary.favorites.collectAsState()
    val key = trackKey(track)
    val fav = key in favorites

    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).pressable(onPlay), verticalAlignment = Alignment.CenterVertically) {
        ThumbnailPlaceholder(track.id, Modifier.size(52.dp).clip(RoundedCornerShape(OmRadius.sm)), isAudio = true, coverPath = track.artworkUrl)
        Column(Modifier.weight(1f).padding(horizontal = OmSpace.s3)) {
            OmText(track.title, weight = FontWeight.Medium, maxLines = 2)
            OmText(
                listOfNotNull(track.artist.ifBlank { null }, track.durationMs.takeIf { it > 0 }?.let(::formatDuration)).joinToString(" · "),
                color = c.text3, size = 12.sp, tnum = true,
            )
        }
        OmIconButton(
            if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if (fav) "Remove ${track.title} from favourites" else "Add ${track.title} to favourites",
            {
                // Keeping the details means the favourite still works after a restart.
                deps.onlineTracks.remember(track)
                deps.onlineLibrary.toggleFavorite(key)
            },
            tint = if (fav) c.primaryHover else c.text3,
        )
        if (onRemove != null) OmIconButton(Icons.Rounded.Delete, "Remove ${track.title} from playlist", onRemove, tint = c.text3)
        OmIconButton(Icons.Rounded.PlaylistAdd, "Add ${track.title} to a playlist", onAddToPlaylist, tint = c.text3)
        if (onDownload != null) OmIconButton(Icons.Rounded.Download, "Download ${track.title}", onDownload, tint = c.text3)
    }
}

@Composable
private fun NewPlaylistDialog(onDone: () -> Unit) {
    val deps = LocalAppDeps.current
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        confirmButton = {
            OmButton("Create", {
                deps.onlineLibrary.createPlaylist(name)
                onDone()
            })
        },
        dismissButton = { OmButton("Cancel", onDone, primary = false) },
        title = { OmText("New playlist", weight = FontWeight.SemiBold) },
        text = { OmTextField(name, { name = it }, placeholder = "Playlist name", description = "Playlist name") },
    )
}

@Composable
private fun AddToPlaylistDialog(tracks: List<OnlineTrack>, onDone: () -> Unit) {
    val c = OmTheme.colors
    val deps = LocalAppDeps.current
    val playlists by deps.onlineLibrary.playlists.collectAsState()
    var name by remember { mutableStateOf("") }
    val keys = remember(tracks) { tracks.map(::trackKey) }

    AlertDialog(
        onDismissRequest = onDone,
        confirmButton = { OmButton("Done", onDone, primary = false) },
        title = { OmText("Add to playlist", weight = FontWeight.SemiBold) },
        text = {
            Column {
                if (playlists.isEmpty()) {
                    OmText("No playlists yet. Name one below.", color = c.text3, size = 12.sp, modifier = Modifier.padding(bottom = OmSpace.s2))
                }
                playlists.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).pressable({
                            deps.onlineLibrary.addToPlaylist(p.id, keys)
                            onDone()
                        }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OmText(p.name, modifier = Modifier.weight(1f), maxLines = 1)
                        OmText("${p.keys.size}", color = c.text3, size = 12.sp, tnum = true)
                    }
                }
                OmTextField(name, { name = it }, Modifier.padding(top = OmSpace.s2), placeholder = "New playlist", description = "New playlist name")
                OmButton("Create and add", {
                    val created = deps.onlineLibrary.createPlaylist(name)
                    deps.onlineLibrary.addToPlaylist(created.id, keys)
                    onDone()
                }, primary = false)
            }
        },
    )
}
