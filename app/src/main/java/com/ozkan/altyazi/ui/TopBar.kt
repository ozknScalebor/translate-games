package com.ozkan.altyazi.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraFront
import androidx.compose.material.icons.rounded.CameraRear
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ozkan.altyazi.R
import com.ozkan.altyazi.camera.CameraSource
import com.ozkan.altyazi.translate.ModelState
import com.ozkan.altyazi.ui.theme.AppColors

@Composable
fun TopBar(
    cameras: List<CameraSource>,
    activeCameraId: String?,
    modelState: ModelState,
    paused: Boolean,
    onSelectCamera: (String) -> Unit,
    onTogglePause: () -> Unit,
    onFocusMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo(Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Spacer(Modifier.width(24.dp))

        // Camera sources
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            cameras.forEach { camera ->
                FilterChip(
                    selected = camera.id == activeCameraId,
                    onClick = { onSelectCamera(camera.id) },
                    label = { Text(camera.label(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(camera.kind.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppColors.Primary.copy(alpha = 0.22f),
                        selectedLabelColor = AppColors.TextPrimary,
                        selectedLeadingIconColor = AppColors.Primary,
                    ),
                )
            }
        }

        Spacer(Modifier.width(12.dp))
        ModelStatusPill(modelState, onDownload, onRetry)
        Spacer(Modifier.width(12.dp))

        FilledTonalIconButton(onClick = onTogglePause) {
            Icon(
                if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                stringResource(if (paused) R.string.action_resume else R.string.action_pause),
            )
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = onFocusMode) {
            Icon(Icons.Rounded.Fullscreen, stringResource(R.string.action_fullscreen))
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, stringResource(R.string.action_settings))
        }
    }
}

@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(AppColors.BrandGradient),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null)
    }
}

@Composable
fun CameraSource.label(): String {
    val base = stringResource(
        when (kind) {
            CameraSource.Kind.EXTERNAL -> R.string.camera_external
            CameraSource.Kind.BACK -> R.string.camera_back
            CameraSource.Kind.FRONT -> R.string.camera_front
            CameraSource.Kind.OTHER -> R.string.camera_other
        }
    )
    return ordinal?.let { stringResource(R.string.camera_numbered, base, it) } ?: base
}

@Composable
private fun ModelStatusPill(state: ModelState, onDownload: () -> Unit, onRetry: () -> Unit) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state) {
                ModelState.Ready -> {
                    Dot(AppColors.Success)
                    PillText(stringResource(R.string.status_ready))
                }
                ModelState.Checking, ModelState.Downloading -> {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = AppColors.Accent)
                    PillText(stringResource(if (state == ModelState.Downloading) R.string.status_downloading else R.string.status_checking))
                }
                is ModelState.NeedsDownload -> {
                    Dot(AppColors.Accent)
                    PillText(stringResource(R.string.status_missing))
                    PillAction(Icons.Rounded.Download, stringResource(R.string.action_download), onDownload)
                }
                is ModelState.Failed -> {
                    Dot(AppColors.Danger)
                    PillText(stringResource(R.string.status_failed))
                    PillAction(Icons.Rounded.Refresh, stringResource(R.string.action_retry), onRetry)
                }
            }
        }
    }
}

@Composable
private fun PillText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp).padding(vertical = 6.dp),
    )
}

@Composable
private fun PillAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp)) {
        Icon(icon, null, Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label)
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(9.dp).clip(CircleShape).background(color))
}

private fun CameraSource.Kind.icon(): ImageVector = when (this) {
    CameraSource.Kind.EXTERNAL -> Icons.Rounded.Usb
    CameraSource.Kind.BACK -> Icons.Rounded.CameraRear
    CameraSource.Kind.FRONT -> Icons.Rounded.CameraFront
    CameraSource.Kind.OTHER -> Icons.Rounded.Videocam
}
