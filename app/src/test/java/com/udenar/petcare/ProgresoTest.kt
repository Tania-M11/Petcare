package com.udenar.petcare

import com.udenar.petcare.domain.cuidados.Autor
import com.udenar.petcare.domain.cuidados.EstadoRegistro
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.Frecuencia
import com.udenar.petcare.domain.cuidados.RegistroCuidado
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.cuidados.TipoFrecuencia
import com.udenar.petcare.domain.recomendaciones.Progreso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Fase 6.4: cumplimiento semanal, racha, equipo y logros. */
class ProgresoTest {
    private val hoy = LocalDate.of(2026, 11, 25) // miércoles
    private val cena = EventoCuidado("c1", "m1", TipoCuidado.COMIDA, "Cena", Frecuencia(TipoFrecuencia.DIARIO, horas = listOf(LocalTime.of(19, 0))), fechaInicio = hoy.minusDays(20))

    private fun hecho(dias: Long, quien: String = "Ana María") =
        RegistroCuidado("r$dias", "m1", "c1", hoy.minusDays(dias).atTime(19, 0), EstadoRegistro.REALIZADA, Autor("u", quien, "PROPIETARIO"), Instant.EPOCH)

    @Test
    fun rachaCuentaDiasCompletosSeguidos() {
        // Hoy aún pendiente; ayer, antier y el día anterior completos; antes uno sin hacer
        val p = Progreso.calcular(listOf(cena), listOf(hecho(1), hecho(2), hecho(3), hecho(5)), emptyList(), emptyList(), hoy, ZoneOffset.UTC)
        assertEquals(3, p.racha)
        // Si hoy también se hizo, la racha incluye hoy
        assertEquals(4, Progreso.calcular(listOf(cena), listOf(hecho(0), hecho(1), hecho(2), hecho(3)), emptyList(), emptyList(), hoy, ZoneOffset.UTC).racha)
    }

    @Test
    fun cumplimientoPorSemanaYEquipo() {
        val p = Progreso.calcular(listOf(cena), listOf(hecho(0, "Ana María"), hecho(1, "Leo Ruiz"), hecho(2, "Leo Ruiz")), emptyList(), emptyList(), hoy, ZoneOffset.UTC)
        val actual = p.semanas.last()
        assertEquals(hoy.minusDays(2), actual.lunes)
        assertEquals(3, actual.total) // lunes, martes y miércoles hasta hoy
        assertEquals(3, actual.hechas)
        assertEquals(mapOf("Ana" to 1, "Leo" to 2), p.porPersona)
        assertTrue(p.logros.single { it.titulo == "Trabajo en equipo" }.conseguido)
        assertTrue(p.logros.single { it.titulo == "Semana perfecta" }.conseguido)
        assertEquals(8, p.semanas.size)
    }
}
