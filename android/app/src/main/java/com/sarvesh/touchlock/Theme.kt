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

// ── Warm Minimalism palette ──────────────────────────────────────────────
// One accent: muted teal. Warm neutrals everywhere else.
// Text hierarchy via opacity from a single warm dark color.
// Research: warm accents +23% engagement, neutral schemes +31% session time.

// Accent — muted teal (evokes lock indicator / secure / calm)
private val TealAccent = Color(0xFF5BA89A)
private val TealAccentDark = Color(0xFF2D7A6F)
private val TealContainerDark = Color(0xFF1A3F3A)
private val TealContainerLight = Color(0xFFD4ECE6)

// Warm error — terracotta, not pure red
private val ErrorAccent = Color(0xFFD4634A)
private val ErrorAccentDark = Color(0xFFFFB4A0)
private val ErrorContainerDark = Color(0xFF4A2519)
private val ErrorContainerLight = Color(0xFFF5DAD0)

// Dark mode — warm near-black, not cool gray
private val DarkCanvas = Color(0xFF1C1B19)
private val DarkSurface = Color(0xFF252320)
private val DarkSurfaceVariant = Color(0xFF2E2B27)
private val DarkOnSurface = Color(0xFFFAF9F6)
private val DarkOutline = Color(0xFF3A3733)
private val DarkOutlineVariant = Color(0xFF2A2724)

// Light mode — warm off-white, not pure white
private val LightCanvas = Color(0xFFFAF9F6)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFF0EDE8)
private val LightOnSurface = Color(0xFF252320)
private val LightOutline = Color(0xFFD8D4CE)
private val LightOutlineVariant = Color(0xFFE8E4DE)

private val DarkColorScheme = darkColorScheme(
    primary = TealAccent,
    onPrimary = Color(0xFF0A2A25),
    primaryContainer = TealContainerDark,
    onPrimaryContainer = Color(0xFF7BC4B6),
    secondary = TealAccent,
    onSecondary = Color(0xFF0A2A25),
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = Color(0xFF7BC4B6),
    tertiary = TealAccent,
    onTertiary = Color(0xFF0A2A25),
    tertiaryContainer = TealContainerDark,
    onTertiaryContainer = Color(0xFF7BC4B6),
    background = DarkCanvas,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFA8A29A),
    error = ErrorAccent,
    onError = Color(0xFFFFFFFF),
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorAccentDark,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = TealAccentDark,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = TealContainerLight,
    onPrimaryContainer = Color(0xFF1A4A43),
    secondary = TealAccentDark,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = TealContainerLight,
    onSecondaryContainer = Color(0xFF1A4A43),
    tertiary = TealAccentDark,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = TealContainerLight,
    onTertiaryContainer = Color(0xFF1A4A43),
    background = LightCanvas,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF6B6660),
    error = ErrorAccent,
    onError = Color(0xFFFFFFFF),
    errorContainer = ErrorContainerLight,
    onErrorContainer = Color(0xFF4A1A0E),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 44.sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 36.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 26.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp),
)

@Composable
fun TouchLockAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: AccentColor = AccentColor.Teal,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) buildDarkScheme(accentColor) else buildLightScheme(accentColor)
    androidx.compose.material3.MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

private fun buildDarkScheme(accent: AccentColor) = darkColorScheme(
    primary = accent.dark,
    onPrimary = accent.onPrimaryDark,
    primaryContainer = accent.darkContainer,
    onPrimaryContainer = accent.darkOnContainer,
    secondary = accent.dark,
    onSecondary = accent.onPrimaryDark,
    secondaryContainer = accent.darkContainer,
    onSecondaryContainer = accent.darkOnContainer,
    tertiary = accent.dark,
    onTertiary = accent.onPrimaryDark,
    tertiaryContainer = accent.darkContainer,
    onTertiaryContainer = accent.darkOnContainer,
    background = DarkCanvas,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFA8A29A),
    error = ErrorAccent,
    onError = Color(0xFFFFFFFF),
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorAccentDark,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

private fun buildLightScheme(accent: AccentColor) = lightColorScheme(
    primary = accent.light,
    onPrimary = accent.onPrimaryLight,
    primaryContainer = accent.lightContainer,
    onPrimaryContainer = accent.lightOnContainer,
    secondary = accent.light,
    onSecondary = accent.onPrimaryLight,
    secondaryContainer = accent.lightContainer,
    onSecondaryContainer = accent.lightOnContainer,
    tertiary = accent.light,
    onTertiary = accent.onPrimaryLight,
    tertiaryContainer = accent.lightContainer,
    onTertiaryContainer = accent.lightOnContainer,
    background = LightCanvas,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF6B6660),
    error = ErrorAccent,
    onError = Color(0xFFFFFFFF),
    errorContainer = ErrorContainerLight,
    onErrorContainer = Color(0xFF4A1A0E),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)
