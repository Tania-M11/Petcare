package com.udenar.petcare

import com.udenar.petcare.domain.cuidados.EstadoRegistro
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.Frecuencia
import com.udenar.petcare.domain.cuidados.RegistroCuidado
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.cuidados.TipoFrecuencia
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.domain.modelo.VeterinarioManual
import com.udenar.petcare.domain.recomendaciones.AccionSugerida
import com.udenar.petcare.domain.recomendaciones.Michi
import com.udenar.petcare.domain.recomendaciones.ContextoRecomendaciones
import com.udenar.petcare.domain.recomendaciones.MotorRecomendaciones
import com.udenar.petcare.domain.recomendaciones.PoseMichi
import com.udenar.petcare.domain.recomendaciones.Tono
import com.udenar.petcare.domain.salud.RegistroPeso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Fase 6.5: reglas de recomendaciones (sin consejos médicos) y mensajes de Michi. */
class RecomendacionesTest {
    private val hoy = LocalDate.of(2026, 11, 23)
    private val vet = VeterinarioManual("Dra. Ruiz", "3001234567")
    private val max = Mascota("m1", "Max", Especie.PERRO, alergias = "Ninguna", veterinario = vet)
    private val comida = EventoCuidado("c1", "m1", TipoCuidado.COMIDA, "Cena", Frecuencia(TipoFrecuencia.DIARIO, horas = listOf(LocalTime.of(19, 0))), fechaInicio = hoy.minusDays(30))

    private fun ctx(
        mascotas: List<Mascota> = listOf(max), eventos: List<EventoCuidado> = listOf(comida),
        registros: List<RegistroCuidado> = emptyList(), pesos: List<RegistroPeso> = listOf(RegistroPeso("p0", "m1", hoy.minusDays(10), 12000)),
    ) = ContextoRecomendaciones(hoy, mascotas, eventos, registros, pesos, emptyList(), ZoneOffset.UTC)

    private fun ids(c: ContextoRecomendaciones) = MotorRecomendaciones.generar(c).map { it.id.substringBefore('_') }

    @Test
    fun vacunaVencidaEsUrgenteYVaPrimero() {
        val vacuna = EventoCuidado("v1", "m1", TipoCuidado.VACUNA, "Rabia", Frecuencia(TipoFrecuencia.UNICO), fechaInicio = hoy.minusDays(5))
        val r = MotorRecomendaciones.generar(ctx(eventos = listOf(comida, vacuna)))
        assertEquals(Tono.URGENTE, r.first().tono)
        assertEquals("Rabia de Max está vencida", r.first().titulo)
        assertTrue(r.first().porque.isNotBlank())
    }

    @Test
    fun cambioDePesoSugiereHablarConElVeterinario() {
        val pesos = listOf(RegistroPeso("p2", "m1", hoy.minusDays(2), 13500), RegistroPeso("p1", "m1", hoy.minusDays(20), 12000))
        val cambio = MotorRecomendaciones.generar(ctx(pesos = pesos)).single { it.id.startsWith("cambio_peso") }
        assertEquals("Max subió 13 % de peso", cambio.titulo)
        assertEquals(AccionSugerida.BUSCAR_VETERINARIO, cambio.accion)
        // Un cambio pequeño no genera tarjeta
        assertTrue("cambio" !in ids(ctx(pesos = listOf(RegistroPeso("p2", "m1", hoy, 12300), RegistroPeso("p1", "m1", hoy.minusDays(20), 12000)))))
    }

    @Test
    fun sinPesoRecienteOFichaIncompleta() {
        assertTrue("sin" in ids(ctx(pesos = emptyList())))
        assertTrue("sin" !in ids(ctx()))
        assertTrue("ficha" in ids(ctx(mascotas = listOf(max.copy(alergias = null, veterinario = null)))))
        assertTrue("ficha" !in ids(ctx()))
    }

    @Test
    fun tomasOmitidasSeguidas() {
        fun reg(dias: Long, estado: EstadoRegistro) = RegistroCuidado("r$dias", "m1", "c1", hoy.minusDays(dias).atTime(19, 0), estado, null, Instant.EPOCH)
        assertTrue("omitidas" in ids(ctx(registros = listOf(reg(0, EstadoRegistro.OMITIDA), reg(1, EstadoRegistro.OMITIDA), reg(2, EstadoRegistro.REALIZADA)))))
        assertTrue("omitidas" !in ids(ctx(registros = listOf(reg(0, EstadoRegistro.OMITIDA), reg(1, EstadoRegistro.REALIZADA)))))
    }

    @Test
    fun cuidadorNoRecibeTareasDeDueno() {
        val ajena = max.copy(esPropietario = false, alergias = null, veterinario = null)
        val r = ids(ctx(mascotas = listOf(ajena), pesos = emptyList()))
        assertTrue("ficha" !in r && "sin" !in r)
    }

    @Test
    fun cumpleanos() {
        val r = MotorRecomendaciones.generar(ctx(mascotas = listOf(max.copy(fechaNacimiento = hoy.minusYears(3)))))
        assertEquals("¡Hoy Max cumple 3 años!", r.single { it.tono == Tono.CELEBRACION }.titulo)
    }

    @Test
    fun nuncaSugiereDosisNiPorciones() {
        val vacuna = EventoCuidado("v1", "m1", TipoCuidado.VACUNA, "Rabia", Frecuencia(TipoFrecuencia.UNICO), fechaInicio = hoy.minusDays(5))
        val med = EventoCuidado("d1", "m1", TipoCuidado.MEDICAMENTO, "Antibiótico", Frecuencia(TipoFrecuencia.DIARIO, horas = listOf(LocalTime.NOON)), fechaInicio = hoy.minusDays(5), fechaFin = hoy.plusDays(1))
        val textos = MotorRecomendaciones.generar(ctx(eventos = listOf(vacuna, med), pesos = emptyList())).joinToString(" ") { it.titulo + it.mensaje + it.porque }.lowercase()
        listOf("mg", "ml ", "gramos de", "porción", "aumenta la dosis", "diagnóstico").forEach { assertTrue("Contiene '$it'", it !in textos) }
    }

    @Test
    fun chopperSaludaSegunElDia() {
        assertEquals(PoseMichi.SALUDO, Michi.cabecera(8, 0, 0, hayMascotas = false).pose)
        val manana = Michi.cabecera(8, 3, 3, hayMascotas = true)
        assertEquals("Buenos días", manana.momento)
        assertEquals("3 cuidados para hoy", manana.estado)
        assertEquals(PoseMichi.CELEBRA, Michi.cabecera(20, 3, 0, hayMascotas = true).pose)
        assertEquals("Llevas 2 de 5", Michi.cabecera(15, 5, 3, hayMascotas = true).estado)
        // Los trucos son cortos (caben en una línea) y rotan desde uno distinto cada día
        assertTrue(Michi.consejos(hoy).all { it.texto.length <= 45 })
        assertTrue(Michi.consejos(hoy).first() != Michi.consejos(hoy.plusDays(1)).first())
        assertEquals("¡Bien! Cena de Max hecho. Quedan 2.", Michi.alMarcar("Cena", "Max", 2))
    }
}

class ClimaTest {
    private val hoy = java.time.LocalDate.of(2026, 11, 23)
    private val max = com.udenar.petcare.domain.modelo.Mascota("m1", "Max", com.udenar.petcare.domain.modelo.Especie.PERRO)
    private val luna = com.udenar.petcare.domain.modelo.Mascota("m2", "Luna", com.udenar.petcare.domain.modelo.Especie.GATO)
    private fun c(t: Double, lluvia: Boolean = false) = com.udenar.petcare.domain.recomendaciones.Clima(t, lluvia)

    @org.junit.Test
    fun sugerenciasDeClimaSoloConPerrosYDeDia() {
        val m = com.udenar.petcare.domain.recomendaciones.MotorRecomendaciones
        org.junit.Assert.assertEquals("Hace calor: 30 °C", m.clima(c(30.2), listOf(max), hoy, 13)!!.titulo)
        org.junit.Assert.assertEquals("Está lloviendo (17 °C)", m.clima(c(17.0, true), listOf(max), hoy, 9)!!.titulo)
        org.junit.Assert.assertEquals(com.udenar.petcare.domain.recomendaciones.Tono.CELEBRACION, m.clima(c(20.0), listOf(max), hoy, 9)!!.tono)
        org.junit.Assert.assertNull(m.clima(c(20.0), listOf(luna), hoy, 9)) // sin perros
        org.junit.Assert.assertNull(m.clima(c(20.0), listOf(max), hoy, 23)) // de noche
        org.junit.Assert.assertNull(m.clima(null, listOf(max), hoy, 9)) // sin permiso o sin internet
    }
}
