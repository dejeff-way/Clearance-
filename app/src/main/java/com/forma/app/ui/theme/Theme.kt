package com.forma.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.forma.app.data.prefs.ThemeMode

object Forma {
    val colors: FormaColors @Composable get() = LocalFormaColors.current
    val type: FormaType @Composable get() = LocalFormaType.current
}

@Composable
fun FormaTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val c = if (dark) DarkColors else LightColors
    val t = FormaTypography

    // Map tokens onto Material 3 so native components (fields, dialogs, switches, sheets) match FORMA.
    val scheme = if (dark) darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.text,
        secondary = c.textSecondary, onSecondary = c.bg, secondaryContainer = c.surfaceAlt, onSecondaryContainer = c.text,
        background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.textSecondary, surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surfaceAlt, surfaceContainerLow = c.surface,
        surfaceContainerLowest = c.bg, outline = c.lineStrong, outlineVariant = c.line, error = c.negative,
        onError = c.onAccent, errorContainer = c.negativeSoft, tertiary = c.warn, surfaceTint = c.surface,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.text,
        secondary = c.textSecondary, onSecondary = c.bg, secondaryContainer = c.surfaceAlt, onSecondaryContainer = c.text,
        background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.textSecondary, surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surfaceAlt, surfaceContainerLow = c.surface,
        surfaceContainerLowest = c.bg, outline = c.lineStrong, outlineVariant = c.line, error = c.negative,
        onError = c.onAccent, errorContainer = c.negativeSoft, tertiary = c.warn, surfaceTint = c.surface,
    )
    val m3Type = Typography(
        bodyLarge = t.body, bodyMedium = t.bodySmall, bodySmall = t.caption,
        labelLarge = t.label, labelMedium = t.caption, labelSmall = t.overline,
        titleLarge = t.titleSmall, titleMedium = t.heading, titleSmall = t.bodyStrong,
        headlineSmall = t.titleSmall, headlineMedium = t.title, headlineLarge = t.display,
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(Radius.s), small = RoundedCornerShape(Radius.s),
        medium = RoundedCornerShape(Radius.m), large = RoundedCornerShape(Radius.l),
        extraLarge = RoundedCornerShape(20.dp),
    )
    CompositionLocalProvider(LocalFormaColors provides c, LocalFormaType provides t) {
        MaterialTheme(colorScheme = scheme, typography = m3Type, shapes = shapes, content = content)
    }
}
