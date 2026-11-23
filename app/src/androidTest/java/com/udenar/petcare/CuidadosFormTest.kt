package com.udenar.petcare

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

/** Partes 2.5, 2.6 y 6.2: cuidados nuevos con el recorrido por pasos desde la ficha. */
@HiltAndroidTest
class CuidadosFormTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var mascotas: RepositorioMascotas
    @Inject lateinit var cuidados: RepositorioCuidados
    @Inject lateinit var eventosDao: com.udenar.petcare.data.cuidados.EventoDao

    @Before
    fun preparar() {
        hilt.inject()
        compose.esperarInterfaz()
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
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("pregunta") }
    }

    private fun continuar() {
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("continuar") }
        compose.onNodeWithTag("continuar").performClick()
    }

    private fun guardar() {
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("guardar_cuidado") }
        compose.onNodeWithTag("guardar_cuidado").performClick()
    }

    @Test
    fun comidaConUnToqueYResumenEnPalabras(): Unit = runBlocking {
        nuevo("COMIDA")
        compose.onNodeWithTag("opcion_Desayuno").performClick() // avanza solo y propone las 7:00
        continuar() // hora
        continuar() // días: todos
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("resumen_cuidado") }
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Todos los días · 7:00 a. m.") }
        guardar()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("cuidado_Desayuno") }
        val e = cuidados.observarEventos().first().single()
        assertEquals(TipoCuidado.COMIDA, e.tipo)
        assertEquals(TipoFrecuencia.DIARIO, e.frecuencia.tipo)
        assertEquals(listOf(LocalTime.of(7, 0)), e.frecuencia.horas)
    }

    @Test
    fun banoCadaQuinceDiasCuentaDesdeLaUltimaVez(): Unit = runBlocking {
        nuevo("HIGIENE")
        compose.onNodeWithTag("higiene_BANO").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("stepper_mas") }
        repeat(14) { compose.onNodeWithTag("stepper_mas").performClick() }
        continuar() // frecuencia
        continuar() // próxima vez: hoy
        guardar()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("cuidado_Baño") }
        val e = cuidados.observarEventos().first().single()
        assertEquals(Ancla.ULTIMA_VEZ, e.ancla)
        assertEquals(15, e.frecuencia.n)
    }

    @Test
    fun medicamentoNoAvanzaSinNombreNiHora(): Unit = runBlocking {
        nuevo("MEDICAMENTO")
        compose.onNodeWithTag("continuar").assertIsNotEnabled()
        compose.onNodeWithTag("titulo_cuidado").performTextInput("Antibiótico")
        continuar()
        compose.onNodeWithTag("continuar").assertIsNotEnabled()
        compose.onNodeWithTag("hora_8").performClick()
        compose.onNodeWithTag("continuar").assertIsEnabled()
        assertEquals(0, cuidados.observarEventos().first().size)
    }

    @Test
    fun vacunaPideLaProximaFecha(): Unit = runBlocking {
        nuevo("VACUNA")
        compose.onNodeWithTag("titulo_cuidado").performTextInput("Rabia")
        continuar()
        compose.onNodeWithTag("asistente_saltar").performClick() // aún no se la aplicaron
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("¿Cuándo es la próxima dosis?") }
        compose.onNodeWithTag("continuar").assertIsNotEnabled()
    }

    @Test
    fun ocultarUnCuidadoDelVeterinarioSinBorrarlo(): Unit = runBlocking {
        cuidados.guardarEvento(
            com.udenar.petcare.domain.cuidados.EventoCuidado(
                "v1", "m1", TipoCuidado.MEDICAMENTO, "Gotas oftálmicas",
                com.udenar.petcare.domain.cuidados.Frecuencia(TipoFrecuencia.DIARIO, horas = listOf(LocalTime.of(9, 0))),
                fechaInicio = java.time.LocalDate.now(),
            ),
        )
        // El autor lo pone el servidor; aquí se simula que llegó así al sincronizar
        eventosDao.guardar(eventosDao.porId("v1")!!.copy(creadoPor = "vet", creadoPorNombre = "Dra. Paz", creadoPorRol = "VETERINARIO", pendiente = false))
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("cuidado_Gotas oftálmicas") }
        compose.onNodeWithTag("cuidado_Gotas oftálmicas").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Editar cuidado") }
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("ocultar_cuidado") }
        compose.onNodeWithTag("ocultar_cuidado").performScrollTo().performClick()

        // Sigue guardado, pero oculto: no aparece en la lista hasta pedir verlo
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("ver_ocultos") }
        assertEquals(true, cuidados.observarEventos().first().single().oculto)
        assertEquals(false, compose.onAllNodesWithTagExists("cuidado_Gotas oftálmicas"))
        compose.onNodeWithTag("ver_ocultos").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("cuidado_Gotas oftálmicas") }
    }
}
