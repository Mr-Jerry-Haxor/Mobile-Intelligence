package com.mobileintelligence.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Color Palettes ──────────────────────────────────────────────────

// AMOLED Black
private val AmoledDarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1A237E),
    secondary = Color(0xFF80DEEA),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF0A0A0A),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF333333),
    surfaceContainer = Color(0xFF0D0D0D),
    surfaceContainerHigh = Color(0xFF121212),
    surfaceContainerHighest = Color(0xFF1A1A1A),
)

// Monochrome
private val MonochromeLightColorScheme = lightColorScheme(
    primary = Color(0xFF424242),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0E0),
    secondary = Color(0xFF757575),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF212121),
    onSurface = Color(0xFF212121),
)

private val MonochromeDarkColorScheme = darkColorScheme(
    primary = Color(0xFFBDBDBD),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF424242),
    secondary = Color(0xFF9E9E9E),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
)

// Pastel Analytics
private val PastelLightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8DEF8),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF7D5260),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
)

private val PastelDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    tertiary = Color(0xFFEFB8C8),
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5),
)

// ── Chart Colors ────────────────────────────────────────────────────

object ChartColors {
    val primary = Color(0xFF6750A4)
    val secondary = Color(0xFF625B71)
    val tertiary = Color(0xFF7D5260)
    val good = Color(0xFF4CAF50)
    val warning = Color(0xFFFF9800)
    val danger = Color(0xFFE53935)
    val nightUsage = Color(0xFF5C6BC0)
    val screenOn = Color(0xFF42A5F5)
    val appColors = listOf(
        Color(0xFF6750A4),
        Color(0xFF42A5F5),
        Color(0xFF66BB6A),
        Color(0xFFFFCA28),
        Color(0xFFEF5350),
        Color(0xFFAB47BC),
        Color(0xFF26C6DA),
        Color(0xFFFF7043),
        Color(0xFF8D6E63),
        Color(0xFF78909C)
    )
}

// ── Theme Composable ────────────────────────────────────────────────

@Composable
fun MobileIntelligenceTheme(
    themeMode: String = "auto",
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        "amoled" -> AmoledDarkColorScheme
        "monochrome" -> if (darkTheme) MonochromeDarkColorScheme else MonochromeLightColorScheme
        "pastel" -> if (darkTheme) PastelDarkColorScheme else PastelLightColorScheme
        else -> { // "auto" - Dynamic color if available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context)
                else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) PastelDarkColorScheme else PastelLightColorScheme
            }
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
