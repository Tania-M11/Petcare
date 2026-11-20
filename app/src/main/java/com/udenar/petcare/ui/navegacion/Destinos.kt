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
@Serializable data object RutaIntenciones
@Serializable data class RutaCuidarCodigo(val codigo: String = "")
@Serializable data class RutaCarne(val id: String)
@Serializable data class RutaPaseo(val mascotaId: String? = null)
@Serializable data class RutaDetallePaseo(val id: String)
@Serializable data class RutaResultadoQr(val texto: String)
@Serializable data class RutaFormMascota(val id: String? = null)
@Serializable data class RutaDetalleMascota(val id: String)

/** [clase] = ClaseCuidado.name; [id] = null para uno nuevo. */
@Serializable data class RutaFormVisita(val mascotaId: String, val id: String? = null)
@Serializable data class RutaFormCuidado(val mascotaId: String, val clase: String, val id: String? = null)

/** Las 4 pestañas del modo personal (plan, sección 3). El calendario vive dentro de Hoy. */
enum class PestanaPersonal(val ruta: Any, val etiqueta: String, val icono: ImageVector, val etiquetaPrueba: String) {
    HOY(RutaHoy, "Hoy", Icons.Rounded.WbSunny, "pestana_hoy"),
    MASCOTAS(RutaMascotas, "Mascotas", Icons.Rounded.Pets, "pestana_mascotas"),
    SERVICIOS(RutaServicios, "Servicios", Icons.Rounded.Storefront, "pestana_servicios"),
    PERFIL(RutaPerfil, "Perfil", Icons.Rounded.Person, "pestana_perfil"),
}
