package com.ozkan.altyazi.core

import kotlin.math.max
import kotlin.math.min

/** Case-insensitive Levenshtein similarity (0..1), used to tell OCR jitter apart from real text changes. */
object TextSimilarity {

    private const val MAX_LENGTH = 400

    fun ratio(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val x = a.lowercase().take(MAX_LENGTH)
        val y = b.lowercase().take(MAX_LENGTH)
        if (x == y) return 1.0

        var prev = IntArray(y.length + 1) { it }
        var cur = IntArray(y.length + 1)
        for (i in 1..x.length) {
            cur[0] = i
            val cx = x[i - 1]
            for (j in 1..y.length) {
                val cost = if (cx == y[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        return 1.0 - prev[y.length].toDouble() / max(x.length, y.length)
    }
}
