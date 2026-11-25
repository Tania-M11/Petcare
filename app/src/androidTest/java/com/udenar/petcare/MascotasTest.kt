package com.udenar.petcare

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/** Parte 2.2: agregar, ver, editar y eliminar una mascota (base de datos en memoria, cuenta falsa). */
@HiltAndroidTest
class MascotasTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var repo: RepositorioMascotas
    @Inject lateinit var programador: ProgramadorFalso

    @Before
    fun preparar() {
        hilt.inject()
        compose.esperarInterfaz()
    }

    private fun abrirFormulario() {
        compose.onNodeWithTag("pestana_mascotas").performClick()
        compose.onNodeWithTag("boton_mas").performClick()
        compose.onNodeWithText("Agregar mascota").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("¿Quién llega a la familia?") }
    }

    @Test
    fun recorridoNoAvanzaSinNombre() {
        abrirFormulario()
        compose.onNodeWithTag("especie_GATO").performClick() // avanza solo
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("nombre_mascota") }
        compose.onNodeWithTag("continuar").assertIsNotEnabled()
        compose.onNodeWithTag("nombre_mascota").performTextInput("Luna")
        compose.onNodeWithTag("continuar").assertIsEnabled()
    }

    @Test
    fun agregarEditarYEliminar() = runBlocking {
        abrirFormulario()
        compose.onNodeWithTag("especie_GATO").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("nombre_mascota") }
        compose.onNodeWithTag("nombre_mascota").performTextInput("Luna")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithTag("continuar").performClick()
        // Foto, edad, detalles y salud se pueden saltar
        repeat(4) {
            compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("asistente_saltar") }
            compose.onNodeWithTag("asistente_saltar").performClick()
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("guardar_mascota") }
        compose.onNodeWithTag("guardar_mascota").performClick()

        // Queda guardada en el teléfono, pendiente de subir, y se abre su ficha
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Editar ficha") }
        val guardada = repo.observar().first().single()
        assertEquals("Luna", guardada.nombre)
        assertTrue(guardada.porSubir)
        assertTrue(programador.solicitudes > 0)

        // Editar el nombre desde la ficha
        compose.onNodeWithText("Editar ficha").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Guardar cambios") }
        compose.onNodeWithText("Luna").performTextClearance()
        compose.onNodeWithText("Nombre").performTextInput("Luna Lunera")
        // Las secciones se abren una a la vez: abrir "Edad y tamaño" para el peso
        compose.onNodeWithTag("seccion_Edad y tamaño").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Peso (opcional)") }
        compose.onNodeWithText("Peso (opcional)").performScrollTo().performTextInput("4,2")
        compose.onNodeWithText("Guardar cambios").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Luna Lunera") }
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Gato · 4,2 kg") }
        assertEquals(4200, repo.observar().first().single().pesoGramos)

        // Eliminar con confirmación: vuelve a la lista vacía
        compose.onNodeWithText("Eliminar mascota").performScrollTo().performClick()
        compose.onNodeWithText("Eliminar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Aún no hay mascotas") }
        assertTrue(repo.observar().first().isEmpty())
    }
}
