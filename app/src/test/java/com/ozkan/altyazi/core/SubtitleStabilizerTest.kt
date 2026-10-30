package com.ozkan.altyazi.core

import com.ozkan.altyazi.core.SubtitleStabilizer.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleStabilizerTest {

    @Test
    fun `commits only after the required number of matching frames`() {
        val s = SubtitleStabilizer(requiredHits = 2)
        assertNull(s.accept("Where are you going?"))
        assertEquals(Event.Commit("Where are you going?"), s.accept("Where are you going?"))
    }

    @Test
    fun `fast mode commits on the first frame`() {
        val s = SubtitleStabilizer(requiredHits = 1)
        assertEquals(Event.Commit("Run!"), s.accept("Run!"))
    }

    @Test
    fun `ocr jitter on the same line does not commit twice`() {
        val s = SubtitleStabilizer(requiredHits = 2)
        s.accept("We have to leave before dawn.")
        s.accept("We have to leave before dawn.")
        assertNull(s.accept("We have to leave before dawn,"))
        assertNull(s.accept("We have to 1eave before dawn."))
    }

    @Test
    fun `a different line commits again`() {
        val s = SubtitleStabilizer(requiredHits = 2)
        s.accept("First line of dialogue"); s.accept("First line of dialogue")
        assertNull(s.accept("Completely different sentence here"))
        assertEquals(Event.Commit("Completely different sentence here"), s.accept("Completely different sentence here"))
    }

    @Test
    fun `typewriter growth is committed as an update`() {
        val s = SubtitleStabilizer(requiredHits = 1)
        assertEquals(Event.Commit("I never thought"), s.accept("I never thought"))
        assertEquals(
            Event.Commit("I never thought I would see you again"),
            s.accept("I never thought I would see you again"),
        )
    }

    @Test
    fun `empty frames clear the state so a repeated line is translated again`() {
        val s = SubtitleStabilizer(requiredHits = 1, emptyFramesToClear = 3)
        assertEquals(Event.Commit("Hello"), s.accept("Hello"))
        assertNull(s.accept(""))
        assertNull(s.accept(""))
        assertEquals(Event.Cleared, s.accept(""))
        assertNull(s.accept("")) // cleared only once
        assertEquals(Event.Commit("Hello"), s.accept("Hello"))
    }

    @Test
    fun `required hits never drops below one`() {
        val s = SubtitleStabilizer(requiredHits = 0)
        assertEquals(1, s.requiredHits)
    }
}
