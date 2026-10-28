package com.omplayer.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omplayer.player.PlayerState
import com.omplayer.theme.OmMotion
import com.omplayer.theme.OmRadius
import com.omplayer.theme.OmSpace
import com.omplayer.theme.OmTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Phone: bottom sheet. Desktop: right side panel. Both animate in/out; Esc / key closes skip animation (caller drops it). */
@Composable
internal fun PanelHost(desktop: Boolean, title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (desktop) SidePanel(title, onDismiss, content) else BottomSheet(title, onDismiss, content)
}

@Composable
private fun SidePanel(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = OmTheme.colors
    val scope = rememberCoroutineScope()
    val widthDp = 340.dp
    val widthPx = with(LocalDensity.current) { widthDp.toPx() }
    val x = remember { Animatable(widthPx) }
    LaunchedEffect(Unit) { x.animateTo(0f, tween(OmMotion.Sheet, easing = OmMotion.EaseDrawer)) }
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(widthDp)
                .graphicsLayer { translationX = x.value }
                .background(c.surface1),
        ) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(c.hairline))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(start = OmSpace.s4, end = OmSpace.s2, top = OmSpace.s2), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    PIconButton(Icons.Rounded.Close, "Close panel", {
                        scope.launch { x.animateTo(widthPx, tween(OmMotion.Base, easing = OmMotion.EaseDrawer)); onDismiss() }
                    }, tint = c.text2)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = OmSpace.s4), content = content)
            }
        }
    }
}

@Composable
private fun BottomSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = OmTheme.colors
    val scope = rememberCoroutineScope()
    var heightPx by remember { mutableFloatStateOf(0f) }
    var opened by remember { mutableStateOf(false) }
    val offset = remember { Animatable(100_000f) }
    val progress = if (heightPx > 0f) (1f - offset.value / heightPx).coerceIn(0f, 1f) else 0f
    suspend fun close() {
        offset.animateTo(heightPx, tween(OmMotion.Base, easing = OmMotion.EaseDrawer))
        onDismiss()
    }
    LaunchedEffect(heightPx) {
        if (heightPx > 0f && !opened) {
            offset.snapTo(heightPx)
            offset.animateTo(0f, tween(OmMotion.Sheet, easing = OmMotion.EaseDrawer))
            opened = true
        }
    }
    val drag = rememberDraggableState { d -> scope.launch { offset.snapTo((offset.value + d).coerceAtLeast(0f)) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(c.scrim.copy(alpha = c.scrim.alpha * progress))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { scope.launch { close() } },
        )
        Column(
            Modifier.align(Alignment.BottomCenter).widthIn(max = 560.dp).fillMaxWidth()
                .heightIn(max = maxHeight * 0.92f)
                .offset { IntOffset(0, offset.value.roundToInt()) }
                .onSizeChanged { heightPx = it.height.toFloat() }
                .clip(RoundedCornerShape(topStart = OmRadius.xl, topEnd = OmRadius.xl))
                .background(c.surface1),
        ) {
            Column(
                Modifier.fillMaxWidth().draggable(drag, Orientation.Vertical, onDragStopped = { v ->
                    if (v > 900f || offset.value > heightPx * 0.4f) close()
                    else offset.animateTo(0f, tween(OmMotion.Fast, easing = OmMotion.EaseOut))
                }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.padding(top = OmSpace.s2).size(36.dp, 4.dp).clip(RoundedCornerShape(50)).background(c.hairlineStrong))
                Row(Modifier.fillMaxWidth().padding(start = OmSpace.s4, end = OmSpace.s2, top = OmSpace.s1), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    PIconButton(Icons.Rounded.Close, "Close sheet", { scope.launch { close() } }, tint = c.text2)
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = OmSpace.s4), content = content)
        }
    }
}

@Composable
internal fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit, sub: String? = null) {
    val c = OmTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = OmSpace.s4, vertical = OmSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (selected) c.text else c.text2, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            if (sub != null) Text(sub, color = c.text3, fontSize = 12.sp)
        }
        if (selected) Icon(Icons.Rounded.Check, "Selected", tint = c.primary)
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, color = OmTheme.colors.text3, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = OmSpace.s4, end = OmSpace.s4, top = OmSpace.s4, bottom = OmSpace.s1))
}

@Composable
internal fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = OmTheme.colors
    Box(
        modifier.height(40.dp).pressable(role = Role.RadioButton, onClick = onClick).clip(RoundedCornerShape(50))
            .background(if (selected) c.primary else c.surface3).padding(horizontal = OmSpace.s3),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) c.onPrimary else c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = OmTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = OmSpace.s4), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary,
                uncheckedTrackColor = c.surface3, uncheckedThumbColor = c.text2, uncheckedBorderColor = c.hairlineStrong,
            ),
        )
    }
}

@Composable
private fun sliderColors() = OmTheme.colors.let {
    SliderDefaults.colors(
        thumbColor = it.primary, activeTrackColor = it.primary, inactiveTrackColor = it.surface3,
        activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
    )
}

@Composable
internal fun AudioPanel(st: PlayerState, onSelect: (String) -> Unit) {
    if (st.audioTracks.isEmpty()) EmptyNote("No audio tracks found")
    st.audioTracks.forEach { OptionRow(it.label, it.selected, { onSelect(it.id) }) }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, color = OmTheme.colors.text3, fontSize = 14.sp, modifier = Modifier.padding(OmSpace.s4))
}

@Composable
internal fun SubtitlePanel(
    st: PlayerState,
    scale: Float,
    background: Boolean,
    onSelect: (String?) -> Unit,
    onScale: (Float) -> Unit,
    onBackground: (Boolean) -> Unit,
    onDelay: (Long) -> Unit,
    onAddFile: () -> Unit,
) {
    val c = OmTheme.colors
    OptionRow("Off", st.subtitleTracks.none { it.selected }, { onSelect(null) })
    st.subtitleTracks.forEach { OptionRow(it.label, it.selected, { onSelect(it.id) }) }
    Text(
        "Add subtitle file…", color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onAddFile)
            .padding(horizontal = OmSpace.s4, vertical = OmSpace.s3),
    )
    SectionLabel("SIZE  ·  ${(scale * 100).roundToInt()}%")
    Slider(
        scale, onScale, valueRange = 0.5f..2f, colors = sliderColors(),
        modifier = Modifier.padding(horizontal = OmSpace.s4),
    )
    SwitchRow("Background", background, onBackground)
    SectionLabel("SYNC OFFSET")
    Row(Modifier.fillMaxWidth().padding(horizontal = OmSpace.s4), verticalAlignment = Alignment.CenterVertically) {
        PIconButton(Icons.Rounded.Remove, "Subtitles 50 milliseconds earlier", { onDelay(st.subtitleDelayMs - 50) }, filled = c.surface3)
        Text(
            delayLabel(st.subtitleDelayMs), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            style = Tnum, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        PIconButton(Icons.Rounded.Add, "Subtitles 50 milliseconds later", { onDelay(st.subtitleDelayMs + 50) }, filled = c.surface3)
    }
    Text("Stays in effect when you seek.", color = c.text3, fontSize = 12.sp, modifier = Modifier.padding(horizontal = OmSpace.s4, vertical = OmSpace.s2))
}

@Composable
internal fun SpeedPanel(speed: Float, onSpeed: (Float) -> Unit) {
    val c = OmTheme.colors
    Text(
        speedLabel(speed), color = c.text, fontSize = 32.sp, fontWeight = FontWeight.Bold, style = Tnum,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = OmSpace.s2),
    )
    Slider(
        speed, { onSpeed(((it * 20).roundToInt() / 20f)) }, valueRange = 0.25f..4f, steps = 74, colors = sliderColors(),
        modifier = Modifier.padding(horizontal = OmSpace.s4),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = OmSpace.s4), horizontalArrangement = Arrangement.spacedBy(OmSpace.s2)) {
        listOf(0.5f, 1f, 1.25f, 1.5f, 2f).forEach {
            Chip(speedLabel(it), speed == it, { onSpeed(it) }, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun MorePanel(
    sleep: SleepMode,
    onSleep: (SleepMode) -> Unit,
    abA: Long?,
    abB: Long?,
    onAbSet: () -> Unit,
    onAbClear: () -> Unit,
    backgroundPlay: Boolean,
    canPip: Boolean,
    onPip: () -> Unit,
    onShortcuts: (() -> Unit)?,
) {
    val c = OmTheme.colors
    SectionLabel("SLEEP TIMER")
    SleepMode.entries.forEach { OptionRow(it.label, sleep == it, { onSleep(it) }) }
    SectionLabel("A-B REPEAT")
    val status = when {
        abA == null -> "Not set"
        abB == null -> "A ${formatTime(abA)}"
        else -> "A ${formatTime(abA)}  →  B ${formatTime(abB)}"
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = OmSpace.s4), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OmSpace.s2)) {
        Text(status, color = c.text, fontSize = 15.sp, style = Tnum, modifier = Modifier.weight(1f))
        Chip(
            when { abA == null -> "Set A"; abB == null -> "Set B"; else -> "Loop on" },
            selected = abA != null && abB != null,
            onClick = { if (abB == null) onAbSet() },
        )
        if (abA != null) Chip("Clear", false, onAbClear)
    }
    SectionLabel("PLAYBACK")
    if (canPip) Text(
        "Picture in picture", color = c.text, fontSize = 15.sp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onPip)
            .padding(horizontal = OmSpace.s4, vertical = OmSpace.s3),
    )
    if (onShortcuts != null) Text(
        "Keyboard shortcuts", color = c.text, fontSize = 15.sp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onShortcuts)
            .padding(horizontal = OmSpace.s4, vertical = OmSpace.s3),
    )
    Text(
        if (backgroundPlay) "Background play is on: audio keeps playing when you leave the app."
        else "Background play is off. Turn it on in Settings to keep audio playing when you leave the app.",
        color = c.text3, fontSize = 12.sp, modifier = Modifier.padding(horizontal = OmSpace.s4, vertical = OmSpace.s2),
    )
}

private val shortcuts = listOf(
    "Space" to "Play / pause",
    "J / L" to "Back / forward (seek step)",
    "← / →" to "Back / forward 5 s",
    "↑ / ↓" to "Volume ±5%",
    "M" to "Mute",
    "[ / ]" to "Speed −/+ 0.25",
    "F" to "Full screen",
    "Esc" to "Close panel or exit full screen",
    "?" to "This list",
)

@Composable
internal fun ShortcutsPanel() {
    val c = OmTheme.colors
    shortcuts.forEach { (k, d) ->
        Row(Modifier.fillMaxWidth().padding(horizontal = OmSpace.s4, vertical = OmSpace.s2), verticalAlignment = Alignment.CenterVertically) {
            Text(k, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(96.dp).background(c.surface3, RoundedCornerShape(OmSpace.s1)).padding(horizontal = OmSpace.s2, vertical = OmSpace.s1))
            Spacer(Modifier.width(OmSpace.s3))
            Text(d, color = c.text2, fontSize = 14.sp, modifier = Modifier.weight(1f))
        }
    }
}
