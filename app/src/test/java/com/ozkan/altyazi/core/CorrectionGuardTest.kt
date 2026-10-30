package com.ozkan.altyazi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CorrectionGuardTest {

    @Test
    fun `genuine ocr fixes are accepted`() {
        assertEquals(
            "This is a fresh one. Pip or Pop must have just coughed it up.",
            CorrectionGuard.accept(
                "This is a fresh one, Pip or Pop must have just coughed ft up.",
                "This is a fresh one. Pip or Pop must have just coughed it up.",
            ),
        )
    }

    @Test
    fun `paraphrases are rejected`() {
        assertNull(CorrectionGuard.accept("I told you, owls have good hearing.", "As I said before, owls can hear very well."))
    }

    @Test
    fun `chatty answers are rejected`() {
        assertNull(CorrectionGuard.accept("Run!", "Sure! Here is the corrected subtitle: Run!"))
    }

    @Test
    fun `unchanged output is reported as no correction`() {
        assertNull(CorrectionGuard.accept("Stay close.", "Stay close."))
    }

    @Test
    fun `only the first line is used and quotes are stripped`() {
        assertEquals("Where did you go?", CorrectionGuard.accept("Where did yougo?", "\"Where did you go?\"\nI fixed the spacing."))
    }

    @Test
    fun `empty output is rejected`() = assertNull(CorrectionGuard.accept("Hello there", "   "))
}
