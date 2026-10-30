package com.ozkan.altyazi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Kamera görüntüsü üzerinde altyazı bölgesini seçmek için sürüklenebilir kutu.
 * İçinden tutunca taşınır, köşelerinden tutunca boyutlanır.
 * Bölge, görüntüye göre 0..1 oranları olarak tutulur (çözünürlükten bağımsız).
 */
class RoiOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Normalize bölge: varsayılan olarak ekranın alt kısmı (oyun altyazıları genelde orada) */
    val roi = RectF(0.10f, 0.72f, 0.90f, 0.95f)
    var onRoiChanged: ((RectF) -> Unit)? = null

    private val box = Paint().apply {
        color = Color.parseColor("#FFC107"); style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val shade = Paint().apply { color = Color.parseColor("#88000000") }
    private val handle = Paint().apply { color = Color.parseColor("#FFC107") }
    private val handleR = 18f
    private val touchSlop = 48f

    private enum class Mode { NONE, MOVE, TL, TR, BL, BR }
    private var mode = Mode.NONE
    private var lastX = 0f
    private var lastY = 0f

    private fun px(): RectF = RectF(roi.left * width, roi.top * height, roi.right * width, roi.bottom * height)

    override fun onDraw(c: Canvas) {
        val r = px()
        c.drawRect(0f, 0f, width.toFloat(), r.top, shade)
        c.drawRect(0f, r.bottom, width.toFloat(), height.toFloat(), shade)
        c.drawRect(0f, r.top, r.left, r.bottom, shade)
        c.drawRect(r.right, r.top, width.toFloat(), r.bottom, shade)
        c.drawRect(r, box)
        for ((x, y) in listOf(r.left to r.top, r.right to r.top, r.left to r.bottom, r.right to r.bottom)) {
            c.drawCircle(x, y, handleR, handle)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val r = px()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mode = when {
                    near(e, r.left, r.top) -> Mode.TL
                    near(e, r.right, r.top) -> Mode.TR
                    near(e, r.left, r.bottom) -> Mode.BL
                    near(e, r.right, r.bottom) -> Mode.BR
                    r.contains(e.x, e.y) -> Mode.MOVE
                    else -> Mode.NONE
                }
                lastX = e.x; lastY = e.y
                return mode != Mode.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.x - lastX) / width
                val dy = (e.y - lastY) / height
                lastX = e.x; lastY = e.y
                val min = 0.05f
                when (mode) {
                    Mode.MOVE -> {
                        val w = roi.width(); val h = roi.height()
                        roi.left = (roi.left + dx).coerceIn(0f, 1f - w)
                        roi.top = (roi.top + dy).coerceIn(0f, 1f - h)
                        roi.right = roi.left + w; roi.bottom = roi.top + h
                    }
                    Mode.TL -> { roi.left = (roi.left + dx).coerceIn(0f, roi.right - min); roi.top = (roi.top + dy).coerceIn(0f, roi.bottom - min) }
                    Mode.TR -> { roi.right = (roi.right + dx).coerceIn(roi.left + min, 1f); roi.top = (roi.top + dy).coerceIn(0f, roi.bottom - min) }
                    Mode.BL -> { roi.left = (roi.left + dx).coerceIn(0f, roi.right - min); roi.bottom = (roi.bottom + dy).coerceIn(roi.top + min, 1f) }
                    Mode.BR -> { roi.right = (roi.right + dx).coerceIn(roi.left + min, 1f); roi.bottom = (roi.bottom + dy).coerceIn(roi.top + min, 1f) }
                    Mode.NONE -> return false
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mode != Mode.NONE) onRoiChanged?.invoke(RectF(roi))
                mode = Mode.NONE
                return true
            }
        }
        return false
    }

    private fun near(e: MotionEvent, x: Float, y: Float) = abs(e.x - x) < touchSlop && abs(e.y - y) < touchSlop

    fun setRoi(r: RectF) { roi.set(r); invalidate() }
}
