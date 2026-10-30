package com.ozkan.altyazi.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ozkan.altyazi.data.SubtitleFont

object AppColors {
    val Background = Color(0xFF0B0C10)
    val Surface = Color(0xFF14161C)
    val SurfaceHigh = Color(0xFF1C1F27)
    val Outline = Color(0xFF2A2E39)
    val Primary = Color(0xFF8B7CF6)
    val PrimaryDeep = Color(0xFF5B4BE0)
    val Accent = Color(0xFFFFC94D)
    val Success = Color(0xFF4ADE80)
    val Danger = Color(0xFFF87171)
    val TextPrimary = Color(0xFFF4F4F7)
    val TextSecondary = Color(0xFF9CA0AD)
    /** Stand-in for a game scene behind the subtitle preview. */
    val PreviewBackdrop = Color(0xFF2A2340)

    val BrandGradient = Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF9333EA)))
}

private val scheme = darkColorScheme(
    primary = AppColors.Primary,
    onPrimary = Color.White,
    primaryContainer = AppColors.PrimaryDeep,
    onPrimaryContainer = Color.White,
    secondary = AppColors.Accent,
    onSecondary = Color(0xFF1A1300),
    secondaryContainer = Color(0xFF3A3020),
    onSecondaryContainer = AppColors.Accent,
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.SurfaceHigh,
    onSurfaceVariant = AppColors.TextSecondary,
    surfaceContainerLow = AppColors.Surface,
    surfaceContainer = AppColors.SurfaceHigh,
    surfaceContainerHigh = AppColors.SurfaceHigh,
    outline = AppColors.Outline,
    outlineVariant = AppColors.Outline,
    error = AppColors.Danger,
)

private val typography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun AltyaziTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

fun SubtitleFont.toFontFamily(): FontFamily = when (this) {
    SubtitleFont.MODERN -> FontFamily.SansSerif
    SubtitleFont.MEDIUM -> FontFamily(Typeface.create("sans-serif-medium", Typeface.NORMAL))
    SubtitleFont.CONDENSED -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
    SubtitleFont.SERIF -> FontFamily.Serif
    SubtitleFont.MONO -> FontFamily.Monospace
    SubtitleFont.CASUAL -> FontFamily(Typeface.create("casual", Typeface.NORMAL))
}
