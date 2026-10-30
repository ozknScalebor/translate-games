package com.ozkan.altyazi.core

/** Text helpers that prepare raw OCR output for translation. */
object SubtitleText {

    /** Glyphs OCR often hallucinates on camera frames that never appear in real subtitles. */
    private val junk = Regex("[|_~`«»•■□▪●◆▶◀]")
    private val spaces = Regex("\\s+")
    /** Word split across lines: "some- thing" -> "something". */
    private val hyphenBreak = Regex("(\\p{L})- (\\p{Ll})")
    /** Speaker prefix such as "ELLIE: Text" or "Ellie: Text". */
    private val speaker = Regex("^([\\p{Lu}][\\p{L}0-9.'\\- ]{0,22}):\\s+(.+)$")

    data class Line(val speaker: String?, val body: String) {
        fun format(translatedBody: String): String =
            if (speaker == null) translatedBody else "$speaker: $translatedBody"
    }

    fun normalize(raw: String): String = raw
        .replace(junk, " ")
        .replace(spaces, " ")
        .trim()
        .replace(hyphenBreak, "$1$2")

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
