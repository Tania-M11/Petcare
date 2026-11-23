package com.udenar.petcare.domain.profesionales

import com.udenar.petcare.domain.cuidados.Autor
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.documentos.TipoDocumento
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.domain.salud.RegistroPeso
import com.udenar.petcare.domain.salud.Visita
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Estados de una solicitud a veterinario (RF-25). */
enum class EstadoSolicitudVet(val etiqueta: String) {
    PENDIENTE("Pendiente"), ACEPTADA("Aceptada"), RECHAZADA("Rechazada"), COMPLETADA("Completada"), CANCELADA("Cancelada");

    /** Mientras está pendiente o aceptada el dueño la puede cancelar. */
    val activa: Boolean get() = this == PENDIENTE || this == ACEPTADA

    companion object {
        fun desdeCodigo(c: String?) = entries.firstOrNull { it.name == c } ?: PENDIENTE
    }
}

enum class Modalidad(val etiqueta: String) { CONSULTORIO("En el consultorio"), DOMICILIO("A domicilio") }

data class SolicitudVet(
    val id: String,
    val mascotaId: String,
    val mascotaNombre: String,
    val mascotaEspecie: Especie,
    val propietarioNombre: String?,
    val propietarioTelefono: String?,
    val veterinarioId: String,
    val veterinarioNombre: String,
    val servicio: ServicioVet,
    val modalidad: Modalidad,
    val fechaPreferida: LocalDateTime,
    val fechaConfirmada: LocalDateTime? = null,
    val mensaje: String? = null,
    val motivoRechazo: String? = null,
    val estado: EstadoSolicitudVet = EstadoSolicitudVet.PENDIENTE,
    val creadaEn: Instant? = null,
) {
    /** La hora acordada si ya se aceptó; si no, la que pidió el dueño. */
    val fecha: LocalDateTime get() = fechaConfirmada ?: fechaPreferida
}

/** Veterinario vinculado a una mascota (lo ve el dueño). */
data class VetVinculado(val id: String, val nombre: String, val verificado: Boolean, val clinica: String? = null, val telefono: String? = null)

data class Paciente(val mascota: Mascota)

data class DocumentoClinico(
    val id: String,
    val nombre: String,
    val tipo: TipoDocumento,
    val fecha: LocalDate,
    val mime: String,
    val ruta: String?,
    val subidoPor: String?,
)

/** Ficha que ve el veterinario vinculado: lectura de todo lo clínico (sección 5). */
data class FichaClinica(
    val mascota: Mascota,
    val cuidados: List<EventoCuidado>,
    val visitas: List<Visita>,
    val pesos: List<RegistroPeso>,
    val documentos: List<DocumentoClinico>,
)

/** Lo que el veterinario puede crear en la ficha (RF-27). */
val TIPOS_CLINICOS = listOf(TipoCuidado.VACUNA, TipoCuidado.DESPARASITACION_INTERNA, TipoCuidado.DESPARASITACION_EXTERNA, TipoCuidado.MEDICAMENTO)

/** Cuidado clínico tal como lo escribe el veterinario; la app no valida ni sugiere dosis. */
data class NuevoCuidadoClinico(
    val tipo: TipoCuidado,
    val titulo: String,
    /** Próxima aplicación (vacunas y desparasitación) o inicio (medicamentos). */
    val fecha: LocalDate,
    /** Solo medicamentos: horas de cada día y último día. */
    val horas: List<LocalTime> = emptyList(),
    val fechaFin: LocalDate? = null,
    val dosis: String? = null,
    val indicaciones: String? = null,
)

/** Rol "VETERINARIO" en el autor = lo registró el veterinario vinculado (RF-27). */
val Autor?.esVeterinario: Boolean get() = this?.rol == "VETERINARIO"

/**
 * Solicitudes (RF-25), vínculo (RF-26) y registros clínicos (RF-27). Requiere internet;
 * los registros que crea el veterinario llegan a los dueños con la sincronización normal.
 */
interface RepositorioAtencionVet {
    // Dueño
    suspend fun solicitar(mascotaId: String, veterinarioId: String, servicio: ServicioVet, modalidad: Modalidad, fecha: LocalDateTime, mensaje: String?): String
    suspend fun misSolicitudes(): List<SolicitudVet>
    suspend fun cancelar(id: String)
    suspend fun vetsDe(mascotaId: String): List<VetVinculado>

    // Veterinario
    suspend fun bandeja(): List<SolicitudVet>
    suspend fun resolver(id: String, aceptar: Boolean, fecha: LocalDateTime? = null, motivo: String? = null)
    suspend fun completar(id: String)
    suspend fun pacientes(): List<Paciente>
    suspend fun ficha(mascotaId: String): FichaClinica
    suspend fun registrarVisita(mascotaId: String, fecha: LocalDate, motivo: String, tratamiento: String?, medicamentos: String?, notas: String?)
    suspend fun registrarCuidado(mascotaId: String, cuidado: NuevoCuidadoClinico)
    suspend fun registrarPeso(mascotaId: String, gramos: Int)
    suspend fun subirDocumento(mascotaId: String, nombre: String, tipo: TipoDocumento, bytes: ByteArray, mime: String)
    suspend fun descargarDocumento(ruta: String): ByteArray

    // Ambos
    suspend fun desvincular(mascotaId: String, veterinarioId: String)
}
