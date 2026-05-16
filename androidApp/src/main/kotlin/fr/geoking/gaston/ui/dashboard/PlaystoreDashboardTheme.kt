package fr.geoking.gaston.ui.dashboard

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import fr.geoking.gaston.ThemeMode

/** Light theme for Play Store phone surfaces (home, diagnostics, map settings). */
val PlaystoreHomeLightScheme = lightColorScheme(
    primary = Color(0xFF1E3A8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFFF97316),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEDD5),
    onSecondaryContainer = Color(0xFF7C2D12),
    tertiary = Color(0xFF3B82F6),
    onTertiary = Color.White,
    surface = Color(0xFFFFFBF3),
    onSurface = Color(0xFF0F172A),
    surfaceContainerHighest = Color(0xFFF3EEDB),
    background = Color(0xFFFFFDF5),
    onBackground = Color(0xFF0F172A)
)

/** Dark theme for Play Store phone surfaces (home, diagnostics, map settings). */
val PlaystoreHomeDarkScheme = darkColorScheme(
    primary = Color(0xFF93C5FD),
    onPrimary = Color(0xFF1E3A8A),
    primaryContainer = Color(0xFF1E40AF),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFFFDBA74),
    onSecondary = Color(0xFF7C2D12),
    secondaryContainer = Color(0xFF9A3412),
    onSecondaryContainer = Color(0xFFFFEDD5),
    tertiary = Color(0xFF60A5FA),
    onTertiary = Color(0xFF1E3A8A),
    surface = Color(0xFF0F2418),
    onSurface = Color(0xFFF8FAFC),
    surfaceContainerHighest = Color(0xFF14301F),
    background = Color(0xFF0B1A12),
    onBackground = Color(0xFFF2F7F2)
)

@Composable
fun GastonTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val colorScheme = if (dark) PlaystoreHomeDarkScheme else PlaystoreHomeLightScheme
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
fun GastonLightTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PlaystoreHomeLightScheme, content = content)
}
