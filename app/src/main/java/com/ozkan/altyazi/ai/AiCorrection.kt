package com.ozkan.altyazi.ai

import android.content.Context
import androidx.concurrent.futures.await
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.proofreading.Proofreader
import com.google.mlkit.genai.proofreading.ProofreaderOptions
import com.google.mlkit.genai.proofreading.Proofreading
import com.google.mlkit.genai.proofreading.ProofreadingRequest
import com.ozkan.altyazi.data.Language
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

enum class AiBackend { GEMINI_NANO, LOCAL_MODEL }

sealed interface AiState {
    data object Off : AiState
    data object Checking : AiState
    data class NeedsDownload(val sizeMb: Int) : AiState
    data class Downloading(val progress: Float?) : AiState
    data class Ready(val backend: AiBackend) : AiState
    data class Failed(val reason: String) : AiState
}

/**
 * Optional on-device AI correction of OCR text, using the lowest-latency engine available:
 *
 * 1. **Gemini Nano** through the ML Kit GenAI Proofreading API, when the device supports it.
 *    Runs on the system model (AICore), so nothing is added to the app.
 * 2. Otherwise **Qwen2.5-0.5B-Instruct** (int8, Apache-2.0) via MediaPipe LLM Inference — the
 *    smallest freely downloadable instruction model, fetched once (~547 MB) after user consent.
 *
 * Callers display the dictionary-repaired subtitle first and only replace it when a correction
 * arrives, so enabling AI never delays the first translation.
 */
class AiCorrection(context: Context, private val scope: CoroutineScope) : Closeable {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<AiState>(AiState.Off)
    val state: StateFlow<AiState> = _state.asStateFlow()

    private val engineLock = Mutex()
    private var proofreader: Proofreader? = null
    private var llm: LlmInference? = null
    private var language: Language = Language.ENGLISH
    private var setupJob: Job? = null

    private val modelFile = File(appContext.filesDir, "models/$LOCAL_MODEL_FILE")

    fun enable(source: Language) {
        if (_state.value !is AiState.Off && source == language) return
        language = source
        setupJob?.cancel()
        setupJob = scope.launch {
            releaseEngines()
            _state.value = AiState.Checking
            if (source == Language.ENGLISH && tryGeminiNano()) return@launch
            if (modelFile.exists()) loadLocalModel() else _state.value = AiState.NeedsDownload(LOCAL_MODEL_SIZE_MB)
        }
    }

    fun disable() {
        setupJob?.cancel()
        scope.launch { releaseEngines() }
        _state.value = AiState.Off
    }

    /** Downloads the local model after the user agreed, then loads it. */
    fun downloadLocalModel() {
        setupJob?.cancel()
        setupJob = scope.launch {
            try {
                _state.value = AiState.Downloading(null)
                ModelDownloader.download(LOCAL_MODEL_URL, modelFile) { p -> _state.value = AiState.Downloading(p) }
                loadLocalModel()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = AiState.Failed(e.localizedMessage ?: e.javaClass.simpleName)
            }
        }
    }

    /** @return the model's correction of [text], or null if AI is not ready or failed. */
    suspend fun correct(text: String): String? {
        val ready = _state.value as? AiState.Ready ?: return null
        return engineLock.withLock {
            try {
                when (ready.backend) {
                    AiBackend.GEMINI_NANO -> proofreader?.let { proofread(it, text) }
                    AiBackend.LOCAL_MODEL -> llm?.let { generate(it, text) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // a busy/quota error just means "no correction this time"
            }
        }
    }

    // ---------- Gemini Nano ----------

    private suspend fun tryGeminiNano(): Boolean {
        val client = try {
            Proofreading.getClient(
                ProofreaderOptions.builder(appContext)
                    .setInputType(ProofreaderOptions.InputType.KEYBOARD)
                    .setLanguage(ProofreaderOptions.Language.ENGLISH)
                    .build()
            )
        } catch (e: Exception) {
            return false
        }
        val status = try {
            client.checkFeatureStatus().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FeatureStatus.UNAVAILABLE
        }
        when (status) {
            FeatureStatus.AVAILABLE, FeatureStatus.DOWNLOADING -> Unit
            FeatureStatus.DOWNLOADABLE -> {
                // System-managed model download through AICore; nothing is stored in the app.
                _state.value = AiState.Downloading(null)
                client.downloadFeature(object : DownloadCallback {
                    override fun onDownloadStarted(bytesToDownload: Long) {}
                    override fun onDownloadProgress(totalBytesDownloaded: Long) {}
                    override fun onDownloadCompleted() {}
                    override fun onDownloadFailed(e: GenAiException) {}
                })
            }
            else -> {
                client.close()
                return false
            }
        }
        runCatching { client.prepareInferenceEngine().await() }
        engineLock.withLock { proofreader = client }
        _state.value = AiState.Ready(AiBackend.GEMINI_NANO)
        return true
    }

    private suspend fun proofread(client: Proofreader, text: String): String? {
        val result = client.runInference(ProofreadingRequest.builder(text).build()).await()
        return result.results.firstOrNull()?.text
    }

    // ---------- Local model ----------

    private suspend fun loadLocalModel() {
        try {
            val engine = withContext(Dispatchers.IO) {
                LlmInference.createFromOptions(
                    appContext,
                    LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(MAX_TOKENS)
                        .build()
                )
            }
            engineLock.withLock { llm = engine }
            _state.value = AiState.Ready(AiBackend.LOCAL_MODEL)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = AiState.Failed(e.localizedMessage ?: e.javaClass.simpleName)
        }
    }

    private suspend fun generate(engine: LlmInference, text: String): String? = withContext(Dispatchers.Default) {
        val options = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(1) // greedy: deterministic, and the fastest to decode
            .setTemperature(0f)
            .build()
        LlmInferenceSession.createFromOptions(engine, options).use { session ->
            session.addQueryChunk(prompt(text))
            session.generateResponse()
        }?.substringBefore("<|im_end|>")?.trim()
    }

    private fun prompt(text: String): String {
        val lang = language.name.lowercase().replaceFirstChar { it.uppercase() }
        return "<|im_start|>system\n" +
            "You fix OCR mistakes in short $lang video subtitles. Repair word spacing, spelling and " +
            "punctuation only. Never rephrase, translate, explain or add words. " +
            "Reply with the corrected subtitle only.<|im_end|>\n" +
            "<|im_start|>user\n$text<|im_end|>\n" +
            "<|im_start|>assistant\n"
    }

    private suspend fun releaseEngines() = engineLock.withLock {
        proofreader?.close(); proofreader = null
        llm?.close(); llm = null
    }

    override fun close() {
        setupJob?.cancel()
        proofreader?.close()
        llm?.close()
    }

    companion object {
        const val LOCAL_MODEL_FILE = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
        const val LOCAL_MODEL_URL =
            "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/$LOCAL_MODEL_FILE"
        const val LOCAL_MODEL_SIZE_MB = 547
        /** Prompt + answer budget; subtitles are short, a small budget keeps the KV cache light. */
        private const val MAX_TOKENS = 256
    }
}
