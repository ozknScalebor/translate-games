package com.ozkan.altyazi.core

import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlin.math.ln

/**
 * Frequency-ranked word list (most common first) used to repair OCR output.
 *
 * Word cost follows Zipf's law, `ln((rank + 1) * ln(N))`, the same model wordninja uses, so a
 * segmentation's total cost is lower when it is made of more common words.
 */
class WordDictionary(words: List<String>) {

    private val rank = HashMap<String, Int>(words.size * 2)
    private val logN = ln(words.size.toDouble().coerceAtLeast(2.0))

    /** Costs of words allowed as *parts* of a split: common words only, short ones whitelisted. */
    private val partCost = HashMap<String, Double>(COMMON_PARTS * 2)
    val maxPartLength: Int

    init {
        words.forEachIndexed { i, raw ->
            val w = raw.trim().lowercase()
            if (w.isNotEmpty()) rank.putIfAbsent(w, i)
        }
        var maxLen = 1
        for ((w, r) in rank) {
            if (r >= COMMON_PARTS) continue
            if (w.length <= 2 && w !in SHORT_WORDS) continue
            partCost[w] = zipf(r)
            if (w.length > maxLen) maxLen = w.length
        }
        maxPartLength = maxLen
    }

    val size: Int get() = rank.size

    fun contains(word: String): Boolean = rank.containsKey(word.lowercase())

    /** Zipf cost of a whole word, or null if unknown. */
    fun cost(word: String): Double? = rank[word.lowercase()]?.let(::zipf)

    fun rankOf(word: String): Int? = rank[word.lowercase()]

    /** Cost of [word] as a split part, or null if it may not be used as one. */
    internal fun partCost(word: String): Double? = partCost[word]

    private fun zipf(r: Int) = ln((r + 1) * logN)

    companion object {
        /** Split parts must be among the most common words; rarer words are too likely to be noise. */
        const val COMMON_PARTS = 30_000

        /** Real one/two-letter English words; everything else that short (ed, gh, ss…) is noise. */
        val SHORT_WORDS = setOf(
            "a", "i", "am", "an", "as", "at", "be", "by", "do", "go", "ha", "he", "hi", "if", "in", "is",
            "it", "me", "my", "no", "of", "oh", "ok", "on", "or", "ow", "so", "to", "uh", "um", "up",
            "us", "we", "ye", "yo",
        )

        fun load(stream: InputStream): WordDictionary =
            GZIPInputStream(stream).bufferedReader().useLines { lines -> WordDictionary(lines.toList()) }
    }
}
