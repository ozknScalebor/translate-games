package com.ozkan.altyazi.ocr

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ozkan.altyazi.core.SubtitleText
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * On-device OCR with the model bundled in the APK.
 * Lines noticeably smaller than the subtitle text (HUD labels, button hints) are dropped.
 */
class SubtitleOcr : Closeable {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Runs synchronously on the analysis thread so CameraX's keep-only-latest strategy provides back-pressure. */
    fun readBlocking(bitmap: Bitmap): String {
        val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 2, TimeUnit.SECONDS)
        return extract(result)
    }

    private fun extract(result: Text): String {
        val lines = result.textBlocks.flatMap { it.lines }.filter { it.boundingBox != null && it.text.isNotBlank() }
        if (lines.isEmpty()) return ""

        val tallest = lines.maxOf { it.boundingBox!!.height() }
        val rowHeight = max(1, tallest / 2)
        return lines
            .filter { it.boundingBox!!.height() >= tallest * 0.55f }
            .sortedWith(compareBy({ it.boundingBox!!.centerY() / rowHeight }, { it.boundingBox!!.left }))
            .joinToString(" ") { it.text }
            .let(SubtitleText::normalize)
    }

    override fun close() = recognizer.close()
}
