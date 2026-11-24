package com.forma.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** FORMA colour tokens. Restrained: one accent, two signal colours, warm neutrals. */
@Immutable
data class FormaColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val line: Color,
    val lineStrong: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val warn: Color,
    val warnSoft: Color,
    val negative: Color,
    val negativeSoft: Color,
    val track: Color,
    val meat: Color,
    val dairy: Color,
    val pareve: Color,
)

val LightColors = FormaColors(
    isDark = false,
    bg = Color(0xFFF5F3EE),
    surface = Color(0xFFFCFBF8),
    surfaceAlt = Color(0xFFEFECE5),
    line = Color(0xFFE5E1D8),
    lineStrong = Color(0xFFD3CEC3),
    text = Color(0xFF1A1B19),
    textSecondary = Color(0xFF6B6A64),
    textTertiary = Color(0xFF9C9A92),
    accent = Color(0xFF2E5B45),
    accentSoft = Color(0xFFDDE7DF),
    onAccent = Color(0xFFF7F6F2),
    warn = Color(0xFFA9772B),
    warnSoft = Color(0xFFF3E7D2),
    negative = Color(0xFFA4493F),
    negativeSoft = Color(0xFFF2DEDA),
    track = Color(0xFFE8E4DC),
    meat = Color(0xFF8E4A3E),
    dairy = Color(0xFF4A6382),
    pareve = Color(0xFF4F7360),
)

val DarkColors = FormaColors(
    isDark = true,
    bg = Color(0xFF111312),
    surface = Color(0xFF181A19),
    surfaceAlt = Color(0xFF1F2220),
    line = Color(0xFF272A28),
    lineStrong = Color(0xFF363A37),
    text = Color(0xFFECEAE4),
    textSecondary = Color(0xFFA3A19A),
    textTertiary = Color(0xFF6F6E69),
    accent = Color(0xFF93B9A0),
    accentSoft = Color(0xFF233229),
    onAccent = Color(0xFF0F1A14),
    warn = Color(0xFFD5A862),
    warnSoft = Color(0xFF33291A),
    negative = Color(0xFFD9887D),
    negativeSoft = Color(0xFF3A2320),
    track = Color(0xFF262927),
    meat = Color(0xFFD39586),
    dairy = Color(0xFF93AACB),
    pareve = Color(0xFF93B9A0),
)

object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val xxxl = 40.dp
    /** Horizontal screen gutter. */
    val gutter = 20.dp
}

object Radius {
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
}

val LocalFormaColors = staticCompositionLocalOf { LightColors }
