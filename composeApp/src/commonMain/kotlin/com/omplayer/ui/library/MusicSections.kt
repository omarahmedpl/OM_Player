package com.omplayer.ui.library

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omplayer.LocalAppDeps
import com.omplayer.model.MediaFolder
import com.omplayer.model.MediaItem
import com.omplayer.theme.OmRadius
import com.omplayer.theme.OmSpace
import com.omplayer.theme.OmTheme
import com.omplayer.ui.components.OmButton
import com.omplayer.ui.components.OmIconButton
import com.omplayer.ui.components.OmText
import com.omplayer.ui.components.ThumbnailPlaceholder
import com.omplayer.ui.components.pressable

internal enum class GroupKind(val label: String) { Artist("Artists"), Album("Albums"), Folder("Folders") }

/** A place songs live: an artist, an album or a folder. */
internal class SongGroup(val key: String, val title: String, val subtitle: String, val items: List<MediaItem>)

internal fun groupsOf(kind: GroupKind, audio: List<MediaItem>, folders: List<MediaFolder>): List<SongGroup> = when (kind) {
    GroupKind.Artist -> audio.groupBy { it.artist.ifBlank { "Unknown artist" } }
        .map { (name, list) -> SongGroup(name, name, songCount(list.size), list) }
    GroupKind.Album -> audio.groupBy { it.album.ifBlank { "Unknown album" } }
        .map { (name, list) ->
            val artists = list.map { it.artist }.filter { it.isNotBlank() }.distinct()
            SongGroup(name, name, listOfNotNull(artists.singleOrNull() ?: if (artists.size > 1) "Various artists" else null, songCount(list.size)).joinToString(" · "), list)
        }
    GroupKind.Folder -> folders.mapNotNull { f ->
        val songs = f.items.filter { it.isAudio }
        if (songs.isEmpty()) null else SongGroup(f.id, f.name, songCount(songs.size), songs)
    }
}.sortedBy { it.title.lowercase() }

private fun songCount(n: Int) = "$n ${if (n == 1) "song" else "songs"}"

/** Plays [items] in random order, starting anywhere. */
@Composable
internal fun rememberShufflePlay(actions: ItemActions): (List<MediaItem>) -> Unit {
    val engine = LocalAppDeps.current.engine
    return { items -> if (items.isNotEmpty()) { engine.setShuffle(true); actions.play(items.random(), items) } }
}

@Composable
internal fun GroupList(kind: GroupKind, groups: List<SongGroup>, actions: ItemActions, onOpen: (SongGroup) -> Unit) {
    val c = OmTheme.colors
    val shuffle = rememberShufflePlay(actions)
    if (groups.isEmpty()) {
        EmptyState(
            when (kind) { GroupKind.Artist -> Icons.Rounded.Person; GroupKind.Album -> Icons.Rounded.Album; GroupKind.Folder -> Icons.Rounded.Folder },
            "No ${kind.label.lowercase()} yet", "Music on your device shows up here.",
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = contentPad()), contentPadding = PaddingValues(vertical = OmSpace.s2)) {
        items(groups, key = { it.key }) { g ->
            val cover = g.items.firstOrNull { it.coverPath.isNotBlank() } ?: g.items.first()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).pressable({ onOpen(g) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(kind, cover)
                Column(Modifier.weight(1f).padding(horizontal = OmSpace.s3)) {
                    OmText(g.title, weight = FontWeight.Medium)
                    OmText(g.subtitle, color = c.text3, size = 12.sp, tnum = true)
                }
                OmIconButton(Icons.Rounded.Shuffle, "Shuffle ${g.title}", { shuffle(g.items) })
            }
        }
    }
}

@Composable
private fun Box(kind: GroupKind, cover: MediaItem) {
    val shape = if (kind == GroupKind.Artist) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(OmRadius.sm)
    ThumbnailPlaceholder(cover.id, Modifier.size(52.dp).clip(shape), isAudio = true, uri = cover.uri, coverPath = cover.coverPath)
}

/** One artist, album or folder: shuffle and play at the top, then its songs. */
@Composable
internal fun GroupDetail(kind: GroupKind, group: SongGroup, prefs: ListPrefs, actions: ItemActions) {
    val c = OmTheme.colors
    val engine = LocalAppDeps.current.engine
    val shuffle = rememberShufflePlay(actions)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = contentPad(), vertical = OmSpace.s2),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OmSpace.s1),
        ) {
            Column(Modifier.weight(1f)) {
                OmText(group.title, size = 18.sp, weight = FontWeight.SemiBold)
                OmText(group.subtitle, color = c.text3, size = 12.sp, tnum = true)
            }
            OmIconButton(Icons.Rounded.Shuffle, "Shuffle ${group.title}", { shuffle(group.items) }, tint = c.primary)
            OmButton("Play", { engine.setShuffle(false); actions.play(group.items.first(), group.items) }, icon = Icons.Rounded.PlayArrow)
        }
        CompositionLocalProvider(LocalScrollKey provides "music/${kind.name}/${group.key}") {
            ItemList(group.items, prefs, actions, Modifier.fillMaxSize())
        }
    }
}
