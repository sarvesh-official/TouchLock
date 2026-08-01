package com.sarvesh.touchlock

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Brand accent: Nothing-style green
private val GreenAccent = Color(0xFF8FDE73)
private val GreenAccentDark = Color(0xFF5BA84F)
private val GreenContainerDark = Color(0xFF1B3A14)
private val GreenContainerLight = Color(0xFFD7F5C8)

private val RedAccent = Color(0xFFEF4444)
private val RedAccentDark = Color(0xFFFFB4AB)
private val RedContainerDark = Color(0xFF4A1A1A)
private val RedContainerLight = Color(0xFFFFDAD6)

private val DarkBackground = Color(0xFF0E0E0E)
private val DarkSurface = Color(0xFF1A1A1A)
private val DarkSurfaceVariant = Color(0xFF242424)
private val DarkOnSurface = Color(0xFFE8E8E8)
private val DarkOnSurfaceVariant = Color(0xFF9A9A9A)
private val DarkOutline = Color(0xFF333333)
private val DarkOutlineVariant = Color(0xFF222222)

private val LightBackground = Color(0xFFFAFAFA)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFF0F0F0)
private val LightOnSurface = Color(0xFF1A1A1A)
private val LightOnSurfaceVariant = Color(0xFF555555)
private val LightOutline = Color(0xFFD0D0D0)
private val LightOutlineVariant = Color(0xFFE8E8E8)

private val DarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = GreenAccent,
    onPrimary = Color(0xFF003300),
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = GreenAccent,
    secondary = Color(0xFF6B9BFF),
    onSecondary = Color(0xFF002255),
    secondaryContainer = Color(0xFF1A2A4A),
    onSecondaryContainer = Color(0xFF8DB5FF),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF4A2A00),
    tertiaryContainer = Color(0xFF4A3010),
    onTertiaryContainer = Color(0xFFFFD699),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = RedAccent,
    onError = Color(0xFFFFFFFF),
    errorContainer = RedContainerDark,
    onErrorContainer = RedAccentDark,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

private val LightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = GreenAccentDark,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = GreenContainerLight,
    onPrimaryContainer = Color(0xFF1B3A14),
    secondary = Color(0xFF3D6BCC),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E4FF),
    onSecondaryContainer = Color(0xFF1A2A4A),
    tertiary = Color(0xFFE69100),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE0B2),
    onTertiaryContainer = Color(0xFF4A2A00),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = Color(0xFFD32F2F),
    onError = Color(0xFFFFFFFF),
    errorContainer = RedContainerLight,
    onErrorContainer = Color(0xFF4A0A0A),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

@Composable
fun TouchLockAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    androidx.compose.material3.MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
