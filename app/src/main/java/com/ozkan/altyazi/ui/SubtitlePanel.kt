package com.ozkan.altyazi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozkan.altyazi.R
import com.ozkan.altyazi.data.AppSettings
import com.ozkan.altyazi.ui.theme.AppColors
import com.ozkan.altyazi.ui.theme.toFontFamily
import kotlinx.coroutines.launch
import kotlin.math.max

/** Subtitle text style built from the user's font, size and color choices. */
fun AppSettings.subtitleStyle(scale: Float = 1f): TextStyle = TextStyle(
    fontFamily = font.toFontFamily(),
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontSize = (textSizeSp * scale).sp,
    lineHeight = (textSizeSp * scale * 1.22f).sp,
    color = Color(textColor),
    textAlign = TextAlign.Center,
    shadow = Shadow(Color.Black.copy(alpha = 0.85f), Offset(0f, 3f), blurRadius = 10f),
)

/** Size of a history line relative to the current subtitle. */
private const val HISTORY_SCALE = 0.55f

/**
 * Scrollable subtitle transcript, newest at the bottom.
 *
 * The current line is large and bright; older lines get smaller and darker the further back they
 * are. The list follows new subtitles automatically unless the user has scrolled back, in which case
 * a button offers a jump back to the latest line.
 */
@Composable
fun SubtitlePanel(
    lines: List<SubtitleLine>,
    settings: AppSettings,
    onScreen: Boolean,
    placeholder: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 32.dp, vertical = 24.dp),
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = settings.background.alpha)),
        contentAlignment = Alignment.Center,
    ) {
        if (lines.isEmpty()) {
            Text(
                placeholder,
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(contentPadding),
            )
        } else {
            Transcript(lines, settings, onScreen, contentPadding)
        }
    }
}

@Composable
private fun Transcript(
    lines: List<SubtitleLine>,
    settings: AppSettings,
    onScreen: Boolean,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val following by remember { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }
    val currentDim by animateFloatAsState(if (onScreen) 1f else 0.7f, tween(400), label = "currentDim")

    // Follow new subtitles while the user is at (or near) the latest line.
    val newestId = lines.first().id
    LaunchedEffect(newestId) {
        if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.Bottom),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(lines, key = { _, line -> line.id }) { index, line ->
                val isCurrent = index == 0
                // Older lines fade progressively: 0.62, 0.56, … down to 0.28.
                val alpha = if (isCurrent) currentDim else max(0.28f, 0.68f - 0.06f * index)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().animateItem().alpha(alpha),
                ) {
                    Text(
                        line.translated,
                        style = if (isCurrent) settings.subtitleStyle()
                        else settings.subtitleStyle(HISTORY_SCALE).copy(shadow = null),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (settings.showOriginal) {
                        Spacer(Modifier.height(if (isCurrent) 12.dp else 4.dp))
                        Text(
                            if (line.refined) "✦ ${line.original}" else line.original,
                            style = TextStyle(
                                fontSize = (settings.textSizeSp * if (isCurrent) 0.42f else 0.3f).coerceAtLeast(12f).sp,
                                color = AppColors.TextSecondary,
                                textAlign = TextAlign.Center,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !following,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            SmallFloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                containerColor = AppColors.Primary,
            ) {
                Icon(Icons.Rounded.KeyboardDoubleArrowDown, stringResource(R.string.history_jump))
            }
        }
    }
}
