package com.omplayer

import com.omplayer.theme.OmMotion
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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

/**
 * @param openRequests items to open straight in the player: Android ACTION_VIEW intents,
 * desktop drag-and-drop and command-line paths. Each emission replaces what's playing.
 */
@Composable
fun App(deps: AppDeps, openRequests: Flow<MediaItem> = emptyFlow()) {
    var settings by remember { mutableStateOf(AppSettings()) }
    var nowPlaying by remember { mutableStateOf<NowPlaying?>(null) }
    LaunchedEffect(openRequests) {
        openRequests.collect { nowPlaying = NowPlaying(it, listOf(it)) }
    }

    CompositionLocalProvider(LocalAppDeps provides deps) {
        OmTheme(settings.theme) {
            Box(Modifier.fillMaxSize().background(OmTheme.colors.bg)) {
                LibraryRoot(
                    settings = settings,
                    onSettingsChange = { settings = it },
                    onPlay = { item, queue -> nowPlaying = NowPlaying(item, queue) },
                )
                // Player opens with a short fade + 0.98→1 scale; closes faster (exit < enter).
                AnimatedVisibility(
                    visible = nowPlaying != null,
                    enter = fadeIn(tween(OmMotion.Base, easing = OmMotion.EaseOut)) +
                        scaleIn(tween(OmMotion.Base, easing = OmMotion.EaseOut), initialScale = 0.98f),
                    exit = fadeOut(tween(OmMotion.Fast, easing = OmMotion.EaseOut)),
                ) {
                    // Keep the last item while the exit animation runs.
                    val last = remember { arrayOfNulls<NowPlaying>(1) }
                    nowPlaying?.let { last[0] = it }
                    last[0]?.let { np ->
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
}
