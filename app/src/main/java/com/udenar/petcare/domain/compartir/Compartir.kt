package com.udenar.petcare.domain.compartir

import com.udenar.petcare.domain.modelo.Especie
import java.time.Instant

/** Lo único que revela un código: nombre y especie (RF-20). No da acceso. */
data class MascotaPorCodigo(val mascotaId: String, val nombre: String, val especie: Especie)

enum class ResultadoSolicitud(val mensaje: String) {
    PENDIENTE("Solicitud enviada. El dueño debe aprobarla."),
    YA_MIEMBRO("Ya cuidas a esta mascota."),
    PROPIA("Esta mascota es tuya."),
    NO_DISPONIBLE("Código no disponible. Pídele al dueño uno nuevo."),
    LIMITE("Tienes 5 solicitudes esperando respuesta. Espera a que te respondan."),
    RECHAZADA_RECIENTE("El dueño rechazó tu solicitud hace poco. Inténtalo de nuevo en 24 horas."),
}

enum class EstadoSolicitud { PENDIENTE, APROBADA, RECHAZADA }

data class SolicitudCuidado(
    val id: String,
    val mascotaId: String,
    val solicitanteId: String,
    val solicitanteNombre: String,
    val estado: EstadoSolicitud,
    val creadoEn: Instant,
)

enum class RolMascota { PROPIETARIO, CUIDADOR }

data class Miembro(val usuarioId: String, val nombre: String, val rol: RolMascota)

class CompartirException(mensaje: String, causa: Throwable? = null) : Exception(mensaje, causa)

/** Cuidar por código, solicitudes y miembros. Requiere internet (las reglas viven en el servidor). */
interface RepositorioCompartir {
    suspend fun buscar(codigo: String): MascotaPorCodigo?
    suspend fun solicitar(codigo: String): ResultadoSolicitud

    /** Solicitudes que yo envié. */
    suspend fun misSolicitudes(): List<SolicitudCuidado>

    /** Solicitudes pendientes para mis mascotas. */
    suspend fun solicitudesRecibidas(): List<SolicitudCuidado>
    suspend fun resolver(solicitudId: String, aprobar: Boolean)

    suspend fun miembros(mascotaId: String): List<Miembro>
    suspend fun quitar(mascotaId: String, usuarioId: String)
    suspend fun salir(mascotaId: String)

    suspend fun regenerarCodigo(mascotaId: String): String
    suspend fun desactivarCodigo(mascotaId: String)

    /**
     * Ajusta el teléfono a las mascotas que el usuario puede ver: si ahora cuida una nueva,
     * se descarga todo de nuevo; si dejó de cuidar una, se borra del teléfono (RF-20).
     */
    suspend fun actualizarMembresias()
}

/** Normaliza lo que escribe la persona: sin espacios ni guiones, en mayúsculas. */
fun normalizarCodigo(texto: String): String = texto.uppercase().filter { it.isLetterOrDigit() }.take(8)

private val CODIGO = Regex("^[A-HJKMNP-Z2-9]{8}$")

fun codigoValido(codigo: String) = CODIGO.matches(codigo)
