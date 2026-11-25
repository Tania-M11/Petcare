package com.udenar.petcare

import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.Frecuencia
import com.udenar.petcare.domain.cuidados.RepositorioCuidados
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.cuidados.TipoFrecuencia
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.domain.salud.RepositorioSalud
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** Parte 2.8: estado de cuidado, peso con gráfica, visitas e historial. */
@HiltAndroidTest
class SaludTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var mascotas: RepositorioMascotas
    @Inject lateinit var cuidados: RepositorioCuidados
    @Inject lateinit var salud: RepositorioSalud

    @Before
    fun preparar() {
        hilt.inject()
        compose.esperarInterfaz()
        runBlocking {
            mascotas.guardar(Mascota("m1", "Luna", Especie.GATO, pesoGramos = 4000))
            cuidados.guardarEvento(EventoCuidado("c1", "m1", TipoCuidado.COMIDA, "Cena", Frecuencia(TipoFrecuencia.DIARIO, horas = listOf(LocalTime.of(23, 59))), fechaInicio = LocalDate.now()))
        }
        compose.onNodeWithTag("pestana_mascotas").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("mascota_Luna") }
        compose.onNodeWithTag("mascota_Luna").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("estado_COMIDAS") }
    }

    @Test
    fun registrarPesoActualizaLaFichaYLaGrafica(): Unit = runBlocking {
        // Cambiar el peso en la ficha ya dejó un registro (4 kg); se agrega otro
        compose.onNodeWithTag("seccion_Salud").performClick()
        compose.onNodeWithTag("registrar_peso").performScrollTo().performClick()
        // Parte del último peso (4 kg): cinco toques en + llegan a 4,5 kg
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("stepper_mas") }
        repeat(5) { compose.onNodeWithTag("stepper_mas").performClick() }
        compose.onNodeWithText("desde el último pesaje", substring = true).assertExists()
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("grafica_peso") }
        assertEquals(4500, mascotas.obtener("m1")!!.pesoGramos)
        assertEquals(2, salud.observarPesos("m1").first().size)
    }

    @Test
    fun visitaConProximaCitaCreaRecordatorioYApareceEnHistorial(): Unit = runBlocking {
        compose.onNodeWithTag("seccion_Salud").performClick()
        compose.onNodeWithTag("agregar_visita").performScrollTo().performClick()
        // Recorrido por pasos: motivo → cuándo → indicaciones (se salta) → próxima cita → resumen
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("motivo_Control") }
        compose.onNodeWithTag("continuar").assertIsNotEnabled()
        compose.onNodeWithTag("motivo_Control").performClick()
        compose.onNodeWithTag("motivo_visita").performTextReplacement("Control anual")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("continuar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("¿Cuándo y con quién?") }
        compose.onNodeWithTag("continuar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("asistente_saltar") }
        compose.onNodeWithTag("asistente_saltar").performClick() // sin indicaciones
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("proxima_En 1 año") }
        compose.onNodeWithTag("proxima_En 1 año").performClick()
        compose.onNodeWithTag("continuar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("guardar_visita") }
        compose.onNodeWithTag("guardar_visita").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("visita_Control anual") }
        // La próxima cita quedó como recordatorio
        compose.waitUntil(5_000) { runBlocking { cuidados.observarEventos().first().any { it.tipo == com.udenar.petcare.domain.cuidados.TipoCuidado.CITA_VET } } }

        compose.onNodeWithTag("seccion_Historial").performClick()
        compose.onNodeWithText("Control anual").assertExists()
        compose.onNodeWithTag("historial_PESO").performClick()
        compose.onNodeWithText("4 kg").assertExists()
    }

    @Test
    fun agregarDocumentoEnPasos() {
        compose.onNodeWithTag("seccion_Salud").performClick()
        compose.onNodeWithTag("agregar_documento").performScrollTo().performClick()
        // Primero qué es, luego de dónde se toma (la cámara o el selector del sistema)
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("tipo_doc_CARNE") }
        compose.onNodeWithTag("tipo_doc_CARNE").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("doc_camara") }
        compose.onNodeWithTag("doc_galeria").assertExists()
        compose.onNodeWithTag("agregar_pdf").assertExists()
    }
}
