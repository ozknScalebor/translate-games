package com.ozkan.altyazi.core

/**
 * Dictionary-based repair of English OCR output, the main fix for camera reads of a TV.
 *
 * Measured on real camera footage, OCR gets nearly every letter right but drops word spaces
 * ("sothey swallowthingswhole", "Mydad's setting upa tent"), which the translation model cannot
 * recover from. This repairs, in order:
 *  1. missing spaces after punctuation ("one,Pip" -> "one, Pip");
 *  2. camel-case joins ("orPop" -> "or Pop");
 *  3. glued words, by Zipf-cost segmentation ("swallowthingswhole" -> "swallow things whole");
 *  4. single-letter typos in unknown lowercase words ("ypu" -> "you").
 *
 * A known word is only split when the split is clearly more likely than the word itself
 * ([SPLIT_MARGIN]), so "into", "upon", "anyway" or "hardworking" stay intact while "upa", "ina"
 * or "inthe" are split. Capitalized known words are treated as names and never split.
 */
class SpellingRepair(private val dictionary: WordDictionary) {

    private val punctuationGlue = Regex("([,.!?;:])(?=\\p{L})")
    private val camelGlue = Regex("(?<=\\p{Ll})(?=\\p{Lu})")
    private val wordToken = Regex("[A-Za-z]+(?:'[a-z]{1,2})?")

    fun repair(text: String): String {
        val spaced = text.replace(punctuationGlue, "$1 ").replace(camelGlue, " ")
        return spaced.replace(wordToken) { m -> repairToken(m.value) }
    }

    private fun repairToken(token: String): String {
        val apostrophe = token.indexOf('\'')
        val core = if (apostrophe >= 0) token.substring(0, apostrophe) else token
        val suffix = if (apostrophe >= 0) token.substring(apostrophe) else ""
        if (core.length < 3) return token

        val lower = core.lowercase()
        val known = dictionary.cost(lower)
        val looksLikeName = core[0].isUpperCase() && core.substring(1).all { it.isLowerCase() }
        if (known != null && looksLikeName) return token

        segment(lower)?.let { (parts, cost) ->
            if (parts.size > 1 && (known == null || cost + SPLIT_MARGIN < known)) {
                return restoreCase(core, parts).joinToString(" ") + suffix
            }
        }

        if (known == null && core.all { it.isLowerCase() } && core.length >= 3) {
            correctTypo(lower)?.let { return it + suffix }
        }
        return token
    }

    /** Minimum-cost split of [word] into dictionary parts, or null if no full split exists. */
    internal fun segment(word: String): Pair<List<String>, Double>? {
        val n = word.length
        val best = DoubleArray(n + 1) { Double.POSITIVE_INFINITY }
        val back = IntArray(n + 1)
        best[0] = 0.0
        for (i in 1..n) {
            for (k in maxOf(0, i - dictionary.maxPartLength) until i) {
                if (best[k] == Double.POSITIVE_INFINITY) continue
                val c = dictionary.partCost(word.substring(k, i)) ?: continue
                if (best[k] + c < best[i]) {
                    best[i] = best[k] + c
                    back[i] = k
                }
            }
        }
        if (best[n] == Double.POSITIVE_INFINITY) return null
        val parts = ArrayList<String>()
        var i = n
        while (i > 0) {
            parts += word.substring(back[i], i)
            i = back[i]
        }
        parts.reverse()
        return parts to best[n]
    }

    /** The most frequent common word one edit away (delete, transpose, replace, insert), if any. */
    internal fun correctTypo(word: String): String? {
        var best: String? = null
        var bestRank = Int.MAX_VALUE
        fun consider(candidate: String) {
            val r = dictionary.rankOf(candidate) ?: return
            if (r < TYPO_MAX_RANK && r < bestRank) {
                best = candidate
                bestRank = r
            }
        }
        val chars = word.toCharArray()
        for (i in chars.indices) consider(word.removeRange(i, i + 1))
        for (i in 0 until chars.size - 1) {
            val c = chars.copyOf()
            c[i] = chars[i + 1]; c[i + 1] = chars[i]
            consider(String(c))
        }
        for (i in chars.indices) for (letter in 'a'..'z') {
            if (letter == chars[i]) continue
            val c = chars.copyOf(); c[i] = letter
            consider(String(c))
        }
        for (i in 0..chars.size) for (letter in 'a'..'z') consider(word.substring(0, i) + letter + word.substring(i))
        return best
    }

    private fun restoreCase(original: String, parts: List<String>): List<String> {
        var i = 0
        return parts.map { p -> original.substring(i, i + p.length).also { i += p.length } }
    }

    companion object {
        /** How much likelier (in Zipf cost) a split must be than keeping a known word intact. */
        const val SPLIT_MARGIN = 2.5
        /** Typo corrections must land on a common word. */
        const val TYPO_MAX_RANK = 30_000
    }
}
