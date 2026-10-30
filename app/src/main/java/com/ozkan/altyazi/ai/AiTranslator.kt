package com.ozkan.altyazi.ai

import android.content.Context
import android.os.StatFs
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.ozkan.altyazi.core.AiPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

enum class AiBackend { GPU, CPU }

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
 * as context. The engine runs on the GPU when available and falls back to the CPU.
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
     * Translates one subtitle. Returns null when the engine is not ready, fails, or replies with
     * something that does not look like a translation (callers then keep the ML Kit translation).
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
                    try {
                        conversation.sendMessage(AiPrompt.userMessage(subtitle, context)).toString()
                    } finally {
                        conversation.close()
                    }
                }
                AiPrompt.acceptTranslation(subtitle, reply, sameLanguage = sourceLanguage == targetLanguage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun load() {
        _state.value = AiState.Loading
        val loaded = createEngine(Backend.GPU())?.let { it to AiBackend.GPU }
            ?: createEngine(Backend.CPU())?.let { it to AiBackend.CPU }
        if (loaded == null) {
            _state.value = AiState.Failed("Model could not be loaded")
            return
        }
        engineLock.withLock { engine = loaded.first }
        _state.value = AiState.Ready(loaded.second)
    }

    private suspend fun createEngine(backend: Backend): Engine? = withContext(Dispatchers.IO) {
        try {
            Engine(
                EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = backend,
                    cacheDir = appContext.cacheDir.path,
                )
            ).also { it.initialize() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }

    private suspend fun release() = engineLock.withLock {
        engine?.close()
        engine = null
    }

    override fun close() {
        setupJob?.cancel()
        engine?.close()
        engine = null
    }

    companion object {
        const val MODEL_FILE = "gemma-4-E2B-it.litertlm"
        const val MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$MODEL_FILE"
        const val MODEL_SIZE_MB = 2583
        private const val STORAGE_HEADROOM_MB = 500
    }
}
