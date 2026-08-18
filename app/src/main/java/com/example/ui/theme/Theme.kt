package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.AccentTheme
import com.example.data.ThemeMode

@Composable
fun AiAgentTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentTheme: AccentTheme = AccentTheme.CYAN_NEON,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemDark
    }

    val isAmoled = themeMode == ThemeMode.AMOLED

    val primaryColor = when (accentTheme) {
        AccentTheme.CYAN_NEON -> CyanNeonPrimary
        AccentTheme.CYBER_VIOLET -> CyberVioletPrimary
        AccentTheme.MATRIX_EMERALD -> MatrixEmeraldPrimary
        AccentTheme.SOLAR_AMBER -> SolarAmberPrimary
    }

    val secondaryColor = when (accentTheme) {
        AccentTheme.CYAN_NEON -> CyanNeonSecondary
        AccentTheme.CYBER_VIOLET -> CyberVioletSecondary
        AccentTheme.MATRIX_EMERALD -> MatrixEmeraldSecondary
        AccentTheme.SOLAR_AMBER -> SolarAmberSecondary
    }

    val tertiaryColor = when (accentTheme) {
        AccentTheme.CYAN_NEON -> CyanNeonTertiary
        AccentTheme.CYBER_VIOLET -> CyberVioletTertiary
        AccentTheme.MATRIX_EMERALD -> MatrixEmeraldTertiary
        AccentTheme.SOLAR_AMBER -> SolarAmberTertiary
    }

    val colorScheme = if (isDark) {
        if (isAmoled) {
            darkColorScheme(
                primary = primaryColor,
                secondary = secondaryColor,
                tertiary = tertiaryColor,
                background = AmoledBg,
                surface = AmoledSurface,
                surfaceVariant = AmoledSurfaceVariant,
                outline = AmoledBorder,
                onPrimary = Color.Black,
                onSecondary = Color.Black,
                onTertiary = Color.Black,
                onBackground = DarkTextPrimary,
                onSurface = DarkTextPrimary,
                onSurfaceVariant = DarkTextSecondary
            )
        } else {
            darkColorScheme(
                primary = primaryColor,
                secondary = secondaryColor,
                tertiary = tertiaryColor,
                background = DarkBg,
                surface = DarkSurface,
                surfaceVariant = DarkSurfaceVariant,
                outline = DarkBorder,
                onPrimary = Color.Black,
                onSecondary = Color.Black,
                onTertiary = Color.Black,
                onBackground = DarkTextPrimary,
                onSurface = DarkTextPrimary,
                onSurfaceVariant = DarkTextSecondary
            )
        }
    } else {
        lightColorScheme(
            primary = primaryColor,
            secondary = secondaryColor,
            tertiary = tertiaryColor,
            background = LightBg,
            surface = LightSurface,
            surfaceVariant = LightSurfaceVariant,
            outline = LightBorder,
            onPrimary = Color.Black,
            onSecondary = Color.White,
            onTertiary = Color.Black,
            onBackground = LightTextPrimary,
            onSurface = LightTextPrimary,
            onSurfaceVariant = LightTextSecondary
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
