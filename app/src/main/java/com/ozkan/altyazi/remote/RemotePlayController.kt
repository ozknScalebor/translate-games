package com.ozkan.altyazi.remote

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.ozkan.altyazi.data.Roi
import com.ozkan.altyazi.remote.chiaki.ChiakiLog
import com.ozkan.altyazi.remote.chiaki.Codec
import com.ozkan.altyazi.remote.chiaki.ConnectInfo
import com.ozkan.altyazi.remote.chiaki.ConnectVideoProfile
import com.ozkan.altyazi.remote.chiaki.Connected
import com.ozkan.altyazi.remote.chiaki.DiscoveryHost
import com.ozkan.altyazi.remote.chiaki.DiscoveryService
import com.ozkan.altyazi.remote.chiaki.LoginPinRequested
import com.ozkan.altyazi.remote.chiaki.Quit
import com.ozkan.altyazi.remote.chiaki.Regist
import com.ozkan.altyazi.remote.chiaki.RegistEvent
import com.ozkan.altyazi.remote.chiaki.RegistEventCanceled
import com.ozkan.altyazi.remote.chiaki.RegistEventFailed
import com.ozkan.altyazi.remote.chiaki.RegistEventSuccess
import com.ozkan.altyazi.remote.chiaki.RegistInfo
import com.ozkan.altyazi.remote.chiaki.Session
import com.ozkan.altyazi.remote.chiaki.SessionEvent
import com.ozkan.altyazi.remote.chiaki.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class ConsoleStatus { READY, STANDBY, OFFLINE }

/** A paired console with its live state from discovery. */
data class ConsoleItem(val console: SavedConsole, val status: ConsoleStatus, val address: String)

/** A console on the network that is not paired with this tablet yet. */
data class FoundConsole(val id: String, val name: String, val host: String, val status: ConsoleStatus)

sealed interface RemoteError {
    /** The console did not wake up or answer in time. */
    data object NotReachable : RemoteError
    /** Another device is already using Remote Play on this console. */
    data object InUse : RemoteError
    /** The native library could not be loaded on this device. */
    data object Unsupported : RemoteError
    data class Other(val message: String) : RemoteError
}

sealed interface Connection {
    val console: SavedConsole?

    data object Idle : Connection { override val console: SavedConsole? = null }
    data class Waking(override val console: SavedConsole) : Connection
    data class Connecting(override val console: SavedConsole) : Connection
    /** The console asks for the Remote Play login PIN set on the PS5 (if one is set). */
    data class NeedsPin(override val console: SavedConsole, val incorrect: Boolean) : Connection
    data class Streaming(override val console: SavedConsole) : Connection
    data class Failed(override val console: SavedConsole, val error: RemoteError) : Connection
}

sealed interface Pairing {
    data object Idle : Pairing
    data object Running : Pairing
    data class Done(val console: SavedConsole) : Pairing
    data class Failed(val message: String?) : Pairing
}

data class RemoteUiState(
    val consoles: List<ConsoleItem> = emptyList(),
    val found: List<FoundConsole> = emptyList(),
    val connection: Connection = Connection.Idle,
    val pairing: Pairing = Pairing.Idle,
    val savedAccountId: String? = null,
    val discoveryAvailable: Boolean = true,
)

/** Latest downscaled stream frame for the on-screen preview; [seq] changes with every frame. */
data class PreviewFrame(val bitmap: Bitmap, val seq: Long)

/**
 * PS5 Remote Play as a subtitle source: finds consoles on the network, pairs with them, wakes them
 * from rest mode and streams their picture. Decoded frames go to [frameSink] (for OCR) and to
 * [preview]; there is no audio and no controller input, so the game stays on the TV and its
 * controller.
 */
class RemotePlayController(context: Context, private val scope: CoroutineScope) {

    private val store = ConsoleStore(context)
    private val discovered = MutableStateFlow<List<DiscoveryHost>>(emptyList())
    private val connection = MutableStateFlow<Connection>(Connection.Idle)
    private val pairing = MutableStateFlow<Pairing>(Pairing.Idle)
    private val discoveryAvailable = MutableStateFlow(true)

    val state: StateFlow<RemoteUiState> = combine(
        combine(store.consoles, discovered, ::Pair),
        connection, pairing, store.accountId, discoveryAvailable,
    ) { (consoles, hosts), conn, pair, account, discoveryOk ->
        val byId = hosts.filter { it.hostId != null }.associateBy { it.hostId!!.lowercase() }
        RemoteUiState(
            consoles = consoles.map { c ->
                val host = byId[c.id]
                ConsoleItem(c, host.status(), host?.hostAddr ?: c.host)
            },
            found = hosts
                .filter { h -> h.hostId != null && h.hostAddr != null && h.isPs5 && consoles.none { it.id == h.hostId!!.lowercase() } }
                .map { h -> FoundConsole(h.hostId!!.lowercase(), h.hostName ?: "PS5", h.hostAddr!!, h.status()) },
            connection = conn,
            pairing = pair,
            savedAccountId = account,
            discoveryAvailable = discoveryOk,
        )
    }.stateIn(scope, SharingStarted.Eagerly, RemoteUiState())

    private val _preview = MutableStateFlow<PreviewFrame?>(null)
    val preview: StateFlow<PreviewFrame?> = _preview.asStateFlow()

    /** Called on the GL thread with every decoded frame; the frame can only be read during the call. */
    @Volatile var frameSink: ((StreamFrame) -> Unit)? = null

    private var discovery: DiscoveryService? = null
    private var session: Session? = null
    /** Identifies the current session, so late events from a session that was replaced are ignored. */
    private var sessionToken: Any? = null
    private var grabber: GlFrameGrabber? = null
    private var regist: Regist? = null
    private var connectJob: Job? = null
    @Volatile private var userStopped = false

    private var lastPreviewAt = 0L
    private var previewSeq = 0L
    private val previewBuffers = arrayOfNulls<Bitmap>(2)

    init {
        // Keep each paired console's last known address, so it can be reached before discovery answers.
        scope.launch {
            discovered.collect { hosts ->
                hosts.forEach { h -> if (h.hostId != null && h.hostAddr != null) store.updateHost(h.hostId!!.lowercase(), h.hostAddr!!) }
            }
        }
    }

    // ---------- Discovery ----------

    fun startDiscovery() {
        if (discovery != null) return
        discovery = try {
            DiscoveryService(BROADCAST) { hosts -> discovered.value = hosts }.also { discoveryAvailable.value = true }
        } catch (e: Throwable) {
            // UnsatisfiedLinkError on unsupported devices, CreateError when the socket cannot be opened.
            discoveryAvailable.value = false
            null
        }
    }

    fun stopDiscovery() {
        discovery?.dispose()
        discovery = null
        discovered.value = emptyList()
    }

    // ---------- Connection ----------

    fun connect(console: SavedConsole) {
        connectJob?.cancel()
        teardownSession()
        userStopped = false
        connectJob = scope.launch {
            startDiscovery()
            var host = hostFor(console)
            if (host?.state != DiscoveryHost.State.READY) {
                connection.value = Connection.Waking(console)
                val ready = wake(console, host)
                if (ready == null && host != null) {
                    // Seen on the network but it never became ready.
                    connection.value = Connection.Failed(console, RemoteError.NotReachable)
                    return@launch
                }
                host = ready
            }
            connection.value = Connection.Connecting(console)
            openSession(console, host?.hostAddr ?: console.host)
        }
    }

    /** Sends wake-up packets until the console reports READY; null if it does not within the timeout. */
    private suspend fun wake(console: SavedConsole, known: DiscoveryHost?): DiscoveryHost? {
        val credential = RemoteKeys.wakeCredential(console.registKey)
        val timeout = if (known == null) UNSEEN_WAIT_MS else WAKE_TIMEOUT_MS
        return withTimeoutOrNull(timeout) {
            val sender = launch {
                while (isActive) {
                    val address = hostFor(console)?.hostAddr ?: console.host
                    if (credential != null) runCatching { discovery?.wakeup(address, credential, Target.fromValue(console.target).isPs5) }
                    delay(WAKE_REPEAT_MS)
                }
            }
            try {
                discovered.first { hosts -> hosts.any { it.matches(console) && it.state == DiscoveryHost.State.READY } }
                    .first { it.matches(console) }
            } finally {
                sender.cancel()
            }
        }?.also { delay(READY_SETTLE_MS) } // the console needs a moment after waking before it accepts sessions
    }

    private fun openSession(console: SavedConsole, address: String) {
        val newGrabber = try {
            GlFrameGrabber(VIDEO_WIDTH, VIDEO_HEIGHT, ::onFrame)
        } catch (e: Throwable) {
            connection.value = Connection.Failed(console, RemoteError.Unsupported)
            return
        }
        val profile = ConnectVideoProfile(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS, VIDEO_BITRATE, Codec.H265)
        val info = ConnectInfo(Target.fromValue(console.target).isPs5, address, console.registKey, console.rpKey, profile)
        val token = Any()
        val newSession = try {
            Session(info) { event -> scope.launch { if (token === sessionToken) onSessionEvent(console, event) } }
        } catch (e: Throwable) {
            newGrabber.release()
            connection.value = Connection.Failed(console, if (e is UnsatisfiedLinkError) RemoteError.Unsupported else RemoteError.Other(e.message ?: e.javaClass.simpleName))
            return
        }
        grabber = newGrabber
        session = newSession
        sessionToken = token
        newSession.setSurface(newGrabber.surface)
        val err = newSession.start()
        if (err != 0) {
            connection.value = Connection.Failed(console, RemoteError.Other("start: $err"))
            teardownSession()
        }
    }

    private fun onSessionEvent(console: SavedConsole, event: SessionEvent) {
        when (event) {
            Connected -> connection.value = Connection.Streaming(console)
            is LoginPinRequested -> connection.value = Connection.NeedsPin(console, event.pinIncorrect)
            is Quit -> {
                teardownSession()
                connection.value = when {
                    userStopped || !event.reason.isError -> Connection.Idle
                    event.reason.isRemotePlayInUse -> Connection.Failed(console, RemoteError.InUse)
                    else -> Connection.Failed(console, RemoteError.Other(event.reasonText?.takeIf { it.isNotBlank() } ?: event.reason.toString()))
                }
            }
        }
    }

    fun submitLoginPin(pin: String) {
        val current = connection.value as? Connection.NeedsPin ?: return
        session?.setLoginPin(pin)
        connection.value = Connection.Connecting(current.console)
    }

    fun disconnect() {
        userStopped = true
        connectJob?.cancel()
        teardownSession()
        connection.value = Connection.Idle
    }

    fun dismissError() {
        if (connection.value is Connection.Failed) connection.value = Connection.Idle
    }

    /** Stops the session off the calling thread: joining it from its own event thread would deadlock. */
    private fun teardownSession() {
        val s = session
        val g = grabber
        session = null
        sessionToken = null
        grabber = null
        _preview.value = null
        if (s == null && g == null) return
        Thread({
            runCatching { s?.stop() }
            runCatching { s?.dispose() } // stops the decoder before its output surface goes away
            runCatching { g?.release() }
        }, "remote-teardown").start()
    }

    /** Runs on the GL thread for every decoded frame. */
    private fun onFrame(frame: StreamFrame) {
        frameSink?.invoke(frame)
        val now = SystemClock.uptimeMillis()
        if (now - lastPreviewAt >= PREVIEW_INTERVAL_MS) {
            lastPreviewAt = now
            val slot = (previewSeq % 2).toInt()
            val bitmap = frame.read(Roi(0f, 0f, 1f, 1f), PREVIEW_WIDTH, PREVIEW_HEIGHT, previewBuffers[slot])
            previewBuffers[slot] = bitmap
            _preview.value = PreviewFrame(bitmap, previewSeq++)
        }
    }

    // ---------- Pairing ----------

    /**
     * Pairs with the console at [host] using the 8-digit PIN from PS5 Settings › System › Remote Play ›
     * Link Device. [accountId] is the Base64 PSN account id of the user signed in on the PS5.
     */
    fun pair(host: String, accountId: String, pin: String, name: String) {
        val account = PsnAccountId.decode(accountId) ?: return
        if (pairing.value == Pairing.Running) return
        scope.launch { store.saveAccountId(accountId) }
        pairing.value = Pairing.Running
        var lastError: String? = null
        val log = ChiakiLog(ChiakiLog.ERROR or ChiakiLog.WARNING) { level, text -> if (level == ChiakiLog.ERROR) lastError = text }
        regist = try {
            Regist(RegistInfo(Target.PS5_1.value, host.trim(), false, account, pin.toInt()), log) { event ->
                scope.launch { onRegistEvent(event, host.trim(), name, lastError) }
            }
        } catch (e: Throwable) {
            pairing.value = Pairing.Failed(e.message)
            null
        }
    }

    private suspend fun onRegistEvent(event: RegistEvent, host: String, name: String, lastError: String?) {
        val finished = regist
        regist = null
        // Freeing joins the registration thread, which is the one delivering this event.
        if (finished != null) Thread({ finished.dispose() }, "regist-free").start()
        pairing.value = when (event) {
            is RegistEventSuccess -> {
                val h = event.host
                val console = SavedConsole(
                    id = RemoteKeys.macId(h.serverMac),
                    name = name.trim().ifEmpty { h.serverNickname ?: "PS5" },
                    consoleName = h.serverNickname ?: "PS5",
                    host = host,
                    target = h.target.value,
                    registKey = h.rpRegistKey,
                    rpKey = h.rpKey,
                )
                store.save(console)
                Pairing.Done(console)
            }
            RegistEventCanceled -> Pairing.Idle
            RegistEventFailed -> Pairing.Failed(lastError)
        }
    }

    fun cancelPairing() {
        regist?.stop()
    }

    fun resetPairing() {
        if (pairing.value != Pairing.Running) pairing.value = Pairing.Idle
    }

    // ---------- Saved consoles ----------

    fun rename(id: String, name: String) {
        if (name.isBlank()) return
        scope.launch { store.rename(id, name.trim()) }
    }

    fun forget(id: String) {
        if (connection.value.console?.id == id) disconnect()
        scope.launch { store.remove(id) }
    }

    fun saveAccountId(accountId: String) {
        scope.launch { store.saveAccountId(accountId) }
    }

    /** Stops everything; used when leaving Remote Play or when the screen is destroyed. */
    fun close() {
        disconnect()
        regist?.stop()
        stopDiscovery()
    }

    private fun hostFor(console: SavedConsole): DiscoveryHost? = discovered.value.firstOrNull { it.matches(console) }

    private fun DiscoveryHost.matches(console: SavedConsole) = hostId?.lowercase() == console.id

    private fun DiscoveryHost?.status(): ConsoleStatus = when (this?.state) {
        DiscoveryHost.State.READY -> ConsoleStatus.READY
        DiscoveryHost.State.STANDBY -> ConsoleStatus.STANDBY
        else -> ConsoleStatus.OFFLINE
    }

    companion object {
        private const val BROADCAST = "255.255.255.255"
        // 1080p keeps subtitle glyphs sharp; 30 fps is plenty for text and halves the decoding work.
        const val VIDEO_WIDTH = 1920
        const val VIDEO_HEIGHT = 1080
        private const val VIDEO_FPS = 30
        private const val VIDEO_BITRATE = 15_000
        private const val WAKE_TIMEOUT_MS = 45_000L
        /** A console discovery has never seen may simply not answer broadcasts: try connecting anyway. */
        private const val UNSEEN_WAIT_MS = 8_000L
        private const val WAKE_REPEAT_MS = 5_000L
        private const val READY_SETTLE_MS = 2_000L
        private const val PREVIEW_INTERVAL_MS = 100L
        private const val PREVIEW_WIDTH = 640
        private const val PREVIEW_HEIGHT = 360
    }
}
