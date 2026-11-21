package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val NasaqLightColorScheme = lightColorScheme(
    primary = NasaqRoyalBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = NasaqNavyDeep,
    secondary = NasaqEmerald,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF0F766E),
    tertiary = NasaqPrimaryBlue,
    onTertiary = Color.White,
    background = NasaqBgLight,
    onBackground = NasaqTextPrimaryLight,
    surface = NasaqCardLight,
    onSurface = NasaqTextPrimaryLight,
    surfaceVariant = NasaqSurfaceVariantLight,
    onSurfaceVariant = NasaqTextSecondaryLight,
    outline = NasaqBorderLight,
    error = StatusRejectedFg,
    onError = Color.White,
    errorContainer = StatusRejectedBg,
    onErrorContainer = StatusRejectedFg,
)

private val NasaqDarkColorScheme = darkColorScheme(
    primary = NasaqSkyCyan,
    onPrimary = NasaqNavyDeep,
    primaryContainer = NasaqRoyalBlue,
    onPrimaryContainer = Color.White,
    secondary = NasaqTurquoise,
    onSecondary = NasaqNavyDeep,
    secondaryContainer = Color(0xFF115E59),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = NasaqPrimaryBlue,
    onTertiary = Color.White,
    background = NasaqBgDark,
    onBackground = NasaqTextPrimaryDark,
    surface = NasaqCardDark,
    onSurface = NasaqTextPrimaryDark,
    surfaceVariant = NasaqSurfaceVariantDark,
    onSurfaceVariant = NasaqTextSecondaryDark,
    outline = NasaqBorderDark,
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF7F1D1D),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
)

val NasaqShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun NasaqTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) NasaqDarkColorScheme else NasaqLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = NasaqTypography,
        shapes = NasaqShapes,
        content = content,
    )
}
