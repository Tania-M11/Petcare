package com.udenar.petcare

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
    fun preparar() = hilt.inject()

    private fun abrirFormulario() {
        compose.onNodeWithTag("pestana_mascotas").performClick()
        compose.onNodeWithTag("boton_mas").performClick()
        compose.onNodeWithText("Agregar mascota").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Nueva mascota") }
    }

    @Test
    fun formularioValidaLoObligatorio() {
        abrirFormulario()
        compose.onNodeWithText("Agregar mascota").performScrollTo().performClick()
        compose.onNodeWithText("Escribe su nombre").assertExists()
        compose.onNodeWithText("Elige su especie").assertExists()
    }

    @Test
    fun agregarEditarYEliminar() = runBlocking {
        abrirFormulario()
        compose.onNodeWithText("Nombre").performTextInput("Luna")
        compose.onNodeWithTag("especie_GATO").performClick()
        compose.onNodeWithText("Peso (opcional)").performScrollTo().performTextInput("4,2")
        compose.onNodeWithText("Agregar mascota").performScrollTo().performClick()

        // Queda guardada en el teléfono, pendiente de subir, y se pidió sincronizar
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Gato · 4,2 kg") }
        val guardada = repo.observar().first().single()
        assertEquals(4200, guardada.pesoGramos)
        assertTrue(guardada.porSubir)
        assertTrue(programador.solicitudes > 0)

        // Editar el nombre desde la ficha
        compose.onNodeWithText("Editar ficha").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Guardar cambios") }
        compose.onNodeWithText("Luna").performTextClearance()
        compose.onNodeWithText("Nombre").performTextInput("Luna Lunera")
        compose.onNodeWithText("Guardar cambios").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Luna Lunera") }

        // Eliminar con confirmación: vuelve a la lista vacía
        compose.onNodeWithText("Eliminar mascota").performScrollTo().performClick()
        compose.onNodeWithText("Eliminar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Aún no hay mascotas") }
        assertTrue(repo.observar().first().isEmpty())
    }
}
