package com.udenar.petcare

import com.udenar.petcare.domain.cuidados.DescripcionCuidados
import com.udenar.petcare.domain.cuidados.Frecuencia
import com.udenar.petcare.domain.cuidados.TipoFrecuencia
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class DescripcionCuidadosTest {
    private fun h(vararg t: String) = t.map { LocalTime.parse(it) }

    @Test
    fun frecuenciasLegibles() {
        assertEquals("Todos los días · 7:00 a. m. y 7:00 p. m.", DescripcionCuidados.frecuencia(Frecuencia(TipoFrecuencia.DIARIO, horas = h("19:00", "07:00"))))
        assertEquals("Cada 15 días", DescripcionCuidados.frecuencia(Frecuencia(TipoFrecuencia.CADA_N_DIAS, n = 15)))
        assertEquals("Cada 3 meses", DescripcionCuidados.frecuencia(Frecuencia(TipoFrecuencia.MENSUAL, n = 3)))
        assertEquals("Cada año", DescripcionCuidados.frecuencia(Frecuencia(TipoFrecuencia.ANUAL)))
        assertEquals("Una vez · 10:30 a. m.", DescripcionCuidados.frecuencia(Frecuencia(TipoFrecuencia.UNICO, horas = h("10:30"))))
        assertEquals(
            "Lun, Jue · 6:00 p. m.",
            DescripcionCuidados.frecuencia(Frecuencia(TipoFrecuencia.PERSONALIZADO, diasSemana = setOf(DayOfWeek.THURSDAY, DayOfWeek.MONDAY), horas = h("18:00"))),
        )
        assertEquals(
            "De lunes a viernes · 8:00 a. m.",
            DescripcionCuidados.frecuencia(Frecuencia(TipoFrecuencia.PERSONALIZADO, diasSemana = DayOfWeek.entries.take(5).toSet(), horas = h("08:00"))),
        )
        assertEquals("Cada 2 semanas · Sáb", DescripcionCuidados.frecuencia(Frecuencia(TipoFrecuencia.SEMANAL, n = 2, diasSemana = setOf(DayOfWeek.SATURDAY))))
    }

    @Test
    fun horas() {
        assertEquals("8:00 a. m., 2:00 p. m. y 9:00 p. m.", DescripcionCuidados.horas(h("21:00", "08:00", "14:00")))
    }
}
