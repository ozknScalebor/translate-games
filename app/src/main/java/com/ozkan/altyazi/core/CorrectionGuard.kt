package com.ozkan.altyazi.core

/**
 * Accepts an AI correction only if it is plausibly the *same* subtitle.
 *
 * Small language models sometimes paraphrase, answer the text, or invent words. OCR repairs only
 * ever touch spacing, a few letters and punctuation, so the letters of a genuine correction stay
 * almost identical to the input. Anything that drifts further is discarded and the
 * dictionary-repaired text is kept.
 */
object CorrectionGuard {

    private const val MIN_LETTER_SIMILARITY = 0.80
    private const val MIN_LENGTH_RATIO = 0.75
    private const val MAX_LENGTH_RATIO = 1.33

    /** @return the cleaned correction if acceptable and actually different, otherwise null */
    fun accept(input: String, output: String?): String? {
        val candidate = output
            ?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }
            ?.removeSurrounding("\"")
            ?.trim()
            ?: return null
        if (candidate.isEmpty()) return null

        val a = letters(input)
        val b = letters(candidate)
        if (a.isEmpty() || b.isEmpty()) return null
        val ratio = b.length.toDouble() / a.length
        if (ratio < MIN_LENGTH_RATIO || ratio > MAX_LENGTH_RATIO) return null
        if (TextSimilarity.ratio(a, b) < MIN_LETTER_SIMILARITY) return null

        return candidate.takeIf { it != input.trim() }
    }

    private fun letters(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()
}
