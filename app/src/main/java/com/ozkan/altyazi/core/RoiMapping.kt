package com.ozkan.altyazi.core

import kotlin.math.max
import kotlin.math.min

/**
 * Maps a normalized rectangle on the displayed (upright) frame onto the raw sensor-oriented frame,
 * so only the small subtitle crop has to be rotated instead of the whole frame.
 */
object RoiMapping {

    /** @return [left, top, right, bottom] in 0..1, relative to the sensor frame */
    fun displayToSensor(left: Float, top: Float, right: Float, bottom: Float, rotationDegrees: Int): FloatArray {
        val rotation = ((rotationDegrees % 360) + 360) % 360
        fun map(u: Float, v: Float): Pair<Float, Float> = when (rotation) {
            90 -> v to 1f - u
            180 -> 1f - u to 1f - v
            270 -> 1f - v to u
            else -> u to v
        }
        val (x1, y1) = map(left, top)
        val (x2, y2) = map(right, bottom)
        return floatArrayOf(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
    }
}
