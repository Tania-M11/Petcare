package com.udenar.petcare.ui.navegacion

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

/** Rutas con tipos seguros (Navigation Compose + kotlinx.serialization). */
@Serializable data object RutaInicio
@Serializable data object RutaMascotas
@Serializable data object RutaCalendario
@Serializable data object RutaExplorar
@Serializable data object RutaPerfil

/** Pestañas del modo personal (spec, sección 10). */
enum class PestanaPersonal(val ruta: Any, val etiqueta: String, val icono: ImageVector) {
    INICIO(RutaInicio, "Inicio", Icons.Outlined.Home),
    MASCOTAS(RutaMascotas, "Mascotas", Icons.Outlined.Pets),
    CALENDARIO(RutaCalendario, "Calendario", Icons.Outlined.CalendarMonth),
    EXPLORAR(RutaExplorar, "Explorar", Icons.Outlined.TravelExplore),
    PERFIL(RutaPerfil, "Perfil", Icons.Outlined.Person),
}
