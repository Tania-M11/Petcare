package com.udenar.petcare.domain.cuidados

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Tipos de cuidado programable (spec 9.1). Todos usan el mismo motor. */
enum class TipoCuidado(val etiqueta: String, val anclaPorDefecto: Ancla) {
    VACUNA("Vacuna", Ancla.CALENDARIO),
    DESPARASITACION_INTERNA("Desparasitación interna", Ancla.ULTIMA_VEZ),
    DESPARASITACION_EXTERNA("Desparasitación externa", Ancla.ULTIMA_VEZ),
    MEDICAMENTO("Medicamento", Ancla.CALENDARIO),
    BANO("Baño", Ancla.ULTIMA_VEZ),
    CORTE_PELO("Corte de pelo", Ancla.ULTIMA_VEZ),
    CEPILLADO("Cepillado", Ancla.ULTIMA_VEZ),
    CORTE_UNAS("Corte de uñas", Ancla.ULTIMA_VEZ),
    LIMPIEZA_DENTAL("Limpieza dental", Ancla.ULTIMA_VEZ),
    LIMPIEZA_OIDOS("Limpieza de oídos", Ancla.ULTIMA_VEZ),
    LIMPIEZA_OJOS("Limpieza de ojos", Ancla.ULTIMA_VEZ),
    CITA_VET("Cita veterinaria", Ancla.CALENDARIO),
    SERVICIO_CONTRATADO("Servicio contratado", Ancla.CALENDARIO),
    PESAJE("Pesaje", Ancla.ULTIMA_VEZ),
    COMIDA("Comida", Ancla.CALENDARIO),
    PERSONALIZADO("Actividad personalizada", Ancla.CALENDARIO);

    /** Medicamentos se "administran"; lo demás se "realiza". */
    val estadoHecho: EstadoRegistro get() = if (this == MEDICAMENTO) EstadoRegistro.ADMINISTRADA else EstadoRegistro.REALIZADA
}

/**
 * CALENDARIO: ocurre en fechas/horas fijas (medicinas, comidas, citas).
 * ULTIMA_VEZ: la próxima fecha se cuenta desde la última vez que se hizo (baño, uñas, desparasitación).
 */
enum class Ancla { CALENDARIO, ULTIMA_VEZ }

enum class TipoFrecuencia { UNICO, DIARIO, SEMANAL, MENSUAL, ANUAL, CADA_N_DIAS, PERSONALIZADO }

/**
 * Regla de repetición. [n] = cada cuántas unidades (días, semanas, meses, años).
 * [horas] vacías = cuidado de todo el día (sin hora fija).
 */
data class Frecuencia(
    val tipo: TipoFrecuencia,
    val n: Int = 1,
    val diasSemana: Set<DayOfWeek> = emptySet(),
    val horas: List<LocalTime> = emptyList(),
)

enum class Origen { MANUAL, SOLICITUD_VET, SOLICITUD_PRESTADOR, VETERINARIO }

/** Quién creó o registró algo (trazabilidad, spec sección 5). */
data class Autor(val usuarioId: String?, val nombre: String?, val rol: String?)

/** Un cuidado programable de una mascota (CareEvent). */
data class EventoCuidado(
    val id: String,
    val mascotaId: String,
    val tipo: TipoCuidado,
    val titulo: String,
    val frecuencia: Frecuencia,
    val ancla: Ancla = tipo.anclaPorDefecto,
    val fechaInicio: LocalDate,
    val fechaFin: LocalDate? = null,
    val avisoAnticipadoDias: Int? = null,
    /** Campos propios del tipo (dosis escrita por el usuario, producto, texto libre de la comida…). */
    val detalle: Map<String, String> = emptyMap(),
    val origen: Origen = Origen.MANUAL,
    val origenRef: String? = null,
    val creadoPor: Autor? = null,
    val oculto: Boolean = false,
    val porSubir: Boolean = false,
)

enum class EstadoRegistro { ADMINISTRADA, REALIZADA, OMITIDA, POSPUESTA }

/** Cumplimiento de una ocurrencia (CareLog). Sin registro = pendiente. */
data class RegistroCuidado(
    val id: String,
    val mascotaId: String,
    val eventoId: String,
    /** Fecha y hora local programadas (a las 00:00 si el cuidado es de todo el día). */
    val fechaProgramada: LocalDateTime,
    val estado: EstadoRegistro,
    val registradoPor: Autor?,
    val registradoEn: Instant,
    val notas: String? = null,
    val porSubir: Boolean = false,
) {
    val hecho: Boolean get() = estado == EstadoRegistro.ADMINISTRADA || estado == EstadoRegistro.REALIZADA
}

/** Una ocurrencia concreta con su estado (lo que se ve en Hoy y suena como alarma). */
data class Toma(
    val evento: EventoCuidado,
    val fechaHora: LocalDateTime,
    /** false = cuidado de todo el día. */
    val conHora: Boolean,
    val registro: RegistroCuidado?,
) {
    val pendiente: Boolean get() = registro == null || registro.estado == EstadoRegistro.POSPUESTA
}

enum class Vencimiento { AL_DIA, PROXIMO, VENCIDO }
