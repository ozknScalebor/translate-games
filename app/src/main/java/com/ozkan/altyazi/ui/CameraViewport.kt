package com.ozkan.altyazi.ui

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ozkan.altyazi.camera.awaitCameraProvider
import kotlinx.coroutines.CancellationException
import java.util.concurrent.Executor

/**
 * Camera preview + analysis. Both use the same 16:9 stream and the preview is FIT_CENTER-ed into a
 * 16:9 box, so the region drawn on screen maps 1:1 onto the analyzed frame.
 *
 * [cameraId] is the effect key: CameraSelector instances are not stable across recompositions.
 */
@Composable
fun CameraViewport(
    cameraId: String,
    selector: CameraSelector,
    analyzer: ImageAnalysis.Analyzer,
    executor: Executor,
    onError: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)

    LaunchedEffect(cameraId, lifecycleOwner) {
        try {
            val provider = context.awaitCameraProvider()
            val resolution = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    // 1080p keeps distant TV text sharp enough for OCR
                    ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                )
                .build()

            val preview = Preview.Builder().setResolutionSelector(resolution).build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(executor, analyzer)

            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            onError(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e.localizedMessage ?: e.javaClass.simpleName)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }
}
