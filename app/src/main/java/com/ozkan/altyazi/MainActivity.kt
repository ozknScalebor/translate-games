package com.ozkan.altyazi

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalLensFacing
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.ozkan.altyazi.databinding.ActivityMainBinding
import java.util.concurrent.Executors

@androidx.annotation.OptIn(ExperimentalLensFacing::class)
class MainActivity : AppCompatActivity(), SubtitlePipeline.Listener {

    private lateinit var b: ActivityMainBinding
    private lateinit var pipeline: SubtitlePipeline
    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var cameras: List<CameraInfo> = emptyList()
    private var cameraIndex = 0
    @Volatile private var roi = RectF(0.10f, 0.72f, 0.90f, 0.95f)
    private var lastAnalysis = 0L
    private val minIntervalMs = 200L   // saniyede ~5 OCR: hızlı ama tableti ısıtmaz

    private val prefs by lazy { getSharedPreferences("ayarlar", MODE_PRIVATE) }

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startCamera() else onStatus("Kamera izni gerekli.")
    }

    // USB kamera takılınca/çıkarılınca kamera listesini yenile
    private val availability = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) = scheduleRebind()
        override fun onCameraUnavailable(cameraId: String) {}
    }
    private val rebindRunnable = Runnable { if (cameraProvider != null) bindCamera(preferExternal = true) }
    private fun scheduleRebind() { mainHandler.removeCallbacks(rebindRunnable); mainHandler.postDelayed(rebindRunnable, 800) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        roi = RectF(
            prefs.getFloat("l", roi.left), prefs.getFloat("t", roi.top),
            prefs.getFloat("r", roi.right), prefs.getFloat("b", roi.bottom)
        )
        b.roi.setRoi(roi)
        b.roi.onRoiChanged = { r ->
            roi = r
            prefs.edit().putFloat("l", r.left).putFloat("t", r.top).putFloat("r", r.right).putFloat("b", r.bottom).apply()
        }

        pipeline = SubtitlePipeline(this, this)
        pipeline.speakEnabled = prefs.getBoolean("speak", true)
        updateSpeakButton()

        b.btnSpeak.setOnClickListener {
            pipeline.speakEnabled = !pipeline.speakEnabled
            if (!pipeline.speakEnabled) pipeline.stopSpeaking()
            prefs.edit().putBoolean("speak", pipeline.speakEnabled).apply()
            updateSpeakButton()
        }
        b.btnCamera.setOnClickListener {
            if (cameras.size > 1) { cameraIndex = (cameraIndex + 1) % cameras.size; bindCamera(preferExternal = false) }
            else Toast.makeText(this, "Başka kamera bulunamadı", Toast.LENGTH_SHORT).show()
        }
        b.btnFocus.setOnClickListener { setFocusMode(true) }
        b.textPanel.setOnClickListener { if (b.cameraFrame.visibility != View.VISIBLE) setFocusMode(false) }

        getSystemService(CameraManager::class.java).registerAvailabilityCallback(availability, mainHandler)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }

    private fun updateSpeakButton() { b.btnSpeak.text = if (pipeline.speakEnabled) "Ses: Açık" else "Ses: Kapalı" }

    /** Tam ekran yazı modu: kamera gizlenir, çeviri tüm ekranı kaplar. Yazıya dokununca geri döner. */
    private fun setFocusMode(on: Boolean) {
        b.cameraFrame.visibility = if (on) View.INVISIBLE else View.VISIBLE
        b.controls.visibility = if (on) View.GONE else View.VISIBLE
        val lp = b.textPanel.layoutParams as ConstraintLayout.LayoutParams
        if (on) { lp.startToEnd = ConstraintLayout.LayoutParams.UNSET; lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID }
        else { lp.startToStart = ConstraintLayout.LayoutParams.UNSET; lp.startToEnd = b.cameraFrame.id }
        b.textPanel.layoutParams = lp
        b.translated.textSize = if (on) 48f else 34f
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
            bindCamera(preferExternal = true)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera(preferExternal: Boolean) {
        val provider = cameraProvider ?: return
        cameras = provider.availableCameraInfos
        if (cameras.isEmpty()) { onStatus("Kamera bulunamadı."); return }

        if (preferExternal) {
            val ext = cameras.indexOfFirst { it.lensFacing == CameraSelector.LENS_FACING_EXTERNAL }
            val back = cameras.indexOfFirst { it.lensFacing == CameraSelector.LENS_FACING_BACK }
            cameraIndex = when { ext >= 0 -> ext; back >= 0 -> back; else -> 0 }
        }
        cameraIndex = cameraIndex.coerceIn(0, cameras.lastIndex)
        val info = cameras[cameraIndex]

        val selector16x9 = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .build()

        val preview = Preview.Builder().setResolutionSelector(selector16x9).build()
            .also { it.surfaceProvider = b.preview.surfaceProvider }

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(selector16x9)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        analysis.setAnalyzer(analysisExecutor, ::analyze)

        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, info.cameraSelector, preview, analysis)
            val name = when (info.lensFacing) {
                CameraSelector.LENS_FACING_EXTERNAL -> "Harici USB kamera"
                CameraSelector.LENS_FACING_BACK -> "Arka kamera"
                CameraSelector.LENS_FACING_FRONT -> "Ön kamera"
                else -> "Kamera"
            }
            if (pipeline.translatorReady) onStatus("$name kullanılıyor. Sarı kutuyu altyazı bölgesine yerleştir.")
            else Toast.makeText(this, "$name kullanılıyor", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            onStatus("Kamera açılamadı: ${e.message}")
        }
    }

    private fun analyze(image: ImageProxy) {
        image.use { img ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastAnalysis < minIntervalMs) return
            lastAnalysis = now

            val full = img.toBitmap()
            val rotated = rotate(full, img.imageInfo.rotationDegrees)
            val r = roi
            val x = (r.left * rotated.width).toInt().coerceIn(0, rotated.width - 1)
            val y = (r.top * rotated.height).toInt().coerceIn(0, rotated.height - 1)
            val w = (r.width() * rotated.width).toInt().coerceIn(1, rotated.width - x)
            val h = (r.height() * rotated.height).toInt().coerceIn(1, rotated.height - y)
            val crop = Bitmap.createBitmap(rotated, x, y, w, h)
            pipeline.processBlocking(crop)
        }
    }

    private fun rotate(src: Bitmap, deg: Int): Bitmap {
        if (deg == 0) return src
        val m = Matrix().apply { postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    override fun onStatus(text: String) = runOnUiThread { b.status.text = text }

    override fun onSubtitle(original: String, translated: String) {
        b.translated.text = translated
        b.original.text = original
    }

    override fun onDestroy() {
        super.onDestroy()
        getSystemService(CameraManager::class.java).unregisterAvailabilityCallback(availability)
        mainHandler.removeCallbacks(rebindRunnable)
        analysisExecutor.shutdown()
        pipeline.close()
    }
}
