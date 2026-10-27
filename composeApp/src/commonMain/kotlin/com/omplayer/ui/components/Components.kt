package com.omplayer.ui.components

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omplayer.theme.OmMotion
import com.omplayer.theme.OmRadius
import com.omplayer.theme.OmSpace
import com.omplayer.theme.OmTheme
import kotlinx.coroutines.delay
import kotlin.math.round

// ---------- formatting ----------

/** "1:05:09" or "4:07". */
fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
}

/** "820 MB", "1.4 GB". */
fun formatSize(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    val mb = bytes / 1_000_000.0
    fun one(x: Double) = (round(x * 10) / 10.0).toString()
    return when {
        gb >= 1 -> "${one(gb)} GB"
        mb >= 1 -> "${mb.toLong()} MB"
        else -> "${bytes / 1000} KB"
    }
}

val TabularNums = TextStyle(fontFeatureSettings = "tnum")

// ---------- text ----------

@Composable
fun OmText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = OmTheme.colors.text,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
    tnum: Boolean = false,
) {
    Text(
        text, modifier, color = color, fontSize = size, fontWeight = weight, maxLines = maxLines,
        overflow = TextOverflow.Ellipsis, style = if (tnum) TabularNums else TextStyle.Default,
    )
}

// ---------- interaction ----------

/**
 * Click with hover + press feedback, no ripple:
 * hand cursor, a soft tint in [hoverShape] that fades in on hover (180 ms ease) and deepens on press,
 * and the 0.97 press scale (140 ms ease-out). Hover never fires on touch, so phones only see the press.
 */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    hoverShape: Shape = RoundedCornerShape(OmRadius.md),
    hoverColor: Color = OmTheme.colors.text,
): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val hovered by src.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) OmMotion.PressScale else 1f,
        tween(OmMotion.Press, easing = OmMotion.EaseOut), label = "press",
    )
    val tint by animateFloatAsState(
        when { !enabled -> 0f; pressed -> 0.12f; hovered -> 0.07f; else -> 0f },
        tween(OmMotion.Fast), label = "hover",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
        .hoverable(src, enabled)
        .background(hoverColor.copy(alpha = tint), hoverShape)
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)
}

/** Hand cursor for clickables that keep their own indication (rows with the default hover/ripple). */
fun Modifier.handCursor(enabled: Boolean = true): Modifier =
    if (enabled) pointerHoverIcon(PointerIcon.Hand) else this

/** List entrance: fade + 12dp rise, 40 ms stagger, only the first 8 items. */
@Composable
fun Modifier.staggerIn(index: Int): Modifier {
    val a = remember { Animatable(if (index >= 8) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (index < 8) {
            delay(index * 40L)
            a.animateTo(1f, tween(OmMotion.Base, easing = OmMotion.EaseOut))
        }
    }
    return this.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 12.dp.toPx() }
}

// ---------- buttons ----------

@Composable
fun OmIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = OmTheme.colors.text2,
    active: Boolean = false,
) {
    val c = OmTheme.colors
    Box(
        modifier.size(OmSpace.tap).clip(CircleShape)
            .background(if (active) c.primarySoft else Color.Transparent)
            .pressable(onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) c.primary else tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun OmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = OmTheme.colors
    Row(
        modifier.heightIn(min = OmSpace.tap)
            .clip(RoundedCornerShape(OmRadius.md))
            .background(if (primary) c.primary else c.surface2)
            .then(if (primary) Modifier else Modifier.border(1.dp, c.hairlineStrong, RoundedCornerShape(OmRadius.md)))
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .pressable(onClick, enabled)
            .padding(horizontal = OmSpace.s5),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (primary) c.onPrimary else c.text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(OmSpace.s2))
        }
        OmText(text, color = if (primary) c.onPrimary else c.text, weight = FontWeight.SemiBold, size = 15.sp)
    }
}

// ---------- chips ----------

/** Small overlay chip on thumbnails (duration, resolution). */
@Composable
fun MetaChip(text: String, modifier: Modifier = Modifier) {
    val c = OmTheme.colors
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(c.scrim).padding(horizontal = 6.dp, vertical = 2.dp),
    ) { OmText(text, color = c.onVideo, size = 11.sp, weight = FontWeight.Medium, tnum = true) }
}

/** Selectable filter chip. */
@Composable
fun OmChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = OmTheme.colors
    val shape = RoundedCornerShape(50)
    Box(
        modifier.heightIn(min = 36.dp).clip(shape)
            .background(if (selected) c.primarySoft else c.surface2)
            .border(1.dp, if (selected) c.primary else c.hairline, shape)
            .pressable(onClick, role = Role.Tab)
            .semantics { this.selected = selected }
            .padding(horizontal = OmSpace.s4),
        contentAlignment = Alignment.Center,
    ) { OmText(label, color = if (selected) c.primary else c.text2, weight = FontWeight.Medium, size = 13.sp) }
}

// ---------- segmented control ----------

@Composable
fun <T> OmSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = OmTheme.colors
    val outer = RoundedCornerShape(OmRadius.md)
    Row(
        modifier.clip(outer).background(c.surface2).border(1.dp, c.hairline, outer).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(OmRadius.sm))
                    .background(if (on) c.primary else Color.Transparent)
                    .pressable({ onSelect(value) }, role = Role.Tab)
                    .semantics { this.selected = on }
                    .padding(horizontal = OmSpace.s2),
                contentAlignment = Alignment.Center,
            ) {
                OmText(label, color = if (on) c.onPrimary else c.text2, weight = FontWeight.Medium, size = 13.sp)
            }
        }
    }
}

// ---------- settings rows ----------

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val c = OmTheme.colors
    Row(
        modifier.fillMaxWidth().padding(top = OmSpace.s6, bottom = OmSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OmText(text.uppercase(), color = c.text3, size = 12.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (trailing != null) OmText(trailing, color = c.text3, size = 12.sp, tnum = true)
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val c = OmTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .pressable({ onChange(!checked) }, role = Role.Switch)
            .padding(vertical = OmSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            OmText(title, size = 15.sp)
            if (subtitle != null) OmText(subtitle, color = c.text3, size = 13.sp, maxLines = 2)
        }
        Spacer(Modifier.width(OmSpace.s3))
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary,
                uncheckedThumbColor = c.text2, uncheckedTrackColor = c.surface3, uncheckedBorderColor = c.hairlineStrong,
            ),
        )
    }
}

/** Title (+ optional subtitle) above a control, e.g. a segmented control. */
@Composable
fun ControlRow(title: String, modifier: Modifier = Modifier, subtitle: String? = null, content: @Composable () -> Unit) {
    val c = OmTheme.colors
    Column(modifier.fillMaxWidth().padding(vertical = OmSpace.s2)) {
        OmText(title, size = 15.sp)
        if (subtitle != null) OmText(subtitle, color = c.text3, size = 13.sp, maxLines = 2)
        Spacer(Modifier.height(OmSpace.s2))
        content()
    }
}

// ---------- progress / thumbnail ----------

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 3.dp) {
    val c = OmTheme.colors
    Box(modifier.fillMaxWidth().height(height).background(c.hairlineStrong)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).background(c.primary))
    }
}

/** Deterministic gradient from the id hash. Real thumbnails come later. */
@Composable
fun ThumbnailPlaceholder(seed: String, modifier: Modifier = Modifier, isAudio: Boolean = false, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {}) {
    val c = OmTheme.colors
    val h = ((seed.hashCode() % 360) + 360) % 360
    val brush = remember(seed) {
        Brush.linearGradient(listOf(Color.hsv(h.toFloat(), 0.45f, 0.42f), Color.hsv(((h + 40) % 360).toFloat(), 0.55f, 0.20f)))
    }
    Box(modifier.background(brush), contentAlignment = Alignment.Center) {
        Icon(
            if (isAudio) Icons.Rounded.MusicNote else Icons.Rounded.PlayArrow, null,
            tint = c.onVideo.copy(alpha = 0.55f), modifier = Modifier.size(28.dp),
        )
        content()
    }
}

// ---------- text field ----------

/** Labelled single-line field with optional error text underneath. */
@Composable
fun OmTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    error: String? = null,
    leading: ImageVector? = null,
    description: String = label ?: placeholder,
    imeAction: ImeAction = ImeAction.Done,
    keyboardType: KeyboardType = KeyboardType.Text,
    onSubmit: () -> Unit = {},
) {
    val c = OmTheme.colors
    val shape = RoundedCornerShape(OmRadius.md)
    Column(modifier) {
        if (label != null) {
            OmText(label, color = c.text2, size = 13.sp, weight = FontWeight.Medium)
            Spacer(Modifier.height(OmSpace.s1 + 2.dp))
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = OmSpace.tap + 4.dp).clip(shape).background(c.surface2)
                .border(1.dp, if (error != null) c.danger else c.hairlineStrong, shape)
                .padding(horizontal = OmSpace.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                Icon(leading, null, tint = c.text3, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(OmSpace.s2))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) OmText(placeholder, color = c.text3, size = 15.sp)
                BasicTextField(
                    value = value, onValueChange = onValueChange, singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = keyboardType),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }, onSearch = { onSubmit() }, onGo = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
                )
            }
        }
        if (error != null) {
            Spacer(Modifier.height(OmSpace.s1))
            OmText(error, color = c.danger, size = 12.sp, maxLines = 2)
        }
    }
}
