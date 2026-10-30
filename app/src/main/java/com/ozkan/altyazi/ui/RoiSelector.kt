package com.ozkan.altyazi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import com.ozkan.altyazi.data.Roi
import com.ozkan.altyazi.ui.theme.AppColors
import kotlin.math.abs

private enum class DragMode { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * Subtitle region picker: drag inside to move, drag a corner to resize.
 * Local state updates while dragging; the region is persisted when the finger lifts.
 */
@Composable
fun RoiSelector(
    roi: Roi,
    onRoiChanged: (Roi) -> Unit,
    modifier: Modifier = Modifier,
) {
    var current by remember { mutableStateOf(roi) }
    var dragging by remember { mutableStateOf(false) }
    val latestOnChanged by rememberUpdatedState(onRoiChanged)

    LaunchedEffect(roi) { if (!dragging) current = roi }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            var mode = DragMode.NONE
            detectDragGestures(
                onDragStart = { start ->
                    val r = current.toRect(size.width.toFloat(), size.height.toFloat())
                    val slop = 56f
                    fun near(p: Offset) = abs(start.x - p.x) < slop && abs(start.y - p.y) < slop
                    mode = when {
                        near(r.topLeft) -> DragMode.TOP_LEFT
                        near(r.topRight) -> DragMode.TOP_RIGHT
                        near(r.bottomLeft) -> DragMode.BOTTOM_LEFT
                        near(r.bottomRight) -> DragMode.BOTTOM_RIGHT
                        r.contains(start) -> DragMode.MOVE
                        else -> DragMode.NONE
                    }
                    dragging = mode != DragMode.NONE
                },
                onDragEnd = {
                    if (dragging) latestOnChanged(current)
                    dragging = false
                    mode = DragMode.NONE
                },
                onDragCancel = { dragging = false; mode = DragMode.NONE },
                onDrag = { change, delta ->
                    if (mode == DragMode.NONE) return@detectDragGestures
                    change.consume()
                    current = current.drag(mode, delta.x / size.width, delta.y / size.height)
                },
            )
        },
    ) {
        val r = current.toRect(size.width, size.height)
        val corner = CornerRadius(14f, 14f)

        // Dim everything outside the region
        val hole = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(r, corner)) }
        clipPath(hole, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.55f))
        }

        val accent = AppColors.Accent
        drawRoundRect(
            color = accent,
            topLeft = r.topLeft,
            size = Size(r.width, r.height),
            cornerRadius = corner,
            style = Stroke(width = if (dragging) 5f else 3.5f),
        )

        // L-shaped corner handles
        val len = 26f
        val stroke = 8f
        listOf(
            r.topLeft to Offset(1f, 1f),
            r.topRight to Offset(-1f, 1f),
            r.bottomLeft to Offset(1f, -1f),
            r.bottomRight to Offset(-1f, -1f),
        ).forEach { (p, dir) ->
            drawLine(accent, p, p + Offset(len * dir.x, 0f), stroke, StrokeCap.Round)
            drawLine(accent, p, p + Offset(0f, len * dir.y), stroke, StrokeCap.Round)
        }
    }
}

private fun Roi.toRect(w: Float, h: Float) = Rect(left * w, top * h, right * w, bottom * h)

private fun Roi.drag(mode: DragMode, dx: Float, dy: Float): Roi {
    val min = Roi.MIN_SIZE
    return when (mode) {
        DragMode.MOVE -> {
            val nl = (left + dx).coerceIn(0f, 1f - width)
            val nt = (top + dy).coerceIn(0f, 1f - height)
            Roi(nl, nt, nl + width, nt + height)
        }
        DragMode.TOP_LEFT -> copy(left = (left + dx).coerceIn(0f, right - min), top = (top + dy).coerceIn(0f, bottom - min))
        DragMode.TOP_RIGHT -> copy(right = (right + dx).coerceIn(left + min, 1f), top = (top + dy).coerceIn(0f, bottom - min))
        DragMode.BOTTOM_LEFT -> copy(left = (left + dx).coerceIn(0f, right - min), bottom = (bottom + dy).coerceIn(top + min, 1f))
        DragMode.BOTTOM_RIGHT -> copy(right = (right + dx).coerceIn(left + min, 1f), bottom = (bottom + dy).coerceIn(top + min, 1f))
        DragMode.NONE -> this
    }
}
