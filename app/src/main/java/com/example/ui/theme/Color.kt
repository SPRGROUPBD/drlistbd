package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Brand Core Accents
val MedicalBlue = Color(0xFF0284C7)
val MedicalTeal = Color(0xFF0D9488)
val EmeraldAvailable = Color(0xFF10B96B)
val AmberPending = Color(0xFFF59E0B)
val RoseAlert = Color(0xFFEF4444)

// Raw Light Palette Constants (used by Theme.kt LightColorScheme)
val LightMedicalBlueDark = Color(0xFF0369A1)
val LightMedicalBlueLight = Color(0xFFE0F2FE)
val LightMedicalBlueContainer = Color(0xFFBAE6FD)
val LightMedicalTealDark = Color(0xFF0F766E)
val LightMedicalTealLight = Color(0xFFCCFBF1)

val LightSurfaceWhite = Color(0xFFFFFFFF)
val LightBackgroundIce = Color(0xFFF6FAFD)
val LightSurfaceMuted = Color(0xFFEBF3FA)
val LightOutlineSoft = Color(0xFFD9E6F2)

val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)
val LightTextMuted = Color(0xFF64748B)

val LightEmeraldSoft = Color(0xFFD1FAE5)
val LightAmberSoft = Color(0xFFFEF3C7)
val LightRoseSoft = Color(0xFFFEE2E2)

// Raw Dark Palette Constants (Midnight Navy + Emerald Medical Theme)
val DarkMedicalBlueDark = Color(0xFF38BDF8)
val DarkMedicalBlueLight = Color(0xFF0F2942)
val DarkMedicalBlueContainer = Color(0xFF163A5C)
val DarkMedicalTealDark = Color(0xFF2DD4BF)
val DarkMedicalTealLight = Color(0xFF0D3530)

val DarkSurface = Color(0xFF121F2E)
val DarkBackground = Color(0xFF0A121C)
val DarkSurfaceMuted = Color(0xFF18283A)
val DarkOutlineSoft = Color(0xFF24384E)

val DarkTextPrimary = Color(0xFFF3F7FC)
val DarkTextSecondary = Color(0xFF9EB2C8)
val DarkTextMuted = Color(0xFF7A91AA)

val DarkEmeraldSoft = Color(0xFF0D3B2A)
val DarkAmberSoft = Color(0xFF3A2B12)
val DarkRoseSoft = Color(0xFF3B1820)

// Theme-Reactive Semantic Tokens — automatically switch ALL screens between Light and Dark
val MedicalBlueDark: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkMedicalBlueDark else LightMedicalBlueDark

val MedicalBlueLight: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkMedicalBlueLight else LightMedicalBlueLight

val MedicalBlueContainer: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkMedicalBlueContainer else LightMedicalBlueContainer

val MedicalTealDark: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkMedicalTealDark else LightMedicalTealDark

val MedicalTealLight: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkMedicalTealLight else LightMedicalTealLight

val SurfaceWhite: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkSurface else LightSurfaceWhite

val BackgroundIce: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkBackground else LightBackgroundIce

val SurfaceMuted: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkSurfaceMuted else LightSurfaceMuted

val OutlineSoft: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkOutlineSoft else LightOutlineSoft

val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkTextPrimary else LightTextPrimary

val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkTextSecondary else LightTextSecondary

val TextMuted: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkTextMuted else LightTextMuted

val EmeraldSoft: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkEmeraldSoft else LightEmeraldSoft

val AmberSoft: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkAmberSoft else LightAmberSoft

val RoseSoft: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkRoseSoft else LightRoseSoft
