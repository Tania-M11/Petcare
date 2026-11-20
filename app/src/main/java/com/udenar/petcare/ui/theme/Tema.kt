package com.udenar.petcare.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val Colores = lightColorScheme(
    primary = Pc.Naranja,
    onPrimary = Pc.Blanco,
    primaryContainer = Pc.Durazno,
    onPrimaryContainer = Pc.Tinta,
    secondary = Pc.Lavanda,
    onSecondary = Pc.Blanco,
    secondaryContainer = Pc.LavandaClara,
    onSecondaryContainer = Pc.Tinta,
    tertiary = Pc.Cielo,
    tertiaryContainer = Pc.CieloClaro,
    background = Pc.Crema,
    onBackground = Pc.Tinta,
    surface = Pc.Blanco,
    onSurface = Pc.Tinta,
    surfaceVariant = Pc.Crema,
    onSurfaceVariant = Pc.TintaSuave,
    surfaceContainerLow = Pc.Blanco,
    surfaceContainer = Pc.Blanco,
    surfaceContainerHigh = Pc.Blanco,
    outline = Pc.Linea,
    outlineVariant = Pc.Linea,
)

/** Formas muy redondeadas: tarjetas de 28 dp, botones en píldora. */
val FormasPetcare = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Tema claro único: la identidad de PetCare se basa en fondos cálidos. */
@Composable
fun PetcareTheme(contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colores,
        typography = TipografiaPetcare,
        shapes = FormasPetcare,
        content = contenido,
    )
}
