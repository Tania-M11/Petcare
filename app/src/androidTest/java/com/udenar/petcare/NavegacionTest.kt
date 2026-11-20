package com.udenar.petcare

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Parte 0.1: la barra inferior del modo personal abre cada sección. */
@HiltAndroidTest
class NavegacionTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun inyectar() = hilt.inject()

    @Test
    fun abreEnInicio() {
        compose.onNodeWithText("Inicio").assertIsSelected()
        compose.onNodeWithText("Aquí verás los cuidados de hoy de tus mascotas.").assertExists()
    }

    @Test
    fun cadaPestanaMuestraSuSeccion() {
        val esperado = mapOf(
            "Mascotas" to "Tus mascotas y las que cuidas (parte 1.3).",
            "Calendario" to "Vista mensual de cuidados (parte 1.12).",
            "Explorar" to "Veterinarios y prestadores (fases 4 y 5).",
            "Perfil" to "Tu cuenta, perfiles profesionales y modos (partes 1.1 y 4.1).",
        )
        esperado.forEach { (pestana, texto) ->
            compose.onNodeWithText(pestana, useUnmergedTree = true).performClick()
            compose.onNodeWithText(texto).assertExists()
        }
        compose.onNodeWithText("Inicio", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Aquí verás los cuidados de hoy de tus mascotas.").assertExists()
    }
}
