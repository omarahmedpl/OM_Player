package com.omplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.omplayer.model.MediaItem
import com.omplayer.theme.OmTheme
import com.omplayer.ui.library.LibraryRoot
import com.omplayer.ui.player.PlayerScreen

/** What's on screen. The player is an overlay above the library so the library keeps its scroll state. */
data class NowPlaying(val item: MediaItem, val queue: List<MediaItem>)

@Composable
fun App(deps: AppDeps) {
    var settings by remember { mutableStateOf(AppSettings()) }
    var nowPlaying by remember { mutableStateOf<NowPlaying?>(null) }

    CompositionLocalProvider(LocalAppDeps provides deps) {
        OmTheme(settings.theme) {
            Box(Modifier.fillMaxSize().background(OmTheme.colors.bg)) {
                LibraryRoot(
                    settings = settings,
                    onSettingsChange = { settings = it },
                    onPlay = { item, queue -> nowPlaying = NowPlaying(item, queue) },
                )
                nowPlaying?.let { np ->
                    PlayerScreen(
                        item = np.item,
                        queue = np.queue,
                        settings = settings,
                        onItemChange = { nowPlaying = np.copy(item = it) },
                        onClose = { nowPlaying = null },
                    )
                }
            }
        }
    }
}
