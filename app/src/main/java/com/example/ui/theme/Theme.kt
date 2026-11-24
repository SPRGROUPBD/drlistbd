package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LocalIsDarkTheme = compositionLocalOf { false }

private val LightColorScheme = lightColorScheme(
    primary = MedicalBlue,
    onPrimary = Color.White,
    primaryContainer = LightMedicalBlueLight,
    onPrimaryContainer = LightMedicalBlueDark,
    secondary = EmeraldAvailable,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F7E5),
    onSecondaryContainer = Color(0xFF0A663B),
    tertiary = EmeraldAvailable,
    onTertiary = Color.White,
    background = LightBackgroundIce,
    onBackground = LightTextPrimary,
    surface = LightSurfaceWhite,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceMuted,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutlineSoft,
    outlineVariant = LightOutlineSoft,
    error = RoseAlert,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkMedicalBlueDark,
    onPrimary = Color(0xFF06253A),
    primaryContainer = DarkMedicalBlueLight,
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF10C96F),
    onSecondary = Color(0xFF042615),
    secondaryContainer = Color(0xFF0D3D2A),
    onSecondaryContainer = Color(0xFFCFFCE5),
    tertiary = Color(0xFF10C96F),
    onTertiary = Color(0xFF042615),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceMuted,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutlineSoft,
    outlineVariant = Color(0xFF1E3044),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DoctorDirectoryTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
