// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
// Kotlin side of the Remote Play JNI bridge, adapted from chiaki-ng
// (android/app/src/main/java/com/metallic/chiaki/lib/Chiaki.kt).
// Field names, constructors and method signatures are read from native code: keep them in sync
// with app/src/main/cpp/remote-jni.c (R8 keeps this package as-is, see proguard-rules.pro).
package com.ozkan.altyazi.remote.chiaki

import android.view.Surface

/** Console generation as used by the Remote Play protocol. */
enum class Target(val value: Int) {
    PS4_UNKNOWN(0),
    PS4_8(800),
    PS4_9(900),
    PS4_10(1000),
    PS5_UNKNOWN(1000000),
    PS5_1(1000100);

    val isPs5 get() = value >= PS5_UNKNOWN.value

    companion object {
        @JvmStatic
        fun fromValue(value: Int): Target = entries.firstOrNull { it.value == value } ?: PS5_1
    }
}

object Codec {
    const val H264 = 0
    const val H265 = 1
}

/** Requested stream; [bitrate] in kbit/s, [codec] one of [Codec]. */
class ConnectVideoProfile(
    @JvmField val width: Int,
    @JvmField val height: Int,
    @JvmField val maxFps: Int,
    @JvmField val bitrate: Int,
    @JvmField val codec: Int,
)

class ConnectInfo(
    @JvmField val ps5: Boolean,
    @JvmField val host: String,
    /** rp_regist_key from registration, 16 bytes. */
    @JvmField val registKey: ByteArray,
    /** rp_key from registration ("morning"), 16 bytes. */
    @JvmField val morning: ByteArray,
    @JvmField val videoProfile: ConnectVideoProfile,
)

class CreateResult(@JvmField var errorCode: Int = 0, @JvmField var ptr: Long = 0)

class CreateError(val errorCode: Int) : Exception("Remote Play: ${ChiakiNative.errorCodeToString(errorCode)}")

internal object ChiakiNative {
    init {
        System.loadLibrary("altyazi-remote")
    }

    @JvmStatic external fun errorCodeToString(value: Int): String
    @JvmStatic external fun quitReasonToString(value: Int): String
    @JvmStatic external fun quitReasonIsError(value: Int): Boolean

    @JvmStatic external fun sessionCreate(result: CreateResult, connectInfo: ConnectInfo, javaSession: Session)
    @JvmStatic external fun sessionFree(ptr: Long)
    @JvmStatic external fun sessionStart(ptr: Long): Int
    @JvmStatic external fun sessionStop(ptr: Long): Int
    @JvmStatic external fun sessionJoin(ptr: Long): Int
    @JvmStatic external fun sessionSetSurface(ptr: Long, surface: Surface?)
    @JvmStatic external fun sessionSetLoginPin(ptr: Long, pin: String)

    @JvmStatic external fun discoveryServiceCreate(result: CreateResult, broadcastAddress: String, port: Int, javaService: DiscoveryService)
    @JvmStatic external fun discoveryServiceFree(ptr: Long)
    @JvmStatic external fun discoveryWakeup(servicePtr: Long, host: String, userCredential: Long, ps5: Boolean): Int

    @JvmStatic external fun registStart(result: CreateResult, registInfo: RegistInfo, log: ChiakiLog, javaRegist: Regist)
    @JvmStatic external fun registStop(ptr: Long)
    @JvmStatic external fun registFree(ptr: Long)
}

/** Forwards native log lines (used to explain a failed registration). */
class ChiakiLog(@JvmField val levelMask: Int = ALL, private val callback: (level: Int, text: String) -> Unit) {
    fun log(level: Int, text: String) = callback(level, text)

    companion object {
        const val ERROR = 1
        const val WARNING = 1 shl 1
        const val INFO = 1 shl 2
        const val ALL = ERROR or WARNING or INFO
    }
}

/** A quit [reason] code from the native session. */
class QuitReason(val value: Int) {
    val isError: Boolean get() = ChiakiNative.quitReasonIsError(value)
    override fun toString(): String = ChiakiNative.quitReasonToString(value)

    /** The console is already streaming to another Remote Play device. */
    val isRemotePlayInUse: Boolean get() = value == RP_IN_USE

    private companion object {
        /** CHIAKI_QUIT_REASON_SESSION_REQUEST_RP_IN_USE in chiaki/session.h. */
        const val RP_IN_USE = 4
    }
}

sealed interface SessionEvent
data object Connected : SessionEvent
data class LoginPinRequested(val pinIncorrect: Boolean) : SessionEvent
data class Quit(val reason: QuitReason, val reasonText: String?) : SessionEvent

/** One Remote Play streaming session. Video goes straight to the surface given to [setSurface]. */
class Session(connectInfo: ConnectInfo, private val onEvent: (SessionEvent) -> Unit) {

    private var ptr: Long

    init {
        val result = CreateResult()
        ChiakiNative.sessionCreate(result, connectInfo, this)
        if (result.errorCode != 0) throw CreateError(result.errorCode)
        ptr = result.ptr
    }

    fun start(): Int = ChiakiNative.sessionStart(ptr)
    fun stop(): Int = ChiakiNative.sessionStop(ptr)
    fun setSurface(surface: Surface?) = ChiakiNative.sessionSetSurface(ptr, surface)
    fun setLoginPin(pin: String) = ChiakiNative.sessionSetLoginPin(ptr, pin)

    /** Waits for the session threads to end and frees native memory. Call after [stop]. */
    fun dispose() {
        if (ptr == 0L) return
        ChiakiNative.sessionJoin(ptr)
        ChiakiNative.sessionFree(ptr)
        ptr = 0L
    }

    // Called from native code.
    private fun eventConnected() = onEvent(Connected)
    private fun eventLoginPinRequest(pinIncorrect: Boolean) = onEvent(LoginPinRequested(pinIncorrect))
    private fun eventQuit(reason: Int, reasonText: String?) = onEvent(Quit(QuitReason(reason), reasonText))
}

/** A console answering on the local network. */
class DiscoveryHost(
    @JvmField val state: State,
    @JvmField val hostRequestPort: Int,
    @JvmField val hostAddr: String?,
    @JvmField val systemVersion: String?,
    @JvmField val deviceDiscoveryProtocolVersion: String?,
    @JvmField val hostName: String?,
    @JvmField val hostType: String?,
    /** The console's MAC address as hex; stable across IP changes. */
    @JvmField val hostId: String?,
    @JvmField val runningAppTitleId: String?,
    @JvmField val runningAppName: String?,
) {
    enum class State { UNKNOWN, READY, STANDBY }

    val isPs5: Boolean get() = deviceDiscoveryProtocolVersion == "00030010" || hostType.equals("PS5", ignoreCase = true)
}

/** Broadcasts discovery pings and reports the consoles that answer. */
class DiscoveryService(broadcastAddress: String, private val onHosts: (List<DiscoveryHost>) -> Unit) {

    private var ptr: Long

    init {
        val result = CreateResult()
        ChiakiNative.discoveryServiceCreate(result, broadcastAddress, PORT, this)
        if (result.errorCode != 0) throw CreateError(result.errorCode)
        ptr = result.ptr
    }

    /** Wakes a console from rest mode; [credential] comes from [RegistHost.rpRegistKey]. */
    fun wakeup(host: String, credential: Long, ps5: Boolean): Int = ChiakiNative.discoveryWakeup(ptr, host, credential, ps5)

    fun dispose() {
        if (ptr == 0L) return
        ChiakiNative.discoveryServiceFree(ptr)
        ptr = 0L
    }

    // Called from native code.
    private fun hostsUpdated(hosts: Array<DiscoveryHost>) = onHosts(hosts.toList())

    companion object {
        const val PORT = 987
    }
}

class RegistInfo(
    @JvmField val target: Int,
    @JvmField val host: String,
    @JvmField val broadcast: Boolean,
    /** 8-byte PSN account id (little endian). */
    @JvmField val psnAccountId: ByteArray,
    @JvmField val pin: Int,
)

/** What registration returns; [rpRegistKey] and [rpKey] are needed for every later connection. */
class RegistHost(
    @JvmField val target: Target,
    @JvmField val apSsid: String?,
    @JvmField val apBssid: String?,
    @JvmField val apKey: String?,
    @JvmField val apName: String?,
    @JvmField val serverMac: ByteArray,
    @JvmField val serverNickname: String?,
    @JvmField val rpRegistKey: ByteArray,
    @JvmField val rpKeyType: Int,
    @JvmField val rpKey: ByteArray,
)

sealed class RegistEvent
object RegistEventCanceled : RegistEvent()
object RegistEventFailed : RegistEvent()
class RegistEventSuccess(@JvmField val host: RegistHost) : RegistEvent()

/** Pairs this tablet with a console using the PIN shown under Remote Play › Link Device. */
class Regist(info: RegistInfo, log: ChiakiLog, private val onEvent: (RegistEvent) -> Unit) {

    private var ptr: Long

    init {
        val result = CreateResult()
        ChiakiNative.registStart(result, info, log, this)
        if (result.errorCode != 0) throw CreateError(result.errorCode)
        ptr = result.ptr
    }

    fun stop() = ChiakiNative.registStop(ptr)

    fun dispose() {
        if (ptr == 0L) return
        ChiakiNative.registFree(ptr)
        ptr = 0L
    }

    // Called from native code.
    private fun event(event: RegistEvent) = onEvent(event)
}
