package com.ozkan.altyazi.core

import com.ozkan.altyazi.core.LineOrder.Box
import org.junit.Assert.assertEquals
import org.junit.Test

class LineOrderTest {

    @Test
    fun `boxes of one line with slightly different heights stay in left-to-right order`() {
        // "good hearing." sits a few pixels higher than "I told you," – the old bucketing flipped them.
        val boxes = listOf(
            Box(centerY = 98f, height = 44f, left = 620f), // good hearing.
            Box(centerY = 103f, height = 50f, left = 80f), // I told you,
            Box(centerY = 101f, height = 46f, left = 330f), // owls have
        )
        assertEquals(listOf(1, 2, 0), LineOrder.order(boxes))
    }

    @Test
    fun `two subtitle lines are read top line first`() {
        val boxes = listOf(
            Box(centerY = 160f, height = 40f, left = 100f), // second line
            Box(centerY = 100f, height = 40f, left = 300f), // first line, right part
            Box(centerY = 102f, height = 40f, left = 80f), // first line, left part
        )
        assertEquals(listOf(2, 1, 0), LineOrder.order(boxes))
    }

    @Test
    fun `empty input yields empty order`() = assertEquals(emptyList<Int>(), LineOrder.order(emptyList()))
}
