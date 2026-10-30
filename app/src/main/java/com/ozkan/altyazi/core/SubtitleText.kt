package com.ozkan.altyazi.core

/** Text helpers that turn raw OCR output into clean, translatable subtitle text. */
object SubtitleText {

    /** Glyphs OCR often hallucinates on camera frames that never appear in real subtitles. */
    private val junk = Regex("[|_~`«»•■□▪●◆▶◀]")
    private val spaces = Regex("\\s+")
    /** Word split across lines: "some- thing" -> "something". */
    private val hyphenBreak = Regex("(\\p{L})- (\\p{Ll})")
    /** Speaker prefix such as "ELLIE: Text" or "Ellie: Text". */
    private val speaker = Regex("^([\\p{Lu}][\\p{L}0-9.'\\- ]{0,22}):\\s+(.+)$")
    /** A lone pipe is almost always a misread capital I in English subtitles. */
    private val lonePipe = Regex("(?<=^|\\s)\\|(?=\\s|$)")
    private val token = Regex("\\S+")
    private val sentenceStart = Regex("(^|[.!?…]\\s+|^[-–—]\\s*|\\s[-–—]\\s+)(\\p{Ll})")
    private val englishI = Regex("\\bi\\b")
    /** Dialogue lines: "- Where are you? - Here." */
    private val dialogueDash = Regex("(?:^|\\s)[-–—]\\s+(?=\\S)")

    data class Line(val speaker: String?, val body: String) {
        fun format(translatedBody: String): String =
            if (speaker == null) translatedBody else "$speaker: $translatedBody"
    }

    /** Full cleanup applied to every OCR read before scoring and voting. */
    fun clean(raw: String, english: Boolean): String {
        var text = raw
        if (english) text = text.replace(lonePipe, "I")
        text = normalize(text)
        return fixOcrConfusions(text, english)
    }

    fun normalize(raw: String): String = raw
        .replace(junk, " ")
        .replace(spaces, " ")
        .trim()
        .replace(hyphenBreak, "$1$2")

    /**
     * Fixes digit/letter confusions inside words ("1eave" -> "leave", "G0D" -> "GOD") and, for
     * English, a lone lowercase "l" that is really "I".
     */
    fun fixOcrConfusions(text: String, english: Boolean): String = text.replace(token) { m ->
        val t = m.value
        val start = t.indexOfFirst { it.isLetterOrDigit() }
        val end = t.indexOfLast { it.isLetterOrDigit() }
        if (start < 0) return@replace t
        val core = t.substring(start, end + 1)
        val fixed = when {
            english && core == "l" -> "I"
            ordinal.matches(core) -> core
            core.any { it.isDigit() } && core.any { it.isLetter() } -> fixDigitsInWord(core)
            else -> core
        }
        t.substring(0, start) + fixed + t.substring(end + 1)
    }

    private val ordinal = Regex("\\d+(st|nd|rd|th|s)", RegexOption.IGNORE_CASE)

    /**
     * Only replaces a digit when its surroundings clearly make it part of a word: between two
     * letters ("G0D"), or at a word edge next to two lowercase letters ("1eave", "unti1").
     * Model names such as "PS5" or "4K" are left alone.
     */
    private fun fixDigitsInWord(word: String): String {
        val upper = word.filter { it.isLetter() }.all { it.isUpperCase() }
        val chars = word.toCharArray()
        for (i in chars.indices) {
            val c = chars[i]
            if (c != '0' && c != '1' && c != '5') continue
            val prev = word.getOrNull(i - 1)
            val next = word.getOrNull(i + 1)
            val inside = prev?.isLetter() == true && next?.isLetter() == true
            val leading = i == 0 && word.getOrNull(1)?.isLowerCase() == true && word.getOrNull(2)?.isLowerCase() == true
            val trailing = i == word.lastIndex && prev?.isLowerCase() == true && word.getOrNull(i - 2)?.isLowerCase() == true
            if (!inside && !leading && !trailing) continue
            chars[i] = when (c) {
                '0' -> if (upper) 'O' else 'o'
                '1' -> if (upper) 'I' else 'l'
                else -> if (upper) 'S' else 's'
            }
        }
        return String(chars)
    }

    /**
     * ALL-CAPS subtitles translate poorly (models treat them as acronyms or shouting),
     * so they are converted to sentence case first.
     */
    fun toSentenceCase(text: String, english: Boolean): String {
        val letters = text.filter { it.isLetter() }
        if (letters.length < 4 || letters.count { it.isUpperCase() } < letters.length * 0.8) return text
        var result = text.lowercase().replace(sentenceStart) { it.groupValues[1] + it.groupValues[2].uppercase() }
        if (english) result = result.replace(englishI, "I")
        return result
    }

    /** Splits "- Line one. - Line two." into separate lines so each is translated on its own. */
    fun splitDialogue(text: String): List<String> {
        val parts = text.split(dialogueDash).map { it.trim() }.filter { it.isNotEmpty() }
        val dashes = dialogueDash.findAll(text).count()
        return if (dashes >= 2 && parts.size >= 2) parts else listOf(text)
    }

    /** Anything with fewer than two letters (noise, stray symbols) is not a subtitle. */
    fun isMeaningful(text: String): Boolean = text.count { it.isLetter() } >= 2

    /** Splits off the speaker name so only the spoken line is translated. */
    fun splitSpeaker(text: String): Line {
        val m = speaker.find(text) ?: return Line(null, text)
        val name = m.groupValues[1].trim()
        val body = m.groupValues[2].trim()
        val words = name.split(' ').count { it.isNotBlank() }
        return if (words in 1..3 && body.isNotEmpty()) Line(name, body) else Line(null, text)
    }
}
