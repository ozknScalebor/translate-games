package com.ozkan.altyazi.camera

import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalLensFacing
import androidx.camera.lifecycle.ProcessCameraProvider

/**
 * A selectable camera. [ordinal] is set (1, 2, …) only when several cameras share the same [kind],
 * so the UI can render "Rear camera 2"; the label itself is localized in the UI layer.
 */
data class CameraSource(val id: String, val kind: Kind, val ordinal: Int?) {
    enum class Kind { EXTERNAL, BACK, FRONT, OTHER }
}

/** Lists available cameras, external (USB/UVC) ones first. */
object CameraSources {

    @OptIn(ExperimentalCamera2Interop::class, ExperimentalLensFacing::class)
    fun list(provider: ProcessCameraProvider): List<Pair<CameraSource, CameraInfo>> {
        val raw = provider.availableCameraInfos.mapNotNull { info ->
            val id = runCatching { Camera2CameraInfo.from(info).cameraId }.getOrNull() ?: return@mapNotNull null
            val kind = when (info.lensFacing) {
                CameraSelector.LENS_FACING_EXTERNAL -> CameraSource.Kind.EXTERNAL
                CameraSelector.LENS_FACING_BACK -> CameraSource.Kind.BACK
                CameraSelector.LENS_FACING_FRONT -> CameraSource.Kind.FRONT
                else -> CameraSource.Kind.OTHER
            }
            Triple(id, kind, info)
        }.sortedBy { it.second.ordinal }

        val totals = raw.groupingBy { it.second }.eachCount()
        val seen = mutableMapOf<CameraSource.Kind, Int>()
        return raw.map { (id, kind, info) ->
            val n = (seen[kind] ?: 0) + 1
            seen[kind] = n
            CameraSource(id, kind, n.takeIf { (totals[kind] ?: 0) > 1 }) to info
        }
    }
}
