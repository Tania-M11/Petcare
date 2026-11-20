package com.udenar.petcare.domain.cuidados

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Una alarma que debe estar programada en el teléfono. */
data class AlarmaPlanificada(
    /** Identifica la alarma en AlarmManager: el id del cuidado, o "id:aviso" para el aviso anticipado. */
    val clave: String,
    val eventoId: String,
    /** Toma a la que corresponde (fecha programada, 00:00 si es de todo el día). */
    val toma: LocalDateTime,
    /** Cuándo debe sonar (hora local). */
    val disparaEn: LocalDateTime,
    val esAviso: Boolean = false,
)

/**
 * Qué alarmas deben existir (RF-14): una por cuidado, la próxima toma pendiente,
 * y el aviso anticipado de vacunas y citas. Sin red: todo sale de lo guardado en el teléfono.
 */
object PlanAlarmas {

    /** Hora a la que avisan los cuidados de todo el día (baño, uñas, vacunas…). */
    val HORA_DIA_COMPLETO: LocalTime = LocalTime.of(9, 0)
    private const val MAX_BUSQUEDA = 60

    fun calcular(
        eventos: List<EventoCuidado>,
        registros: List<RegistroCuidado>,
        ahora: LocalDateTime,
        zona: ZoneId,
        horaDia: LocalTime = HORA_DIA_COMPLETO,
    ): List<AlarmaPlanificada> {
        val resueltas = registros.filter { it.estado != EstadoRegistro.POSPUESTA }.map { it.eventoId to it.fechaProgramada }.toSet()
        val alarmas = mutableListOf<AlarmaPlanificada>()

        for (e in eventos.filter { !it.oculto }) {
            val conHora = e.frecuencia.horas.isNotEmpty()
            fun disparo(t: LocalDateTime) = if (conHora && e.ancla == Ancla.CALENDARIO) t else t.toLocalDate().atTime(horaDia)

            val proxima: LocalDateTime? = when (e.ancla) {
                Ancla.CALENDARIO -> {
                    // Desde ayer, para no perder un cuidado de todo el día de hoy (00:00) que avisa a las 9:00
                    var cursor = ahora.minusDays(1)
                    var encontrada: LocalDateTime? = null
                    repeat(MAX_BUSQUEDA) {
                        if (encontrada != null) return@repeat
                        val t = MotorCuidados.siguiente(e, cursor) ?: return@repeat
                        cursor = t
                        if (disparo(t).isAfter(ahora) && (e.id to t) !in resueltas) encontrada = t
                    }
                    encontrada
                }
                Ancla.ULTIMA_VEZ -> MotorCuidados.proximaDesdeUltima(e, MotorCuidados.ultimaVez(e, registros, zona))
                    ?.atStartOfDay()
                    ?.takeIf { disparo(it).isAfter(ahora) && (e.id to it) !in resueltas }
            }
            if (proxima == null) continue
            alarmas += AlarmaPlanificada(e.id, e.id, proxima, disparo(proxima))

            // Aviso anticipado (vacunas y citas): "en N días"
            val dias = e.avisoAnticipadoDias ?: 0
            if (dias > 0 && (e.tipo == TipoCuidado.VACUNA || e.tipo == TipoCuidado.CITA_VET)) {
                val aviso = proxima.toLocalDate().minusDays(dias.toLong()).atTime(horaDia)
                if (aviso.isAfter(ahora)) alarmas += AlarmaPlanificada("${e.id}:aviso", e.id, proxima, aviso, esAviso = true)
            }
        }
        return alarmas
    }

    /** Texto de la notificación (la app no agrega consejos: solo lo que escribió el usuario). */
    fun titulo(e: EventoCuidado, nombreMascota: String, esAviso: Boolean, toma: LocalDate, hoy: LocalDate): String = when {
        esAviso -> {
            val dias = toma.toEpochDay() - hoy.toEpochDay()
            val cuando = when (dias) {
                0L -> "hoy"
                1L -> "mañana"
                else -> "en $dias días"
            }
            "${e.tipo.etiqueta} de $nombreMascota $cuando"
        }
        e.tipo == TipoCuidado.COMIDA -> "Hora de darle de comer a $nombreMascota"
        e.tipo == TipoCuidado.MEDICAMENTO -> "$nombreMascota: ${e.titulo}"
        else -> "$nombreMascota: ${e.titulo}"
    }

    fun texto(e: EventoCuidado): String? = listOfNotNull(
        e.titulo.takeIf { e.tipo == TipoCuidado.COMIDA },
        e.detalle["dosis"]?.let { "Dosis: $it" },
        e.detalle["texto"],
        e.detalle["producto"]?.let { "Producto: $it" },
        e.detalle["indicaciones"],
    ).joinToString(" · ").ifBlank { null }
}
