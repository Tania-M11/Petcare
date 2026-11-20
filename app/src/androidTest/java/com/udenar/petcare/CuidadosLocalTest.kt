package com.udenar.petcare

import com.udenar.petcare.domain.cuidados.EstadoRegistro
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.Frecuencia
import com.udenar.petcare.domain.cuidados.RepositorioCuidados
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.cuidados.TipoFrecuencia
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.Mascota
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** Parte 2.3: cuidados en el teléfono (Room en memoria, sin red). */
@HiltAndroidTest
class CuidadosLocalTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var mascotas: RepositorioMascotas
    @Inject lateinit var cuidados: RepositorioCuidados
    @Inject lateinit var cuenta: CuentaFalsa

    @Before
    fun preparar() = hilt.inject()

    @Test
    fun registrarYDeshacerUnaToma() = runBlocking {
        val hoy = LocalDate.now()
        mascotas.guardar(Mascota("m1", "Luna", Especie.GATO))
        cuidados.guardarEvento(
            EventoCuidado(
                "e1", "m1", TipoCuidado.MEDICAMENTO, "Antibiótico",
                Frecuencia(TipoFrecuencia.DIARIO, horas = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))),
                fechaInicio = hoy, detalle = mapOf("dosis" to "Media tableta"),
            ),
        )
        val tomas = cuidados.agenda(hoy).first()
        assertEquals(2, tomas.size)
        assertEquals("Media tableta", tomas.first().evento.detalle["dosis"])

        // Marcar la de las 8:00: queda hecha, a nombre del usuario actual y pendiente de subir
        cuidados.registrar(tomas.first(), EstadoRegistro.ADMINISTRADA)
        val despues = cuidados.agenda(hoy).first()
        assertFalse(despues.first().pendiente)
        assertEquals(CuentaFalsa.ANA.nombre, despues.first().registro!!.registradoPor!!.nombre)
        assertTrue(despues.first().registro!!.porSubir)

        // Marcarla otra vez no crea un segundo registro
        cuidados.registrar(despues.first(), EstadoRegistro.OMITIDA)
        assertEquals(1, cuidados.observarRegistros().first().size)

        // Deshacer: vuelve a pendiente
        cuidados.deshacer(despues.first())
        assertTrue(cuidados.agenda(hoy).first().first().pendiente)
    }

    @Test
    fun losCuidadosDeUnaMascotaEliminadaDesaparecen() = runBlocking {
        val hoy = LocalDate.now()
        mascotas.guardar(Mascota("m2", "Max", Especie.PERRO))
        cuidados.guardarEvento(EventoCuidado("e2", "m2", TipoCuidado.COMIDA, "Desayuno", Frecuencia(TipoFrecuencia.DIARIO), fechaInicio = hoy))
        assertEquals(1, cuidados.agenda(hoy).first().size)
        mascotas.eliminar("m2")
        assertTrue(cuidados.agenda(hoy).first().isEmpty())
    }
}
