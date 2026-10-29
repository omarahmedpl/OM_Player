package com.omplayer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omplayer.LocalAppDeps
import com.omplayer.data.LyricsParser
import com.omplayer.model.MediaItem
import com.omplayer.theme.OmSpace
import com.omplayer.theme.OmTheme
import com.omplayer.ui.components.OmButton
import com.omplayer.ui.components.pressable

/** Lyrics in place of the cover. Synced lyrics highlight the current line and follow the song; tap a line to jump to it. */
@Composable
internal fun LyricsView(item: MediaItem, positionMs: Long, onSeek: (Long) -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val c = OmTheme.colors
    val saved by LocalAppDeps.current.userData.lyrics.collectAsState()
    val text = saved[item.id].orEmpty()
    if (text.isBlank()) {
        Column(modifier.fillMaxSize().padding(OmSpace.s4), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("No lyrics yet", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Find them online or paste your own.", color = c.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = OmSpace.s2))
            OmButton("Add lyrics", onEdit)
        }
        return
    }
    val lines = remember(text) { LyricsParser.parse(text) }
    val current = LyricsParser.currentIndex(lines, positionMs)
    val state = rememberLazyListState()
    LaunchedEffect(current) { if (current >= 0) state.animateScrollToItem((current - 2).coerceAtLeast(0)) }
    LazyColumn(modifier.fillMaxSize(), state = state, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = OmSpace.s6)) {
        itemsIndexed(lines) { i, line ->
            val t = line.timeMs
            Box(
                Modifier.fillMaxWidth().then(if (t != null) Modifier.pressable({ onSeek(t) }) else Modifier).padding(vertical = 6.dp, horizontal = OmSpace.s2),
            ) {
                Text(
                    line.text.ifBlank { " " },
                    color = when { current < 0 -> c.text; i == current -> c.text; else -> c.text3 },
                    fontSize = if (i == current) 24.sp else 20.sp,
                    fontWeight = if (i == current || current < 0) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}
