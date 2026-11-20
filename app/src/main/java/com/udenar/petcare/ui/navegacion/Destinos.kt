package com.udenar.petcare.ui.navegacion

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

/** Rutas con tipos seguros (Navigation Compose + kotlinx.serialization). */
@Serializable data object RutaHoy
@Serializable data object RutaMascotas
@Serializable data object RutaServicios
@Serializable data object RutaPerfil

/** Las 4 pestañas del modo personal (plan, sección 3). El calendario vive dentro de Hoy. */
enum class PestanaPersonal(val ruta: Any, val etiqueta: String, val icono: ImageVector, val etiquetaPrueba: String) {
    HOY(RutaHoy, "Hoy", Icons.Rounded.WbSunny, "pestana_hoy"),
    MASCOTAS(RutaMascotas, "Mascotas", Icons.Rounded.Pets, "pestana_mascotas"),
    SERVICIOS(RutaServicios, "Servicios", Icons.Rounded.Storefront, "pestana_servicios"),
    PERFIL(RutaPerfil, "Perfil", Icons.Rounded.Person, "pestana_perfil"),
}
