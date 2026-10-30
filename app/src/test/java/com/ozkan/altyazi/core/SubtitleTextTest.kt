package com.ozkan.altyazi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTextTest {

    @Test
    fun `normalize collapses whitespace and strips ocr junk`() {
        assertEquals("Get down! Now!", SubtitleText.normalize("  Get   down! | Now!  "))
    }

    @Test
    fun `normalize joins words hyphenated across lines`() {
        assertEquals("This is impossible", SubtitleText.normalize("This is impos- sible"))
    }

    @Test
    fun `meaningful requires at least two letters`() {
        assertFalse(SubtitleText.isMeaningful("—"))
        assertFalse(SubtitleText.isMeaningful("A"))
        assertTrue(SubtitleText.isMeaningful("Go"))
    }

    @Test
    fun `speaker prefix is split off`() {
        val line = SubtitleText.splitSpeaker("ELLIE: Stay close to me.")
        assertEquals("ELLIE", line.speaker)
        assertEquals("Stay close to me.", line.body)
        assertEquals("ELLIE: Bana yakın dur.", line.format("Bana yakın dur."))
    }

    @Test
    fun `mixed case two word speaker is recognized`() {
        val line = SubtitleText.splitSpeaker("Captain Price: Move out.")
        assertEquals("Captain Price", line.speaker)
    }

    @Test
    fun `sentences with a colon in the middle are not treated as speakers`() {
        val text = "Remember what I told you about the plan: we go at night."
        val line = SubtitleText.splitSpeaker(text)
        assertNull(line.speaker)
        assertEquals(text, line.body)
    }

    @Test
    fun `digits misread inside words are fixed`() {
        assertEquals("We have to leave now", SubtitleText.fixOcrConfusions("We have to 1eave n0w", english = true))
        assertEquals("GOD HELP US", SubtitleText.fixOcrConfusions("G0D HELP US", english = true))
        assertEquals("until", SubtitleText.fixOcrConfusions("unti1", english = true))
    }

    @Test
    fun `real alphanumeric words are left alone`() {
        assertEquals("Play it on the PS5 in 4K, 1st try", SubtitleText.fixOcrConfusions("Play it on the PS5 in 4K, 1st try", english = true))
    }

    @Test
    fun `lone l and pipe become I in English`() {
        assertEquals("I know. I do.", SubtitleText.clean("l know. | do.", english = true))
    }

    @Test
    fun `all caps becomes sentence case`() {
        assertEquals("I told you. Get out!", SubtitleText.toSentenceCase("I TOLD YOU. GET OUT!", english = true))
    }

    @Test
    fun `mixed case is not touched`() {
        assertEquals("Stay with ME.", SubtitleText.toSentenceCase("Stay with ME.", english = true))
    }

    @Test
    fun `dialogue lines are split`() {
        assertEquals(listOf("Are you coming?", "Not today."), SubtitleText.splitDialogue("- Are you coming? - Not today."))
    }

    @Test
    fun `a single dash is not dialogue`() {
        val text = "Wait - who is that?"
        assertEquals(listOf(text), SubtitleText.splitDialogue(text))
    }
}
