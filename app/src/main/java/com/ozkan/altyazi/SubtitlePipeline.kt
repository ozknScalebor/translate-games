package com.ozkan.altyazi

import android.content.Context
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * OCR -> kararlılık kontrolü -> çeviri -> ekran + ses.
 *
 * Hız için:
 *  - OCR sadece seçili bölgede çalışır.
 *  - Aynı metin art arda iki karede okunmadan çeviriye gönderilmez
 *    (kamera bulanıklığı / altyazı geçiş animasyonu yüzünden yarım okumaları eler).
 *  - Önceki altyazıya çok benzeyen okumalar yok sayılır (OCR titremesi).
 *  - Çeviri ve OCR tamamen cihaz üzerinde, ağ beklemesi yok.
 */
class SubtitlePipeline(
    context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onStatus(text: String)
        fun onSubtitle(original: String, translated: String)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val translator: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.TURKISH)
            .build()
    )

    @Volatile var translatorReady = false
        private set
    @Volatile var speakEnabled = true

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var candidate = ""          // son okunan, henüz onaylanmamış metin
    private var candidateHits = 0
    @Volatile private var lastShown = ""          // en son çevrilip gösterilen metin
    private var emptyFrames = 0
    private val translationCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 300
    }

    init {
        listener.onStatus("Çeviri modeli hazırlanıyor (ilk seferde internet gerekir)…")
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                translatorReady = true
                listener.onStatus("Hazır. Sarı kutuyu altyazı bölgesine yerleştir.")
            }
            .addOnFailureListener {
                listener.onStatus("Çeviri modeli indirilemedi: ${it.message}. İnternete bağlanıp uygulamayı yeniden aç.")
            }

        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = tts?.setLanguage(Locale("tr", "TR"))
                ttsReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
                tts?.setSpeechRate(1.15f)
                if (!ttsReady) listener.onStatus("Türkçe ses paketi yok: Ayarlar > Genel yönetim > Metin okuma bölümünden Türkçe sesi indir.")
            }
        }
    }

    /** Kamera thread'inden çağrılır; bitmap zaten altyazı bölgesine kırpılmış olmalı. Senkron bekler. */
    fun processBlocking(bitmap: Bitmap) {
        if (!translatorReady) return
        val text = try {
            val result = com.google.android.gms.tasks.Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
            normalize(result.textBlocks.joinToString(" ") { b -> b.lines.joinToString(" ") { it.text } })
        } catch (e: Exception) {
            return
        }

        if (text.length < 2) {
            // Altyazı ekrandan kalktıysa bir süre sonra "son metni" sıfırla ki aynı replik tekrar gelirse yine okunsun
            if (++emptyFrames > 6) { lastShown = ""; candidate = "" }
            return
        }
        emptyFrames = 0

        if (similarity(text, candidate) > 0.9) candidateHits++ else { candidate = text; candidateHits = 1 }
        if (candidateHits < 2) return
        if (similarity(candidate, lastShown) > 0.85) return

        lastShown = candidate
        val source = candidate
        scope.launch { translateAndEmit(source) }
    }

    private suspend fun translateAndEmit(source: String) {
        val tr = translationCache[source] ?: try {
            translator.translate(source).await().also { translationCache[source] = it }
        } catch (e: Exception) {
            return
        }
        if (source != lastShown) return // bu arada yeni altyazı geldiyse eskiyi gösterme
        withContext(Dispatchers.Main) {
            listener.onSubtitle(source, tr)
            if (speakEnabled && ttsReady) tts?.speak(tr, TextToSpeech.QUEUE_FLUSH, null, source.hashCode().toString())
        }
    }

    fun stopSpeaking() { tts?.stop() }

    fun close() {
        recognizer.close(); translator.close()
        tts?.stop(); tts?.shutdown()
    }

    private fun normalize(s: String): String = s
        .replace(Regex("[|_~`]"), " ")      // OCR'ın sık uydurduğu karakterler
        .replace(Regex("\\s+"), " ")
        .trim()

    /** 0..1 arası benzerlik (Levenshtein tabanlı) */
    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return if (a == b) 1.0 else 0.0
        val x = a.lowercase(); val y = b.lowercase()
        var prev = IntArray(y.length + 1) { it }
        var cur = IntArray(y.length + 1)
        for (i in 1..x.length) {
            cur[0] = i
            for (j in 1..y.length) {
                val cost = if (x[i - 1] == y[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return 1.0 - prev[y.length].toDouble() / max(x.length, y.length)
    }
}
