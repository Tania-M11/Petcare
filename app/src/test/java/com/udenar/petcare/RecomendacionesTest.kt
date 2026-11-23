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
import com.udenar.petcare.domain.recomendaciones.Chopper
import com.udenar.petcare.domain.recomendaciones.ContextoRecomendaciones
import com.udenar.petcare.domain.recomendaciones.MotorRecomendaciones
import com.udenar.petcare.domain.recomendaciones.PoseChopper
import com.udenar.petcare.domain.recomendaciones.Tono
import com.udenar.petcare.domain.salud.RegistroPeso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Fase 6.5: reglas de recomendaciones (sin consejos médicos) y mensajes de Chopper. */
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
        assertEquals(PoseChopper.SALUDO, Chopper.saludo("Ana", 8, 0, 0, emptyList(), hoy).pose)
        assertEquals("¡Buenos días, Ana! Hoy hay 3 cuidados para Max y Luna. Vamos uno por uno.", Chopper.saludo("Ana", 8, 3, 3, listOf("Max", "Luna"), hoy).texto)
        assertEquals(PoseChopper.AMIGOS, Chopper.saludo("Ana", 20, 3, 0, listOf("Max"), hoy).pose)
        assertEquals("¡Vas muy bien! Llevas 2 de 5; quedan 3.", Chopper.saludo("Ana", 15, 5, 3, listOf("Max"), hoy).texto)
        assertEquals("¡Bien! Cena de Max ✓ — quedan 2", Chopper.alMarcar("Cena", "Max", 2))
    }
}
