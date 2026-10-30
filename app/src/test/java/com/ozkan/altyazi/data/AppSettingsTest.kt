package com.ozkan.altyazi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    private val base = AppSettings(sourceLanguage = Language.ENGLISH, targetLanguage = Language.TURKISH)

    @Test
    fun `choosing the target as source swaps the pair`() {
        val s = base.withSource(Language.TURKISH)
        assertEquals(Language.TURKISH, s.sourceLanguage)
        assertEquals(Language.ENGLISH, s.targetLanguage)
    }

    @Test
    fun `choosing the source as target swaps the pair`() {
        val s = base.withTarget(Language.ENGLISH)
        assertEquals(Language.TURKISH, s.sourceLanguage)
        assertEquals(Language.ENGLISH, s.targetLanguage)
    }

    @Test
    fun `a non-latin target cannot become the source`() {
        val s = base.copy(targetLanguage = Language.JAPANESE)
        assertSame(s, s.swapped())
    }

    @Test
    fun `keyed lookup falls back to the default for unknown keys`() {
        assertEquals(Quality.BEST, keyedValueOf("nope", Quality.BEST))
        assertEquals(Quality.FAST, keyedValueOf("fast", Quality.BEST))
    }

    @Test
    fun `best quality is the default`() {
        assertEquals(Quality.BEST, AppSettings().quality)
    }

    @Test
    fun `only latin script languages are offered as game languages`() {
        assertTrue(Language.sources.all { it.ocrSupported })
        assertFalse(Language.JAPANESE in Language.sources)
    }
}
