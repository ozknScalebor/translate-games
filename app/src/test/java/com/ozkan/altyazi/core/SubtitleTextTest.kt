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
}
