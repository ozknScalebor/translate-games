package com.ozkan.altyazi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ozkan.altyazi.R
import com.ozkan.altyazi.ai.AiTranslator
import com.ozkan.altyazi.data.FrameSource
import com.ozkan.altyazi.remote.Connection
import com.ozkan.altyazi.remote.SavedConsole
import com.ozkan.altyazi.translate.ModelState
import com.ozkan.altyazi.translate.OfflineTranslator
import com.ozkan.altyazi.ui.remote.RemotePlayActions
import com.ozkan.altyazi.ui.remote.RemotePlayScreen
import com.ozkan.altyazi.ui.theme.AppColors

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val remote by viewModel.remote.state.collectAsStateWithLifecycle()
    val remoteActions = remember(viewModel) { remoteActionsFor(viewModel) }

    Box(Modifier.fillMaxSize().background(AppColors.Background)) {
        when (state.source) {
            null -> SourcePicker(onSelect = viewModel::selectSource)
            FrameSource.CAMERA -> {
                BackHandler { viewModel.selectSource(null) }
                CameraPermissionGate(onBack = { viewModel.selectSource(null) }) {
                    TranslatorLayout(state, viewModel, streamingFrom = null)
                }
            }
            FrameSource.REMOTE_PLAY -> {
                val connection = remote.connection
                if (connection is Connection.Streaming) {
                    BackHandler { viewModel.remote.disconnect() }
                    TranslatorLayout(state, viewModel, streamingFrom = connection.console.name)
                } else {
                    RemotePlayScreen(remote, remoteActions)
                }
            }
        }

        if (state.showAiDownloadPrompt) {
            AlertDialog(
                onDismissRequest = viewModel::dismissAiDownload,
                containerColor = AppColors.SurfaceHigh,
                title = { Text(stringResource(R.string.ai_dialog_title)) },
                text = { Text(stringResource(R.string.ai_dialog_body, stringResource(R.string.ai_size_gb, AiTranslator.MODEL_SIZE_MB / 1024f))) },
                confirmButton = { TextButton(onClick = viewModel::confirmAiDownload) { Text(stringResource(R.string.dialog_download_confirm)) } },
                dismissButton = { TextButton(onClick = viewModel::dismissAiDownload) { Text(stringResource(R.string.dialog_download_later)) } },
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

private fun remoteActionsFor(viewModel: MainViewModel) = object : RemotePlayActions {
    private val remote = viewModel.remote
    override fun back() = viewModel.selectSource(null)
    override fun useCamera() = viewModel.selectSource(FrameSource.CAMERA)
    override fun connect(console: SavedConsole) = remote.connect(console)
    override fun cancelConnect() = remote.disconnect()
    override fun dismissError() = remote.dismissError()
    override fun submitLoginPin(pin: String) = remote.submitLoginPin(pin)
    override fun pair(host: String, accountId: String, pin: String, name: String) = remote.pair(host, accountId, pin, name)
    override fun cancelPairing() = remote.cancelPairing()
    override fun resetPairing() = remote.resetPairing()
    override fun rename(id: String, name: String) = remote.rename(id, name)
    override fun forget(id: String) = remote.forget(id)
}

/**
 * Picture on the left, translated subtitles on the right (or fullscreen subtitles with a small
 * picture in the corner). [streamingFrom] is the PS5's name in Remote Play mode, null for a camera.
 */
@Composable
private fun TranslatorLayout(state: UiState, viewModel: MainViewModel, streamingFrom: String?) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val remoteMode = streamingFrom != null

    // The picture card is movable content: switching between the split and fullscreen layouts moves
    // the live preview instead of tearing it down, so recognition never stops.
    val pictureCard = remember(viewModel, remoteMode) {
        movableContentOf { s: UiState, compact: Boolean, modifier: Modifier ->
            if (remoteMode) StreamCard(s, viewModel, compact, modifier) else CameraCard(s, viewModel, compact, modifier)
        }
    }

    if (state.focusMode) {
        FocusLayout(
            state = state,
            camera = { modifier -> pictureCard(state, true, modifier) },
            onTogglePause = viewModel::togglePause,
            onExit = { viewModel.setFocusMode(false) },
        )
    } else {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                sourceControl = {
                    if (streamingFrom != null) RemoteSourceChip(streamingFrom, onDisconnect = viewModel.remote::disconnect)
                    else CameraPicker(state.cameras, state.activeCameraId, viewModel::selectCamera)
                },
                modelState = state.modelState,
                paused = state.paused,
                onChangeSource = { viewModel.selectSource(null) },
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
                    pictureCard(state, false, Modifier.fillMaxWidth())
                    CameraFooter(state, remoteMode)
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
            aiState = state.aiState,
            onAiDownload = viewModel::requestAiDownload,
            onAiRetry = viewModel::retryAi,
            onUpdate = viewModel::updateSettings,
            onResetRoi = viewModel::resetRoi,
            onClearHistory = viewModel::clearHistory,
            onDismiss = { settingsOpen = false },
        )
    }
}

/** The PS5's own picture (Remote Play) with the subtitle-region selector on top. */
@Composable
private fun StreamCard(state: UiState, viewModel: MainViewModel, compact: Boolean, modifier: Modifier) {
    val preview by viewModel.remote.preview.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(if (compact) 16.dp else 24.dp)
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, AppColors.Outline, shape)
    ) {
        val frame = preview
        if (frame != null) {
            // seq changes with every frame; the bitmaps themselves are reused.
            val image = remember(frame.seq) { frame.bitmap.asImageBitmap() }
            Image(image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = AppColors.Primary)
                if (!compact) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.remote_waiting_picture), color = AppColors.TextSecondary)
                }
            }
        }
        RoiSelector(
            roi = state.settings.streamRoi,
            onRoiChanged = viewModel::updateRoi,
            modifier = Modifier.fillMaxSize(),
        )
        PausedBadge(state.paused, compact)
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

        PausedBadge(state.paused, compact)
    }
}

@Composable
private fun BoxScope.PausedBadge(paused: Boolean, compact: Boolean) {
    if (!paused) return
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

@Composable
private fun CameraFooter(state: UiState, remoteMode: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.TouchApp, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (remoteMode) R.string.hint_roi_stream else R.string.hint_roi),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
        }
        if (!remoteMode) state.cameraError?.let {
            Text(stringResource(R.string.camera_error, it), style = MaterialTheme.typography.bodySmall, color = AppColors.Danger)
            Text(stringResource(R.string.camera_hint_external), style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        }
        Spacer(Modifier.weight(1f))
        state.metrics?.let {
            Text(
                if (it.aiMs != null) stringResource(R.string.metrics_ai, it.ocrMs.toInt(), it.aiMs.toInt())
                else stringResource(R.string.metrics, it.ocrMs.toInt(), (it.translateMs ?: 0L).toInt()),
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
private fun FocusLayout(
    state: UiState,
    camera: @Composable (Modifier) -> Unit,
    onTogglePause: () -> Unit,
    onExit: () -> Unit,
) {
    // The system back gesture leaves fullscreen instead of closing the app.
    BackHandler(onBack = onExit)
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
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = onTogglePause,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = AppColors.SurfaceHigh),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    if (state.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    stringResource(if (state.paused) R.string.action_resume else R.string.action_pause),
                )
            }
            Button(
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Icon(Icons.Rounded.FullscreenExit, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_exit_fullscreen))
            }
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
