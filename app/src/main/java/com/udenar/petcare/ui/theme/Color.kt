package com.udenar.petcare.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/*
 * Paleta de PetCare: tonos cálidos y naturales (referencia de bienestar) alineados
 * con el logo (lavanda y durazno). Cada color "Claro" es el fondo de su par.
 *
 * Modo oscuro (Fase 6.1): cada color tiene su par nocturno y [Pc.oscuro] decide cuál se usa.
 * Como es un estado de Compose, cambiarlo repinta toda la app sin tocar cada pantalla.
 */
object Pc {
    /** Lo fija el tema según el sistema o la preferencia de Perfil. */
    var oscuro by mutableStateOf(false)

    private fun par(claro: Long, noche: Long) = if (oscuro) Color(noche) else Color(claro)

    val Crema: Color get() = par(0xFFFBF6F1, 0xFF1C1820)        // fondo de la app
    val Blanco: Color get() = par(0xFFFFFFFF, 0xFF29232E)       // tarjetas
    val Linea: Color get() = par(0xFFEFE6DE, 0xFF3B3342)

    /** Texto e íconos sobre los colores de acción (botón naranja, chips de color): siempre blanco. */
    val SobreAcento = Color(0xFFFFFFFF)

    val Naranja = Color(0xFFF08A4B)                              // acción principal
    val NaranjaClaro = Color(0xFFFCB484)                         // durazno del logo
    val Durazno: Color get() = par(0xFFFCDCC6, 0xFF4B3326)

    val Lavanda = Color(0xFFA98EE0)                              // lavanda del logo
    val LavandaClara: Color get() = par(0xFFECE6FB, 0xFF342C4A)
    val LavandaOscura: Color get() = par(0xFF8F78D6, 0xFFC4B3F2)

    val Cielo = Color(0xFF7BA7CF)                                // acento: veterinarios
    val CieloClaro: Color get() = par(0xFFDDEAF6, 0xFF213347)
    val CieloOscuro: Color get() = par(0xFF5B89BA, 0xFF9CC2E6)

    val Sol = Color(0xFFF5D565)                                  // acento: cuidar / prestadores
    val SolClaro: Color get() = par(0xFFFBF0C6, 0xFF3E3720)

    val Menta = Color(0xFF9FD3B6)
    val MentaClara: Color get() = par(0xFFE3F3EA, 0xFF1D3A2D)

    val Tinta: Color get() = par(0xFF2D2433, 0xFFF4EEF7)         // texto principal
    val TintaSuave: Color get() = par(0xFF8C8394, 0xFFB2A8BA)    // texto secundario
}

/** Preferencia de apariencia guardada en el teléfono: seguir al sistema, claro u oscuro. */
enum class Apariencia(val etiqueta: String) {
    SISTEMA("Como el teléfono"), CLARO("Claro"), OSCURO("Oscuro");

    companion object {
        private const val ARCHIVO = "apariencia"
        private const val CLAVE = "tema"

        /** Estado compartido para que el cambio en Perfil se vea al instante. */
        var actual by mutableStateOf(CLARO)
            private set

        /** Por ahora empieza en claro: el oscuro se elige en Perfil hasta revisarlo en más pantallas. */
        fun cargar(contexto: Context) {
            val v = contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).getString(CLAVE, null)
            actual = entries.firstOrNull { it.name == v } ?: CLARO
        }

        fun guardar(contexto: Context, a: Apariencia) {
            contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).edit().putString(CLAVE, a.name).apply()
            actual = a
        }
    }
}
