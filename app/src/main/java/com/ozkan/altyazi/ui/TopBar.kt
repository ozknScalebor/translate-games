package com.ozkan.altyazi.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraFront
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.CameraRear
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ozkan.altyazi.R
import com.ozkan.altyazi.camera.CameraSource
import com.ozkan.altyazi.translate.ModelState
import com.ozkan.altyazi.ui.theme.AppColors

@Composable
fun TopBar(
    sourceControl: @Composable () -> Unit,
    modelState: ModelState,
    paused: Boolean,
    onChangeSource: () -> Unit,
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

        sourceControl()
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onChangeSource) {
            Icon(Icons.Rounded.SwapHoriz, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.source_change), maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
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

/** Connected PS5 shown in the top bar while streaming, with a disconnect action. */
@Composable
fun RemoteSourceChip(name: String, onDisconnect: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = AppColors.SurfaceHigh) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SportsEsports, null, tint = AppColors.Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(stringResource(R.string.remote_streaming_from), style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onDisconnect) { Text(stringResource(R.string.remote_disconnect), color = AppColors.Danger) }
        }
    }
}

/** Camera source dropdown; cameras plugged in at runtime show up here automatically. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraPicker(cameras: List<CameraSource>, activeCameraId: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val active = cameras.firstOrNull { it.id == activeCameraId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it && cameras.isNotEmpty() },
    ) {
        OutlinedTextField(
            value = active?.label() ?: stringResource(R.string.camera_none),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.camera_source)) },
            leadingIcon = { Icon(active?.kind?.icon() ?: Icons.Rounded.VideocamOff, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.Primary,
                unfocusedBorderColor = AppColors.Outline,
            ),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).width(280.dp),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = AppColors.SurfaceHigh,
        ) {
            cameras.forEach { camera ->
                DropdownMenuItem(
                    text = { Text(camera.label()) },
                    leadingIcon = { Icon(camera.kind.icon(), contentDescription = null) },
                    trailingIcon = {
                        if (camera.id == activeCameraId) Icon(Icons.Rounded.Check, stringResource(R.string.selected), tint = AppColors.Primary)
                    },
                    onClick = {
                        onSelect(camera.id)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
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
