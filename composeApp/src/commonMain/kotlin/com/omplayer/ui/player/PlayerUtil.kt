package com.omplayer.ui.player

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omplayer.theme.OmMotion
import com.omplayer.theme.OmSpace
import com.omplayer.theme.OmTheme
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class PlayerPanel { Audio, Subtitles, Speed, More, Shortcuts }

internal enum class SleepMode(val label: String) {
    Off("Off"), M15("15 min"), M30("30 min"), M60("60 min"), EndOfVideo("End of video")
}

internal fun formatTime(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    fun p(v: Long) = if (v < 10) "0$v" else "$v"
    return if (h > 0) "$h:${p(m)}:${p(sec)}" else "$m:${p(sec)}"
}

internal fun formatSigned(ms: Long): String = (if (ms < 0) "-" else "+") + formatTime(abs(ms))

internal fun speedLabel(f: Float): String {
    val r = (f * 100).roundToInt() / 100f
    return if (r == r.toInt().toFloat()) "${r.toInt()}x" else "${r}x"
}

internal fun delayLabel(ms: Long): String = when {
    ms == 0L -> "0 ms"
    ms > 0 -> "+$ms ms"
    else -> "$ms ms"
}

/** Player click: hand cursor, circular hover tint over video, 0.97 press scale (140 ms ease-out). */
internal fun Modifier.pressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    hoverShape: Shape = CircleShape,
    onClick: () -> Unit,
): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val hovered by src.collectIsHoveredAsState()
    val s by animateFloatAsState(if (pressed) OmMotion.PressScale else 1f, tween(OmMotion.Press, easing = OmMotion.EaseOut))
    val tint by animateFloatAsState(
        when { !enabled -> 0f; pressed -> 0.22f; hovered -> 0.14f; else -> 0f }, tween(OmMotion.Fast),
    )
    this.graphicsLayer { scaleX = s; scaleY = s }
        .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
        .hoverable(src, enabled)
        .background(Color.White.copy(alpha = tint), hoverShape)
        .clickable(interactionSource = src, indication = null, enabled = enabled, role = role, onClick = onClick)
}

@Composable
internal fun PIconButton(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = OmSpace.tap,
    iconSize: Dp = 24.dp,
    tint: Color = OmTheme.colors.onVideo,
    active: Boolean = false,
    enabled: Boolean = true,
    filled: Color? = null,
) {
    val c = OmTheme.colors
    Box(
        modifier.size(size).pressable(enabled = enabled, onClick = onClick).clip(CircleShape)
            .background(filled ?: if (active) c.primarySoft else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, Modifier.size(iconSize), tint = if (enabled) (if (active) c.primaryHover else tint) else tint.copy(alpha = 0.35f))
    }
}

@Composable
internal fun PTextButton(label: String, desc: String, onClick: () -> Unit, active: Boolean = false) {
    val c = OmTheme.colors
    Box(
        Modifier.height(OmSpace.tap).pressable(onClick = onClick).clip(RoundedCornerShape(50))
            .background(if (active) c.primarySoft else Color.Transparent).padding(horizontal = OmSpace.s3),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) c.primaryHover else c.onVideo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(0.dp))
    }
}

@Composable
internal fun HudPill(icon: ImageVector?, text: String, fraction: Float? = null, boost: Boolean = false, modifier: Modifier = Modifier) {
    val c = OmTheme.colors
    Row(
        modifier.clip(RoundedCornerShape(50)).background(c.glass).padding(horizontal = OmSpace.s4, vertical = OmSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (boost) c.warning else c.onVideo)
            Box(Modifier.size(OmSpace.s2))
        }
        Text(text, color = if (boost) c.warning else c.onVideo, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            style = Tnum)
        if (fraction != null) {
            Box(Modifier.size(OmSpace.s3))
            Box(Modifier.size(96.dp, 4.dp).clip(RoundedCornerShape(50)).background(c.hairlineStrong)) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).background(if (boost) c.warning else c.onVideo))
            }
        }
    }
}

/** Tabular numerals so times don't jitter. */
internal val Tnum = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
