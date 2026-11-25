package com.udenar.petcare.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Se arma en cada composición: los colores de [Pc] cambian con el modo. */
private fun colores() = (if (Pc.oscuro) androidx.compose.material3.darkColorScheme() else lightColorScheme()).copy(
    primary = Pc.Naranja,
    onPrimary = Pc.SobreAcento,
    primaryContainer = Pc.Durazno,
    onPrimaryContainer = Pc.Tinta,
    secondary = Pc.Lavanda,
    onSecondary = Pc.SobreAcento,
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

/** Tema cálido en claro u oscuro: sigue al teléfono salvo que se elija otro en Perfil. */
@Composable
fun PetcareTheme(contenido: @Composable () -> Unit) {
    val contexto = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.remember { Apariencia.cargar(contexto) }
    val sistema = androidx.compose.foundation.isSystemInDarkTheme()
    val oscuro = when (Apariencia.actual) {
        Apariencia.SISTEMA -> sistema
        Apariencia.CLARO -> false
        Apariencia.OSCURO -> true
    }
    // Se fija antes de que cualquier pantalla lea los colores
    if (Pc.oscuro != oscuro) Pc.oscuro = oscuro
    val vista = androidx.compose.ui.platform.LocalView.current
    if (!vista.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            (vista.context as? android.app.Activity)?.window?.let { w ->
                androidx.core.view.WindowCompat.getInsetsController(w, vista).apply {
                    isAppearanceLightStatusBars = !oscuro
                    isAppearanceLightNavigationBars = !oscuro
                }
            }
        }
    }
    MaterialTheme(
        colorScheme = colores(),
        typography = TipografiaPetcare,
        shapes = FormasPetcare,
        content = contenido,
    )
}
