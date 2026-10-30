package com.ozkan.altyazi.ocr

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Builds a high-contrast copy of the subtitle crop for a second OCR pass.
 *
 * Streaming and game subtitles are light, colorless (white/grey) text with a dark outline, shadow or
 * box. Bright *colored* scenery is the typical OCR failure: sand, sky or foliage either gets read as
 * letters or blurs into them. A pixel is therefore kept as ink only when it is both
 *  1. light and nearly neutral (low saturation), and
 *  2. close to a dark pixel, i.e. part of something that has an outline.
 * Bright backgrounds fail (1) when colored and (2) when they have no outline, so they drop out and
 * the model sees clean black letters on white. The pipeline OCRs this copy and the original crop and
 * keeps whichever read scores better, so scenes this heuristic does not fit lose nothing.
 */
object SubtitleEnhancer {

    private const val INK = 0xFF000000.toInt()
    private const val PAPER = 0xFFFFFFFF.toInt()

    /** Minimum value of the darkest RGB channel for a pixel to count as light text. */
    private const val MIN_LIGHT = 150
    /** Maximum channel spread (saturation proxy) for a pixel to count as colorless. */
    private const val MAX_SPREAD = 40
    /** Luminance at or below which a pixel counts as part of an outline, shadow or box. */
    private const val MAX_DARK_LUMA = 110

    fun isolateOutlinedText(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val light = BooleanArray(px.size)
        val dark = BooleanArray(px.size)
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val hi = max(r, max(g, b))
            val lo = min(r, min(g, b))
            light[i] = lo >= MIN_LIGHT && hi - lo <= MAX_SPREAD
            dark[i] = (r * 299 + g * 587 + b * 114) / 1000 <= MAX_DARK_LUMA
        }

        // Outline reach scales with glyph size; the crop height is a good proxy for it.
        val radius = (h / 22).coerceIn(2, 12)
        val nearDark = dilate(dark, w, h, radius)

        for (i in px.indices) px[i] = if (light[i] && nearDark[i]) INK else PAPER
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(px, 0, w, 0, 0, w, h) }
    }

    /** Square dilation as two separable running-window passes: O(pixels) regardless of radius. */
    internal fun dilate(mask: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
        val horizontal = BooleanArray(mask.size)
        for (y in 0 until h) {
            val row = y * w
            var count = 0
            for (x in 0 until min(r, w)) if (mask[row + x]) count++
            for (x in 0 until w) {
                val enter = x + r
                if (enter < w && mask[row + enter]) count++
                horizontal[row + x] = count > 0
                val leave = x - r
                if (leave >= 0 && mask[row + leave]) count--
            }
        }
        val out = BooleanArray(mask.size)
        for (x in 0 until w) {
            var count = 0
            for (y in 0 until min(r, h)) if (horizontal[y * w + x]) count++
            for (y in 0 until h) {
                val enter = y + r
                if (enter < h && horizontal[enter * w + x]) count++
                out[y * w + x] = count > 0
                val leave = y - r
                if (leave >= 0 && horizontal[leave * w + x]) count--
            }
        }
        return out
    }
}
