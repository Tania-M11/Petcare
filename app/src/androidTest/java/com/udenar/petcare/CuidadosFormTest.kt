package com.udenar.petcare

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.udenar.petcare.domain.cuidados.Ancla
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime
import javax.inject.Inject

/** Partes 2.5 y 2.6: formularios de cuidados desde la ficha de la mascota. */
@HiltAndroidTest
class CuidadosFormTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var mascotas: RepositorioMascotas
    @Inject lateinit var cuidados: RepositorioCuidados

    @Before
    fun preparar() {
        hilt.inject()
        runBlocking { mascotas.guardar(Mascota("m1", "Luna", Especie.GATO)) }
        compose.onNodeWithTag("pestana_mascotas").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("mascota_Luna") }
        compose.onNodeWithTag("mascota_Luna").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("seccion_Cuidados") }
        compose.onNodeWithTag("seccion_Cuidados").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("agregar_cuidado") }
    }

    private fun nuevo(clase: String) {
        compose.onNodeWithTag("agregar_cuidado").performScrollTo().performClick()
        compose.onNodeWithTag("clase_$clase").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Nuevo cuidado") }
    }

    @Test
    fun comidaConSugerenciaYHoraPorDefecto(): Unit = runBlocking {
        nuevo("COMIDA")
        compose.onNodeWithText("Desayuno").performClick()
        compose.onNodeWithText("Guardar cuidado").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("cuidado_Desayuno") }
        compose.onNodeWithText("Todos los días · 7:00 a. m.").assertExists()
        val e = cuidados.observarEventos().first().single()
        assertEquals(TipoCuidado.COMIDA, e.tipo)
        assertEquals(TipoFrecuencia.DIARIO, e.frecuencia.tipo)
        assertEquals(listOf(LocalTime.of(7, 0)), e.frecuencia.horas)
    }

    @Test
    fun banoCadaQuinceDiasCuentaDesdeLaUltimaVez(): Unit = runBlocking {
        nuevo("HIGIENE")
        compose.onNodeWithText("N").performTextInput("15")
        compose.onNodeWithText("Guardar cuidado").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("cuidado_Baño") }
        val e = cuidados.observarEventos().first().single()
        assertEquals(Ancla.ULTIMA_VEZ, e.ancla)
        assertEquals(15, e.frecuencia.n)
    }

    @Test
    fun medicamentoExigeNombreYHora(): Unit = runBlocking {
        nuevo("MEDICAMENTO")
        compose.onNodeWithText("Guardar cuidado").performScrollTo().performClick()
        compose.onNodeWithText("Escribe un nombre").assertExists()
        compose.onNodeWithText("Agrega al menos una hora").assertExists()
        assertEquals(0, cuidados.observarEventos().first().size)
    }

    @Test
    fun vacunaConProximaFechaExigida(): Unit = runBlocking {
        nuevo("VACUNA")
        compose.onNodeWithText("Nombre de la vacuna").performTextInput("Rabia")
        compose.onNodeWithText("Guardar cuidado").performScrollTo().performClick()
        compose.onNodeWithText("Elige la próxima fecha").assertExists()
    }
}
