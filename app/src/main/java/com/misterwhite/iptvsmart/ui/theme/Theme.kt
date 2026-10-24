package com.misterwhite.iptvsmart.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object IptvColors {
    val Background = Color(0xFF0B0E1A)
    val BackgroundTop = Color(0xFF1A1F35)
    val Surface = Color(0xFF1C2033)
    val SurfaceHigh = Color(0xFF262B42)
    val Accent = Color(0xFF1E90FF)
    val AccentBright = Color(0xFF4FB0FF)
    val TextPrimary = Color(0xFFF9F9F9)
    val TextSecondary = Color(0xFFA9AEC2)
    val Error = Color(0xFFFF6B6B)

    val screenGradient = Brush.verticalGradient(listOf(BackgroundTop, Background))
}

private val colors = darkColorScheme(
    primary = IptvColors.Accent,
    onPrimary = Color.White,
    secondary = IptvColors.AccentBright,
    background = IptvColors.Background,
    onBackground = IptvColors.TextPrimary,
    surface = IptvColors.Surface,
    onSurface = IptvColors.TextPrimary,
    surfaceVariant = IptvColors.SurfaceHigh,
    onSurfaceVariant = IptvColors.TextSecondary,
    error = IptvColors.Error,
)

private val typography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 0.5.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 1.sp),
)

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
