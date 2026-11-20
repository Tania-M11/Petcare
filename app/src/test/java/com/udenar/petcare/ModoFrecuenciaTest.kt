package com.udenar.petcare

import com.udenar.petcare.domain.cuidados.Frecuencia
import com.udenar.petcare.domain.cuidados.ModoFrecuencia
import com.udenar.petcare.domain.cuidados.TipoFrecuencia
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ModoFrecuenciaTest {
    private val ocho = listOf(LocalTime.of(8, 0))

    @Test
    fun todosLosDiasDeLaSemanaEsDiario() {
        assertEquals(TipoFrecuencia.DIARIO, ModoFrecuencia.DIAS_SEMANA.aFrecuencia(1, DayOfWeek.entries.toSet(), ocho).tipo)
        val lj = ModoFrecuencia.DIAS_SEMANA.aFrecuencia(1, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), ocho)
        assertEquals(Frecuencia(TipoFrecuencia.SEMANAL, diasSemana = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), horas = ocho), lj)
    }

    @Test
    fun idaYVuelta() {
        for (modo in ModoFrecuencia.entries) {
            val f = modo.aFrecuencia(3, setOf(DayOfWeek.FRIDAY), ocho)
            val esperado = if (modo == ModoFrecuencia.DIARIO) ModoFrecuencia.DIARIO else modo
            assertEquals("Modo $modo", esperado, ModoFrecuencia.desde(f))
        }
    }

    @Test
    fun horasSinRepetirYOrdenadas() {
        val f = ModoFrecuencia.DIARIO.aFrecuencia(1, emptySet(), listOf(LocalTime.of(20, 0), LocalTime.of(8, 0), LocalTime.of(20, 0)))
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), f.horas)
    }
}
