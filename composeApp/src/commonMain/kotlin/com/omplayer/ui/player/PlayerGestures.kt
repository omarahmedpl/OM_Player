package com.omplayer.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

internal enum class Phase { Start, Update, End, Cancel }

internal data class GestureFlags(
    val brightness: Boolean,
    val volume: Boolean,
    val seek: Boolean,
    val pinch: Boolean,
    val longPress: Boolean,
)

internal class GestureCallbacks(
    /** xFrac = tap x / width. */
    val onTap: (xFrac: Float) -> Unit,
    /** deltaFrac: cumulative, fraction of height, positive = swipe up. */
    val onVertical: (leftHalf: Boolean, deltaFrac: Float, phase: Phase) -> Unit,
    /** deltaFrac: cumulative, fraction of width, positive = swipe right. */
    val onHorizontal: (deltaFrac: Float, phase: Phase) -> Unit,
    val onLongPress: (active: Boolean) -> Unit,
    /** scale relative to the pinch start. */
    val onPinch: (scale: Float, phase: Phase) -> Unit,
)

/**
 * One gesture state machine for the phone player: tap, vertical/horizontal drag, long-press, pinch.
 * Drags starting within 24 dp of either edge are ignored so they never fight the system back gesture.
 */
internal fun Modifier.playerGestures(flags: GestureFlags, callbacks: GestureCallbacks): Modifier = composed {
    val f by rememberUpdatedState(flags)
    val cb by rememberUpdatedState(callbacks)
    pointerInput(Unit) {
        val edge = 24.dp.toPx()
        val lpMs = viewConfiguration.longPressTimeoutMillis
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            val slop = viewConfiguration.touchSlop
            val onEdge = down.position.x < edge || down.position.x > w - edge
            val left = down.position.x < w / 2f
            // 0 undecided, 1 vertical, 2 horizontal, 3 long-press, 4 pinch, 5 ignored drag
            var mode = 0
            var now = down.uptimeMillis
            val t0 = now
            var pinchStart = 0f
            var lastScale = 1f
            var lastDy = 0f
            var lastDx = 0f
            while (true) {
                val ev = if (mode == 0 && f.longPress) {
                    withTimeoutOrNull((lpMs - (now - t0)).coerceAtLeast(1L)) { awaitPointerEvent() }
                } else awaitPointerEvent()
                if (ev == null) {
                    mode = 3
                    cb.onLongPress(true)
                    continue
                }
                now = ev.changes.first().uptimeMillis
                val pressed = ev.changes.filter { it.pressed }
                if (mode == 4 && pressed.size < 2) {
                    cb.onPinch(lastScale, Phase.End)
                    ev.changes.forEach { it.consume() }
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.changes.none { it.pressed }) break
                    }
                    return@awaitEachGesture
                }
                if (pressed.size >= 2 && f.pinch && mode != 3) {
                    val d = (pressed[0].position - pressed[1].position).getDistance()
                    if (mode != 4) {
                        if (mode == 1) cb.onVertical(left, lastDy, Phase.Cancel)
                        if (mode == 2) cb.onHorizontal(lastDx, Phase.Cancel)
                        mode = 4
                        pinchStart = d.coerceAtLeast(1f)
                        lastScale = 1f
                        cb.onPinch(1f, Phase.Start)
                    } else {
                        lastScale = d / pinchStart
                        cb.onPinch(lastScale, Phase.Update)
                    }
                    ev.changes.forEach { it.consume() }
                    continue
                }
                if (mode == 4) continue
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.first()
                if (!ch.pressed) {
                    when (mode) {
                        0 -> cb.onTap(down.position.x / w)
                        1 -> cb.onVertical(left, lastDy, Phase.End)
                        2 -> cb.onHorizontal(lastDx, Phase.End)
                        3 -> cb.onLongPress(false)
                    }
                    ch.consume()
                    break
                }
                val total: Offset = ch.position - down.position
                if (mode == 0 && total.getDistance() > slop) {
                    mode = when {
                        onEdge -> 5
                        abs(total.x) > abs(total.y) -> if (f.seek) 2 else 5
                        else -> if (if (left) f.brightness else f.volume) 1 else 5
                    }
                    if (mode == 1) cb.onVertical(left, 0f, Phase.Start)
                    if (mode == 2) cb.onHorizontal(0f, Phase.Start)
                }
                if (mode == 1) {
                    lastDy = -total.y / h
                    cb.onVertical(left, lastDy, Phase.Update)
                    ch.consume()
                } else if (mode == 2) {
                    lastDx = total.x / w
                    cb.onHorizontal(lastDx, Phase.Update)
                    ch.consume()
                }
            }
        }
    }
}

/** Desktop pointer: any move wakes the controls, wheel = volume, click / double-click on the video. */
internal fun Modifier.desktopMoveAndWheel(onMove: () -> Unit, onWheel: (Float) -> Unit): Modifier = composed {
    val move by rememberUpdatedState(onMove)
    val wheel by rememberUpdatedState(onWheel)
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                when (e.type) {
                    PointerEventType.Move -> move()
                    PointerEventType.Scroll -> {
                        val dy = e.changes.first().scrollDelta.y
                        if (dy != 0f) wheel(dy)
                        e.changes.forEach { it.consume() }
                    }
                }
            }
        }
    }
}

internal fun Modifier.desktopClicks(onClick: () -> Unit, onDoubleClick: () -> Unit): Modifier = composed {
    val click by rememberUpdatedState(onClick)
    val dbl by rememberUpdatedState(onDoubleClick)
    pointerInput(Unit) { detectTapGestures(onDoubleTap = { dbl() }, onTap = { click() }) }
}
