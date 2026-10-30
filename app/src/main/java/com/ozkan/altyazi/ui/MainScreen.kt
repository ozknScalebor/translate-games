package com.ozkan.altyazi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ozkan.altyazi.R
import com.ozkan.altyazi.translate.ModelState
import com.ozkan.altyazi.translate.OfflineTranslator
import com.ozkan.altyazi.ui.theme.AppColors

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    // The camera card is movable content: switching between the split and fullscreen layouts moves
    // the live preview instead of tearing it down, so recognition never stops.
    val cameraCard = remember(viewModel) {
        movableContentOf { s: UiState, compact: Boolean, modifier: Modifier ->
            CameraCard(s, viewModel, compact, modifier)
        }
    }

    Box(Modifier.fillMaxSize().background(AppColors.Background)) {
        if (state.focusMode) {
            FocusLayout(
                state = state,
                camera = { modifier -> cameraCard(state, true, modifier) },
                onExit = { viewModel.setFocusMode(false) },
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                TopBar(
                    cameras = state.cameras,
                    activeCameraId = state.activeCameraId,
                    modelState = state.modelState,
                    paused = state.paused,
                    onSelectCamera = viewModel::selectCamera,
                    onTogglePause = viewModel::togglePause,
                    onFocusMode = { viewModel.setFocusMode(true) },
                    onOpenSettings = { settingsOpen = true },
                    onDownload = viewModel::requestDownload,
                    onRetry = viewModel::retryModel,
                )
                Row(
                    modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Column(Modifier.weight(0.46f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        cameraCard(state, false, Modifier.fillMaxWidth())
                        CameraFooter(state)
                    }
                    SubtitlePanel(
                        lines = state.lines,
                        settings = state.settings,
                        onScreen = state.subtitleOnScreen,
                        placeholder = placeholderFor(state),
                        modifier = Modifier.weight(0.54f).fillMaxHeight(),
                    )
                }
            }
        }

        if (settingsOpen) {
            SettingsSheet(
                settings = state.settings,
                voiceMissing = state.voiceMissing,
                onUpdate = viewModel::updateSettings,
                onResetRoi = viewModel::resetRoi,
                onClearHistory = viewModel::clearHistory,
                onDismiss = { settingsOpen = false },
            )
        }

        val needs = state.modelState as? ModelState.NeedsDownload
        if (state.showDownloadPrompt && needs != null) {
            DownloadDialog(
                source = stringResource(state.settings.sourceLanguage.label),
                target = stringResource(state.settings.targetLanguage.label),
                sizeMb = needs.missing.size * OfflineTranslator.PACK_SIZE_MB,
                onConfirm = viewModel::confirmDownload,
                onDismiss = viewModel::dismissDownloadPrompt,
            )
        }
    }
}

/** Live camera preview with the subtitle-region selector on top. */
@Composable
private fun CameraCard(state: UiState, viewModel: MainViewModel, compact: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(if (compact) 16.dp else 24.dp)
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, AppColors.Outline, shape)
    ) {
        val cameraId = state.activeCameraId
        val selector = viewModel.selectorFor(cameraId)
        if (cameraId != null && selector != null) {
            CameraViewport(
                cameraId = cameraId,
                selector = selector,
                analyzer = viewModel.analyzer,
                executor = viewModel.analysisExecutor,
                onError = viewModel::onCameraError,
                modifier = Modifier.fillMaxSize(),
            )
            RoiSelector(
                roi = state.settings.roi,
                onRoiChanged = viewModel::updateRoi,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (state.camerasLoaded) {
            CenterMessage(Icons.Rounded.VideocamOff, stringResource(R.string.camera_none))
        }

        if (state.paused) {
            Surface(
                color = AppColors.Accent,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(if (compact) 6.dp else 12.dp),
            ) {
                Text(
                    stringResource(R.string.paused_badge),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun CameraFooter(state: UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.TouchApp, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.hint_roi), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
        }
        state.cameraError?.let {
            Text(stringResource(R.string.camera_error, it), style = MaterialTheme.typography.bodySmall, color = AppColors.Danger)
            Text(stringResource(R.string.camera_hint_external), style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        }
        Spacer(Modifier.weight(1f))
        state.metrics?.let {
            Text(
                stringResource(R.string.metrics, it.ocrMs.toInt(), it.translateMs.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary,
            )
        }
    }
}

/**
 * Fullscreen subtitles. A small live preview stays in the top-left corner so the tablet can be
 * re-aimed (and the subtitle frame adjusted) without leaving fullscreen.
 */
@Composable
private fun FocusLayout(state: UiState, camera: @Composable (Modifier) -> Unit, onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(20.dp)) {
        SubtitlePanel(
            lines = state.lines,
            settings = state.settings.copy(textSizeSp = state.settings.textSizeSp * 1.25f),
            onScreen = state.subtitleOnScreen,
            placeholder = placeholderFor(state),
            modifier = Modifier.fillMaxSize(),
            // Keep the newest lines clear of the mini preview in the corner.
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 200.dp, bottom = 32.dp),
        )
        camera(Modifier.align(Alignment.TopStart).width(300.dp))
        FilledTonalIconButton(
            onClick = onExit,
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = AppColors.SurfaceHigh.copy(alpha = 0.85f)),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(Icons.Rounded.FullscreenExit, stringResource(R.string.action_exit_fullscreen))
        }
    }
}

@Composable
private fun placeholderFor(state: UiState): String = stringResource(
    when {
        state.paused -> R.string.placeholder_paused
        state.modelState is ModelState.NeedsDownload -> R.string.placeholder_missing
        state.modelState is ModelState.Failed -> R.string.placeholder_failed
        state.modelState !is ModelState.Ready -> R.string.placeholder_preparing
        else -> R.string.placeholder_waiting
    }
)

@Composable
private fun CenterMessage(icon: ImageVector, text: String) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = AppColors.TextSecondary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(text, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DownloadDialog(source: String, target: String, sizeMb: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.SurfaceHigh,
        title = { Text(stringResource(R.string.dialog_download_title)) },
        text = { Text(stringResource(R.string.dialog_download_body, source, target, sizeMb)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.dialog_download_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_download_later)) } },
    )
}
