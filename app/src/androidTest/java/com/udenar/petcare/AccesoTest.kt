package com.udenar.petcare

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/** Parte 1.1: bienvenida, registro, inicio y cierre de sesión (con la cuenta falsa). */
@HiltAndroidTest
class AccesoTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var cuenta: CuentaFalsa

    @Before
    fun preparar() {
        hilt.inject()
        compose.esperarInterfaz()
        cuenta.sinSesion()
        compose.waitForIdle()
    }

    @Test
    fun sinSesionMuestraBienvenida() {
        compose.onNodeWithText("Cuidar juntos es más fácil").assertExists()
        compose.onNodeWithText("Crear cuenta").assertExists()
        compose.onNodeWithText("Ya tengo cuenta").assertExists()
    }

    @Test
    fun registroExigeAceptarPrivacidad() {
        compose.onNodeWithText("Crear cuenta").performClick()
        compose.onNodeWithText("Nombre").performTextInput("Tania")
        compose.onNodeWithText("Correo").performTextInput("tania@petcare.test")
        compose.onNodeWithText("Contraseña (mínimo 8)").performTextInput("clave1234")
        compose.onNodeWithText("Crear cuenta").performClick()
        compose.onNodeWithText("Debes aceptar la política de privacidad").assertExists()

        compose.onNodeWithTag("casilla_privacidad").performClick()
        compose.onNodeWithText("Crear cuenta").performClick()
        // Recién registrada: primero "¿Qué te trae a PetCare?"
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("¿Qué te trae a PetCare?") }
        compose.onNodeWithText("Ahora no").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("pestana_hoy") }
        compose.onNodeWithText("Tania").assertExists() // saludo en Hoy
    }

    @Test
    fun correoExistenteMuestraError() {
        compose.onNodeWithText("Crear cuenta").performClick()
        compose.onNodeWithText("Nombre").performTextInput("Otra Ana")
        compose.onNodeWithText("Correo").performTextInput("ana@petcare.test")
        compose.onNodeWithText("Contraseña (mínimo 8)").performTextInput("clave1234")
        compose.onNodeWithTag("casilla_privacidad").performClick()
        compose.onNodeWithText("Crear cuenta").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("error_general") }
        compose.onNodeWithText("Ya existe una cuenta con ese correo.").assertExists()
    }

    @Test
    fun inicioDeSesionYCierre() {
        compose.onNodeWithText("Ya tengo cuenta").performClick()
        compose.onNodeWithText("Correo").performTextInput("ana@petcare.test")
        compose.onNodeWithText("Contraseña").performTextInput("mala")
        compose.onNodeWithText("Entrar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("error_general") }
        compose.onNodeWithText("Correo o contraseña incorrectos.").assertExists()

        compose.onNodeWithText("Contraseña").performTextClearance()
        compose.onNodeWithText("Contraseña").performTextInput("clave1234")
        compose.onNodeWithText("Entrar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("pestana_hoy") }
        compose.onNodeWithText("Ana").assertExists()

        compose.onNodeWithTag("pestana_perfil").performClick()
        compose.onNodeWithText("ana@petcare.test").assertExists()
        compose.onNodeWithText("Cerrar sesión").performClick()
        compose.onNodeWithText("¿Cerrar sesión?").assertExists()
        compose.onAllNodesWithTextFirst("Cerrar sesión", 1).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Cuidar juntos es más fácil") }
    }
}
