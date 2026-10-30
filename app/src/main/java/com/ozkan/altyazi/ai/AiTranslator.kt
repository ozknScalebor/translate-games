package com.ozkan.altyazi.ai

import android.content.Context
import android.os.StatFs
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.ozkan.altyazi.core.AiPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.File

/** Where the model runs, and whether multi-token-prediction speculative decoding is active. */
data class AiBackend(val gpu: Boolean, val speculative: Boolean)

sealed interface AiState {
    data object Off : AiState
    data class NeedsDownload(val sizeMb: Int) : AiState
    data class Downloading(val progress: Float?) : AiState
    data object Loading : AiState
    data class Ready(val backend: AiBackend) : AiState
    data class Failed(val reason: String) : AiState
    data class NotEnoughStorage(val neededMb: Int, val freeMb: Int) : AiState
}

/**
 * Offline subtitle translation with Gemma 4 E2B (Apache-2.0) on LiteRT-LM.
 *
 * Chosen because it gave the best translation quality among small local models in an independent
 * game-text benchmark, while decoding ~50 tokens/s with a 0.3 s time-to-first-token on a current
 * flagship GPU. It also repairs leftover OCR mistakes in the same pass and uses the previous lines
 * as context. The engine runs on the GPU when available and falls back to the CPU, with Gemma 4's
 * MTP drafter (speculative decoding) enabled when the runtime accepts it — measured elsewhere at
 * ~20% lower sentence latency for translation with unchanged quality. Replies are streamed
 * internally so a newer subtitle can stop generation right away; callers get the full line once.
 *
 * The model (~2.6 GB) is downloaded once after the user agrees. There are no tokens, quotas or
 * costs: everything runs on the tablet.
 */
class AiTranslator(context: Context, private val scope: CoroutineScope) : Closeable {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<AiState>(AiState.Off)
    val state: StateFlow<AiState> = _state.asStateFlow()

    private val engineLock = Mutex()
    private var engine: Engine? = null
    private var setupJob: Job? = null

    /** The conversation currently generating, and a signal that fires when its native run ends. */
    @Volatile private var active: Pair<Conversation, CompletableDeferred<Unit>>? = null

    private val modelFile = File(appContext.filesDir, "models/$MODEL_FILE")

    fun enable() {
        if (_state.value !is AiState.Off && _state.value !is AiState.Failed) return
        setupJob?.cancel()
        setupJob = scope.launch {
            if (modelFile.exists()) load() else _state.value = AiState.NeedsDownload(MODEL_SIZE_MB)
        }
    }

    fun disable() {
        setupJob?.cancel()
        scope.launch { release() }
        _state.value = AiState.Off
    }

    /** Downloads the model after the user agreed, then loads it. */
    fun download() {
        setupJob?.cancel()
        setupJob = scope.launch {
            val freeMb = (StatFs(appContext.filesDir.path).availableBytes / (1024 * 1024)).toInt()
            val partialMb = (File(modelFile.path + ".part").takeIf { it.exists() }?.length() ?: 0L) / (1024 * 1024)
            val neededMb = (MODEL_SIZE_MB - partialMb.toInt()) + STORAGE_HEADROOM_MB
            if (freeMb < neededMb) {
                _state.value = AiState.NotEnoughStorage(neededMb, freeMb)
                return@launch
            }
            try {
                _state.value = AiState.Downloading(null)
                ModelDownloader.download(MODEL_URL, modelFile) { p -> _state.value = AiState.Downloading(p) }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = AiState.Failed(e.localizedMessage ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * Translates one subtitle and returns the complete line.
     * Returns null when the engine is not ready, fails, or replies with something that does not look
     * like a translation (callers then fall back to the regular translator).
     * Cancelling the calling coroutine stops collection and closes the conversation.
     */
    suspend fun translate(
        subtitle: String,
        sourceLanguage: String,
        targetLanguage: String,
        context: List<AiPrompt.ContextLine>,
    ): String? {
        if (_state.value !is AiState.Ready) return null
        return engineLock.withLock {
            val current = engine ?: return@withLock null
            try {
                val reply = withContext(Dispatchers.Default) {
                    val conversation = current.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(AiPrompt.systemInstruction(sourceLanguage, targetLanguage)),
                            // Greedy decoding: deterministic and the fastest to produce.
                            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.1),
                        )
                    )
                    generate(conversation, AiPrompt.userMessage(subtitle, context))
                }
                AiPrompt.acceptTranslation(subtitle, reply, sameLanguage = sourceLanguage == targetLanguage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Runs one reply on the native thread and returns the full text.
     *
     * When the caller is cancelled (a newer subtitle or the timeout), generation is stopped with
     * cancelProcess() and the conversation is closed only after the native run has really ended.
     * Closing it while the model is still writing frees memory the native thread keeps using, which
     * crashes the whole app. If the run does not end in time, the conversation is left open (a small
     * leak) rather than risking that crash.
     */
    private suspend fun generate(conversation: Conversation, prompt: String): String {
        val reply = StringBuilder()
        val finished = CompletableDeferred<Unit>()
        active = conversation to finished
        try {
            conversation.sendMessageAsync(
                Contents.of(prompt),
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        synchronized(reply) { reply.append(message.toString()) }
                    }

                    override fun onDone() {
                        finished.complete(Unit)
                    }

                    override fun onError(throwable: Throwable) {
                        finished.completeExceptionally(throwable)
                    }
                },
            )
            finished.await()
            return synchronized(reply) { reply.toString() }
        } finally {
            withContext(NonCancellable) {
                if (!finished.isCompleted) runCatching { conversation.cancelProcess() }
                val ended = withTimeoutOrNull(STOP_TIMEOUT_MS) { runCatching { finished.await() } } != null
                active = null
                if (ended) runCatching { conversation.close() }
            }
        }
    }

    private suspend fun load() {
        _state.value = AiState.Loading
        // Fastest first: GPU + speculative decoding, then without the drafter, then the CPU.
        val attempts = listOf(
            AiBackend(gpu = true, speculative = true),
            AiBackend(gpu = true, speculative = false),
            AiBackend(gpu = false, speculative = true),
            AiBackend(gpu = false, speculative = false),
        )
        var loaded: Pair<Engine, AiBackend>? = null
        for (attempt in attempts) {
            val e = createEngine(if (attempt.gpu) Backend.GPU() else Backend.CPU(), attempt.speculative) ?: continue
            loaded = e to attempt
            break
        }
        if (loaded == null) {
            _state.value = AiState.Failed("Model could not be loaded")
            return
        }
        engineLock.withLock { engine = loaded.first }
        _state.value = AiState.Ready(loaded.second)
        warmUp()
    }

    /** The first inference compiles GPU kernels; doing it now keeps the first real subtitle fast. */
    private suspend fun warmUp() {
        runCatching { translate("Hello there.", "English", "Turkish", emptyList()) }
    }

    // The engine-level flag is the speculative-decoding switch available in LiteRT-LM 0.19 (default:
    // off); newer releases move it to EngineConfig.speculativeDecodingConfig.
    @OptIn(ExperimentalApi::class)
    @Suppress("DEPRECATION")
    private suspend fun createEngine(backend: Backend, speculative: Boolean): Engine? = withContext(Dispatchers.IO) {
        var candidate: Engine? = null
        try {
            // Read when the engine is constructed; throws at initialize() if the model has no drafter.
            ExperimentalFlags.enableSpeculativeDecoding = speculative
            candidate = Engine(
                EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = backend,
                    cacheDir = appContext.cacheDir.path,
                )
            )
            candidate.initialize()
            candidate
        } catch (e: CancellationException) {
            runCatching { candidate?.close() }
            throw e
        } catch (e: Throwable) {
            // A failed attempt must not keep native memory (GPU buffers) alive.
            runCatching { candidate?.close() }
            null
        }
    }

    private suspend fun release() = engineLock.withLock {
        runCatching { engine?.close() }
        engine = null
    }

    /**
     * Called when the screen is destroyed. Any running reply is stopped first and the engine is
     * freed on a background thread once it has ended, so closing never races the native decoder.
     */
    override fun close() {
        setupJob?.cancel()
        val running = active
        val toClose = engine
        engine = null
        Thread({
            running?.let { (conversation, finished) ->
                runCatching { conversation.cancelProcess() }
                val deadline = System.currentTimeMillis() + STOP_TIMEOUT_MS
                while (!finished.isCompleted && System.currentTimeMillis() < deadline) Thread.sleep(20)
                if (!finished.isCompleted) return@Thread // still running: leave it to process exit
            }
            runCatching { toClose?.close() }
        }, "ai-release").start()
    }

    companion object {
        const val MODEL_FILE = "gemma-4-E2B-it.litertlm"
        const val MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$MODEL_FILE"
        const val MODEL_SIZE_MB = 2583
        private const val STORAGE_HEADROOM_MB = 500
        private const val STOP_TIMEOUT_MS = 3_000L
    }
}
