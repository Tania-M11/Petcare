package com.udenar.petcare.domain.cuidados

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * Motor único de cuidados (spec 9.1, principio 5): toda la lógica de frecuencias y
 * próximas fechas vive aquí. Es Kotlin puro (sin Android) para probarlo con fechas fijas.
 * La app no sugiere frecuencias: solo calcula a partir de lo que escribió el usuario.
 */
object MotorCuidados {

    /** Límite de búsqueda hacia adelante (un cuidado anual cabe de sobra). */
    private const val DIAS_BUSQUEDA = 800L
    const val AVISO_POR_DEFECTO = 7
    const val AVISO_FIN_TRATAMIENTO = 2

    // ---------- Validación ----------

    fun validar(f: Frecuencia, inicio: LocalDate, fin: LocalDate?): String? = when {
        f.n !in 1..365 -> "El intervalo debe estar entre 1 y 365"
        f.horas.size > 12 -> "Máximo 12 horarios por día"
        f.horas.toSet().size != f.horas.size -> "Hay horarios repetidos"
        f.tipo == TipoFrecuencia.PERSONALIZADO && f.diasSemana.isEmpty() -> "Elige al menos un día"
        f.tipo == TipoFrecuencia.PERSONALIZADO && f.horas.isEmpty() -> "Agrega al menos una hora"
        fin != null && fin.isBefore(inicio) -> "La fecha final no puede ser anterior al inicio"
        else -> null
    }

    // ---------- Calendario ----------

    /** ¿El cuidado de calendario ocurre ese día? */
    fun ocurreEl(e: EventoCuidado, fecha: LocalDate): Boolean {
        if (fecha.isBefore(e.fechaInicio)) return false
        if (e.fechaFin != null && fecha.isAfter(e.fechaFin)) return false
        val f = e.frecuencia
        val inicio = e.fechaInicio
        return when (f.tipo) {
            TipoFrecuencia.UNICO -> fecha == inicio
            TipoFrecuencia.DIARIO -> ChronoUnit.DAYS.between(inicio, fecha) % f.n == 0L
            TipoFrecuencia.CADA_N_DIAS -> ChronoUnit.DAYS.between(inicio, fecha) % f.n == 0L
            TipoFrecuencia.SEMANAL -> {
                val dias = f.diasSemana.ifEmpty { setOf(inicio.dayOfWeek) }
                val semanas = ChronoUnit.WEEKS.between(lunes(inicio), lunes(fecha))
                fecha.dayOfWeek in dias && semanas % f.n == 0L
            }
            TipoFrecuencia.PERSONALIZADO -> fecha.dayOfWeek in f.diasSemana
            TipoFrecuencia.MENSUAL -> {
                val meses = ChronoUnit.MONTHS.between(inicio.withDayOfMonth(1), fecha.withDayOfMonth(1))
                meses % f.n == 0L && fecha.dayOfMonth == diaEnMes(inicio.dayOfMonth, fecha)
            }
            TipoFrecuencia.ANUAL -> {
                val anios = (fecha.year - inicio.year).toLong()
                anios % f.n == 0L && fecha.month == inicio.month && fecha.dayOfMonth == diaEnMes(inicio.dayOfMonth, fecha)
            }
        }
    }

    /** Ocurrencias de un cuidado de calendario entre dos fechas (incluidas). */
    fun ocurrencias(e: EventoCuidado, desde: LocalDate, hasta: LocalDate): List<LocalDateTime> {
        val resultado = mutableListOf<LocalDateTime>()
        var d = maxOf(desde, e.fechaInicio)
        val ultimo = if (e.fechaFin != null) minOf(hasta, e.fechaFin) else hasta
        while (!d.isAfter(ultimo)) {
            if (ocurreEl(e, d)) resultado += horasDe(e).map { d.atTime(it) }
            d = d.plusDays(1)
        }
        return resultado
    }

    /** Próxima ocurrencia de calendario estrictamente después de [despues], o null si ya terminó. */
    fun siguiente(e: EventoCuidado, despues: LocalDateTime): LocalDateTime? {
        var d = maxOf(despues.toLocalDate(), e.fechaInicio)
        val limite = despues.toLocalDate().plusDays(DIAS_BUSQUEDA)
        while (!d.isAfter(limite)) {
            if (e.fechaFin != null && d.isAfter(e.fechaFin)) return null
            if (ocurreEl(e, d)) horasDe(e).map { d.atTime(it) }.firstOrNull { it.isAfter(despues) }?.let { return it }
            d = d.plusDays(1)
        }
        return null
    }

    // ---------- Desde la última vez ----------

    /** Próxima fecha de un cuidado relativo: inicio si nunca se hizo; si no, última + intervalo (RF-06, RF-08). */
    fun proximaDesdeUltima(e: EventoCuidado, ultimaVez: LocalDate?): LocalDate? {
        if (ultimaVez == null) return e.fechaInicio
        val f = e.frecuencia
        val proxima = when (f.tipo) {
            TipoFrecuencia.UNICO -> return null
            TipoFrecuencia.DIARIO, TipoFrecuencia.CADA_N_DIAS -> ultimaVez.plusDays(f.n.toLong())
            TipoFrecuencia.SEMANAL, TipoFrecuencia.PERSONALIZADO -> ultimaVez.plusWeeks(f.n.toLong())
            TipoFrecuencia.MENSUAL -> ultimaVez.plusMonths(f.n.toLong())
            TipoFrecuencia.ANUAL -> ultimaVez.plusYears(f.n.toLong())
        }
        return if (e.fechaFin != null && proxima.isAfter(e.fechaFin)) null else proxima
    }

    /** Fecha de la última vez que se hizo (registros REALIZADA/ADMINISTRADA de ese cuidado). */
    fun ultimaVez(e: EventoCuidado, registros: List<RegistroCuidado>, zona: ZoneId): LocalDate? =
        registros.filter { it.eventoId == e.id && it.hecho }
            .maxOfOrNull { it.registradoEn.atZone(zona).toLocalDate() }

    // ---------- Agenda ----------

    /**
     * Tomas de un día para varios cuidados, cruzadas con sus registros.
     * Los cuidados relativos aparecen el día que vencen (y siguen apareciendo, vencidos, hasta hacerse).
     */
    fun agendaDelDia(
        fecha: LocalDate,
        hoy: LocalDate,
        eventos: List<EventoCuidado>,
        registros: List<RegistroCuidado>,
        zona: ZoneId,
    ): List<Toma> {
        val porClave = registros.associateBy { it.eventoId to it.fechaProgramada }
        val tomas = mutableListOf<Toma>()
        for (e in eventos.filter { !it.oculto }) {
            when (e.ancla) {
                Ancla.CALENDARIO -> ocurrencias(e, fecha, fecha).forEach { fh ->
                    tomas += Toma(e, fh, e.frecuencia.horas.isNotEmpty(), porClave[e.id to fh])
                }
                Ancla.ULTIMA_VEZ -> {
                    val proxima = proximaDesdeUltima(e, ultimaVez(e, registros, zona)) ?: continue
                    // Vencido: se muestra hoy (no en días pasados ni futuros)
                    val dia = if (proxima.isBefore(hoy)) hoy else proxima
                    if (dia == fecha) {
                        val fh = proxima.atStartOfDay()
                        tomas += Toma(e, fh, false, porClave[e.id to fh])
                    }
                }
            }
        }
        return tomas.sortedWith(compareBy({ !it.conHora }, { it.fechaHora }, { it.evento.titulo }))
    }

    // ---------- Estados ----------

    /** Vacunas y similares (RF-05): vencida / próxima (dentro del aviso) / al día. */
    fun vencimiento(proxima: LocalDate, hoy: LocalDate, avisoDias: Int?): Vencimiento = when {
        proxima.isBefore(hoy) -> Vencimiento.VENCIDO
        !proxima.isAfter(hoy.plusDays((avisoDias ?: AVISO_POR_DEFECTO).toLong())) -> Vencimiento.PROXIMO
        else -> Vencimiento.AL_DIA
    }

    /** RF-07: avisar cuando el tratamiento está por terminar (por defecto 2 días antes). */
    fun terminaPronto(e: EventoCuidado, hoy: LocalDate, dias: Int = AVISO_FIN_TRATAMIENTO): Boolean =
        e.fechaFin != null && !hoy.isAfter(e.fechaFin) && !e.fechaFin.isAfter(hoy.plusDays(dias.toLong()))

    // ---------- Identidad de registros ----------

    /**
     * Id determinista de un registro: el mismo cuidado a la misma hora produce el mismo id
     * en cualquier teléfono, así dos personas sin internet no crean registros duplicados.
     */
    fun idRegistro(eventoId: String, fechaProgramada: LocalDateTime): String =
        UUID.nameUUIDFromBytes("$eventoId|$fechaProgramada".toByteArray(StandardCharsets.UTF_8)).toString()

    // ---------- Auxiliares ----------

    private fun horasDe(e: EventoCuidado): List<LocalTime> = e.frecuencia.horas.sorted().ifEmpty { listOf(LocalTime.MIDNIGHT) }

    private fun lunes(d: LocalDate) = d.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

    /** El día 31 cae el último día en meses cortos; el 29 de febrero, el 28 en años no bisiestos. */
    private fun diaEnMes(dia: Int, fecha: LocalDate) = minOf(dia, fecha.lengthOfMonth())
}
