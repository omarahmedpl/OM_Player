package com.omplayer.ui.player

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlin.time.TimeMark
import com.omplayer.ScreenOrientation
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omplayer.AppSettings
import com.omplayer.FormFactor
import com.omplayer.LocalAppDeps
import com.omplayer.FullscreenHost
import com.omplayer.PlatformBackHandler
import com.omplayer.VideoSurface
import com.omplayer.data.LibraryState
import com.omplayer.model.MediaItem
import com.omplayer.player.ScaleMode
import com.omplayer.theme.DarkColors
import com.omplayer.theme.OmMotion
import com.omplayer.theme.OmScheme
import com.omplayer.theme.OmSpace
import com.omplayer.theme.OmTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAP_WINDOW_MS = 250L

private data class OsdMsg(val text: String, val id: Int)
private data class GestureHud(val icon: ImageVector?, val text: String, val fraction: Float? = null, val boost: Boolean = false, val visible: Boolean = true)
private data class RippleMsg(val side: Int, val seconds: Int, val id: Int, val visible: Boolean = true)

private class TapState { var count = 0; var zone = 0; var job: Job? = null }
private class DragState { var base = 0f; var seekBase = 0L; var seekTarget = 0L; var speedBefore = 1f; var zoomBase = 1f; var hudJob: Job? = null }

/**
 * Full-screen player. CONTRACT (called from App.kt): signature must stay as-is.
 * Always dark (OmScheme(DarkColors)), even when the app theme is light.
 */
@Composable
fun PlayerScreen(
    item: MediaItem,
    queue: List<MediaItem>,
    settings: AppSettings,
    onItemChange: (MediaItem) -> Unit,
    onClose: () -> Unit,
) {
    OmScheme(DarkColors) { PlayerContent(item, queue, settings, onItemChange, onClose) }
}

@Composable
private fun PlayerContent(
    item: MediaItem,
    queue: List<MediaItem>,
    settings: AppSettings,
    onItemChange: (MediaItem) -> Unit,
    onClose: () -> Unit,
) {
    val deps = LocalAppDeps.current
    val engine = deps.engine
    val platform = deps.platform
    val desktop = platform.formFactor == FormFactor.Desktop
    val c = OmTheme.colors
    val scope = rememberCoroutineScope()
    val st by engine.state.collectAsState()
    val library by deps.repository.state.collectAsState()

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var seekDragging by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    var scaleMode by remember { mutableStateOf(ScaleMode.Fit) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var locked by remember { mutableStateOf(false) }
    var unlockVisible by remember { mutableStateOf(false) }
    var osd by remember { mutableStateOf<OsdMsg?>(null) }
    var osdLast by remember { mutableStateOf("") }
    var osdCounter by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf<GestureHud?>(null) }
    var ripple by remember { mutableStateOf<RippleMsg?>(null) }
    var rippleCounter by remember { mutableIntStateOf(0) }
    var sleep by remember { mutableStateOf(SleepMode.Off) }
    var abA by remember(item.id) { mutableStateOf<Long?>(null) }
    var abB by remember(item.id) { mutableStateOf<Long?>(null) }
    var subScale by remember { mutableFloatStateOf(settings.subtitleScale) }
    var subBg by remember { mutableStateOf(settings.subtitleBackground) }
    var fullscreen by remember { mutableStateOf(!desktop) }
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var preMute by remember { mutableFloatStateOf(1f) }
    val focus = remember { FocusRequester() }
    val tap = remember { TapState() }
    val drag = remember { DragState() }

    val idx = queue.indexOfFirst { it.id == item.id }
    val prevItem = if (idx > 0) queue[idx - 1] else null
    val nextItem = if (idx >= 0 && idx < queue.lastIndex) queue[idx + 1] else null
    val step = settings.seekStepSec.coerceAtLeast(1)

    fun showOsd(text: String) { osdCounter++; osd = OsdMsg(text, osdCounter) }
    // Rotate button turns the whole screen (Activity orientation); closing the player returns to Auto.
    val windowSize = LocalWindowInfo.current.containerSize
    fun rotateScreen() {
        val toLandscape = windowSize.width <= windowSize.height
        platform.setScreenOrientation(if (toLandscape) ScreenOrientation.Landscape else ScreenOrientation.Portrait)
        showOsd(if (toLandscape) "Landscape" else "Portrait")
    }
    DisposableEffect(Unit) { onDispose { platform.setScreenOrientation(ScreenOrientation.Auto) } }

    fun poke() { interaction++; controlsVisible = true }

    // ---- engine lifecycle -------------------------------------------------------------------------------
    LaunchedEffect(item.id) {
        val start = if (settings.resumePlayback) deps.progress.positionMs(item.id) ?: 0L else 0L
        engine.open(item, start)
        scaleMode = ScaleMode.Fit
        zoom = 1f
    }
    fun saveProgress() {
        val s = engine.state.value
        val it = s.item
        if (it != null && it.id == item.id) deps.progress.save(it.id, s.positionMs, s.durationMs)
    }
    DisposableEffect(item.id) { onDispose { saveProgress() } }
    LaunchedEffect(item.id, st.isPlaying) {
        while (st.isPlaying) { delay(5_000); saveProgress() }
    }
    DisposableEffect(Unit) {
        if (!desktop) platform.setFullscreen(true)
        onDispose {
            saveProgress()
            platform.setPlayerBrightness(null)
            platform.keepScreenOn(false)
            platform.setFullscreen(false)
            engine.stop()
        }
    }
    LaunchedEffect(st.isPlaying) { platform.keepScreenOn(st.isPlaying) }
    LaunchedEffect(Unit) { focus.requestFocus() }

    // End of item: advance or offer replay.
    LaunchedEffect(st.ended, st.item?.id) {
        if (st.ended && st.item?.id == item.id) {
            saveProgress()
            if (sleep == SleepMode.EndOfVideo) { sleep = SleepMode.Off; showOsd("Sleep timer: stopped") }
            else nextItem?.let { onItemChange(it) }
        }
    }

    // Subtitle style lives in the engine so the platform renderer can apply it.
    LaunchedEffect(subScale, subBg) { engine.setSubtitleStyle(subScale, subBg) }

    // Picture-in-picture: no chrome, no panels.
    val inPip by platform.inPictureInPicture.collectAsState()
    LaunchedEffect(inPip) {
        if (inPip) { panel = null; controlsVisible = false } else poke()   // expanded back: show controls
    }
    // Closing the PiP window (not expanding it) stops playback and closes the player.
    LaunchedEffect(Unit) {
        platform.pictureInPictureDismissed.collect { engine.stop(); onClose() }
    }

    // System Back: locked → just reveal the unlock button; else close the open panel first, then the player.
    PlatformBackHandler {
        when {
            locked -> { unlockVisible = true; interaction++ }
            panel != null -> panel = null
            else -> onClose()
        }
    }

    // Auto-hide.
    LaunchedEffect(controlsVisible, interaction, st.isPlaying, panel, seekDragging, locked) {
        if (controlsVisible && st.isPlaying && panel == null && !seekDragging && !locked) {
            delay(if (desktop) 2_500 else 3_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(unlockVisible, interaction) { if (unlockVisible) { delay(2_500); unlockVisible = false } }

    // OSD text fades out after a moment.
    LaunchedEffect(osd?.id) { osd?.let { osdLast = it.text; delay(1_200); osd = null } }
    LaunchedEffect(ripple?.id) { ripple?.let { delay(600); ripple = ripple?.copy(visible = false) } }

    // Sleep timer.
    LaunchedEffect(sleep) {
        val mins = when (sleep) { SleepMode.M15 -> 15; SleepMode.M30 -> 30; SleepMode.M60 -> 60; else -> return@LaunchedEffect }
        delay(mins * 60_000L)
        engine.pause()
        sleep = SleepMode.Off
        showOsd("Sleep timer: paused")
    }
    // A-B repeat.
    LaunchedEffect(abA, abB) {
        val a = abA; val b = abB
        if (a != null && b != null) engine.state.collect { if (it.positionMs >= b) engine.seekTo(a) }
    }

    // ---- actions ----------------------------------------------------------------------------------------
    fun togglePlay() {
        if (st.ended) { engine.seekTo(0); engine.play() } else engine.togglePlay()
    }
    fun seekStep(dir: Int, seconds: Int) {
        engine.seekBy(dir * seconds * 1000L)
        showOsd((if (dir > 0) "+" else "−") + "$seconds s")
    }
    fun setVolume(v: Float) {
        val nv = v.coerceIn(0f, 2f)
        engine.setVolume(nv)
        if (nv > 0f) preMute = nv
        showOsd(if (nv == 0f) "Muted" else "Volume ${(nv * 100).roundToInt()}%")
    }
    fun toggleMute() { if (st.volume > 0f) { preMute = st.volume; engine.setVolume(0f); showOsd("Muted") } else setVolume(if (preMute > 0f) preMute else 1f) }
    fun cycleScale() {
        scaleMode = ScaleMode.entries[(scaleMode.ordinal + 1) % ScaleMode.entries.size]
        zoom = 1f
        showOsd(scaleName(scaleMode))
    }
    // While the desktop window switches placement, the OS re-creates the native frame and re-delivers the still-held
    // key (F/Esc), which flipped fullscreen straight back. Ignore toggles until the switch has settled.
    var lastFullscreenToggle by remember { mutableStateOf<TimeMark?>(null) }
    fun toggleFullscreen() {
        if (lastFullscreenToggle?.let { it.elapsedNow() < 500.milliseconds } == true) return
        lastFullscreenToggle = TimeSource.Monotonic.markNow()
        fullscreen = !fullscreen; platform.setFullscreen(fullscreen)
    }
    if (desktop) {
        // Follow the window's real state (Esc is also handled at window level) and keep keyboard focus after the switch.
        val windowFs by platform.windowFullscreen.collectAsState()
        LaunchedEffect(windowFs) {
            fullscreen = windowFs
            delay(120)   // the UI may have just moved into/out of the fullscreen window
            runCatching { focus.requestFocus() }
        }
    }
    fun changeSpeed(delta: Float) {
        val v = (((st.speed + delta) * 100).roundToInt() / 100f).coerceIn(0.25f, 4f)
        engine.setSpeed(v); showOsd("Speed ${speedLabel(v)}")
    }
    fun openPanel(p: PlayerPanel) { panel = p; poke() }
    // One press closes the player. On phones the player is always fullscreen, so "exit fullscreen first" made Back
    // and ← look dead on the first press (owner report). Only an open panel is dismissed first.
    fun handleClose() {
        when {
            panel != null -> panel = null
            // Desktop/Linux fullscreen lives in its own window while the main window is hidden (and its composition
            // paused); leave it first so the main window can recompose and close the player in the same press.
            desktop && fullscreen -> { fullscreen = false; platform.setFullscreen(false); onClose() }
            else -> onClose()
        }
    }

    fun hideHudSoon() {
        drag.hudJob?.cancel()
        drag.hudJob = scope.launch { delay(450); hud = hud?.copy(visible = false) }
    }

    fun singleTap() {
        if (controlsVisible) controlsVisible = false else poke()
    }
    fun handleTap(xFrac: Float) {
        if (locked) { unlockVisible = true; interaction++; return }
        val zone = when { xFrac < 1f / 3f -> -1; xFrac > 2f / 3f -> 1; else -> 0 }
        if (!settings.gestureDoubleTap) { singleTap(); return }
        if (tap.count >= 1 && tap.zone == zone) {
            tap.count++
            tap.job?.cancel()
            if (zone == 0) poke()
            else {
                engine.seekBy(zone * step * 1000L)
                rippleCounter++
                ripple = RippleMsg(zone, (tap.count - 1) * step, rippleCounter)
            }
            tap.job = scope.launch { delay(TAP_WINDOW_MS); tap.count = 0 }
        } else {
            tap.job?.cancel()
            tap.count = 1
            tap.zone = zone
            tap.job = scope.launch { delay(TAP_WINDOW_MS); if (tap.count == 1) singleTap(); tap.count = 0 }
        }
    }

    val gestureCallbacks = GestureCallbacks(
        onTap = ::handleTap,
        onVertical = { left, delta, phase ->
            when (phase) {
                Phase.Start -> { drag.hudJob?.cancel(); drag.base = if (left) brightness else st.volume }
                Phase.Update -> if (left) {
                    brightness = (drag.base + delta * 1.2f).coerceIn(0.02f, 1f)
                    platform.setPlayerBrightness(brightness)
                    hud = GestureHud(Icons.Rounded.BrightnessMedium, "${(brightness * 100).roundToInt()}%", brightness)
                } else {
                    val v = (drag.base + delta * 1.2f).coerceIn(0f, 2f)
                    engine.setVolume(v)
                    hud = GestureHud(if (v == 0f) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, "${(v * 100).roundToInt()}%", v / 2f, boost = v > 1f)
                }
                Phase.End, Phase.Cancel -> hideHudSoon()
            }
        },
        onHorizontal = { delta, phase ->
            when (phase) {
                Phase.Start -> { drag.hudJob?.cancel(); drag.seekBase = st.positionMs; drag.seekTarget = st.positionMs; seekDragging = true }
                Phase.Update -> {
                    val target = (drag.seekBase + (delta * 120_000f).toLong()).coerceIn(0L, maxOf(0L, st.durationMs))
                    drag.seekTarget = target
                    val diff = target - drag.seekBase
                    hud = GestureHud(if (diff < 0) Icons.Rounded.FastRewind else Icons.Rounded.FastForward,
                        "${formatSigned(diff)} · ${formatTime(target)} / ${formatTime(st.durationMs)}")
                }
                Phase.End -> { engine.seekTo(drag.seekTarget); seekDragging = false; hideHudSoon() }
                Phase.Cancel -> { seekDragging = false; hideHudSoon() }
            }
        },
        onLongPress = { active ->
            if (active) { drag.speedBefore = st.speed; engine.setSpeed(2f); drag.hudJob?.cancel(); hud = GestureHud(Icons.Rounded.Speed, "2x speed") }
            else { engine.setSpeed(drag.speedBefore); hideHudSoon() }
        },
        onPinch = { scale, phase ->
            when (phase) {
                Phase.Start -> drag.zoomBase = zoom
                Phase.Update -> {
                    zoom = (drag.zoomBase * scale).coerceIn(1f, 4f)
                    hud = GestureHud(Icons.Rounded.ZoomIn, "Zoom ${((zoom * 10).roundToInt() / 10f)}x")
                }
                Phase.End -> {
                    when {
                        zoom < 1.15f -> { zoom = 1f; scaleMode = ScaleMode.Fit; showOsd(scaleName(scaleMode)) }
                        zoom < 1.6f -> { zoom = 1f; scaleMode = ScaleMode.Crop; showOsd(scaleName(scaleMode)) }
                        else -> showOsd("Zoom ${((zoom * 10).roundToInt() / 10f)}x")
                    }
                    hud = null
                }
                Phase.Cancel -> hud = null
            }
        },
    )
    val gestureFlags = GestureFlags(
        settings.gestureBrightness, settings.gestureVolume, settings.gestureSeek, settings.gesturePinchZoom, settings.gestureLongPressSpeed,
    )

    fun onKey(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        if (e.isCtrlPressed || e.isAltPressed || e.isMetaPressed) return false
        if (locked) return false
        when {
            e.key == Key.Spacebar -> togglePlay()
            e.key == Key.J -> seekStep(-1, step)
            e.key == Key.L -> seekStep(1, step)
            e.key == Key.DirectionLeft -> seekStep(-1, 5)
            e.key == Key.DirectionRight -> seekStep(1, 5)
            e.key == Key.DirectionUp -> setVolume(st.volume + 0.05f)
            e.key == Key.DirectionDown -> setVolume(st.volume - 0.05f)
            e.key == Key.F -> toggleFullscreen()
            e.key == Key.M -> toggleMute()
            e.key == Key.LeftBracket -> changeSpeed(-0.25f)
            e.key == Key.RightBracket -> changeSpeed(0.25f)
            e.key == Key.Escape -> when {
                panel != null -> panel = null
                fullscreen -> toggleFullscreen()
                else -> Unit
            }
            (e.key == Key.Slash && e.isShiftPressed) || e.utf16CodePoint == '?'.code -> panel = if (panel == PlayerPanel.Shortcuts) null else PlayerPanel.Shortcuts
            else -> return false
        }
        return true
    }

    // ---- UI ---------------------------------------------------------------------------------------------
    val controlsAlpha by animateFloatAsState(
        if (controlsVisible && !locked && !inPip) 1f else 0f, tween(OmMotion.Base, easing = OmMotion.EaseOut),
    )
    val folderName = (library as? LibraryState.Ready)?.folders?.firstOrNull { it.id == item.folderId }?.name
    val resLabel = item.copy(
        width = st.videoWidth.takeIf { it > 0 } ?: item.width,
        height = st.videoHeight.takeIf { it > 0 } ?: item.height,
    ).resolutionLabel
    val meta = listOfNotNull(folderName, resLabel.ifEmpty { null }, if (item.isAudio) null else settings.decoder.name).joinToString(" · ")

    FullscreenHost(active = desktop && fullscreen, onExit = { if (fullscreen) toggleFullscreen() }) {
        Box(
            Modifier.fillMaxSize().background(c.bg)
                .focusRequester(focus).focusable()
                .onPreviewKeyEvent(::onKey)
                .then(if (desktop) Modifier.desktopMoveAndWheel(
                    onMove = { if (!locked) poke() },
                    onWheel = { dy -> setVolume(st.volume + (if (dy < 0) 0.05f else -0.05f)) },
                ) else Modifier),
        ) {
            // Video + gestures
            Box(
                Modifier.fillMaxSize()
                    .then(
                        if (desktop) Modifier.desktopClicks(
                            onClick = { focus.requestFocus(); poke() },
                            onDoubleClick = { toggleFullscreen() },
                        ) else if (locked) Modifier.playerGestures(
                            GestureFlags(false, false, false, false, false), GestureCallbacks({ handleTap(it) }, { _, _, _ -> }, { _, _ -> }, {}, { _, _ -> }),
                        ) else Modifier.playerGestures(gestureFlags, gestureCallbacks),
                    ),
            ) {
                VideoSurface(engine, scaleMode, zoom, Modifier.fillMaxSize())
            }

            if (st.isBuffering && st.error == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(72.dp), color = c.onVideo, strokeWidth = 3.dp)
            }

            // Double-tap ripple
            ripple?.let { r ->
                val a by animateFloatAsState(if (r.visible) 1f else 0f, tween(OmMotion.Fast, easing = OmMotion.EaseOut))
                if (a > 0.01f) Box(
                    Modifier.align(if (r.side < 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .fillMaxSize(0.33f).graphicsLayer { alpha = a }.background(c.hairline, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Icon(
                            if (r.side < 0) Icons.Rounded.FastRewind else Icons.Rounded.FastForward, null, Modifier.size(32.dp), tint = c.onVideo,
                        )
                        Text("${r.seconds} s", color = c.onVideo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = Tnum)
                    }
                }
            }

            // Controls
            if (controlsAlpha > 0.01f && !locked) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = controlsAlpha }.safeDrawingPadding()) {
                    TopBar(
                        item.title, meta, onBack = ::handleClose,
                        onRotate = if (desktop) null else ::rotateScreen,
                        onExitFullscreen = if (desktop && fullscreen) ::toggleFullscreen else null,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                    if (st.error == null) CenterControl(st.isPlaying, st.ended, ::togglePlay, Modifier.align(Alignment.Center))
                    BottomBar(
                        st = st, desktop = desktop, hasPrev = prevItem != null, hasNext = nextItem != null,
                        scaleMode = scaleMode, speed = st.speed, fullscreen = fullscreen, muted = st.volume == 0f,
                        onSeek = { engine.seekTo(it); poke() },
                        onDragging = { seekDragging = it },
                        onTogglePlay = ::togglePlay,
                        onPrev = { prevItem?.let(onItemChange) },
                        onNext = { nextItem?.let(onItemChange) },
                        onScale = ::cycleScale,
                        onSpeed = { openPanel(PlayerPanel.Speed) },
                        onSubs = { openPanel(PlayerPanel.Subtitles) },
                        onAudio = { openPanel(PlayerPanel.Audio) },
                        onMore = { openPanel(PlayerPanel.More) },
                        onLock = { locked = true; controlsVisible = false; unlockVisible = true; interaction++ },
                        onFullscreen = ::toggleFullscreen,
                        onMute = ::toggleMute,
                        volume = st.volume,
                        onVolume = { setVolume(it); poke() },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            // Lock overlay
            if (locked) {
                val a by animateFloatAsState(if (unlockVisible) 1f else 0f, tween(OmMotion.Base, easing = OmMotion.EaseOut))
                if (a > 0.01f) Box(Modifier.fillMaxSize().safeDrawingPadding().graphicsLayer { alpha = a }) {
                    PIconButton(
                        Icons.Rounded.Lock, "Unlock controls", { locked = false; poke() },
                        Modifier.align(Alignment.CenterEnd).padding(end = OmSpace.s4), size = 56.dp, filled = c.glass,
                    )
                }
            }

            // Gesture HUD + OSD
            val hudAlpha by animateFloatAsState(if (hud?.visible == true) 1f else 0f, tween(OmMotion.Press, easing = OmMotion.EaseOut))
            if (hudAlpha > 0.01f) hud?.let {
                HudPill(it.icon, it.text, it.fraction, it.boost, Modifier.align(Alignment.TopCenter).padding(top = 72.dp).graphicsLayer { alpha = hudAlpha })
            }
            val osdAlpha by animateFloatAsState(if (osd != null) 1f else 0f, tween(OmMotion.Press, easing = OmMotion.EaseOut))
            if (osdAlpha > 0.01f) HudPill(null, osd?.text ?: osdLast, modifier = Modifier.align(Alignment.TopCenter).padding(top = 120.dp).graphicsLayer { alpha = osdAlpha })

            // Error
            st.error?.let { msg ->
                Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp)).background(c.surface2).padding(OmSpace.s6).widthIn(max = 360.dp)) {
                    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Can't play this video", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(msg, color = c.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = OmSpace.s2))
                        Chip("Close", true, onClose)
                    }
                }
            }

            // Panels
            panel?.let { p ->
                PanelHost(
                    desktop = desktop,
                    title = when (p) {
                        PlayerPanel.Audio -> "Audio"; PlayerPanel.Subtitles -> "Subtitles"; PlayerPanel.Speed -> "Speed"
                        PlayerPanel.More -> "More"; PlayerPanel.Shortcuts -> "Keyboard shortcuts"
                    },
                    onDismiss = { panel = null },
                ) {
                    when (p) {
                        PlayerPanel.Audio -> AudioPanel(st) { engine.selectAudioTrack(it) }
                        PlayerPanel.Subtitles -> SubtitlePanel(
                            st, subScale, subBg,
                            onSelect = { engine.selectSubtitleTrack(it) },
                            onScale = { subScale = it },
                            onBackground = { subBg = it },
                            onDelay = { engine.setSubtitleDelay(it) },
                            onAddFile = { platform.pickMediaFile { uri -> if (uri != null) engine.addSubtitle(uri) } },
                        )
                        PlayerPanel.Speed -> SpeedPanel(st.speed) { engine.setSpeed(it) }
                        PlayerPanel.More -> MorePanel(
                            sleep, { sleep = it; if (it != SleepMode.Off) showOsd("Sleep timer: ${it.label}") },
                            abA, abB,
                            onAbSet = { if (abA == null) abA = st.positionMs else if (st.positionMs > abA!!) abB = st.positionMs },
                            onAbClear = { abA = null; abB = null },
                            backgroundPlay = settings.backgroundPlay,
                            canPip = !desktop,
                            onPip = { panel = null; platform.enterPictureInPicture() },
                            onShortcuts = if (desktop) ({ panel = PlayerPanel.Shortcuts }) else null,
                        )
                        PlayerPanel.Shortcuts -> ShortcutsPanel()
                    }
                }
            }
        }
    }
}

private fun scaleName(m: ScaleMode) = when (m) {
    ScaleMode.Fit -> "Fit"
    ScaleMode.Fill -> "Fill"
    ScaleMode.Crop -> "Crop"
    ScaleMode.Original -> "100%"
}
