package com.ozkan.altyazi.core

import com.ozkan.altyazi.core.AiPrompt.ContextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPromptTest {

    @Test
    fun `system instruction names both languages and asks for the translation only`() {
        val s = AiPrompt.systemInstruction("English", "Turkish")
        assertTrue("English" in s && "Turkish" in s && "translation only" in s)
    }

    @Test
    fun `user message carries context before the subtitle`() {
        val m = AiPrompt.userMessage("Where is Pip?", listOf(ContextLine("Hello, Pip.", "Merhaba Pip.")))
        assertTrue(m.indexOf("Hello, Pip.") < m.indexOf("Where is Pip?"))
        assertTrue(m.endsWith("Where is Pip?"))
    }

    @Test
    fun `user message without context is just the subtitle request`() {
        assertEquals("Subtitle to translate:\nRun!", AiPrompt.userMessage("Run!", emptyList()))
    }

    @Test
    fun `a clean translation is accepted`() {
        assertEquals("Sana söyledim, baykuşların kulakları iyi duyar.",
            AiPrompt.acceptTranslation("I told you, owls have good hearing.", "Sana söyledim, baykuşların kulakları iyi duyar."))
    }

    @Test
    fun `quotes and chatty prefixes are stripped`() {
        assertEquals("Hadi gidelim!", AiPrompt.acceptTranslation("Let's go!", "Here is the translation: \"Hadi gidelim!\""))
    }

    @Test
    fun `dialogue keeps its two lines`() {
        assertEquals("– Büyük yatakta mı uyuyacağız?\n– Hayır Donny. Dışarıda!",
            AiPrompt.acceptTranslation("-Do we get to sleep in the big bed? -No, Donny. Outside!",
                "– Büyük yatakta mı uyuyacağız?\n– Hayır Donny. Dışarıda!"))
    }

    @Test
    fun `an untranslated echo is rejected`() {
        assertNull(AiPrompt.acceptTranslation("I told you, owls have good hearing.", "I told you, owls have good hearing."))
    }

    @Test
    fun `rambling or empty replies are rejected`() {
        assertNull(AiPrompt.acceptTranslation("Run!", "Koş! Bu cümle bir karakterin kaçması gerektiğini anlatıyor ve oyunun bu bölümünde tehlike yaklaşıyor."))
        assertNull(AiPrompt.acceptTranslation("Run!", "  "))
    }
}
