package com.ozkan.altyazi.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Optional read-aloud using the system TTS engine (offline when the voice is installed).
 * The engine is only created once the user turns the feature on.
 */
class Speaker(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var engineReady = false
    private var locale: Locale = Locale.forLanguageTag("tr-TR")
    private var voiceReady = false

    /** null = unknown yet, false = no voice for the selected language */
    private val _voiceAvailable = MutableStateFlow<Boolean?>(null)
    val voiceAvailable: StateFlow<Boolean?> = _voiceAvailable.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        if (!enabled) { stop(); return }
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            engineReady = status == TextToSpeech.SUCCESS
            if (engineReady) applyLocale() else _voiceAvailable.value = false
        }
    }

    fun setLanguage(languageTag: String) {
        locale = Locale.forLanguageTag(languageTag)
        if (engineReady) applyLocale()
    }

    fun speak(text: String) {
        if (voiceReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "subtitle")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        engineReady = false
        voiceReady = false
    }

    private fun applyLocale() {
        val engine = tts ?: return
        voiceReady = engine.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE
        engine.setSpeechRate(1.1f)
        _voiceAvailable.value = voiceReady
    }
}
