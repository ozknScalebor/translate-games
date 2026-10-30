package com.ozkan.altyazi.remote

import com.ozkan.altyazi.data.Roi
import java.util.Base64
import kotlin.math.roundToInt

/**
 * A PS5 paired with this tablet. [id] is the console's MAC address (hex), which stays the same when
 * its IP address changes. [registKey] and [rpKey] are the 16-byte secrets from registration.
 */
data class SavedConsole(
    val id: String,
    val name: String,
    val consoleName: String,
    val host: String,
    val target: Int,
    val registKey: ByteArray,
    val rpKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is SavedConsole &&
        id == other.id && name == other.name && consoleName == other.consoleName && host == other.host &&
        target == other.target && registKey.contentEquals(other.registKey) && rpKey.contentEquals(other.rpKey)

    override fun hashCode(): Int = id.hashCode()
}

/** Line-based storage format for [SavedConsole]s; every field is Base64 so separators never clash. */
object ConsoleCodec {
    private const val VERSION = "v1"
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()

    fun encode(consoles: List<SavedConsole>): String = consoles.joinToString("\n") { c ->
        listOf(
            VERSION,
            text(c.id), text(c.name), text(c.consoleName), text(c.host),
            c.target.toString(),
            b64.encodeToString(c.registKey), b64.encodeToString(c.rpKey),
        ).joinToString("|")
    }

    /** Unreadable lines are skipped, so one bad entry never loses the others. */
    fun decode(stored: String?): List<SavedConsole> = stored.orEmpty().lineSequence().mapNotNull { line ->
        runCatching {
            val f = line.split('|')
            if (f.size != 8 || f[0] != VERSION) return@runCatching null
            SavedConsole(
                id = untext(f[1]), name = untext(f[2]), consoleName = untext(f[3]), host = untext(f[4]),
                target = f[5].toInt(),
                registKey = unb64.decode(f[6]), rpKey = unb64.decode(f[7]),
            ).takeIf { it.id.isNotEmpty() && it.registKey.size == KEY_SIZE && it.rpKey.size == KEY_SIZE }
        }.getOrNull()
    }.toList()

    private fun text(s: String) = b64.encodeToString(s.toByteArray(Charsets.UTF_8))
    private fun untext(s: String) = String(unb64.decode(s), Charsets.UTF_8)

    const val KEY_SIZE = 16
}

/** The PSN account id the console expects during registration: 8 bytes, little endian, Base64. */
object PsnAccountId {
    const val SIZE = 8

    fun fromUserId(userId: Long): String {
        val bytes = ByteArray(SIZE) { i -> (userId ushr (8 * i)).toByte() }
        return Base64.getEncoder().encodeToString(bytes)
    }

    /** Decodes a pasted account id; null when it is not 8 bytes of valid Base64. */
    fun decode(text: String): ByteArray? = runCatching { Base64.getDecoder().decode(text.trim()) }
        .getOrNull()?.takeIf { it.size == SIZE }
}

object RemoteKeys {
    /**
     * The wake-up credential is the registration key read as ASCII hex (up to the first NUL),
     * e.g. "1a2b3c4d" → 0x1a2b3c4d. Null when the key does not contain hex digits.
     */
    fun wakeCredential(registKey: ByteArray): Long? {
        val end = registKey.indexOf(0).let { if (it >= 0) it else registKey.size }
        val text = String(registKey, 0, end, Charsets.US_ASCII)
        return text.toULongOrNull(16)?.toLong()
    }

    /** MAC address bytes as the lowercase hex id discovery reports for the console. */
    fun macId(mac: ByteArray): String = mac.joinToString("") { "%02x".format(it) }

    /** PS5 Remote Play PINs: 8 digits for pairing, 4 digits for the optional login PIN. */
    fun isPairingPin(text: String) = text.length == 8 && text.all { it.isDigit() }
    fun isLoginPin(text: String) = text.length == 4 && text.all { it.isDigit() }

    fun isIpv4(text: String): Boolean {
        val parts = text.trim().split('.')
        return parts.size == 4 && parts.all { p -> p.isNotEmpty() && p.length <= 3 && p.all(Char::isDigit) && p.toInt() in 0..255 }
    }
}

/** Pixel rectangle of [roi] inside a decoded frame whose visible area is [frameWidth]×[frameHeight] at ([left], [top]). */
data class PixelRect(val x: Int, val y: Int, val width: Int, val height: Int)

fun Roi.toPixels(left: Int, top: Int, frameWidth: Int, frameHeight: Int): PixelRect {
    // Chroma is subsampled 2×2, so the crop starts on even coordinates.
    val x = ((this.left * frameWidth).roundToInt().coerceIn(0, frameWidth - 2) and 1.inv())
    val y = ((this.top * frameHeight).roundToInt().coerceIn(0, frameHeight - 2) and 1.inv())
    val r = (this.right * frameWidth).roundToInt().coerceIn(x + 2, frameWidth)
    val b = (this.bottom * frameHeight).roundToInt().coerceIn(y + 2, frameHeight)
    return PixelRect(left + x, top + y, r - x, b - y)
}
