package com.ozkan.altyazi.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteModelsTest {

    private fun console(id: String, name: String = "Salon | PS5\nnew") = SavedConsole(
        id = id,
        name = name,
        consoleName = "PS5-123",
        host = "192.168.1.20",
        target = 1000100,
        registKey = ByteArray(16) { it.toByte() },
        rpKey = ByteArray(16) { (it * 3).toByte() },
    )

    @Test
    fun `consoles survive a storage round trip, including separators in names`() {
        val list = listOf(console("a1b2c3d4e5f6"), console("001122334455", name = "Oğuz'un PS5"))
        assertEquals(list, ConsoleCodec.decode(ConsoleCodec.encode(list)))
    }

    @Test
    fun `broken or foreign lines are skipped without losing valid consoles`() {
        val good = ConsoleCodec.encode(listOf(console("a1b2c3d4e5f6")))
        val decoded = ConsoleCodec.decode("garbage\nv9|x\n$good\nv1|||||x|y|z")
        assertEquals(1, decoded.size)
        assertEquals("a1b2c3d4e5f6", decoded.single().id)
        assertTrue(ConsoleCodec.decode(null).isEmpty())
    }

    @Test
    fun `account id is the user id as 8 little-endian bytes in Base64`() {
        // 0x0102030405060708 → 08 07 06 05 04 03 02 01
        val id = PsnAccountId.fromUserId(0x0102030405060708L)
        assertEquals("CAcGBQQDAgE=", id)
        assertArrayEquals(byteArrayOf(8, 7, 6, 5, 4, 3, 2, 1), PsnAccountId.decode(id))
        assertNull(PsnAccountId.decode("not base64!"))
        assertNull(PsnAccountId.decode("AQID")) // 3 bytes
    }

    @Test
    fun `large user ids keep their high bit`() {
        val userId = -0x7FFFFFFFFFFFFFF0L // high bit set when seen as unsigned
        val bytes = PsnAccountId.decode(PsnAccountId.fromUserId(userId))!!
        val back = bytes.indices.fold(0L) { acc, i -> acc or ((bytes[i].toLong() and 0xFF) shl (8 * i)) }
        assertEquals(userId, back)
    }

    @Test
    fun `wake credential is the registration key read as hex up to the first NUL`() {
        val key = "1a2b3c4d".toByteArray(Charsets.US_ASCII) + ByteArray(8)
        assertEquals(0x1a2b3c4dL, RemoteKeys.wakeCredential(key))
        assertNull(RemoteKeys.wakeCredential("zz".toByteArray() + ByteArray(14)))
        assertEquals(-1L, RemoteKeys.wakeCredential("ffffffffffffffff".toByteArray()))
    }

    @Test
    fun `mac id matches the hex discovery reports`() {
        assertEquals("a1b2c30d0e0f", RemoteKeys.macId(byteArrayOf(0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte(), 0x0D, 0x0E, 0x0F)))
    }

    @Test
    fun `input validation`() {
        assertTrue(RemoteKeys.isIpv4("192.168.1.20"))
        assertFalse(RemoteKeys.isIpv4("192.168.1"))
        assertFalse(RemoteKeys.isIpv4("192.168.1.256"))
        assertFalse(RemoteKeys.isIpv4("a.b.c.d"))
        assertTrue(RemoteKeys.isPairingPin("12345678"))
        assertFalse(RemoteKeys.isPairingPin("1234567"))
        assertTrue(RemoteKeys.isLoginPin("0000"))
        assertFalse(RemoteKeys.isLoginPin("00a0"))
    }
}
