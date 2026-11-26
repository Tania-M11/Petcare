package com.udenar.petcare.domain.recomendaciones

import com.udenar.petcare.domain.modelo.Icono
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.MotorCuidados
import com.udenar.petcare.domain.cuidados.RegistroCuidado
import com.udenar.petcare.domain.cuidados.TipoCuidado
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

/** Cifras de los últimos 30 días y paseos por mes (solo lo registrado). */
data class Estadisticas(
    /** Los últimos 6 meses, del más antiguo al actual. */
    val paseosPorMes: List<Pair<java.time.YearMonth, Int>> = emptyList(),
    val duracionPromedioMin: Int? = null,
    /** Minutos de paseos y actividades con duración, últimos 30 días. */
    val actividadMin30: Int = 0,
    val banos30: Int = 0,
    val banosTotal: Int = 0,
    val medicamentos30: Int = 0,
    val medicamentosTotal: Int = 0,
) {
    val hayDatos: Boolean get() = paseosPorMes.any { it.second > 0 } || banosTotal > 0 || medicamentosTotal > 0 || actividadMin30 > 0
}

data class ProgresoMascota(
    val semanas: List<SemanaProgreso>,
    /** Días seguidos (hasta hoy) con todo lo programado hecho. */
    val racha: Int,
    val paseos: List<SemanaPaseos>,
    /** Registros de los últimos 30 días por persona (nombre → cantidad). */
    val porPersona: Map<String, Int>,
    val logros: List<Logro>,
    val estadisticas: Estadisticas = Estadisticas(),
) {
    val hayDatos: Boolean get() = semanas.any { it.total > 0 } || paseos.any { it.cantidad > 0 } || estadisticas.hayDatos
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
        // Estadísticas: solo cuentan lo que se registró
        val tipoDe = eventos.associate { it.id to it.tipo }
        val hechos = registros.filter { it.hecho }
        val recientes = hechos.filter { !it.fechaProgramada.toLocalDate().isBefore(desde) }
        val mesActual = java.time.YearMonth.from(hoy)
        val paseosPorMes = (5 downTo 0).map { k ->
            val mes = mesActual.minusMonths(k.toLong())
            mes to paseos.count { java.time.YearMonth.from(it.inicio.atZone(zona)) == mes }
        }
        val paseos30 = paseos.filter { !it.inicio.atZone(zona).toLocalDate().isBefore(desde) }
        val actividades30 = recientes.filter { tipoDe[it.eventoId] == TipoCuidado.PERSONALIZADO || tipoDe[it.eventoId] == TipoCuidado.PASEO }
            .sumOf { r -> eventos.firstOrNull { it.id == r.eventoId }?.detalle?.get("duracionMin")?.toIntOrNull() ?: 0 }
        val estadisticas = Estadisticas(
            paseosPorMes = paseosPorMes,
            duracionPromedioMin = paseos30.takeIf { it.isNotEmpty() }?.let { l -> l.sumOf { it.duracionS } / l.size / 60 },
            actividadMin30 = paseos30.sumOf { it.duracionS } / 60 + actividades30,
            banos30 = recientes.count { tipoDe[it.eventoId] == TipoCuidado.BANO },
            banosTotal = hechos.count { tipoDe[it.eventoId] == TipoCuidado.BANO },
            medicamentos30 = recientes.count { tipoDe[it.eventoId] == TipoCuidado.MEDICAMENTO },
            medicamentosTotal = hechos.count { tipoDe[it.eventoId] == TipoCuidado.MEDICAMENTO },
        )
        return ProgresoMascota(semanasProgreso, racha, paseosSemana, porPersona, logros, estadisticas)
    }
}

/**
 * Notificación del domingo (Fase 6.4): un resumen corto y en positivo de la semana. Solo cuenta
 * lo registrado; no habla de la salud. null = no hubo nada que contar.
 */
object ResumenSemanal {
    fun texto(progresos: List<Pair<String, ProgresoMascota>>): Pair<String, String>? {
        val conDatos = progresos.filter { (_, p) -> (p.semanas.lastOrNull()?.total ?: 0) > 0 || (p.paseos.lastOrNull()?.cantidad ?: 0) > 0 }
        if (conDatos.isEmpty()) return null
        val nombres = conDatos.map { it.first }
        val quien = if (nombres.size == 1) nombres[0] else nombres.dropLast(1).joinToString(", ") + " y " + nombres.last()
        val hechas = conDatos.sumOf { it.second.semanas.lastOrNull()?.hechas ?: 0 }
        val total = conDatos.sumOf { it.second.semanas.lastOrNull()?.total ?: 0 }
        val paseos = conDatos.sumOf { it.second.paseos.lastOrNull()?.cantidad ?: 0 }
        val km = conDatos.sumOf { it.second.paseos.lastOrNull()?.km ?: 0.0 }
        val mejor = conDatos.maxByOrNull { it.second.racha }?.takeIf { it.second.racha >= 2 }
        val partes = buildList {
            if (total > 0) add("Cuidados: $hechas de $total (${hechas * 100 / total} %)")
            if (paseos > 0) add("Paseos: $paseos" + if (km >= 0.1) " · " + String.format(java.util.Locale.forLanguageTag("es-CO"), "%.1f km", km) else "")
            mejor?.let { add("Racha de ${it.first}: ${it.second.racha} días") }
        }
        val cierre = when {
            total > 0 && hechas == total -> "Semana completa"
            total > 0 && hechas * 10 >= total * 8 -> "Buen avance esta semana"
            else -> "La próxima semana va a ir aún mejor."
        }
        return "Tu semana con $quien" to (partes.joinToString(". ") + ". " + cierre)
    }
}
