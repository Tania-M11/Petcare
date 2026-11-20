package com.udenar.petcare

import com.udenar.petcare.domain.cuidados.Ancla
import com.udenar.petcare.domain.cuidados.EstadoRegistro
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.Frecuencia
import com.udenar.petcare.domain.cuidados.MotorCuidados
import com.udenar.petcare.domain.cuidados.RegistroCuidado
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.cuidados.TipoFrecuencia
import com.udenar.petcare.domain.cuidados.Vencimiento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class MotorCuidadosTest {
    private val zona = ZoneId.of("America/Bogota")
    private val hoy = LocalDate.of(2026, 11, 19) // jueves

    private fun evento(
        f: Frecuencia,
        inicio: LocalDate = hoy,
        fin: LocalDate? = null,
        tipo: TipoCuidado = TipoCuidado.MEDICAMENTO,
        ancla: Ancla = tipo.anclaPorDefecto,
    ) = EventoCuidado("e1", "m1", tipo, "Prueba", f, ancla, inicio, fin)

    private fun h(vararg t: String) = t.map { LocalTime.parse(it) }

    @Test
    fun diarioConVariasHoras() {
        val e = evento(Frecuencia(TipoFrecuencia.DIARIO, horas = h("20:00", "08:00")))
        val o = MotorCuidados.ocurrencias(e, hoy, hoy.plusDays(1))
        assertEquals(
            listOf(hoy.atTime(8, 0), hoy.atTime(20, 0), hoy.plusDays(1).atTime(8, 0), hoy.plusDays(1).atTime(20, 0)),
            o,
        )
    }

    @Test
    fun cadaNDiasDesdeElInicio() {
        val e = evento(Frecuencia(TipoFrecuencia.CADA_N_DIAS, n = 3, horas = h("09:00")))
        val dias = MotorCuidados.ocurrencias(e, hoy, hoy.plusDays(9)).map { it.toLocalDate() }
        assertEquals(listOf(hoy, hoy.plusDays(3), hoy.plusDays(6), hoy.plusDays(9)), dias)
    }

    @Test
    fun semanalConDiasElegidosYCadaDosSemanas() {
        val e = evento(Frecuencia(TipoFrecuencia.SEMANAL, n = 2, diasSemana = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), horas = h("07:00")))
        val dias = MotorCuidados.ocurrencias(e, hoy, hoy.plusDays(20)).map { it.toLocalDate() }
        // Semana del inicio: jue 19; la siguiente no; luego lun 30 nov y jue 3 dic
        assertEquals(listOf(hoy, LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 3)), dias)
    }

    @Test
    fun semanalSinDiasUsaElDiaDeInicio() {
        val e = evento(Frecuencia(TipoFrecuencia.SEMANAL))
        assertTrue(MotorCuidados.ocurreEl(e, hoy.plusWeeks(1)))
        assertFalse(MotorCuidados.ocurreEl(e, hoy.plusDays(1)))
    }

    @Test
    fun mensualEl31CaeAlUltimoDiaDelMes() {
        val e = evento(Frecuencia(TipoFrecuencia.MENSUAL), inicio = LocalDate.of(2026, 1, 31))
        val dias = MotorCuidados.ocurrencias(e, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30)).map { it.toLocalDate() }
        assertEquals(listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)), dias)
    }

    @Test
    fun anualEl29DeFebrero() {
        val e = evento(Frecuencia(TipoFrecuencia.ANUAL), inicio = LocalDate.of(2024, 2, 29), tipo = TipoCuidado.VACUNA)
        assertTrue(MotorCuidados.ocurreEl(e, LocalDate.of(2025, 2, 28)))
        assertTrue(MotorCuidados.ocurreEl(e, LocalDate.of(2028, 2, 29)))
        assertFalse(MotorCuidados.ocurreEl(e, LocalDate.of(2028, 2, 28)))
    }

    @Test
    fun unicoYFechaFin() {
        val unico = evento(Frecuencia(TipoFrecuencia.UNICO, horas = h("10:30")), tipo = TipoCuidado.CITA_VET)
        assertEquals(listOf(hoy.atTime(10, 30)), MotorCuidados.ocurrencias(unico, hoy.minusDays(5), hoy.plusDays(5)))

        val conFin = evento(Frecuencia(TipoFrecuencia.DIARIO, horas = h("08:00")), fin = hoy.plusDays(2))
        assertEquals(3, MotorCuidados.ocurrencias(conFin, hoy, hoy.plusDays(10)).size)
        assertNull(MotorCuidados.siguiente(conFin, hoy.plusDays(2).atTime(9, 0)))
    }

    @Test
    fun siguienteBuscaLaProximaHora() {
        val e = evento(Frecuencia(TipoFrecuencia.DIARIO, horas = h("08:00", "20:00")))
        assertEquals(hoy.atTime(20, 0), MotorCuidados.siguiente(e, hoy.atTime(8, 0)))
        assertEquals(hoy.plusDays(1).atTime(8, 0), MotorCuidados.siguiente(e, hoy.atTime(21, 0)))

        val personalizada = evento(Frecuencia(TipoFrecuencia.PERSONALIZADO, diasSemana = setOf(DayOfWeek.SATURDAY), horas = h("10:00")))
        assertEquals(LocalDate.of(2026, 11, 21).atTime(10, 0), MotorCuidados.siguiente(personalizada, hoy.atTime(12, 0)))

        val anual = evento(Frecuencia(TipoFrecuencia.ANUAL), inicio = LocalDate.of(2026, 3, 1), tipo = TipoCuidado.VACUNA)
        assertEquals(LocalDate.of(2027, 3, 1).atStartOfDay(), MotorCuidados.siguiente(anual, hoy.atStartOfDay()))
    }

    @Test
    fun desdeLaUltimaVez() {
        val bano = evento(Frecuencia(TipoFrecuencia.CADA_N_DIAS, n = 15), tipo = TipoCuidado.BANO)
        assertEquals(Ancla.ULTIMA_VEZ, bano.ancla)
        assertEquals(hoy, MotorCuidados.proximaDesdeUltima(bano, null))
        assertEquals(LocalDate.of(2026, 12, 4), MotorCuidados.proximaDesdeUltima(bano, hoy))

        val desparasitar = evento(Frecuencia(TipoFrecuencia.MENSUAL, n = 3), tipo = TipoCuidado.DESPARASITACION_INTERNA)
        assertEquals(LocalDate.of(2027, 2, 19), MotorCuidados.proximaDesdeUltima(desparasitar, hoy))
    }

    @Test
    fun agendaDelDiaCruzaRegistrosYMuestraVencidos() {
        val med = evento(Frecuencia(TipoFrecuencia.DIARIO, horas = h("08:00", "20:00"))).copy(id = "med")
        val bano = evento(Frecuencia(TipoFrecuencia.CADA_N_DIAS, n = 15), inicio = hoy.minusDays(30), tipo = TipoCuidado.BANO).copy(id = "bano")
        val dosisManana = RegistroCuidado(
            MotorCuidados.idRegistro("med", hoy.atTime(8, 0)), "m1", "med", hoy.atTime(8, 0), EstadoRegistro.ADMINISTRADA,
            null, hoy.atTime(8, 5).atZone(zona).toInstant(),
        )
        val tomas = MotorCuidados.agendaDelDia(hoy, hoy, listOf(med, bano), listOf(dosisManana), zona)
        assertEquals(3, tomas.size)
        // Con hora primero (8:00 hecha, 20:00 pendiente), luego el baño vencido sin hora
        assertFalse(tomas[0].pendiente)
        assertTrue(tomas[1].pendiente)
        assertEquals("bano", tomas[2].evento.id)
        assertFalse(tomas[2].conHora)

        // Un baño vencido no aparece en días pasados ni futuros, solo hoy
        assertTrue(MotorCuidados.agendaDelDia(hoy.plusDays(1), hoy, listOf(bano), emptyList(), zona).isEmpty())
    }

    @Test
    fun alHacerElBanoSaleDeHoyYVuelveEnQuinceDias() {
        val bano = evento(Frecuencia(TipoFrecuencia.CADA_N_DIAS, n = 15), inicio = hoy, tipo = TipoCuidado.BANO).copy(id = "bano")
        val hecho = RegistroCuidado(
            MotorCuidados.idRegistro("bano", hoy.atStartOfDay()), "m1", "bano", hoy.atStartOfDay(), EstadoRegistro.REALIZADA,
            null, hoy.atTime(11, 0).atZone(zona).toInstant(),
        )
        // El registro de hoy queda visible como hecho y la próxima vez es en 15 días
        assertTrue(MotorCuidados.agendaDelDia(hoy.plusDays(15), hoy.plusDays(15), listOf(bano), listOf(hecho), zona).single().pendiente)
        assertTrue(MotorCuidados.agendaDelDia(hoy.plusDays(5), hoy.plusDays(5), listOf(bano), listOf(hecho), zona).isEmpty())
    }

    @Test
    fun vencimientoYFinDeTratamiento() {
        assertEquals(Vencimiento.VENCIDO, MotorCuidados.vencimiento(hoy.minusDays(1), hoy, null))
        assertEquals(Vencimiento.PROXIMO, MotorCuidados.vencimiento(hoy.plusDays(7), hoy, null))
        assertEquals(Vencimiento.AL_DIA, MotorCuidados.vencimiento(hoy.plusDays(8), hoy, null))
        assertEquals(Vencimiento.PROXIMO, MotorCuidados.vencimiento(hoy.plusDays(20), hoy, 30))

        val tratamiento = evento(Frecuencia(TipoFrecuencia.DIARIO), fin = hoy.plusDays(2))
        assertTrue(MotorCuidados.terminaPronto(tratamiento, hoy))
        assertFalse(MotorCuidados.terminaPronto(tratamiento, hoy.minusDays(1)))
        assertFalse(MotorCuidados.terminaPronto(tratamiento, hoy.plusDays(3)))
    }

    @Test
    fun idDeRegistroEsElMismoEnCualquierTelefono() {
        val a = MotorCuidados.idRegistro("e1", LocalDateTime.of(2026, 11, 19, 8, 0))
        val b = MotorCuidados.idRegistro("e1", LocalDateTime.of(2026, 11, 19, 8, 0))
        val c = MotorCuidados.idRegistro("e1", LocalDateTime.of(2026, 11, 19, 20, 0))
        assertEquals(a, b)
        assertNotNull(a)
        assertFalse(a == c)
    }

    @Test
    fun validacionDeFrecuencias() {
        assertNull(MotorCuidados.validar(Frecuencia(TipoFrecuencia.DIARIO, horas = h("08:00")), hoy, null))
        assertEquals("Elige al menos un día", MotorCuidados.validar(Frecuencia(TipoFrecuencia.PERSONALIZADO, horas = h("08:00")), hoy, null))
        assertEquals("Hay horarios repetidos", MotorCuidados.validar(Frecuencia(TipoFrecuencia.DIARIO, horas = h("08:00", "08:00")), hoy, null))
        assertEquals("El intervalo debe estar entre 1 y 365", MotorCuidados.validar(Frecuencia(TipoFrecuencia.CADA_N_DIAS, n = 0), hoy, null))
        assertEquals("La fecha final no puede ser anterior al inicio", MotorCuidados.validar(Frecuencia(TipoFrecuencia.DIARIO), hoy, hoy.minusDays(1)))
    }
}
