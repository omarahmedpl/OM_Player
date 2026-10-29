package com.omplayer.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omplayer.LocalAppDeps
import com.omplayer.PlatformBackHandler
import com.omplayer.data.LibraryState
import com.omplayer.model.MediaItem
import com.omplayer.player.PlayerState
import com.omplayer.player.RepeatMode
import com.omplayer.ui.library.PlaylistPicker
import com.omplayer.theme.DarkColors
import com.omplayer.theme.OmMotion
import com.omplayer.theme.OmRadius
import com.omplayer.theme.OmScheme
import com.omplayer.theme.OmSpace
import com.omplayer.theme.OmTheme
import com.omplayer.ui.components.OmIconButton
import com.omplayer.ui.components.OmText
import com.omplayer.ui.components.ProgressBar
import com.omplayer.ui.components.ThumbnailPlaceholder
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState

@OptIn(kotlin.time.ExperimentalTime::class)
private fun nowMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

private enum class AudioPanel { Speed, Sleep, Queue, Playlists }

private fun MediaItem.subtitle(fallback: String): String = artist.ifBlank { fallback }

/** Full-screen audio player. Reads and drives the one shared engine; no video chrome. Always dark, like the video player. */
@Composable
fun AudioNowPlaying(onCollapse: () -> Unit) {
    OmScheme(DarkColors) { AudioNowPlayingContent(onCollapse) }
}

@Composable
private fun AudioNowPlayingContent(onCollapse: () -> Unit) {
    val engine = LocalAppDeps.current.engine
    val c = OmTheme.colors
    val st by engine.state.collectAsState()
    val item = st.item ?: return
    var panel by remember { mutableStateOf<AudioPanel?>(null) }
    var sleepMinutes by remember { mutableStateOf<Int?>(null) }

    PlatformBackHandler { if (panel != null) panel = null else onCollapse() }

    val hasNext = st.hasNext

    // Drag the cover (or the top bar) down to minimize, like the button: the screen follows the finger, then either
    // minimizes past the threshold / on a fast flick, or settles back.
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { 140.dp.toPx() }
    val dragState = rememberDraggableState { d -> scope.launch { offset.snapTo((offset.value + d).coerceAtLeast(0f)) } }
    val dragToMinimize = Modifier.draggable(
        dragState, Orientation.Vertical,
        onDragStopped = { v ->
            if (offset.value > threshold || v > 1800f) onCollapse()
            else offset.animateTo(0f, tween(OmMotion.Fast, easing = OmMotion.EaseOut))
        },
    )
    // Restrained artwork motion: it settles slightly smaller while paused.
    val artScale by animateFloatAsState(if (st.isPlaying) 1f else 0.92f, tween(OmMotion.Sheet, easing = OmMotion.EaseOut))

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { translationY = offset.value; alpha = 1f - (offset.value / (size.height * 1.5f)).coerceIn(0f, 0.5f) }
            .background(c.bg),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val wide = maxWidth > maxHeight
            Column(Modifier.fillMaxSize().padding(horizontal = OmSpace.s4)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).then(dragToMinimize), verticalAlignment = Alignment.CenterVertically) {
                    OmIconButton(Icons.Rounded.KeyboardArrowDown, "Minimize player", onCollapse, tint = c.text)
                    Text(
                        "NOW PLAYING", color = c.text3, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                    OmIconButton(Icons.Rounded.QueueMusic, "Queue", { panel = AudioPanel.Queue }, tint = c.text)
                }
                if (wide) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(OmSpace.s6), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(item, artScale, Modifier.fillMaxHeight(0.85f).aspectRatio(1f).then(dragToMinimize))
                        Controls(st, item, hasNext, sleepMinutes, { panel = it }, Modifier.weight(1f))
                    }
                } else {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.weight(1f).fillMaxWidth().then(dragToMinimize), contentAlignment = Alignment.Center) {
                            Artwork(item, artScale, Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f))
                        }
                        Controls(st, item, hasNext, sleepMinutes, { panel = it }, Modifier.padding(bottom = OmSpace.s6))
                    }
                }
            }
        }

        when (panel) {
            AudioPanel.Speed -> PanelHost(false, "Speed", { panel = null }) { SpeedPanel(st.speed) { engine.setSpeed(it) } }
            AudioPanel.Sleep -> PanelHost(false, "Sleep timer", { panel = null }) {
                SleepPanel(st, sleepMinutes) { minutes, atEnd ->
                    sleepMinutes = minutes
                    engine.setSleepTimer(minutes, atEnd)
                }
            }
            AudioPanel.Queue -> PanelHost(false, "Up next", { panel = null }) {
                QueuePanel(st) { engine.skipTo(it) }
            }
            AudioPanel.Playlists -> PanelHost(false, "Add to playlist", { panel = null }) {
                val lib = LocalAppDeps.current.userData
                val lists by lib.playlists.collectAsState()
                PlaylistPicker(
                    lists, lists.filter { item.id in it.itemIds }.map { it.id }.toSet(),
                    onPick = { p -> lib.addToPlaylist(p.id, listOf(item.id)); panel = null },
                    onCreate = { n -> lib.createPlaylist(n, listOf(item.id)); panel = null },
                )
            }
            null -> {}
        }
    }
}

@Composable
private fun Artwork(item: MediaItem, scale: Float, modifier: Modifier) {
    Box(modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(OmRadius.xl))) {
        ThumbnailPlaceholder(item.id, Modifier.fillMaxSize(), isAudio = true, uri = item.uri)
    }
}

@Composable
private fun Controls(
    st: PlayerState,
    item: MediaItem,
    hasNext: Boolean,
    sleepMinutes: Int?,
    openPanel: (AudioPanel) -> Unit,
    modifier: Modifier,
) {
    val engine = LocalAppDeps.current.engine
    val c = OmTheme.colors
    val library by LocalAppDeps.current.repository.state.collectAsState()
    val folder = (library as? LibraryState.Ready)?.folders?.firstOrNull { it.id == item.folderId }?.name.orEmpty()
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val lib = LocalAppDeps.current.userData
        val favorites by lib.favorites.collectAsState()
        val fav = item.id in favorites
        Row(Modifier.fillMaxWidth().padding(top = OmSpace.s4), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.title, color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.subtitle(folder.ifBlank { "Audio" }), color = c.text2, fontSize = 15.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = OmSpace.s1),
                )
            }
            PIconButton(
                if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (fav) "Remove from favourites" else "Add to favourites",
                { lib.toggleFavorite(item.id) }, tint = if (fav) c.primaryHover else c.onVideo,
            )
        }
        Spacer(Modifier.size(OmSpace.s4))
        SeekBar(st.positionMs, st.bufferedMs, st.durationMs, { engine.seekTo(it) }, {}, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(horizontal = OmSpace.s1)) {
            Text(formatTime(st.positionMs), color = c.text3, fontSize = 12.sp, style = Tnum, modifier = Modifier.weight(1f))
            Text(
                if (st.durationMs > 0) "-" + formatTime(st.durationMs - st.positionMs) else "--:--",
                color = c.text3, fontSize = 12.sp, style = Tnum,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = OmSpace.s2), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PIconButton(Icons.Rounded.SkipPrevious, "Previous track", { engine.skipPrevious() }, size = 52.dp, iconSize = 32.dp)
            PIconButton(Icons.Rounded.Replay10, "Back 10 seconds", { engine.seekBy(-10_000) }, size = 48.dp, iconSize = 28.dp)
            PIconButton(
                if (st.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (st.isPlaying) "Pause" else "Play",
                { if (st.ended) { engine.seekTo(0); engine.play() } else engine.togglePlay() },
                size = 72.dp, iconSize = 40.dp, filled = c.primary, tint = c.onPrimary,
            )
            PIconButton(Icons.Rounded.Forward30, "Forward 30 seconds", { engine.seekBy(30_000) }, size = 48.dp, iconSize = 28.dp)
            PIconButton(Icons.Rounded.SkipNext, "Next track", { engine.skipNext() }, size = 52.dp, iconSize = 32.dp, enabled = hasNext)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = OmSpace.s2), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PIconButton(Icons.Rounded.Shuffle, if (st.shuffle) "Shuffle on" else "Shuffle off", { engine.setShuffle(!st.shuffle) }, active = st.shuffle)
            PIconButton(
                if (st.repeat == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "Repeat ${st.repeat.name.lowercase()}",
                { engine.setRepeatMode(when (st.repeat) { RepeatMode.Off -> RepeatMode.All; RepeatMode.All -> RepeatMode.One; RepeatMode.One -> RepeatMode.Off }) },
                active = st.repeat != RepeatMode.Off,
            )
            PTextButton(speedLabel(st.speed), "Playback speed", { openPanel(AudioPanel.Speed) }, active = st.speed != 1f)
            val sleeping = st.sleepEndsAtMs > 0 || st.sleepAtTrackEnd
            PIconButton(Icons.Rounded.PlaylistAdd, "Add to playlist", { openPanel(AudioPanel.Playlists) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                PIconButton(Icons.Rounded.Bedtime, "Sleep timer", { openPanel(AudioPanel.Sleep) }, active = sleeping)
                if (sleeping) Text(
                    if (st.sleepAtTrackEnd) "End of track" else "${sleepMinutes ?: ""} min",
                    color = c.primaryHover, fontSize = 12.sp, style = Tnum,
                )
            }
        }
    }
}

@Composable
private fun SleepPanel(st: PlayerState, selectedMinutes: Int?, onPick: (minutes: Int?, atTrackEnd: Boolean) -> Unit) {
    val now by produceState(0L) { while (true) { value = nowMs(); delay(15_000) } }
    val off = st.sleepEndsAtMs == 0L && !st.sleepAtTrackEnd
    OptionRow("Off", off, { onPick(null, false) })
    listOf(15, 30, 60).forEach { m ->
        val on = st.sleepEndsAtMs > 0 && selectedMinutes == m
        val left = if (on) "${maxOf(1, ((st.sleepEndsAtMs - now + 59_999) / 60_000))} min left" else null
        OptionRow("$m minutes", on, { onPick(m, false) }, sub = left)
    }
    OptionRow("End of track", st.sleepAtTrackEnd, { onPick(null, true) })
}

@Composable
private fun QueuePanel(st: PlayerState, onPick: (Int) -> Unit) {
    val c = OmTheme.colors
    if (st.queue.isEmpty()) Text("Nothing queued", color = c.text3, fontSize = 14.sp, modifier = Modifier.padding(OmSpace.s4))
    st.queue.forEachIndexed { i, q ->
        val current = i == st.queueIndex
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onPick(i) }
                .padding(horizontal = OmSpace.s4, vertical = OmSpace.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                OmText(q.title, color = if (current) c.primaryHover else c.text, weight = if (current) FontWeight.SemiBold else FontWeight.Normal, size = 15.sp)
                if (q.artist.isNotBlank()) OmText(q.artist, color = c.text3, size = 12.sp)
            }
            if (current) Text(if (st.isPlaying) "Playing" else "Paused", color = c.primaryHover, fontSize = 12.sp)
            else if (q.durationMs > 0) Text(formatTime(q.durationMs), color = c.text3, fontSize = 12.sp, style = Tnum)
        }
    }
}

/**
 * Persistent mini-player for the library, shown while audio is loaded. Tap to open the full player; the same
 * engine and queue back both, so nothing restarts.
 */
@Composable
fun MiniPlayer(onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val engine = LocalAppDeps.current.engine
    val c = OmTheme.colors
    val st by engine.state.collectAsState()
    val item = st.item?.takeIf { it.isAudio } ?: return
    val hasNext = st.hasNext
    Column(modifier.fillMaxWidth().background(c.surface2)) {
        ProgressBar(if (st.durationMs > 0) st.positionMs.toFloat() / st.durationMs else 0f, height = 2.dp)
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open player", onClick = onExpand)
                .padding(start = OmSpace.s3, end = OmSpace.s1, top = OmSpace.s1, bottom = OmSpace.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThumbnailPlaceholder(item.id, Modifier.size(44.dp).clip(RoundedCornerShape(OmRadius.sm)), isAudio = true, uri = item.uri)
            Column(Modifier.weight(1f).padding(horizontal = OmSpace.s3)) {
                OmText(item.title, weight = FontWeight.SemiBold, size = 14.sp)
                OmText(item.artist.ifBlank { "Audio" }, color = c.text3, size = 12.sp)
            }
            OmIconButton(
                if (st.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (st.isPlaying) "Pause" else "Play",
                { if (st.ended) { engine.seekTo(0); engine.play() } else engine.togglePlay() },
                tint = c.text,
            )
            OmIconButton(Icons.Rounded.SkipNext, "Next track", { if (hasNext) engine.skipNext() }, tint = if (hasNext) c.text else c.text3)
            OmIconButton(Icons.Rounded.Close, "Stop and close player", { engine.stop() })
        }
    }
}
