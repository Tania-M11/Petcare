package com.udenar.petcare.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.udenar.petcare.R

/** Kalnia (títulos) y Quicksand (todo lo demás); ambas son fuentes variables de Google Fonts. */
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, peso: Int) = Font(
    res,
    weight = FontWeight(peso),
    variationSettings = FontVariation.Settings(FontVariation.weight(peso)),
)

val Kalnia = FontFamily(
    variable(R.font.kalnia, 400),
    variable(R.font.kalnia, 500),
    variable(R.font.kalnia, 600),
    variable(R.font.kalnia, 700),
)

val Quicksand = FontFamily(
    variable(R.font.quicksand, 400),
    variable(R.font.quicksand, 500),
    variable(R.font.quicksand, 600),
    variable(R.font.quicksand, 700),
)

private fun titulo(tam: Int, peso: Int = 600) =
    TextStyle(fontFamily = Kalnia, fontWeight = FontWeight(peso), fontSize = tam.sp, lineHeight = (tam * 1.15).sp, color = Pc.Tinta)

private fun texto(tam: Int, peso: Int = 500, alto: Double = 1.4) =
    TextStyle(fontFamily = Quicksand, fontWeight = FontWeight(peso), fontSize = tam.sp, lineHeight = (tam * alto).sp)

val TipografiaPetcare = Typography(
    displayLarge = titulo(48, 600),
    displayMedium = titulo(40, 600),
    displaySmall = titulo(34, 600),
    headlineLarge = titulo(30),
    headlineMedium = titulo(26),
    headlineSmall = titulo(22),
    titleLarge = texto(20, 700, 1.3),
    titleMedium = texto(16, 700, 1.3),
    titleSmall = texto(14, 700, 1.3),
    bodyLarge = texto(16),
    bodyMedium = texto(14),
    bodySmall = texto(12),
    labelLarge = texto(14, 700, 1.2),
    labelMedium = texto(12, 700, 1.2),
    labelSmall = texto(11, 700, 1.2),
)
