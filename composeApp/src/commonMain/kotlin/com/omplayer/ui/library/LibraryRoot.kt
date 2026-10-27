package com.omplayer.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omplayer.AppSettings
import com.omplayer.LocalAppDeps
import com.omplayer.data.LibraryState
import com.omplayer.model.MediaItem
import com.omplayer.theme.OmTheme
import com.omplayer.ui.OmWordmark

/**
 * Library shell. CONTRACT (called from App.kt): signature must stay as-is.
 * Placeholder body; replaced by the library agent (home, folders, search, sort, settings, network).
 */
@Composable
fun LibraryRoot(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onPlay: (item: MediaItem, queue: List<MediaItem>) -> Unit,
) {
    val repo = LocalAppDeps.current.repository
    val state by repo.state.collectAsState()
    LaunchedEffect(Unit) { repo.refresh() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OmWordmark()
        when (val s = state) {
            LibraryState.Loading -> Text("Scanning…", color = OmTheme.colors.text2)
            LibraryState.NeedsPermission -> Text("Allow access to your videos", color = OmTheme.colors.text2, modifier = Modifier.clickable { repo.requestAccess() })
            is LibraryState.Failed -> Text(s.message, color = OmTheme.colors.danger)
            is LibraryState.Ready -> LazyColumn {
                s.folders.forEach { f ->
                    items(f.items, key = { it.id }) { item ->
                        Text(item.title, color = OmTheme.colors.text, modifier = Modifier.clickable { onPlay(item, f.items) }.padding(vertical = 12.dp))
                    }
                }
            }
        }
    }
}
