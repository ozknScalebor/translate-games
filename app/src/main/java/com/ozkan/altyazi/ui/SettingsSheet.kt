@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ozkan.altyazi.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.ozkan.altyazi.R
import com.ozkan.altyazi.ai.AiBackend
import com.ozkan.altyazi.ai.AiState
import com.ozkan.altyazi.data.AppSettings
import com.ozkan.altyazi.data.Language
import com.ozkan.altyazi.data.PanelBackground
import com.ozkan.altyazi.data.Quality
import com.ozkan.altyazi.data.SubtitleFont
import com.ozkan.altyazi.ui.theme.AppColors
import com.ozkan.altyazi.ui.theme.toFontFamily
import kotlin.math.roundToInt

private enum class AppLanguage(val tag: String, val label: Int) {
    SYSTEM("", R.string.app_lang_system),
    ENGLISH("en", R.string.app_lang_en),
    TURKISH("tr", R.string.app_lang_tr),
}

@Composable
fun SettingsSheet(
    settings: AppSettings,
    voiceMissing: Boolean,
    aiState: AiState,
    onAiDownload: () -> Unit,
    onAiRetry: () -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onResetRoi: () -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = AppColors.Surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(bottom = 28.dp)
                .navigationBarsPadding(),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)

            // ---------- Translation ----------
            Group(stringResource(R.string.settings_translation))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label(stringResource(R.string.settings_source))
                    LanguageChips(Language.sources, settings.sourceLanguage) { lang -> onUpdate { it.withSource(lang) } }
                }
                FilledTonalIconButton(
                    onClick = { onUpdate { it.swapped() } },
                    enabled = settings.targetLanguage.ocrSupported,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Icon(Icons.Rounded.SwapHoriz, stringResource(R.string.settings_swap))
                }
                Column(Modifier.weight(1f)) {
                    Label(stringResource(R.string.settings_target))
                    LanguageChips(Language.targets, settings.targetLanguage) { lang -> onUpdate { it.withTarget(lang) } }
                }
            }
            Hint(stringResource(R.string.settings_lang_hint))

            // ---------- Appearance ----------
            Group(stringResource(R.string.settings_appearance))
            PreviewCard(settings)

            Label(stringResource(R.string.settings_text_size))
            var size by remember { mutableFloatStateOf(settings.textSizeSp) }
            LaunchedEffect(settings.textSizeSp) { size = settings.textSizeSp }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = size,
                    onValueChange = { size = it },
                    onValueChangeFinished = { onUpdate { s -> s.copy(textSizeSp = size) } },
                    valueRange = AppSettings.MIN_TEXT_SP..AppSettings.MAX_TEXT_SP,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.sp_value, size.roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(56.dp),
                )
            }

            Label(stringResource(R.string.settings_font))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SubtitleFont.entries.forEach { font ->
                    FilterChip(
                        selected = settings.font == font,
                        onClick = { onUpdate { it.copy(font = font) } },
                        label = {
                            Text(stringResource(font.label), style = TextStyle(fontFamily = font.toFontFamily(), fontSize = 16.sp))
                        },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
            SwitchRow(stringResource(R.string.settings_bold), settings.bold) { v -> onUpdate { it.copy(bold = v) } }

            Label(stringResource(R.string.settings_color))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppSettings.TEXT_COLORS.forEach { argb ->
                    ColorSwatch(Color(argb), selected = settings.textColor == argb) { onUpdate { it.copy(textColor = argb) } }
                }
            }

            Label(stringResource(R.string.settings_background))
            Segmented(PanelBackground.entries, settings.background, { stringResource(it.label) }) { v ->
                onUpdate { it.copy(background = v) }
            }

            // ---------- Behavior ----------
            Group(stringResource(R.string.settings_behavior))
            Label(stringResource(R.string.settings_response))
            Segmented(Quality.entries, settings.quality, { stringResource(it.label) }) { v -> onUpdate { it.copy(quality = v) } }
            Hint(stringResource(R.string.quality_hint, settings.quality.approxDelayMs, stringResource(settings.quality.description)))

            Spacer(Modifier.height(12.dp))
            SwitchRow(stringResource(R.string.settings_show_original), settings.showOriginal) { v -> onUpdate { it.copy(showOriginal = v) } }
            SwitchRow(stringResource(R.string.settings_speak), settings.speak) { v -> onUpdate { it.copy(speak = v) } }
            if (settings.speak && voiceMissing) {
                Hint(stringResource(R.string.settings_voice_missing, stringResource(settings.targetLanguage.label)), AppColors.Danger)
            }

            Spacer(Modifier.height(8.dp))
            SwitchRow(stringResource(R.string.ai_title), settings.aiCorrection) { v -> onUpdate { it.copy(aiCorrection = v) } }
            Hint(stringResource(R.string.ai_desc))
            if (settings.aiCorrection) AiStatus(aiState, onAiDownload, onAiRetry)

            Label(stringResource(R.string.settings_app_language))
            AppLanguagePicker()

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onResetRoi) {
                    Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_reset_roi))
                }
                OutlinedButton(onClick = onClearHistory) {
                    Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_clear_history))
                }
            }
        }
    }
}

@Composable
private fun AiStatus(state: AiState, onDownload: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.padding(top = 10.dp)) {
        val text = when (state) {
            AiState.Off -> null
            AiState.Checking -> stringResource(R.string.ai_status_checking)
            is AiState.Ready -> stringResource(
                if (state.backend == AiBackend.GEMINI_NANO) R.string.ai_status_gemini else R.string.ai_status_local
            )
            is AiState.NeedsDownload -> stringResource(R.string.ai_status_needs_download, state.sizeMb)
            is AiState.Downloading -> state.progress?.let { stringResource(R.string.ai_status_downloading, (it * 100).roundToInt()) }
                ?: stringResource(R.string.ai_status_downloading_indeterminate)
            is AiState.Failed -> stringResource(R.string.ai_status_failed, state.reason)
        }
        if (text != null) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = when (state) {
                    is AiState.Ready -> AppColors.Success
                    is AiState.Failed -> AppColors.Danger
                    else -> AppColors.TextSecondary
                },
            )
        }
        when (state) {
            is AiState.Downloading -> {
                Spacer(Modifier.height(8.dp))
                val progress = state.progress
                if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is AiState.NeedsDownload -> OutlinedButton(onClick = onDownload, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_download))
            }
            is AiState.Failed -> OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.action_retry))
            }
            else -> Unit
        }
    }
}

@Composable
private fun PreviewCard(settings: AppSettings) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.PreviewBackdrop)
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = settings.background.alpha))
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(stringResource(R.string.settings_preview_sample), style = settings.subtitleStyle())
        }
    }
}

@Composable
private fun AppLanguagePicker() {
    // AppCompat persists the choice and recreates the activity; the ViewModel survives it.
    var current by remember {
        mutableStateOf(
            AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-').let { tag ->
                AppLanguage.entries.firstOrNull { it.tag == tag && tag.isNotEmpty() } ?: AppLanguage.SYSTEM
            }
        )
    }
    Segmented(AppLanguage.entries, current, { stringResource(it.label) }) { lang ->
        current = lang
        AppCompatDelegate.setApplicationLocales(
            if (lang == AppLanguage.SYSTEM) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(lang.tag)
        )
    }
}

@Composable
private fun LanguageChips(options: List<Language>, selected: Language, onSelect: (Language) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { lang ->
            FilterChip(
                selected = lang == selected,
                onClick = { onSelect(lang) },
                label = { Text(stringResource(lang.label)) },
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

@Composable
private fun Group(title: String) {
    Spacer(Modifier.height(28.dp))
    HorizontalDivider(color = AppColors.Outline)
    Spacer(Modifier.height(20.dp))
    Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = AppColors.Primary)
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = AppColors.TextSecondary,
        modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
    )
}

@Composable
private fun Hint(text: String, color: Color = AppColors.TextSecondary) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label(option), maxLines = 1) }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 3.dp else 1.dp, if (selected) AppColors.Primary else Color.White.copy(alpha = 0.15f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.selected), tint = Color.Black.copy(alpha = 0.75f))
    }
}
