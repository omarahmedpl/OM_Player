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
import com.omplayer.ui.player.AudioNowPlaying
import com.omplayer.ui.player.PlayerScreen
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay

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
    var audioExpanded by remember { mutableStateOf(false) }
    val phone = deps.platform.formFactor == FormFactor.Phone
    val engineState by deps.engine.state.collectAsState()
    // The engine is the single source of truth: audio is "active" whenever it holds an audio item (also after Activity recreation).
    val audioActive = phone && engineState.item?.isAudio == true

    // Phones play audio in the audio-first player (shared engine + queue); video and desktop use the video player.
    fun play(item: MediaItem, queue: List<MediaItem>) {
        if (phone && item.isAudio) {
            val q = queue.filter { it.isAudio }.ifEmpty { listOf(item) }
            val start = if (settings.resumePlayback) deps.progress.positionMs(item.id) ?: 0L else 0L
            nowPlaying = null
            deps.engine.openQueue(q, q.indexOfFirst { it.id == item.id }.coerceAtLeast(0), start)
            deps.platform.onAudioPlaybackStarted()
            audioExpanded = true
        } else nowPlaying = NowPlaying(item, queue)
    }
    LaunchedEffect(openRequests) {
        openRequests.collect { play(it, listOf(it)) }
    }
    LaunchedEffect(audioActive) { if (!audioActive) audioExpanded = false }
    if (phone) AudioProgressSaver(deps)

    CompositionLocalProvider(LocalAppDeps provides deps) {
        OmTheme(settings.theme) {
            Box(Modifier.fillMaxSize().background(OmTheme.colors.bg)) {
                LibraryRoot(
                    settings = settings,
                    onSettingsChange = { settings = it },
                    onPlay = ::play,
                    onOpenAudioPlayer = { audioExpanded = true },
                )
                AnimatedVisibility(
                    visible = audioExpanded && audioActive,
                    enter = slideInVertically(tween(OmMotion.Sheet, easing = OmMotion.EaseDrawer)) { it } + fadeIn(tween(OmMotion.Fast)),
                    exit = slideOutVertically(tween(OmMotion.Base, easing = OmMotion.EaseDrawer)) { it } + fadeOut(tween(OmMotion.Fast)),
                ) {
                    AudioNowPlaying(onCollapse = { audioExpanded = false })
                }
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

/**
 * Keeps resume points for audio. Video saves its own from the player screen. Runs while the app UI is alive;
 * a track that ended naturally is saved at its end, which clears the resume point.
 */
@Composable
private fun AudioProgressSaver(deps: AppDeps) {
    LaunchedEffect(deps) {
        var id: String? = null
        var pos = 0L
        var dur = 0L
        var playing = false
        var lastSave = 0L
        deps.engine.state.collect { s ->
            val item = s.item?.takeIf { it.isAudio }
            if (item == null) { id = null; playing = false; return@collect }
            val previous = id
            if (previous != null && previous != item.id) deps.progress.save(previous, pos, dur)
            if (playing && !s.isPlaying) deps.progress.save(item.id, s.positionMs, s.durationMs)
            id = item.id; pos = s.positionMs; dur = s.durationMs; playing = s.isPlaying
            if (s.isPlaying && s.positionMs - lastSave !in -5_000L..5_000L) {
                lastSave = s.positionMs
                deps.progress.save(item.id, s.positionMs, s.durationMs)
            }
        }
    }
}
