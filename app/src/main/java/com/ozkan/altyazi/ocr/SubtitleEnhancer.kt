package com.ozkan.altyazi.ocr

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Builds a high-contrast copy of the subtitle crop for a second OCR pass.
 *
 * Streaming and game subtitles are almost always white (sometimes yellow) text with a dark outline
 * or box. On a busy, colorful scene the OCR model gets confused by background texture, so this pass
 * keeps only bright, unsaturated (white) or warm-bright (yellow) pixels as black ink on a white page.
 * The dark outline separates the letters from bright backgrounds, so even snowy or sunny scenes stay
 * readable. The pipeline OCRs both versions and keeps whichever read scores better.
 */
object SubtitleEnhancer {

    private const val INK = 0xFF000000.toInt()
    private const val PAPER = 0xFFFFFFFF.toInt()

    fun isolateBrightText(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val hi = max(r, max(g, b))
            val lo = min(r, min(g, b))
            val white = lo >= 165 && hi - lo <= 60
            val yellow = r >= 170 && g >= 150 && b <= 130 && r - b >= 70
            px[i] = if (white || yellow) INK else PAPER
        }

        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(px, 0, w, 0, 0, w, h) }
    }
}
