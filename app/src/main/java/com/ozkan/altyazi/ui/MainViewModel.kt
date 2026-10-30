package com.ozkan.altyazi.ui

import android.app.Application
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ozkan.altyazi.camera.CameraSource
import com.ozkan.altyazi.camera.CameraSources
import com.ozkan.altyazi.camera.FrameCropper
import com.ozkan.altyazi.core.SubtitleStabilizer
import com.ozkan.altyazi.core.SubtitleText
import com.ozkan.altyazi.data.AppSettings
import com.ozkan.altyazi.data.Roi
import com.ozkan.altyazi.data.SettingsRepository
import com.ozkan.altyazi.ocr.SubtitleOcr
import com.ozkan.altyazi.translate.ModelState
import com.ozkan.altyazi.translate.OfflineTranslator
import com.ozkan.altyazi.tts.Speaker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class SubtitleLine(val id: Long, val original: String, val translated: String)

data class Metrics(val ocrMs: Long, val translateMs: Long)

data class UiState(
    val settings: AppSettings = AppSettings(),
    val settingsLoaded: Boolean = false,
    val cameras: List<CameraSource> = emptyList(),
    val camerasLoaded: Boolean = false,
    val activeCameraId: String? = null,
    val cameraError: String? = null,
    val modelState: ModelState = ModelState.Checking,
    val showDownloadPrompt: Boolean = false,
    val current: SubtitleLine? = null,
    val history: List<SubtitleLine> = emptyList(),
    val subtitleOnScreen: Boolean = false,
    val metrics: Metrics? = null,
    val paused: Boolean = false,
    val focusMode: Boolean = false,
    val voiceMissing: Boolean = false,
)

/**
 * Pipeline: camera frame → subtitle crop → OCR → stabilizer → offline translation → screen (+ voice).
 *
 * OCR runs synchronously on the analysis thread (CameraX keeps only the latest frame, which gives
 * natural back-pressure). Translation runs on the main scope and the newest subtitle always wins.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepository = SettingsRepository(app)
    private val ocr = SubtitleOcr()
    private val translator = OfflineTranslator(viewModelScope)
    private val speaker = Speaker(app)
    private val stabilizer = SubtitleStabilizer()

    val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "subtitle-analysis") }
    val analyzer = ImageAnalysis.Analyzer { image -> image.use(::analyze) }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    // Snapshots read by the analysis thread.
    @Volatile private var settings = AppSettings()
    @Volatile private var paused = false
    @Volatile private var modelReady = false
    private var lastFrameAt = 0L

    private var translateJob: Job? = null
    private var nextLineId = 0L

    private val cameraInfos = mutableMapOf<String, CameraInfo>()
    private val cameraManager = app.getSystemService(CameraManager::class.java)
    private var cameraRefreshJob: Job? = null
    private val availabilityCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) = scheduleCameraRefresh()
        override fun onCameraUnavailable(cameraId: String) = scheduleCameraRefresh()
    }

    init {
        viewModelScope.launch {
            var first = true
            settingsRepository.settings.collect { s ->
                val previous = settings
                settings = s
                val pairChanged = previous.sourceLanguage != s.sourceLanguage || previous.targetLanguage != s.targetLanguage
                if (first || pairChanged) {
                    translator.prepare(s.sourceLanguage, s.targetLanguage)
                    stabilizer.reset()
                }
                if (first || previous.targetLanguage != s.targetLanguage) speaker.setLanguage(s.targetLanguage.key)
                if (first || previous.speak != s.speak) speaker.setEnabled(s.speak)
                if (previous.roi != s.roi) stabilizer.reset()
                _ui.update { it.copy(settings = s, settingsLoaded = true) }
                if (first || previous.cameraId != s.cameraId) chooseCamera()
                first = false
            }
        }
        viewModelScope.launch {
            translator.state.collect { state ->
                modelReady = state is ModelState.Ready
                _ui.update { it.copy(modelState = state, showDownloadPrompt = state is ModelState.NeedsDownload) }
            }
        }
        viewModelScope.launch {
            speaker.voiceAvailable.collect { available ->
                _ui.update { it.copy(voiceMissing = available == false) }
            }
        }
        cameraManager.registerAvailabilityCallback(availabilityCallback, Handler(Looper.getMainLooper()))
        viewModelScope.launch { refreshCameras() }
    }

    // ---------- Camera ----------

    fun selectorFor(id: String?): CameraSelector? = id?.let { cameraInfos[it]?.cameraSelector }

    fun selectCamera(id: String) {
        _ui.update { it.copy(activeCameraId = id, cameraError = null) }
        updateSettings { it.copy(cameraId = id) }
    }

    fun onCameraError(message: String?) = _ui.update { it.copy(cameraError = message) }

    private fun scheduleCameraRefresh() {
        cameraRefreshJob?.cancel()
        cameraRefreshJob = viewModelScope.launch {
            delay(700) // coalesce the burst of events a USB plug/unplug produces
            refreshCameras()
        }
    }

    private suspend fun refreshCameras() {
        val provider = try {
            ProcessCameraProvider.awaitInstance(getApplication<Application>())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(cameraError = e.localizedMessage ?: e.javaClass.simpleName, camerasLoaded = true) }
            return
        }
        val previousIds = cameraInfos.keys.toSet()
        val sources = CameraSources.list(provider)
        cameraInfos.clear()
        sources.forEach { (source, info) -> cameraInfos[source.id] = info }
        _ui.update { it.copy(cameras = sources.map { s -> s.first }, camerasLoaded = true) }

        // A camera plugged in while the app is running is picked automatically.
        val newExternal = sources.map { it.first }.firstOrNull {
            it.kind == CameraSource.Kind.EXTERNAL && previousIds.isNotEmpty() && it.id !in previousIds
        }
        if (newExternal != null) selectCamera(newExternal.id) else chooseCamera()
    }

    private fun chooseCamera() {
        val cameras = _ui.value.cameras
        if (cameras.isEmpty()) {
            _ui.update { it.copy(activeCameraId = null) }
            return
        }
        val remembered = settings.cameraId?.takeIf { id -> cameras.any { it.id == id } }
        val chosen = remembered
            ?: cameras.firstOrNull { it.kind == CameraSource.Kind.EXTERNAL }?.id
            ?: cameras.firstOrNull { it.kind == CameraSource.Kind.BACK }?.id
            ?: cameras.first().id
        if (chosen != _ui.value.activeCameraId) _ui.update { it.copy(activeCameraId = chosen, cameraError = null) }
    }

    // ---------- Analysis pipeline ----------

    private fun analyze(image: ImageProxy) {
        if (paused || !modelReady) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameAt < MIN_FRAME_INTERVAL_MS) return
        lastFrameAt = now

        val s = settings
        val started = SystemClock.elapsedRealtime()
        val text = try {
            ocr.readBlocking(FrameCropper.crop(image, s.roi))
        } catch (e: Exception) {
            ""
        }
        val ocrMs = SystemClock.elapsedRealtime() - started

        stabilizer.requiredHits = s.stability.requiredHits
        when (val event = stabilizer.accept(if (SubtitleText.isMeaningful(text)) text else "")) {
            is SubtitleStabilizer.Event.Commit -> onCommit(event.text, ocrMs)
            SubtitleStabilizer.Event.Cleared -> _ui.update { it.copy(subtitleOnScreen = false) }
            null -> Unit
        }
    }

    private fun onCommit(text: String, ocrMs: Long) {
        viewModelScope.launch {
            translateJob?.cancel() // a newer subtitle makes the pending translation obsolete
            translateJob = launch {
                val line = SubtitleText.splitSpeaker(text)
                val started = SystemClock.elapsedRealtime()
                val translatedBody = try {
                    translator.translate(line.body)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@launch
                }
                val translated = line.format(translatedBody)
                val translateMs = SystemClock.elapsedRealtime() - started

                val newLine = SubtitleLine(nextLineId++, text, translated)
                _ui.update { state ->
                    state.copy(
                        current = newLine,
                        history = (listOfNotNull(state.current) + state.history).take(HISTORY_SIZE),
                        subtitleOnScreen = true,
                        metrics = Metrics(ocrMs, translateMs),
                    )
                }
                if (settings.speak) speaker.speak(translated)
            }
        }
    }

    // ---------- User actions ----------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun updateRoi(roi: Roi) = updateSettings { it.copy(roi = roi) }

    fun resetRoi() = updateRoi(Roi.DEFAULT)

    fun togglePause() {
        paused = !paused
        if (paused) speaker.stop() else stabilizer.reset()
        _ui.update { it.copy(paused = paused) }
    }

    fun setFocusMode(enabled: Boolean) = _ui.update { it.copy(focusMode = enabled) }

    fun confirmDownload() {
        _ui.update { it.copy(showDownloadPrompt = false) }
        translator.download()
    }

    fun dismissDownloadPrompt() = _ui.update { it.copy(showDownloadPrompt = false) }

    fun requestDownload() {
        if (_ui.value.modelState is ModelState.NeedsDownload) _ui.update { it.copy(showDownloadPrompt = true) }
    }

    fun retryModel() = translator.retry()

    override fun onCleared() {
        cameraManager.unregisterAvailabilityCallback(availabilityCallback)
        analysisExecutor.shutdown()
        ocr.close()
        translator.close()
        speaker.shutdown()
    }

    private companion object {
        /** At most ~8 OCR passes per second: catches subtitles instantly without heating the tablet. */
        const val MIN_FRAME_INTERVAL_MS = 120L
        const val HISTORY_SIZE = 2
    }
}
