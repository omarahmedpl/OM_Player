package com.omplayer.ui.player

import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omplayer.player.PlayerState
import com.omplayer.player.ScaleMode
import com.omplayer.theme.OmMotion
import com.omplayer.theme.OmSpace
import com.omplayer.theme.OmTheme
import kotlin.math.roundToInt

@Composable
internal fun TopBar(
    title: String,
    meta: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRotate: (() -> Unit)? = null,
    onExitFullscreen: (() -> Unit)? = null,
) {
    val c = OmTheme.colors
    Row(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(c.scrim.copy(alpha = 0.7f), Color.Transparent)))
            .padding(horizontal = OmSpace.s2).padding(top = OmSpace.s2, bottom = OmSpace.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
        Spacer(Modifier.width(OmSpace.s2))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.onVideo, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) Text(meta, color = c.text2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRotate != null) PIconButton(Icons.Rounded.ScreenRotation, "Rotate screen", onRotate)
        // Desktop fullscreen has no window title bar, so give it an obvious way out (Esc and F also work).
        if (onExitFullscreen != null) PIconButton(Icons.Rounded.Close, "Exit full screen", onExitFullscreen, pointerIcon = PointerIcon.Hand)
    }
}

/** Centre play / pause (or replay at the end) with the buffering ring. */
@Composable
internal fun CenterControl(playing: Boolean, ended: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = OmTheme.colors
    val (icon, desc) = when {
        ended -> Icons.Rounded.Replay to "Replay"
        playing -> Icons.Rounded.Pause to "Pause"
        else -> Icons.Rounded.PlayArrow to "Play"
    }
    PIconButton(icon, desc, onClick, modifier, size = 64.dp, iconSize = 36.dp, filled = c.glass)
}

@Composable
internal fun BottomBar(
    st: PlayerState,
    desktop: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    scaleMode: ScaleMode,
    speed: Float,
    fullscreen: Boolean,
    muted: Boolean,
    onSeek: (Long) -> Unit,
    onDragging: (Boolean) -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onScale: () -> Unit,
    onSpeed: () -> Unit,
    onSubs: () -> Unit,
    onAudio: () -> Unit,
    onMore: () -> Unit,
    onLock: () -> Unit,
    onFullscreen: () -> Unit,
    onMute: () -> Unit,
    volume: Float = 1f,
    onVolume: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = OmTheme.colors
    val buttonPointer = if (desktop) PointerIcon.Hand else PointerIcon.Default
    val volumeHoverSource = remember { MutableInteractionSource() }
    val volumeFocusSource = remember { MutableInteractionSource() }
    val volumeHovered by volumeHoverSource.collectIsHoveredAsState()
    val volumeFocused by volumeFocusSource.collectIsFocusedAsState()
    val playerActions: @Composable () -> Unit = {
        PIconButton(Icons.Rounded.AspectRatio, "Scale mode: ${scaleMode.name}", onScale, pointerIcon = buttonPointer)
        PTextButton(speedLabel(speed), "Playback speed", onSpeed, active = speed != 1f, pointerIcon = buttonPointer)
        PIconButton(Icons.Rounded.Subtitles, "Subtitles", onSubs, pointerIcon = buttonPointer)
        PIconButton(Icons.Rounded.Audiotrack, "Audio tracks", onAudio, pointerIcon = buttonPointer)
        PIconButton(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, if (fullscreen) "Exit full screen" else "Full screen", onFullscreen, pointerIcon = buttonPointer)
        PIconButton(Icons.Rounded.MoreVert, "More", onMore, pointerIcon = buttonPointer)
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact = !desktop && maxWidth < 600.dp
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, c.scrim.copy(alpha = 0.8f))))
                .then(if (desktop) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {},
                ) else Modifier)
                .padding(horizontal = OmSpace.s4).padding(top = OmSpace.s8, bottom = OmSpace.s2),
        ) {
            SeekBar(st.positionMs, st.bufferedMs, st.durationMs, onSeek, onDragging, Modifier.fillMaxWidth())
            // Portrait phone: lock · previous · play/pause · next · time spread edge to edge (owner request).
            Row(
                if (compact) Modifier.fillMaxWidth() else Modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (compact) Arrangement.SpaceBetween else Arrangement.spacedBy(2.dp),
            ) {
                if (!desktop) {
                    PIconButton(Icons.Rounded.Lock, "Lock controls", onLock, pointerIcon = buttonPointer)
                }
                PIconButton(Icons.Rounded.SkipPrevious, "Previous video", onPrev, enabled = hasPrev, pointerIcon = buttonPointer)
                PIconButton(if (st.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (st.isPlaying) "Pause" else "Play", onTogglePlay, pointerIcon = buttonPointer)
                PIconButton(Icons.Rounded.SkipNext, "Next video", onNext, enabled = hasNext, pointerIcon = buttonPointer)
                if (desktop) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.hoverable(volumeHoverSource),
                    ) {
                        PIconButton(
                            if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                            if (muted) "Unmute, volume 0%" else "Mute, volume ${(volume * 100).toInt()}%",
                            onMute,
                            interactionSource = volumeFocusSource,
                            pointerIcon = buttonPointer,
                        )
                        AnimatedVisibility(
                            visible = volumeHovered || volumeFocused,
                            enter = expandHorizontally(tween(OmMotion.Fast, easing = OmMotion.EaseOut)) + fadeIn(tween(OmMotion.Fast)),
                            exit = shrinkHorizontally(tween(OmMotion.Fast, easing = OmMotion.EaseOut)) + fadeOut(tween(OmMotion.Fast)),
                        ) {
                            // 0..200%; above 100% is software boost (warning colour).
                            Slider(
                                value = volume, onValueChange = onVolume, valueRange = 0f..2f,
                                modifier = Modifier.width(112.dp)
                                    .semantics { contentDescription = "Volume ${(volume * 100).toInt()}%" },
                                interactionSource = volumeFocusSource,
                                colors = SliderDefaults.colors(
                                    thumbColor = c.onVideo,
                                    activeTrackColor = if (volume > 1f) c.warning else c.primary,
                                    inactiveTrackColor = c.onVideo.copy(alpha = 0.3f),
                                ),
                            )
                        }
                    }
                }
                if (!compact) Spacer(Modifier.width(OmSpace.s2))
                Text(
                    "${formatTime(st.positionMs)} / ${formatTime(st.durationMs)}",
                    color = c.onVideo, fontSize = 13.sp, style = Tnum, maxLines = 1,
                )
                if (!compact) {
                    Spacer(Modifier.weight(1f))
                    playerActions()
                }
            }
            if (compact) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                playerActions()
            }
        }
    }
}

/** Seek bar: buffered fill, played fill, thumb; drag shows a time bubble and commits on release. */
@Composable
internal fun SeekBar(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onDragging: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = OmTheme.colors
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableIntStateOf(0) }
    val curSeek by rememberUpdatedState(onSeek)
    val curDrag by rememberUpdatedState(onDragging)
    val dur by rememberUpdatedState(durationMs)
    val frac = dragFrac ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufFrac = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val trackH by animateDpAsState(if (dragFrac != null) 6.dp else 4.dp, tween(OmMotion.Press, easing = OmMotion.EaseOut))
    val thumbR by animateDpAsState(if (dragFrac != null) 9.dp else 6.dp, tween(OmMotion.Press, easing = OmMotion.EaseOut))
    Box(
        modifier.height(OmSpace.tap)
            .onSizeChanged { widthPx = it.width }
            .semantics {
                contentDescription = "Seek"
                stateDescription = "${formatTime(positionMs)} of ${formatTime(durationMs)}"
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (dur <= 0) return@awaitEachGesture
                    down.consume()
                    curDrag(true)
                    fun f(x: Float) = (x / size.width).coerceIn(0f, 1f)
                    dragFrac = f(down.position.x)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        dragFrac = f(ch.position.x)
                        ch.consume()
                        if (!ch.pressed) break
                    }
                    dragFrac?.let { curSeek((it * dur).toLong()) }
                    dragFrac = null
                    curDrag(false)
                }
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val h = trackH.toPx()
            val y = (size.height - h) / 2f
            val r = CornerRadius(h / 2f)
            drawRoundRect(c.hairlineStrong, Offset(0f, y), Size(size.width, h), r)
            drawRoundRect(c.text3.copy(alpha = 0.6f), Offset(0f, y), Size(size.width * bufFrac, h), r)
            drawRoundRect(c.primary, Offset(0f, y), Size((size.width * frac).coerceAtLeast(h), h), r)
            drawCircle(c.onVideo, thumbR.toPx(), Offset(size.width * frac, size.height / 2f))
        }
        if (dragFrac != null && durationMs > 0) {
            val bubbleW = 64.dp
            Text(
                formatTime((frac * durationMs).toLong()),
                color = c.onVideo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = Tnum,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset {
                        val bw = bubbleW.roundToPx()
                        val x = (frac * widthPx).roundToInt() - bw / 2
                        IntOffset(x.coerceIn(0, (widthPx - bw).coerceAtLeast(0)), -34.dp.roundToPx())
                    }
                    .width(bubbleW)
                    .background(c.glass, RoundedCornerShape(OmSpace.s2))
                    .padding(vertical = OmSpace.s1),
            )
        }
    }
}
