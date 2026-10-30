package com.ozkan.altyazi.ocr

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ozkan.altyazi.core.LineOrder
import java.io.Closeable
import java.util.concurrent.TimeUnit

/** Raw OCR output: line text joined in reading order, plus ML Kit's mean line confidence. */
data class OcrRead(val text: String, val confidence: Float?)

/**
 * On-device OCR with the model bundled in the APK.
 * Lines noticeably smaller than the subtitle text (HUD labels, button hints) are dropped.
 */
class SubtitleOcr : Closeable {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Runs synchronously on the analysis thread so CameraX's keep-only-latest strategy provides back-pressure. */
    fun readBlocking(bitmap: Bitmap): OcrRead {
        val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 2, TimeUnit.SECONDS)
        return extract(result)
    }

    private fun extract(result: Text): OcrRead {
        val lines = result.textBlocks.flatMap { it.lines }.filter { it.boundingBox != null && it.text.isNotBlank() }
        if (lines.isEmpty()) return OcrRead("", null)

        val tallest = lines.maxOf { it.boundingBox!!.height() }
        val candidates = lines.filter { it.boundingBox!!.height() >= tallest * 0.55f }
        val order = LineOrder.order(
            candidates.map { line ->
                val box = line.boundingBox!!
                LineOrder.Box(box.exactCenterY(), box.height().toFloat(), box.left.toFloat())
            }
        )
        val kept = order.map { candidates[it] }

        val confidence = kept.map { it.confidence }.filter { it > 0f }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        return OcrRead(kept.joinToString(" ") { it.text }, confidence)
    }

    override fun close() = recognizer.close()
}
