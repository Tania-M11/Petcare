package com.udenar.petcare.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Tema PROVISIONAL (colores por defecto de Material 3).
 * Se reemplaza en la parte 0.4 con la identidad visual de PetCare.
 */
@Composable
fun PetcareTheme(
    oscuro: Boolean = isSystemInDarkTheme(),
    contenido: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (oscuro) darkColorScheme() else lightColorScheme(),
        content = contenido,
    )
}
