package com.ozkan.altyazi.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozkan.altyazi.data.AppSettings
import com.ozkan.altyazi.ui.theme.AppColors
import com.ozkan.altyazi.ui.theme.toFontFamily

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

@Composable
fun SubtitlePanel(
    current: SubtitleLine?,
    history: List<SubtitleLine>,
    settings: AppSettings,
    onScreen: Boolean,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val dim by animateFloatAsState(if (onScreen || current == null) 1f else 0.6f, tween(400), label = "dim")

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = settings.background.alpha))
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (current == null) {
            Text(
                placeholder,
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        } else Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Previous lines, small and faded, for anything the player missed
            history.asReversed().forEach { line ->
                Text(
                    line.translated,
                    style = settings.subtitleStyle(scale = 0.5f).copy(shadow = null),
                    modifier = Modifier.fillMaxWidth().alpha(0.38f).padding(bottom = 8.dp),
                    maxLines = 2,
                )
            }
            if (history.isNotEmpty()) Spacer(Modifier.height(8.dp))

            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 6 }) togetherWith fadeOut(tween(120))
                },
                contentKey = { it.id },
                label = "subtitle",
            ) { line ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(dim)) {
                    Text(line.translated, style = settings.subtitleStyle(), modifier = Modifier.fillMaxWidth())
                    if (settings.showOriginal) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            line.original,
                            style = TextStyle(
                                fontSize = (settings.textSizeSp * 0.42f).coerceAtLeast(13f).sp,
                                color = AppColors.TextSecondary,
                                textAlign = TextAlign.Center,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
