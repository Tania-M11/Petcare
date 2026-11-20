package com.udenar.petcare.domain.cuidados

import java.time.DayOfWeek
import java.time.LocalTime

/** Cómo elige el usuario la repetición en los formularios (se traduce a [Frecuencia]). */
enum class ModoFrecuencia(val etiqueta: String, val usaN: Boolean = false, val unidad: String = "") {
    UNA_VEZ("Una vez"),
    DIARIO("Todos los días"),
    DIAS_SEMANA("Días de la semana"),
    CADA_N_DIAS("Cada N días", true, "días"),
    CADA_N_SEMANAS("Cada N semanas", true, "semanas"),
    CADA_N_MESES("Cada N meses", true, "meses"),
    CADA_N_ANIOS("Cada N años", true, "años");

    fun aFrecuencia(n: Int, dias: Set<DayOfWeek>, horas: List<LocalTime>): Frecuencia {
        val h = horas.distinct().sorted()
        return when (this) {
            UNA_VEZ -> Frecuencia(TipoFrecuencia.UNICO, horas = h)
            DIARIO -> Frecuencia(TipoFrecuencia.DIARIO, horas = h)
            DIAS_SEMANA ->
                if (dias.size == 7) Frecuencia(TipoFrecuencia.DIARIO, horas = h)
                else Frecuencia(TipoFrecuencia.SEMANAL, diasSemana = dias, horas = h)
            CADA_N_DIAS -> Frecuencia(TipoFrecuencia.CADA_N_DIAS, n = n, horas = h)
            CADA_N_SEMANAS -> Frecuencia(TipoFrecuencia.SEMANAL, n = n, horas = h)
            CADA_N_MESES -> Frecuencia(TipoFrecuencia.MENSUAL, n = n, horas = h)
            CADA_N_ANIOS -> Frecuencia(TipoFrecuencia.ANUAL, n = n, horas = h)
        }
    }

    companion object {
        /** Al editar: de la regla guardada al modo del formulario. */
        fun desde(f: Frecuencia): ModoFrecuencia = when (f.tipo) {
            TipoFrecuencia.UNICO -> UNA_VEZ
            TipoFrecuencia.DIARIO -> if (f.n == 1) DIARIO else CADA_N_DIAS
            TipoFrecuencia.CADA_N_DIAS -> CADA_N_DIAS
            TipoFrecuencia.SEMANAL -> if (f.diasSemana.isNotEmpty() && f.n == 1) DIAS_SEMANA else CADA_N_SEMANAS
            TipoFrecuencia.PERSONALIZADO -> DIAS_SEMANA
            TipoFrecuencia.MENSUAL -> CADA_N_MESES
            TipoFrecuencia.ANUAL -> CADA_N_ANIOS
        }

        /** Para higiene y desparasitación (cuentan desde la última vez): solo intervalos. */
        val INTERVALOS = listOf(CADA_N_DIAS, CADA_N_SEMANAS, CADA_N_MESES)
    }
}
