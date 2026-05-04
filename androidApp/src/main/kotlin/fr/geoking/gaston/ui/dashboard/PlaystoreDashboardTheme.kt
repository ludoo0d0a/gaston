package fr.geoking.gaston.ui.dashboard

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Light theme for Play Store phone surfaces (home, diagnostics, map settings). */
val PlaystoreHomeLightScheme = lightColorScheme(
    primary = Color(0xFF3E8E5A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFF3E6),
    onPrimaryContainer = Color(0xFF0E3A24),
    secondary = Color(0xFFF2C94C),
    onSecondary = Color(0xFF2A2100),
    secondaryContainer = Color(0xFFFFF2B3),
    onSecondaryContainer = Color(0xFF2A2100),
    tertiary = Color(0xFF7BC96F),
    onTertiary = Color(0xFF0E3A24),
    surface = Color(0xFFFFFBF3),
    onSurface = Color(0xFF0F172A),
    surfaceContainerHighest = Color(0xFFF3EEDB),
    background = Color(0xFFFFFDF5),
    onBackground = Color(0xFF0F172A)
)

/** Dark theme for Play Store phone surfaces (home, diagnostics, map settings). */
val PlaystoreHomeDarkScheme = darkColorScheme(
    primary = Color(0xFF9FE2B3),
    onPrimary = Color(0xFF0B2A17),
    primaryContainer = Color(0xFF1E4D33),
    onPrimaryContainer = Color(0xFFDFF3E6),
    secondary = Color(0xFFF6E27A),
    onSecondary = Color(0xFF2A2100),
    secondaryContainer = Color(0xFF4A3C10),
    onSecondaryContainer = Color(0xFFFFF2B3),
    tertiary = Color(0xFF7BC96F),
    onTertiary = Color(0xFF052012),
    surface = Color(0xFF0F2418),
    onSurface = Color(0xFFF8FAFC),
    surfaceContainerHighest = Color(0xFF14301F),
    background = Color(0xFF0B1A12),
    onBackground = Color(0xFFF2F7F2)
)

@Composable
fun PlaystoreTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) PlaystoreHomeDarkScheme else PlaystoreHomeLightScheme
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
fun PlaystoreLightTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PlaystoreHomeLightScheme, content = content)
}
