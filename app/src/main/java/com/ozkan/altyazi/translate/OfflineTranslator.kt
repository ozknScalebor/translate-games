package com.ozkan.altyazi.translate

import android.util.LruCache
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.ozkan.altyazi.data.Language
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.Closeable

sealed interface ModelState {
    data object Checking : ModelState
    /** The pair needs language packs that are not on the device; nothing is downloaded without consent. */
    data class NeedsDownload(val missing: List<Language>) : ModelState
    data object Downloading : ModelState
    data object Ready : ModelState
    data class Failed(val reason: String) : ModelState
}

/**
 * Fully on-device translation via ML Kit.
 *
 * Language packs (~30 MB each) are only downloaded after the user agrees ([download]); afterwards
 * translation never touches the network. Once a pair is ready a short warm-up translation loads the
 * model into memory, so the first real subtitle is as fast as the rest.
 */
class OfflineTranslator(private val scope: CoroutineScope) : Closeable {

    private val _state = MutableStateFlow<ModelState>(ModelState.Checking)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private val cache = LruCache<String, String>(512)
    private var translator: Translator? = null
    private var pair: Pair<Language, Language>? = null
    private var job: Job? = null

    fun prepare(source: Language, target: Language) {
        val newPair = source to target
        if (newPair == pair && _state.value !is ModelState.Failed) return
        pair = newPair
        job?.cancel()
        translator?.close()
        cache.evictAll()

        val client = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source.key)
                .setTargetLanguage(target.key)
                .build()
        )
        translator = client
        job = scope.launch { check(client, newPair) }
    }

    /** Starts downloading the missing language packs for the current pair. */
    fun download() {
        val client = translator ?: return
        job?.cancel()
        job = scope.launch {
            guard(client) {
                _state.value = ModelState.Downloading
                client.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                warmUpAndMarkReady(client)
            }
        }
    }

    fun retry() {
        val (source, target) = pair ?: return
        pair = null
        prepare(source, target)
    }

    suspend fun translate(text: String): String {
        cache.get(text)?.let { return it }
        val client = translator ?: error("Translator is not ready")
        return client.translate(text).await().also { cache.put(text, it) }
    }

    private suspend fun check(client: Translator, pair: Pair<Language, Language>) = guard(client) {
        _state.value = ModelState.Checking
        val missing = missingModels(pair)
        if (missing.isEmpty()) warmUpAndMarkReady(client)
        else _state.value = ModelState.NeedsDownload(missing)
    }

    private suspend fun missingModels(pair: Pair<Language, Language>): List<Language> {
        val downloaded = RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java).await()
            .map { it.language }
            .toSet()
        // English ships with ML Kit and is always available.
        return listOf(pair.first, pair.second).filter { it != Language.ENGLISH && it.key !in downloaded }
    }

    private suspend fun warmUpAndMarkReady(client: Translator) {
        client.translate(WARM_UP_TEXT).await()
        if (client === translator) _state.value = ModelState.Ready
    }

    private suspend fun guard(client: Translator, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (client === translator) _state.value = ModelState.Failed(e.localizedMessage ?: e.javaClass.simpleName)
        }
    }

    override fun close() {
        job?.cancel()
        translator?.close()
        translator = null
    }

    companion object {
        const val PACK_SIZE_MB = 30
        private const val WARM_UP_TEXT = "Hello there."
    }
}
