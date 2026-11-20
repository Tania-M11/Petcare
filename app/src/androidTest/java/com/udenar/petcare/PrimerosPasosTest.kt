package com.udenar.petcare

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.udenar.petcare.domain.guia.Guia
import com.udenar.petcare.domain.guia.Intencion
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/** Parte 1.2: "¿Qué te trae a PetCare?" y la tarjeta Primeros pasos. */
@HiltAndroidTest
class PrimerosPasosTest {

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
    }

    @Test
    fun sinResponderPreguntaYArmaLaGuia() {
        cuenta.conAna(respondida = false)
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("¿Qué te trae a PetCare?") }

        compose.onNodeWithText("Elige al menos una").assertIsNotEnabled()
        compose.onNodeWithTag("intencion_MASCOTAS").performClick()
        compose.onNodeWithTag("intencion_VETERINARIO").performClick()
        compose.onNodeWithText("Continuar").performScrollTo().assertIsEnabled().performClick()

        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("primeros_pasos") }
        assertEquals(setOf(Intencion.MASCOTAS, Intencion.VETERINARIO), cuenta.usuario.value?.intenciones)
        compose.onNodeWithText("Agrega a tu primera mascota").assertExists()
        compose.onNodeWithText("Sube tu certificado").assertExists()
    }

    @Test
    fun omitirUnPasoAvanzaElProgreso() {
        cuenta.conAna(intenciones = setOf(Intencion.CUIDAR))
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("primeros_pasos") }
        compose.onNodeWithTag("progreso_guia").assertTextEquals("0 de 2")

        compose.onNodeWithTag("omitir_codigo_cuidar").performClick()
        compose.onNodeWithTag("progreso_guia").assertTextEquals("1 de 2")

        compose.onNodeWithTag("omitir_aprobacion").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("¡Todo listo!") }
        compose.onNodeWithText("¡Todo listo!").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Agrega a tu primera mascota") }
        assert(Guia.CERRADA in cuenta.usuario.value!!.pasosOmitidos)
    }

    @Test
    fun desdePerfilSePuedeCambiarLaRespuesta() {
        cuenta.conAna(intenciones = setOf(Intencion.MASCOTAS))
        compose.onNodeWithTag("pestana_perfil").performClick()
        compose.onNodeWithText("Tengo mascotas").assertExists() // resumen en la fila
        compose.onNodeWithText("¿Qué te trae a PetCare?").performClick()
        compose.onNodeWithTag("intencion_SERVICIOS").performClick()
        compose.onNodeWithText("Continuar").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Tengo mascotas · Ofrezco servicios") }
    }
}
