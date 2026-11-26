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

class ResumenSemanalTest {
    private val lunes = java.time.LocalDate.of(2026, 11, 23)
    private fun p(hechas: Int, total: Int, paseos: Int = 0, km: Double = 0.0, racha: Int = 0) = com.udenar.petcare.domain.recomendaciones.ProgresoMascota(
        listOf(com.udenar.petcare.domain.recomendaciones.SemanaProgreso(lunes, hechas, total)), racha,
        listOf(com.udenar.petcare.domain.recomendaciones.SemanaPaseos(lunes, km, 30 * paseos, paseos)), emptyMap(), emptyList(),
    )

    @org.junit.Test
    fun resumeLaSemanaDeTodasLasMascotas() {
        val (titulo, cuerpo) = com.udenar.petcare.domain.recomendaciones.ResumenSemanal.texto(listOf("Venus" to p(14, 14, 3, 4.25, 7), "Apolo" to p(6, 7)))!!
        org.junit.Assert.assertEquals("Tu semana con Venus y Apolo", titulo)
        org.junit.Assert.assertEquals("Cuidados: 20 de 21 (95 %). Paseos: 3 · 4,3 km. Racha de Venus: 7 días. Buen avance esta semana", cuerpo)
    }

    @org.junit.Test
    fun sinRegistrosNoSeEnviaNada() {
        org.junit.Assert.assertNull(com.udenar.petcare.domain.recomendaciones.ResumenSemanal.texto(listOf("Luna" to p(0, 0))))
    }
}

class EsperaResumenTest {
    @org.junit.Test
    fun programaElProximoDomingoALasSiete() {
        val espera = com.udenar.petcare.data.resumen.ProgramadorResumen.Companion::esperaHastaDomingo
        // Martes 24-nov 10:00 → domingo 29-nov 19:00
        org.junit.Assert.assertEquals(java.time.Duration.ofDays(5).plusHours(9), espera(java.time.LocalDateTime.of(2026, 11, 24, 10, 0)))
        // Domingo a las 20:00 ya pasó: el de la otra semana
        org.junit.Assert.assertEquals(java.time.Duration.ofDays(6).plusHours(23), espera(java.time.LocalDateTime.of(2026, 11, 29, 20, 0)))
    }
}
