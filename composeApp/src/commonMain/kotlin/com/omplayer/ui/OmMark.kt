package com.omplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omplayer.theme.OmTheme

/**
 * The Playhead mark: a resume ring (75% arc from 12 o'clock, clockwise) with the playhead dot at its end.
 * Construction on a 100-unit grid: ring r=34, stroke 12, dot ⌀18 at (16,50). Below 24 dp the track drops out.
 */
@Composable
fun OmMark(
    size: Dp,
    modifier: Modifier = Modifier,
    arc: Color = OmTheme.colors.primary,
    track: Color = OmTheme.colors.surface3,
    dot: Color = OmTheme.colors.text,
    progress: Float = 0.75f,
) {
    val small = size <= 24.dp
    Canvas(modifier.size(size).semantics { contentDescription = "OM Player" }) {
        val u = this.size.minDimension / 100f
        val stroke = (if (small) 18f else 12f) * u
        val r = 34f * u
        val topLeft = Offset(center.x - r, center.y - r)
        val box = Size(r * 2, r * 2)
        if (!small) drawArc(track, 0f, 360f, false, topLeft, box, style = Stroke(stroke))
        drawArc(arc, -90f, 360f * progress, false, topLeft, box, style = Stroke(stroke, cap = StrokeCap.Round))
        // Dot sits on the arc's end point.
        val angle = (-90.0 + 360.0 * progress) * kotlin.math.PI / 180.0
        val end = Offset(center.x + r * kotlin.math.cos(angle).toFloat(), center.y + r * kotlin.math.sin(angle).toFloat())
        drawCircle(dot, radius = (if (small) 13f else 9f) * u, center = end)
    }
}

/** Mark + "OM Player" wordmark (OM ExtraBold, Player Medium at text2). */
@Composable
fun OmWordmark(markSize: Dp = 28.dp, fontSize: TextUnit = 20.sp, modifier: Modifier = Modifier) {
    val c = OmTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        OmMark(markSize)
        Spacer(Modifier.width(10.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = c.text)) { append("OM") }
                append(" ")
                withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = c.text2)) { append("Player") }
            },
            fontSize = fontSize,
            letterSpacing = (-0.3).sp,
        )
    }
}
