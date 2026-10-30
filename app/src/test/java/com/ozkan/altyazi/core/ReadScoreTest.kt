package com.ozkan.altyazi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadScoreTest {

    @Test
    fun `clean words score higher than fragments`() {
        assertTrue(ReadScore.of("Where did you go?") > ReadScore.of("Wh3re d1d y#u g@?"))
    }

    @Test
    fun `stray symbols are penalized`() {
        assertTrue(ReadScore.of("Stay here.") > ReadScore.of("Stay here. ¤ § ©"))
    }

    @Test
    fun `numbers and times are neutral`() {
        assertTrue(ReadScore.of("Meet me at 10:30 tonight.") > 0.8)
    }

    @Test
    fun `ocr confidence contributes to the score`() {
        assertTrue(ReadScore.of("Run", 0.95f) > ReadScore.of("Run", 0.3f))
    }

    @Test
    fun `blank text scores zero`() = assertEquals(0.0, ReadScore.of("  "), 0.0)
}
