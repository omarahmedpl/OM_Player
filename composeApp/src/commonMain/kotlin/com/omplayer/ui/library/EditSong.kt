package com.omplayer.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omplayer.LocalAppDeps
import com.omplayer.data.LyricsResult
import com.omplayer.data.SongEdit
import com.omplayer.model.MediaItem
import com.omplayer.theme.OmRadius
import com.omplayer.theme.OmSpace
import com.omplayer.theme.OmTheme
import com.omplayer.ui.components.OmButton
import com.omplayer.ui.components.OmText
import com.omplayer.ui.components.OmTextField
import com.omplayer.ui.components.ThumbnailPlaceholder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Edit how a song is shown in OM Player: title, artist, cover, and lyrics (typed, pasted, or found online).
 * Changes are kept in the app; the audio file itself isn't modified.
 */
@Composable
internal fun EditSongDialog(item: MediaItem, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val c = OmTheme.colors
    val deps = LocalAppDeps.current
    val lib = deps.userData
    val scope = rememberCoroutineScope()
    val savedLyrics by lib.lyrics.collectAsState()
    var title by remember { mutableStateOf(item.title) }
    var artist by remember { mutableStateOf(item.artist) }
    var cover by remember { mutableStateOf(item.coverPath) }
    var lyrics by remember { mutableStateOf(savedLyrics[item.id].orEmpty()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var coverBusy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface2,
        title = { OmText("Edit song", weight = FontWeight.SemiBold, size = 17.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(OmSpace.s3)) {
                OmTextField(title, { title = it }, Modifier.fillMaxWidth(), label = "Title", placeholder = "Title")
                OmTextField(artist, { artist = it }, Modifier.fillMaxWidth(), label = "Artist", placeholder = "Artist")
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OmSpace.s3)) {
                    ThumbnailPlaceholder(item.id, Modifier.size(72.dp).clip(RoundedCornerShape(OmRadius.md)), isAudio = true, uri = item.uri, coverPath = cover)
                    Column(verticalArrangement = Arrangement.spacedBy(OmSpace.s2)) {
                        OmButton("Choose cover", { deps.platform.pickImage { p -> if (p != null) cover = p else status = "No image was chosen" } }, primary = false, icon = Icons.Rounded.Image)
                        if (cover.isNotBlank()) OmButton("Remove cover", { cover = "" }, primary = false)
                    }
                }
                OmButton(
                    if (coverBusy) "Searching…" else "Find cover online",
                    {
                        scope.launch {
                            coverBusy = true; status = null
                            val bytes = deps.covers.find(title, artist, item.album)
                            val saved = bytes?.let { withContext(Dispatchers.IO) { deps.platform.saveCoverImage(it) } }
                            if (saved != null) { cover = saved; status = "Cover found" }
                            else status = if (bytes == null) "No cover found online. You can choose an image instead." else "Couldn't use that image."
                            coverBusy = false
                        }
                    },
                    Modifier.fillMaxWidth(), primary = false, icon = Icons.Rounded.Search, enabled = !coverBusy && title.isNotBlank(),
                )
                Spacer(Modifier.height(OmSpace.s1))
                OmTextField(
                    lyrics, { lyrics = it }, Modifier.fillMaxWidth(), label = "Lyrics",
                    placeholder = "Paste lyrics here, or find them online. Lines like [01:23.45] make them follow the song.",
                    multiline = true,
                )
                OmButton(
                    if (busy) "Searching…" else "Find lyrics online",
                    {
                        scope.launch {
                            busy = true; status = null
                            when (val r = deps.lyrics.find(title, artist, item.album, item.durationMs)) {
                                is LyricsResult.Found -> { lyrics = r.lyrics.best.orEmpty(); status = if (r.lyrics.synced != null) "Found synced lyrics" else "Found lyrics" }
                                LyricsResult.NotFound -> status = "No lyrics found. You can paste them above."
                                is LyricsResult.Failed -> status = r.message
                            }
                            busy = false
                        }
                    },
                    Modifier.fillMaxWidth(), primary = false, icon = Icons.Rounded.Search, enabled = !busy && title.isNotBlank(),
                )
                status?.let { OmText(it, color = c.text2, size = 13.sp, maxLines = 3) }
                OmText("Changes apply inside OM Player. The audio file itself isn't changed. Covers come from MusicBrainz and the Cover Art Archive, lyrics from LRCLIB.", color = c.text3, size = 12.sp, maxLines = 2)
            }
        },
        confirmButton = {
            OmButton("Save", {
                lib.setEdit(item.id, SongEdit(title.trim(), artist.trim(), cover))
                lib.setLyrics(item.id, lyrics)
                deps.engine.updateItem(item.copy(title = title.trim().ifBlank { item.title }, artist = artist.trim(), coverPath = cover))
                onDone("Saved")
            })
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(OmSpace.s2)) {
                OmButton("Cancel", onDismiss, primary = false)
            }
        },
    )
}
