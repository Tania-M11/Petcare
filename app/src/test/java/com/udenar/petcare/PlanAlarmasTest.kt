package com.udenar.petcare

import com.udenar.petcare.domain.cuidados.EstadoRegistro
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.Frecuencia
import com.udenar.petcare.domain.cuidados.MotorCuidados
import com.udenar.petcare.domain.cuidados.PlanAlarmas
import com.udenar.petcare.domain.cuidados.RegistroCuidado
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.cuidados.TipoFrecuencia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class PlanAlarmasTest {
    private val zona = ZoneId.of("America/Bogota")
    private val hoy = LocalDate.of(2026, 11, 19)
    private fun h(vararg t: String) = t.map { LocalTime.parse(it) }

    private fun registro(eventoId: String, t: LocalDateTime, estado: EstadoRegistro) = RegistroCuidado(
        MotorCuidados.idRegistro(eventoId, t), "m1", eventoId, t, estado, null, t.atZone(zona).toInstant(),
    )

    @Test
    fun laProximaTomaPendiente() {
        val med = EventoCuidado("med", "m1", TipoCuidado.MEDICAMENTO, "Gotas", Frecuencia(TipoFrecuencia.DIARIO, horas = h("08:00", "20:00")), fechaInicio = hoy)
        // A las 7:00 suena a las 8:00
        assertEquals(hoy.atTime(8, 0), PlanAlarmas.calcular(listOf(med), emptyList(), hoy.atTime(7, 0), zona).single().disparaEn)
        // Si la de las 20:00 ya se dio (antes de tiempo), salta a mañana 8:00
        val dada = registro("med", hoy.atTime(20, 0), EstadoRegistro.ADMINISTRADA)
        assertEquals(hoy.plusDays(1).atTime(8, 0), PlanAlarmas.calcular(listOf(med), listOf(dada), hoy.atTime(9, 0), zona).single().disparaEn)
        // Pospuesta sigue pendiente
        val pospuesta = registro("med", hoy.atTime(20, 0), EstadoRegistro.POSPUESTA)
        assertEquals(hoy.atTime(20, 0), PlanAlarmas.calcular(listOf(med), listOf(pospuesta), hoy.atTime(9, 0), zona).single().disparaEn)
    }

    @Test
    fun cuidadosDeTodoElDiaAvisanALas9() {
        val bano = EventoCuidado("bano", "m1", TipoCuidado.BANO, "Baño", Frecuencia(TipoFrecuencia.CADA_N_DIAS, n = 15), fechaInicio = hoy)
        assertEquals(hoy.atTime(9, 0), PlanAlarmas.calcular(listOf(bano), emptyList(), hoy.atTime(7, 0), zona).single().disparaEn)
        // Ya pasadas las 9 y sin hacer: no suena de nuevo (se ve vencido en Hoy)
        assertTrue(PlanAlarmas.calcular(listOf(bano), emptyList(), hoy.atTime(10, 0), zona).isEmpty())
        // Hecho hoy: el próximo en 15 días a las 9:00
        val hecho = registro("bano", hoy.atStartOfDay(), EstadoRegistro.REALIZADA)
        assertEquals(hoy.plusDays(15).atTime(9, 0), PlanAlarmas.calcular(listOf(bano), listOf(hecho), hoy.atTime(10, 0), zona).single().disparaEn)
    }

    @Test
    fun avisoAnticipadoDeVacuna() {
        val vacuna = EventoCuidado(
            "vac", "m1", TipoCuidado.VACUNA, "Rabia", Frecuencia(TipoFrecuencia.UNICO), fechaInicio = hoy.plusDays(10), avisoAnticipadoDias = 7,
        )
        val alarmas = PlanAlarmas.calcular(listOf(vacuna), emptyList(), hoy.atTime(7, 0), zona)
        assertEquals(2, alarmas.size)
        val aviso = alarmas.single { it.esAviso }
        assertEquals(hoy.plusDays(3).atTime(9, 0), aviso.disparaEn)
        assertEquals("vac:aviso", aviso.clave)
        assertEquals("Vacuna de Luna en 7 días", PlanAlarmas.titulo(vacuna, "Luna", true, hoy.plusDays(10), hoy.plusDays(3)))
    }

    @Test
    fun cuidadoTerminadoOOcultoNoSuena() {
        val terminado = EventoCuidado("t", "m1", TipoCuidado.MEDICAMENTO, "X", Frecuencia(TipoFrecuencia.DIARIO, horas = h("08:00")), fechaInicio = hoy.minusDays(5), fechaFin = hoy.minusDays(1))
        val oculto = terminado.copy(id = "o", fechaFin = null, oculto = true)
        assertTrue(PlanAlarmas.calcular(listOf(terminado, oculto), emptyList(), hoy.atTime(7, 0), zona).isEmpty())
    }

    @Test
    fun textosDeLaNotificacion() {
        val comida = EventoCuidado("c", "m1", TipoCuidado.COMIDA, "Desayuno", Frecuencia(TipoFrecuencia.DIARIO), fechaInicio = hoy, detalle = mapOf("texto" to "Media taza"))
        assertEquals("Hora de darle de comer a Luna", PlanAlarmas.titulo(comida, "Luna", false, hoy, hoy))
        assertEquals("Desayuno · Media taza", PlanAlarmas.texto(comida))
    }
}
