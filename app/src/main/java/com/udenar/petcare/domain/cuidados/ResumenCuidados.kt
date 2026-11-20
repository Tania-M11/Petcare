package com.udenar.petcare.domain.cuidados

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Resumen de un cuidado para la ficha: próxima fecha y estado (RF-05, RF-07, RF-16). */
data class ResumenCuidado(
    val evento: EventoCuidado,
    val descripcion: String,
    val proxima: LocalDate?,
    val vencimiento: Vencimiento?,
    val terminaPronto: Boolean,
)

fun resumir(e: EventoCuidado, registros: List<RegistroCuidado>, ahora: LocalDateTime, zona: ZoneId): ResumenCuidado {
    val hoy = ahora.toLocalDate()
    val hecha = registros.any { it.eventoId == e.id && it.hecho }
    val proxima = when (e.ancla) {
        // Una vez (vacuna, cita) ya pasada y sin hacer: su fecha sigue siendo la "próxima" (vencida)
        Ancla.CALENDARIO -> MotorCuidados.siguiente(e, ahora.toLocalDate().minusDays(1).atTime(23, 59))?.toLocalDate()
            ?: e.fechaInicio.takeIf { e.frecuencia.tipo == TipoFrecuencia.UNICO && !hecha }
        Ancla.ULTIMA_VEZ -> MotorCuidados.proximaDesdeUltima(e, MotorCuidados.ultimaVez(e, registros, zona))
    }
    val conEstado = e.tipo == TipoCuidado.VACUNA || e.ancla == Ancla.ULTIMA_VEZ
    val yaHecha = e.tipo == TipoCuidado.VACUNA && hecha
    return ResumenCuidado(
        evento = e,
        descripcion = DescripcionCuidados.frecuencia(e.frecuencia),
        proxima = proxima,
        vencimiento = if (conEstado && proxima != null && !yaHecha) MotorCuidados.vencimiento(proxima, hoy, e.avisoAnticipadoDias) else null,
        terminaPronto = MotorCuidados.terminaPronto(e, hoy),
    )
}

