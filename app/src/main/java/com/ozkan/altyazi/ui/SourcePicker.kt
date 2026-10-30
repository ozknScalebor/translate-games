package com.ozkan.altyazi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozkan.altyazi.R
import com.ozkan.altyazi.data.FrameSource
import com.ozkan.altyazi.ui.theme.AppColors

/** First screen on every launch: read the subtitles with a camera or straight from the PS5. */
@Composable
fun SourcePicker(onSelect: (FrameSource) -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(Color(0xFF1B1640), AppColors.Background),
                radius = 1800f,
            )
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 980.dp).padding(32.dp),
        ) {
            AppLogo(Modifier.size(72.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.source_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.source_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            ) {
                SourceCard(
                    icon = Icons.Rounded.SportsEsports,
                    title = stringResource(R.string.source_remote),
                    body = stringResource(R.string.source_remote_desc),
                    perks = listOf(stringResource(R.string.source_remote_perk1), stringResource(R.string.source_remote_perk2)),
                    badge = stringResource(R.string.source_recommended),
                    accent = Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF7C3AED))),
                    onClick = { onSelect(FrameSource.REMOTE_PLAY) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                SourceCard(
                    icon = Icons.Rounded.Videocam,
                    title = stringResource(R.string.source_camera),
                    body = stringResource(R.string.source_camera_desc),
                    perks = listOf(stringResource(R.string.source_camera_perk1), stringResource(R.string.source_camera_perk2)),
                    badge = null,
                    accent = Brush.linearGradient(listOf(Color(0xFF0EA5E9), Color(0xFF14B8A6))),
                    onClick = { onSelect(FrameSource.CAMERA) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun SourceCard(
    icon: ImageVector,
    title: String,
    body: String,
    perks: List<String>,
    badge: String?,
    accent: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.Outline),
        modifier = modifier,
    ) {
        Column(Modifier.padding(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.weight(1f))
                if (badge != null) {
                    Surface(shape = RoundedCornerShape(50), color = AppColors.Primary.copy(alpha = 0.18f)) {
                        Text(
                            badge,
                            color = AppColors.Primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp))
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            Spacer(Modifier.height(18.dp))
            perks.forEach { perk ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = AppColors.Success, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(perk, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.source_continue), style = MaterialTheme.typography.labelLarge, color = AppColors.Primary)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(26.dp).clip(CircleShape).background(AppColors.Primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
