package com.udenar.petcare

import com.udenar.petcare.domain.cuidados.AreaCuidado
import com.udenar.petcare.domain.cuidados.EstadoCuidado
import com.udenar.petcare.domain.cuidados.EstadoRegistro
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.Frecuencia
import com.udenar.petcare.domain.cuidados.MotorCuidados
import com.udenar.petcare.domain.cuidados.NivelCuidado
import com.udenar.petcare.domain.cuidados.RegistroCuidado
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.cuidados.TipoFrecuencia
import com.udenar.petcare.domain.cuidados.resumir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class EstadoCuidadoTest {
    private val zona = ZoneId.of("America/Bogota")
    private val hoy = LocalDate.of(2026, 11, 20)

    private fun calcular(eventos: List<EventoCuidado>, registros: List<RegistroCuidado>, hora: Int) =
        hoy.atTime(hora, 0).let { ahora ->
            EstadoCuidado.calcular(
                eventos.map { resumir(it, registros, ahora, zona) },
                MotorCuidados.agendaDelDia(hoy, hoy, eventos, registros, zona),
                ahora,
            )
        }

    @Test
    fun comidaPasadaSinMarcarEsPendienteYMarcadaEsAlDia() {
        val desayuno = EventoCuidado("c", "m", TipoCuidado.COMIDA, "Desayuno", Frecuencia(TipoFrecuencia.DIARIO, horas = listOf(LocalTime.of(7, 0))), fechaInicio = hoy)
        assertEquals(NivelCuidado.PROXIMO, calcular(listOf(desayuno), emptyList(), 6)[AreaCuidado.COMIDAS])
        assertEquals(NivelCuidado.PENDIENTE, calcular(listOf(desayuno), emptyList(), 9)[AreaCuidado.COMIDAS])
        val dada = RegistroCuidado("r", "m", "c", hoy.atTime(7, 0), EstadoRegistro.REALIZADA, null, hoy.atTime(7, 5).atZone(zona).toInstant())
        assertEquals(NivelCuidado.AL_DIA, calcular(listOf(desayuno), listOf(dada), 9)[AreaCuidado.COMIDAS])
    }

    @Test
    fun vacunaVencidaYProxima() {
        val vencida = EventoCuidado("v1", "m", TipoCuidado.VACUNA, "Rabia", Frecuencia(TipoFrecuencia.UNICO), fechaInicio = hoy.minusDays(2))
        assertEquals(NivelCuidado.PENDIENTE, calcular(listOf(vencida), emptyList(), 9)[AreaCuidado.VACUNAS])
        val proxima = vencida.copy(fechaInicio = hoy.plusDays(5))
        assertEquals(NivelCuidado.PROXIMO, calcular(listOf(proxima), emptyList(), 9)[AreaCuidado.VACUNAS])
        val lejana = vencida.copy(fechaInicio = hoy.plusDays(60))
        assertEquals(NivelCuidado.AL_DIA, calcular(listOf(lejana), emptyList(), 9)[AreaCuidado.VACUNAS])
    }

    @Test
    fun soloAparecenLasAreasConCuidados() {
        val bano = EventoCuidado("b", "m", TipoCuidado.BANO, "Baño", Frecuencia(TipoFrecuencia.CADA_N_DIAS, n = 15), fechaInicio = hoy.plusDays(10))
        val estado = calcular(listOf(bano), emptyList(), 9)
        assertEquals(setOf(AreaCuidado.HIGIENE), estado.keys)
        assertEquals(NivelCuidado.AL_DIA, estado[AreaCuidado.HIGIENE])
        assertFalse(AreaCuidado.VACUNAS in estado)
    }
}
