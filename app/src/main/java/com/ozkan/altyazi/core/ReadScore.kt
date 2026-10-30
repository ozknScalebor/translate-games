package com.ozkan.altyazi.core

import kotlin.math.max

/** A single OCR read of the subtitle region, scored by how plausible it looks. */
data class Read(val text: String, val score: Double) {
    companion object {
        val EMPTY = Read("", 0.0)
        fun of(text: String, confidence: Float? = null, dictionary: WordDictionary? = null) =
            Read(text, ReadScore.of(text, confidence, dictionary))
    }
}

/**
 * Heuristic quality score (roughly 0..1) for an OCR read.
 *
 * Camera reads of a TV are noisy: busy backgrounds produce stray symbols and words with digits mixed
 * into them. A read made of clean alphabetic words beats one full of fragments, which lets the
 * pipeline pick the better of several passes/frames without any language model.
 *
 * With a [WordDictionary] (English), half of the score is the share of tokens that are real words:
 * on real camera footage this is the signal that reliably tells a clean read from a garbled one.
 */
object ReadScore {

    private const val ALLOWED_PUNCTUATION = ".,!?'\"-–—:;…()¿¡"

    private val wordToken = Regex("[A-Za-z]+(?:'[a-z]+)?")

    fun of(text: String, confidence: Float? = null, dictionary: WordDictionary? = null): Double {
        val shape = shapeScore(text, confidence)
        if (dictionary == null || shape == 0.0) return shape
        val tokens = wordToken.findAll(text).map { it.value.substringBefore('\'') }.toList()
        if (tokens.isEmpty()) return shape
        val known = tokens.count { dictionary.contains(it) }.toDouble() / tokens.size
        return 0.5 * known + 0.5 * shape
    }

    private fun shapeScore(text: String, confidence: Float?): Double {
        if (text.isBlank()) return 0.0
        var good = 0
        var bad = 0
        for (token in text.split(' ')) {
            if (token.isBlank()) continue
            val core = token.trim { !it.isLetterOrDigit() }
            when {
                core.isEmpty() -> bad++
                core.all { it.isLetter() || it == '\'' || it == '-' } -> good++
                core.all { it.isDigit() || it == '.' || it == ',' || it == ':' } -> Unit // numbers, times
                else -> bad++
            }
        }
        val plausibility = good.toDouble() / max(1, good + bad)
        val junk = text.count { !it.isLetterOrDigit() && !it.isWhitespace() && it !in ALLOWED_PUNCTUATION }
            .toDouble() / text.length
        val ocrConfidence = confidence?.toDouble()?.coerceIn(0.0, 1.0) ?: plausibility
        return plausibility * 0.6 + ocrConfidence * 0.4 - junk * 2
    }
}
