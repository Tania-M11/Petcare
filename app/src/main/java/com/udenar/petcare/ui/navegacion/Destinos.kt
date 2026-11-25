package com.udenar.petcare.ui.navegacion

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.History
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
@Serializable data object RutaCalendario
@Serializable data object RutaHistorial
@Serializable data object RutaIntenciones
@Serializable data class RutaCuidarCodigo(val codigo: String = "")
@Serializable data class RutaCarne(val id: String)
@Serializable data class RutaPaseo(val mascotaId: String? = null)
@Serializable data class RutaDetallePaseo(val id: String)
@Serializable data class RutaResultadoQr(val texto: String)
@Serializable data class RutaFormMascota(val id: String? = null)
@Serializable data class RutaDetalleMascota(val id: String)

/** [clase] = ClaseCuidado.name; [id] = null para uno nuevo. */
@Serializable data class RutaNuevoSintoma(val mascotaId: String)
@Serializable data class RutaFormVisita(val mascotaId: String, val id: String? = null)
@Serializable data class RutaFormCuidado(val mascotaId: String, val clase: String, val id: String? = null)

/** Pestaña de una barra inferior (modo personal o profesional). */
interface Pestana {
    val ruta: Any
    val etiqueta: String
    val icono: ImageVector
    val etiquetaPrueba: String
}

@Serializable data object RutaFormVeterinario
@Serializable data object RutaDirectorioVet
@Serializable data class RutaDetalleVet(val id: String)
@Serializable data class RutaSolicitarVet(val id: String)
@Serializable data class RutaFormPrestador(val categoria: String? = null)
@Serializable data object RutaDirectorioPres
@Serializable data class RutaDetallePrestador(val id: String)
@Serializable data class RutaSolicitarPrestador(val id: String)

/** Pestañas del modo personal: las 5 de la spec (Inicio, Mascotas, Calendario, Historial, Perfil) y Servicios. */
enum class PestanaPersonal(override val ruta: Any, override val etiqueta: String, override val icono: ImageVector, override val etiquetaPrueba: String) : Pestana {
    HOY(RutaHoy, "Hoy", Icons.Rounded.WbSunny, "pestana_hoy"),
    MASCOTAS(RutaMascotas, "Mascotas", Icons.Rounded.Pets, "pestana_mascotas"),
    CALENDARIO(RutaCalendario, "Calendario", Icons.Rounded.CalendarMonth, "pestana_calendario"),
    HISTORIAL(RutaHistorial, "Historial", Icons.Rounded.History, "pestana_historial"),
    SERVICIOS(RutaServicios, "Servicios", Icons.Rounded.Storefront, "pestana_servicios"),
    PERFIL(RutaPerfil, "Perfil", Icons.Rounded.Person, "pestana_perfil"),
}
