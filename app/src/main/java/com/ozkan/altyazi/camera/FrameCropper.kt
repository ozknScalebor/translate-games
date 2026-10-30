package com.ozkan.altyazi.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.ozkan.altyazi.core.RoiMapping
import com.ozkan.altyazi.data.Roi
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Extracts the subtitle region from a camera frame.
 * Crops on the raw frame first and only rotates/scales the small crop, which is far cheaper than
 * rotating the full frame.
 */
object FrameCropper {

    /** Plenty for OCR; larger crops cost time without improving accuracy. */
    private const val MAX_OCR_WIDTH = 1600

    fun crop(image: ImageProxy, roi: Roi): Bitmap {
        val rotation = image.imageInfo.rotationDegrees
        val frame = image.toBitmap()
        val (l, t, r, b) = RoiMapping.displayToSensor(roi.left, roi.top, roi.right, roi.bottom, rotation).toList()

        val x = (l * frame.width).roundToInt().coerceIn(0, frame.width - 1)
        val y = (t * frame.height).roundToInt().coerceIn(0, frame.height - 1)
        val w = ((r - l) * frame.width).roundToInt().coerceIn(1, frame.width - x)
        val h = ((b - t) * frame.height).roundToInt().coerceIn(1, frame.height - y)

        val uprightWidth = if (rotation % 180 == 0) w else h
        val scale = min(1f, MAX_OCR_WIDTH.toFloat() / uprightWidth)

        val matrix = Matrix()
        if (rotation != 0) matrix.postRotate(rotation.toFloat())
        if (scale < 1f) matrix.postScale(scale, scale)

        return if (matrix.isIdentity) Bitmap.createBitmap(frame, x, y, w, h)
        else Bitmap.createBitmap(frame, x, y, w, h, matrix, true)
    }
}
