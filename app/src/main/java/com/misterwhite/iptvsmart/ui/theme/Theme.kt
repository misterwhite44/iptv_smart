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
    val Background = Color(0xFF0E0818)
    val BackgroundTop = Color(0xFF2B1248)
    val Surface = Color(0xFF1D1430)
    val SurfaceHigh = Color(0xFF2C2045)
    val Accent = Color(0xFF8B5CF6)
    val AccentBright = Color(0xFFB794FF)
    val TextPrimary = Color(0xFFF9F7FC)
    val TextSecondary = Color(0xFFB4A9CC)
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
