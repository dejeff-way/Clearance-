package com.forma.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.forma.app.R

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Editorial serif for titles only — gives FORMA a calm, distinctive voice. */
val Serif = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

private const val TNUM = "tnum, ss01"

@Immutable
data class FormaType(
    val display: TextStyle,
    val title: TextStyle,
    val titleSmall: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val bodySmall: TextStyle,
    val caption: TextStyle,
    val label: TextStyle,
    val overline: TextStyle,
    val numberHero: TextStyle,
    val numberLarge: TextStyle,
    val number: TextStyle,
    val numberSmall: TextStyle,
)

private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

val FormaTypography = FormaType(
    display = TextStyle(fontFamily = Serif, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.01).em),
    title = TextStyle(fontFamily = Serif, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.01).em),
    titleSmall = TextStyle(fontFamily = Serif, fontSize = 24.sp, lineHeight = 28.sp),
    heading = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.01).em),
    body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyStrong = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
    overline = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.12.em),
    numberHero = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-0.035).em, fontFeatureSettings = TNUM, lineHeightStyle = trim),
    numberLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.025).em, fontFeatureSettings = TNUM),
    number = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.01).em, fontFeatureSettings = TNUM),
    numberSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp, fontFeatureSettings = TNUM),
)

val LocalFormaType = staticCompositionLocalOf { FormaTypography }
