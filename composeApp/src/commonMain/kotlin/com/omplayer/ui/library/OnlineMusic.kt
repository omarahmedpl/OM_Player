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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.omplayer.LocalAppDeps
import com.omplayer.LocalImage
import com.omplayer.data.OnlineResult
import com.omplayer.data.toMediaItem
import com.omplayer.theme.OmRadius
import com.omplayer.theme.OmSpace
import com.omplayer.theme.OmTheme
import com.omplayer.ui.components.OmText
import com.omplayer.ui.components.OmTextField
import com.omplayer.ui.components.ThumbnailPlaceholder
import com.omplayer.ui.components.formatDuration
import com.omplayer.ui.components.pressable
import kotlinx.coroutines.delay

/** Discover: trending and searchable music from an online catalogue that allows streaming (Audius). */
@Composable
internal fun OnlineMusicScreen(actions: ItemActions) {
    val c = OmTheme.colors
    val source = LocalAppDeps.current.online
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<OnlineResult?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(query, retry) {
        result = null
        if (query.isNotBlank()) delay(450) // wait for a pause in typing
        result = if (query.isBlank()) source.trending() else source.search(query)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = contentPad())) {
        OmTextField(
            query, { query = it }, Modifier.fillMaxWidth().padding(top = OmSpace.s2),
            placeholder = "Search ${source.name}", leading = Icons.Rounded.Search, description = "Search ${source.name}",
        )
        if (source.attribution.isNotBlank()) {
            OmText(source.attribution, color = c.text3, size = 12.sp, maxLines = 2, modifier = Modifier.padding(vertical = OmSpace.s2))
        }
        when (val r = result) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { OmText("Loading…", color = c.text3) }
            is OnlineResult.Failed -> FailedState(r.message) { retry++ }
            is OnlineResult.Tracks -> {
                val items = remember(r) { r.tracks.map { it.toMediaItem("audius") } }
                if (items.isEmpty()) EmptyState(Icons.Rounded.MusicNote, "Nothing found", "Try another search.")
                else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = OmSpace.s6)) {
                    item {
                        OmText(
                            if (query.isBlank()) "Trending" else "${items.size} results", color = c.text3, size = 12.sp,
                            weight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = OmSpace.s1),
                        )
                    }
                    items(items, key = { it.id }) { t ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp).pressable({ actions.play(t, items) }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ThumbnailPlaceholder(t.id, Modifier.size(52.dp).clip(RoundedCornerShape(OmRadius.sm)), isAudio = true, coverPath = t.coverPath)
                            Column(Modifier.weight(1f).padding(horizontal = OmSpace.s3)) {
                                OmText(t.title, weight = FontWeight.Medium)
                                OmText(
                                    listOfNotNull(t.artist.ifBlank { null }, t.durationMs.takeIf { it > 0 }?.let(::formatDuration)).joinToString(" · "),
                                    color = c.text3, size = 12.sp, tnum = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
