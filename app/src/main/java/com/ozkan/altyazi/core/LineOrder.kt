package com.ozkan.altyazi.core

import kotlin.math.abs
import kotlin.math.max

/**
 * Restores reading order for OCR boxes.
 *
 * OCR engines sometimes return one subtitle line as several boxes whose vertical positions differ
 * slightly (italics, descenders). Bucketing by fixed y-intervals can then split a line and produce
 * "good hearing. I told you". Instead, boxes are clustered into rows by vertical overlap, and each
 * row is read left to right.
 */
object LineOrder {

    data class Box(val centerY: Float, val height: Float, val left: Float)

    /** @return indices into [boxes] in reading order */
    fun order(boxes: List<Box>): List<Int> {
        val byY = boxes.indices.sortedBy { boxes[it].centerY }
        val rows = mutableListOf<MutableList<Int>>()
        var rowCenter = 0f
        var rowHeight = 0f
        for (i in byY) {
            val b = boxes[i]
            val sameRow = rows.isNotEmpty() && abs(b.centerY - rowCenter) <= 0.5f * max(b.height, rowHeight)
            if (sameRow) {
                val row = rows.last()
                row += i
                rowCenter = row.map { boxes[it].centerY }.average().toFloat()
                rowHeight = max(rowHeight, b.height)
            } else {
                rows += mutableListOf(i)
                rowCenter = b.centerY
                rowHeight = b.height
            }
        }
        return rows.flatMap { row -> row.sortedBy { boxes[it].left } }
    }
}
