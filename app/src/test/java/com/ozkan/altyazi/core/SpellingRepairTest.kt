package com.ozkan.altyazi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Uses the real bundled word list; cases come from actual camera reads of a TV. */
class SpellingRepairTest {

    companion object {
        private lateinit var dictionary: WordDictionary
        private lateinit var repair: SpellingRepair

        @BeforeClass
        @JvmStatic
        fun load() {
            dictionary = File("src/main/assets/dict/en_words.txt.gz").inputStream().use(WordDictionary::load)
            repair = SpellingRepair(dictionary)
        }
    }

    private fun assertRepaired(expected: String, ocr: String) = assertEquals(expected, repair.repair(ocr))

    @Test
    fun `dictionary loads the full list`() = assertTrue(dictionary.size > 100_000)

    @Test
    fun `glued words from real footage are split`() {
        assertRepaired("so they swallow things whole and then cough up all the yucky bits,",
            "sothey swallowthingswhole and then cough up all the yucky bits,")
        assertRepaired("And then, guess where we're gonna be sleeping?", "Andthen, guess where we're gonna be sleeping?")
        assertRepaired("My dad's setting up a tent! Come on, I'll show you.", "Mydad's setting upa tent! Comeon, I'll showyou.")
    }

    @Test
    fun `punctuation and camel case joins are spaced`() {
        assertRepaired("This is a fresh one, Pip or Pop must have just coughed it up.",
            "This is a fresh one,Pip orPop must have just coughed it up.")
        assertRepaired("-Do we get to sleep in the big bed? -No, Donny. Outside!",
            "-Do we get to sleep in the bigbed? -No,Donny.Outside!")
    }

    @Test
    fun `common compound words are never split`() {
        val text = "We went into the room upon arrival anyway, hardworking as always, inside the spaceship."
        assertRepaired(text, text)
    }

    @Test
    fun `rare but real words are kept`() {
        assertRepaired("Ugh, that was yucky and he coughed.", "Ugh, that was yucky and he coughed.")
    }

    @Test
    fun `names are not split`() = assertRepaired("Ina said nothing to Hank.", "Ina said nothing to Hank.")

    @Test
    fun `single letter typos in unknown words are corrected`() {
        assertRepaired("Come on, I'll show you.", "Come on, I'll show ypu.")
    }

    @Test
    fun `garbage that cannot be explained is left untouched`() {
        assertRepaired("hssgonnabggreat", "hssgonnabggreat")
    }
}
