package com.udenar.petcare.domain.recomendaciones

import com.udenar.petcare.domain.modelo.Icono
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.MotorCuidados
import com.udenar.petcare.domain.cuidados.RegistroCuidado
import com.udenar.petcare.domain.paseos.Paseo
import com.udenar.petcare.domain.salud.RegistroPeso
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Una semana (lunes a domingo) de cuidados programados y hechos. */
data class SemanaProgreso(val lunes: LocalDate, val hechas: Int, val total: Int) {
    val fraccion: Float get() = if (total == 0) 0f else hechas.toFloat() / total
}

data class SemanaPaseos(val lunes: LocalDate, val km: Double, val minutos: Int, val cantidad: Int)

data class Logro(val icono: Icono, val titulo: String, val descripcion: String, val conseguido: Boolean)

data class ProgresoMascota(
    val semanas: List<SemanaProgreso>,
    /** Días seguidos (hasta hoy) con todo lo programado hecho. */
    val racha: Int,
    val paseos: List<SemanaPaseos>,
    /** Registros de los últimos 30 días por persona (nombre → cantidad). */
    val porPersona: Map<String, Int>,
    val logros: List<Logro>,
) {
    val hayDatos: Boolean get() = semanas.any { it.total > 0 } || paseos.any { it.cantidad > 0 }
}

/**
 * Progreso de una mascota (Fase 6.4): solo cuenta lo que la familia registró; no evalúa
 * la salud. Las semanas van de la más antigua a la actual.
 */
object Progreso {
    fun calcular(
        eventos: List<EventoCuidado>,
        registros: List<RegistroCuidado>,
        paseos: List<Paseo>,
        pesos: List<RegistroPeso>,
        hoy: LocalDate,
        zona: ZoneId,
        semanas: Int = 8,
    ): ProgresoMascota {
        val lunesActual = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val inicio = lunesActual.minusWeeks((semanas - 1).toLong())
        // Tomas de cada día desde el inicio hasta hoy (las futuras no cuentan)
        val porDia = generateSequence(inicio) { it.plusDays(1) }.takeWhile { !it.isAfter(hoy) }
            .associateWith { d -> MotorCuidados.agendaDelDia(d, hoy, eventos, registros, zona) }
        val semanasProgreso = (0 until semanas).map { i ->
            val lunes = inicio.plusWeeks(i.toLong())
            val tomas = (0..6).flatMap { porDia[lunes.plusDays(it.toLong())].orEmpty() }
            SemanaProgreso(lunes, tomas.count { it.registro?.hecho == true }, tomas.size)
        }
        // Racha: hacia atrás desde hoy (si hoy aún tiene pendientes, se cuenta desde ayer)
        var racha = 0
        var dia = hoy
        val hoyCompleto = porDia[hoy].orEmpty().let { t -> t.isNotEmpty() && t.all { it.registro?.hecho == true } }
        if (!hoyCompleto) dia = hoy.minusDays(1)
        while (!dia.isBefore(inicio)) {
            val tomas = porDia[dia].orEmpty()
            if (tomas.isEmpty()) {
                dia = dia.minusDays(1)
                continue
            }
            if (tomas.all { it.registro?.hecho == true }) racha++ else break
            dia = dia.minusDays(1)
        }
        val paseosSemana = (0 until semanas).map { i ->
            val lunes = inicio.plusWeeks(i.toLong())
            val dela = paseos.filter { p -> p.inicio.atZone(zona).toLocalDate().let { !it.isBefore(lunes) && it.isBefore(lunes.plusWeeks(1)) } }
            SemanaPaseos(lunes, dela.sumOf { (it.distanciaM ?: 0) / 1000.0 }, dela.sumOf { it.duracionS } / 60, dela.size)
        }
        val desde = hoy.minusDays(30)
        val porPersona = (
            registros.filter { it.hecho && !it.fechaProgramada.toLocalDate().isBefore(desde) }.mapNotNull { it.registradoPor?.nombre } +
                paseos.filter { !it.inicio.atZone(zona).toLocalDate().isBefore(desde) }.mapNotNull { it.registradoPor?.nombre }
            ).map { it.substringBefore(' ') }.groupingBy { it }.eachCount()
        val totalPaseos = paseos.size
        val logros = listOf(
            Logro(Icono.ESTRELLA, "Semana perfecta", "Todos los cuidados de una semana hechos", semanasProgreso.any { it.total > 0 && it.hechas == it.total }),
            Logro(Icono.FUEGO, "7 días seguidos", "Una semana completa sin olvidar nada", racha >= 7),
            Logro(Icono.PASEO, "10 paseos", "Diez paseos registrados", totalPaseos >= 10),
            Logro(Icono.EQUIPO, "Trabajo en equipo", "Dos o más personas ayudaron este mes", porPersona.size >= 2),
            Logro(Icono.PESO, "Peso al día", "Un peso registrado en el último mes", pesos.any { ChronoUnit.DAYS.between(it.fecha, hoy) <= 30 }),
        )
        return ProgresoMascota(semanasProgreso, racha, paseosSemana, porPersona, logros)
    }
}
