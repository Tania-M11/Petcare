package com.udenar.petcare.domain.profesionales

import com.udenar.petcare.domain.modelo.Especie
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** Estados de una solicitud a un prestador (RF-35). */
enum class EstadoSolicitudPres(val etiqueta: String) {
    PENDIENTE("Pendiente"), COTIZADA("Cotizada"), ACEPTADA("Aceptada"), RECHAZADA("Rechazada"), COMPLETADA("Completada"), CANCELADA("Cancelada");

    val abierta: Boolean get() = this == PENDIENTE || this == COTIZADA || this == ACEPTADA

    companion object {
        fun desdeCodigo(c: String?) = entries.firstOrNull { it.name == c } ?: PENDIENTE
    }
}

/** Lo que siempre ve el prestador: una copia, nunca la ficha (RF-36). */
data class ResumenMascota(
    val nombre: String,
    val especie: Especie,
    val raza: String? = null,
    val sexo: String? = null,
    val edad: String? = null,
    val tamano: String? = null,
    val pesoGramos: Int? = null,
)

/** Datos de salud opcionales: el prestador los lee mientras la solicitud esté abierta. */
data class SaludCompartida(
    val alergias: String? = null,
    val condiciones: String? = null,
    val comportamiento: String? = null,
    val indicaciones: String? = null,
) {
    val vacia: Boolean get() = listOf(alergias, condiciones, comportamiento, indicaciones).all { it.isNullOrBlank() }
}

/** Contacto opcional: el prestador lo lee solo mientras la solicitud esté aceptada. */
data class ContactoCompartido(val telefono: String? = null, val direccion: String? = null) {
    val vacio: Boolean get() = telefono.isNullOrBlank() && direccion.isNullOrBlank()
}

data class PasoHistorial(val actor: String, val estado: EstadoSolicitudPres, val fecha: Instant?, val nota: String? = null)

data class SolicitudPrestador(
    val id: String,
    val mascotaId: String?,
    val propietarioNombre: String?,
    val prestadorId: String,
    val prestadorNombre: String,
    val categoriaId: String,
    val servicioNombre: String,
    val precioModo: ModoPrecio,
    val precioReferencia: Int?,
    val modalidad: ModalidadPrestador,
    val inicio: LocalDateTime,
    val fin: LocalDateTime? = null,
    val descripcion: String,
    val mensaje: String? = null,
    val estado: EstadoSolicitudPres = EstadoSolicitudPres.PENDIENTE,
    val resumen: ResumenMascota,
    val cotizacionPrecio: Int? = null,
    val cotizacionObservaciones: String? = null,
    val cotizacionVigencia: LocalDate? = null,
    val propuestaInicio: LocalDateTime? = null,
    val propuestaFin: LocalDateTime? = null,
    val motivo: String? = null,
    val historial: List<PasoHistorial> = emptyList(),
    val creadaEn: Instant? = null,
    /** Rutas en el bucket privado "solicitudes" (máximo 3, RF-34). */
    val fotos: List<String> = emptyList(),
) {
    /** RF-35: aceptar sin cotizar solo si el servicio tiene precio fijo o "desde". */
    val aceptableDirecto: Boolean get() = estado == EstadoSolicitudPres.PENDIENTE && precioModo != ModoPrecio.COTIZACION

    val hayPropuesta: Boolean get() = cotizacionPrecio != null || propuestaInicio != null

    /** Fecha del servicio: la propuesta mientras esté cotizada, la acordada después. */
    val fecha: LocalDateTime get() = if (estado == EstadoSolicitudPres.COTIZADA) propuestaInicio ?: inicio else inicio

    fun cotizacionVencida(hoy: LocalDate) = cotizacionVigencia?.isBefore(hoy) == true
}

/** Detalle con lo compartido que el servidor deja leer en ese momento. */
data class DetalleSolicitudPres(val solicitud: SolicitudPrestador, val salud: SaludCompartida?, val contacto: ContactoCompartido?)

data class Propuesta(val precioCop: Int?, val observaciones: String?, val vigencia: LocalDate?, val inicio: LocalDateTime?, val fin: LocalDateTime?)

interface RepositorioCotizaciones {
    // Propietario
    suspend fun solicitar(
        mascotaId: String, prestadorId: String, categoriaId: String, servicioId: String?, modalidad: ModalidadPrestador,
        inicio: LocalDateTime, fin: LocalDateTime?, descripcion: String, mensaje: String?, salud: SaludCompartida?, contacto: ContactoCompartido?,
    ): String
    suspend fun misSolicitudes(): List<SolicitudPrestador>
    suspend fun confirmar(id: String)
    suspend fun dejarDeCompartir(id: String)

    // Prestador
    suspend fun bandeja(): List<SolicitudPrestador>
    suspend fun cotizar(id: String, propuesta: Propuesta)
    suspend fun aceptar(id: String)
    suspend fun rechazar(id: String, motivo: String?)
    suspend fun completar(id: String)

    // Ambos
    suspend fun detalle(id: String): DetalleSolicitudPres
    suspend fun cancelar(id: String, motivo: String?)
}
