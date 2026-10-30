package com.ozkan.altyazi.core

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class RoiMappingTest {

    private val bottomStrip = floatArrayOf(0f, 0.8f, 1f, 1f)
    private val delta = 1e-6f

    private fun map(rotation: Int) =
        RoiMapping.displayToSensor(bottomStrip[0], bottomStrip[1], bottomStrip[2], bottomStrip[3], rotation)

    @Test
    fun `no rotation is identity`() = assertArrayEquals(bottomStrip, map(0), delta)

    @Test
    fun `90 degrees maps the bottom strip to the sensor's right column`() =
        assertArrayEquals(floatArrayOf(0.8f, 0f, 1f, 1f), map(90), delta)

    @Test
    fun `180 degrees maps the bottom strip to the sensor's top strip`() =
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 0.2f), map(180), delta)

    @Test
    fun `270 degrees maps the bottom strip to the sensor's left column`() =
        assertArrayEquals(floatArrayOf(0f, 0f, 0.2f, 1f), map(270), delta)

    @Test
    fun `negative rotation is normalized`() = assertArrayEquals(map(270), map(-90), delta)
}
