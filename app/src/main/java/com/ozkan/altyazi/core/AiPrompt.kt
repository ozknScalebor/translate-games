package com.ozkan.altyazi.core

/**
 * Prompting and output validation for on-device LLM subtitle translation.
 *
 * The model receives the OCR text (which may still contain recognition mistakes) together with the
 * last few subtitles and their translations, so names, forms of address and tone stay consistent.
 * Its reply is accepted only if it looks like a translation of this one subtitle.
 */
object AiPrompt {

    data class ContextLine(val source: String, val translation: String)

    fun systemInstruction(sourceLanguage: String, targetLanguage: String): String =
        "You translate video game and TV subtitles from $sourceLanguage to $targetLanguage. " +
            "The $sourceLanguage text was read from a camera image by OCR and may contain spacing, " +
            "spelling or punctuation mistakes; silently correct them. " +
            "Translate naturally, the way a native $targetLanguage speaker would say it in a game " +
            "or show, and keep it as short as a subtitle. Keep character names unchanged. " +
            "Reply with the $targetLanguage translation only: no quotes, notes or explanations."

    fun userMessage(subtitle: String, context: List<ContextLine>): String = buildString {
        if (context.isNotEmpty()) {
            append("Earlier subtitles, for context only (do not translate them again):\n")
            context.forEach { append("- ").append(it.source).append(" => ").append(it.translation).append('\n') }
            append('\n')
        }
        append("Subtitle to translate:\n").append(subtitle)
    }

    private val chattyPrefix = Regex(
        "^(here(?:'s| is)[^:]*:|translation:|translated:|çeviri:|türkçe:|sure[,!]?)\\s*",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Cleans the model reply and checks it is plausibly a translation of [source].
     * @return the translation, or null if the reply should be discarded
     */
    fun acceptTranslation(source: String, reply: String?, sameLanguage: Boolean = false): String? {
        val lines = reply
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toList()
            .orEmpty()
        if (lines.isEmpty()) return null

        // Subtitles have at most a few lines; anything longer is the model rambling.
        val text = lines.take(MAX_LINES).joinToString("\n")
            .replace(chattyPrefix, "")
            .trim()
            .removeSurrounding("\"").removeSurrounding("“", "”").removeSurrounding("«", "»")
            .trim()
        if (text.isEmpty()) return null

        val ratio = text.length.toDouble() / source.length.coerceAtLeast(1)
        if (ratio < MIN_LENGTH_RATIO || ratio > MAX_LENGTH_RATIO) return null

        // An echo of the source means the model did not translate.
        if (!sameLanguage && TextSimilarity.ratio(text, source) > ECHO_SIMILARITY) return null
        return text
    }

    private const val MAX_LINES = 3
    private const val MIN_LENGTH_RATIO = 0.3
    private const val MAX_LENGTH_RATIO = 3.0
    private const val ECHO_SIMILARITY = 0.9
}
