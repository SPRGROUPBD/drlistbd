package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = MedicalBlue,
    onPrimary = Color.White,
    primaryContainer = MedicalBlueLight,
    onPrimaryContainer = MedicalBlueDark,
    secondary = Color(0xFF10B96B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F7E5),
    onSecondaryContainer = Color(0xFF0A663B),
    tertiary = EmeraldAvailable,
    onTertiary = Color.White,
    background = Color(0xFFF6FAFD),
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFEBF3FA),
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFFD9E6F2),
    outlineVariant = OutlineSoft,
    error = RoseAlert,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF06253A),
    primaryContainer = Color(0xFF0C3654),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF10C96F),
    onSecondary = Color(0xFF042615),
    secondaryContainer = Color(0xFF0D3D2A),
    onSecondaryContainer = Color(0xFFCFFCE5),
    tertiary = Color(0xFF10C96F),
    onTertiary = Color(0xFF042615),
    background = Color(0xFF0A121C),
    onBackground = Color(0xFFF3F7FC),
    surface = Color(0xFF121F2E),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF18283A),
    onSurfaceVariant = Color(0xFF9EB2C8),
    outline = Color(0xFF24384E),
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
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
