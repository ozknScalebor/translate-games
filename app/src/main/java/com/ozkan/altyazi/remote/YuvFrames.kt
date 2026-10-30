package com.ozkan.altyazi.remote

import android.graphics.Bitmap
import android.media.Image
import com.ozkan.altyazi.data.Roi
import java.nio.ByteBuffer

/**
 * Converts decoded YUV 4:2:0 video frames to RGB bitmaps. Only the part that is needed is
 * converted: the subtitle region at full resolution for OCR, and a small preview for the screen.
 * Colors use BT.709 limited range, which PlayStation streams use.
 */
object YuvFrames {

    /** Full-resolution RGB copy of [roi] (normalized to the visible frame). */
    fun crop(image: Image, roi: Roi): Bitmap {
        val visible = image.cropRect
        val rect = roi.toPixels(visible.left, visible.top, visible.width(), visible.height())
        val pixels = IntArray(rect.width * rect.height)
        convert(image, rect.x, rect.y, rect.width, rect.height, step = 1, out = pixels)
        return Bitmap.createBitmap(pixels, rect.width, rect.height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Downscaled preview (nearest neighbour, every [step]th pixel). [reuse] is filled when it has the
     * right size, so steady-state previews allocate nothing.
     */
    fun preview(image: Image, step: Int, reuse: Bitmap?, scratch: IntArray?): Pair<Bitmap, IntArray> {
        val visible = image.cropRect
        val w = visible.width() / step
        val h = visible.height() / step
        val pixels = scratch?.takeIf { it.size == w * h } ?: IntArray(w * h)
        convert(image, visible.left, visible.top, w, h, step, pixels)
        val bitmap = reuse?.takeIf { it.width == w && it.height == h && !it.isRecycled }
            ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap to pixels
    }

    /** Converts a [width]×[height] output area starting at ([x0], [y0]), sampling every [step]th source pixel. */
    private fun convert(image: Image, x0: Int, y0: Int, width: Int, height: Int, step: Int, out: IntArray) {
        val planes = image.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val cRowStride = uPlane.rowStride
        val cPixelStride = uPlane.pixelStride

        val srcWidth = (width - 1) * step + 1
        val yRow = ByteArray((srcWidth - 1) * yPixelStride + 1)
        val chromaWidth = (x0 + srcWidth - 1) / 2 - x0 / 2 + 1
        val cRowLen = (chromaWidth - 1) * cPixelStride + 1
        val uRow = ByteArray(cRowLen)
        val vRow = ByteArray(cRowLen)

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        var lastChromaRow = -1

        for (row in 0 until height) {
            val sy = y0 + row * step
            readRow(yBuf, sy * yRowStride + x0 * yPixelStride, yRow)
            val cy = sy / 2
            if (cy != lastChromaRow) {
                val cOffset = cy * cRowStride + (x0 / 2) * cPixelStride
                readRow(uBuf, cOffset, uRow)
                readRow(vBuf, cOffset, vRow)
                lastChromaRow = cy
            }
            val outRow = row * width
            for (col in 0 until width) {
                val sx = col * step
                val yy = (yRow[sx * yPixelStride].toInt() and 0xFF) - 16
                val ci = ((x0 + sx) / 2 - x0 / 2) * cPixelStride
                val u = (uRow[ci].toInt() and 0xFF) - 128
                val v = (vRow[ci].toInt() and 0xFF) - 128
                // BT.709 limited range, fixed point (×1024).
                val c = 1192 * yy.coerceAtLeast(0)
                val r = (c + 1836 * v) shr 10
                val g = (c - 218 * u - 546 * v) shr 10
                val b = (c + 2163 * u) shr 10
                out[outRow + col] = (0xFF shl 24) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)
            }
        }
    }

    /** Copies up to dst.size bytes from [offset]; a short last row (buffer end) is tolerated. */
    private fun readRow(buffer: ByteBuffer, offset: Int, dst: ByteArray) {
        val len = minOf(dst.size, buffer.limit() - offset)
        if (len <= 0) return
        buffer.position(offset)
        buffer.get(dst, 0, len)
    }

    private fun clamp(v: Int) = if (v < 0) 0 else if (v > 255) 255 else v
}
