package com.ozkan.altyazi.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Persists [AppSettings]. The UI language is stored separately by AppCompat (per-app locales). */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    private object Keys {
        val textSize = floatPreferencesKey("text_size")
        val font = stringPreferencesKey("font")
        val bold = booleanPreferencesKey("bold")
        val textColor = longPreferencesKey("text_color")
        val background = stringPreferencesKey("background")
        val showOriginal = booleanPreferencesKey("show_original")
        val speak = booleanPreferencesKey("speak")
        val aiCorrection = booleanPreferencesKey("ai_correction")
        val sourceLanguage = stringPreferencesKey("source_language")
        val targetLanguage = stringPreferencesKey("target_language")
        val quality = stringPreferencesKey("quality")
        val roiLeft = floatPreferencesKey("roi_left")
        val roiTop = floatPreferencesKey("roi_top")
        val roiRight = floatPreferencesKey("roi_right")
        val roiBottom = floatPreferencesKey("roi_bottom")
        val cameraId = stringPreferencesKey("camera_id")
    }

    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }
        .distinctUntilChanged()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        val roi = Roi(
            this[Keys.roiLeft] ?: d.roi.left,
            this[Keys.roiTop] ?: d.roi.top,
            this[Keys.roiRight] ?: d.roi.right,
            this[Keys.roiBottom] ?: d.roi.bottom,
        ).takeIf { it.width >= Roi.MIN_SIZE && it.height >= Roi.MIN_SIZE } ?: Roi.DEFAULT

        val source = keyedValueOf(this[Keys.sourceLanguage], d.sourceLanguage)
            .takeIf { it.ocrSupported } ?: d.sourceLanguage
        val target = keyedValueOf(this[Keys.targetLanguage], d.targetLanguage)
            .takeIf { it != source } ?: if (source == Language.TURKISH) Language.ENGLISH else Language.TURKISH

        return AppSettings(
            textSizeSp = (this[Keys.textSize] ?: d.textSizeSp).coerceIn(AppSettings.MIN_TEXT_SP, AppSettings.MAX_TEXT_SP),
            font = keyedValueOf(this[Keys.font], d.font),
            bold = this[Keys.bold] ?: d.bold,
            textColor = this[Keys.textColor] ?: d.textColor,
            background = keyedValueOf(this[Keys.background], d.background),
            showOriginal = this[Keys.showOriginal] ?: d.showOriginal,
            speak = this[Keys.speak] ?: d.speak,
            aiCorrection = this[Keys.aiCorrection] ?: d.aiCorrection,
            sourceLanguage = source,
            targetLanguage = target,
            quality = keyedValueOf(this[Keys.quality], d.quality),
            roi = roi,
            cameraId = this[Keys.cameraId],
        )
    }

    private fun MutablePreferences.write(s: AppSettings) {
        this[Keys.textSize] = s.textSizeSp
        this[Keys.font] = s.font.key
        this[Keys.bold] = s.bold
        this[Keys.textColor] = s.textColor
        this[Keys.background] = s.background.key
        this[Keys.showOriginal] = s.showOriginal
        this[Keys.speak] = s.speak
        this[Keys.aiCorrection] = s.aiCorrection
        this[Keys.sourceLanguage] = s.sourceLanguage.key
        this[Keys.targetLanguage] = s.targetLanguage.key
        this[Keys.quality] = s.quality.key
        this[Keys.roiLeft] = s.roi.left
        this[Keys.roiTop] = s.roi.top
        this[Keys.roiRight] = s.roi.right
        this[Keys.roiBottom] = s.roi.bottom
        if (s.cameraId != null) this[Keys.cameraId] = s.cameraId else remove(Keys.cameraId)
    }
}
