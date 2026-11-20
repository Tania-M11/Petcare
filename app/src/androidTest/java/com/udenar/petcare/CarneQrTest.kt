package com.udenar.petcare

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.domain.modelo.VeterinarioManual
import com.udenar.petcare.ui.compartir.ResultadoQrScreen
import com.udenar.petcare.ui.theme.PetcareTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/** Parte 3.3: carné con QR, veterinario con Llamar (RF-04) y lectura de QR (RF-19). */
@HiltAndroidTest
class CarneQrTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var mascotas: RepositorioMascotas

    @Before
    fun preparar() {
        hilt.inject()
        compose.esperarInterfaz()
        runBlocking {
            mascotas.guardar(
                Mascota(
                    "m1", "Luna", Especie.GATO, raza = "Criolla", alergias = "Pollo",
                    veterinario = VeterinarioManual(nombre = "Dra. Ruiz", telefono = "3001234567", clinica = "Vet Pasto"),
                ),
            )
        }
        compose.onNodeWithTag("pestana_mascotas").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("mascota_Luna") }
        compose.onNodeWithTag("mascota_Luna").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("ver_carne") }
    }

    @Test
    fun veterinarioConBotonLlamar() {
        compose.onNodeWithText("Dra. Ruiz").performScrollTo()
        compose.onNodeWithText("Llamar 3001234567").assertExists()
    }

    @Test
    fun carneMuestraQrYPermiteElegirCampos() {
        compose.onNodeWithTag("ver_carne").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("imagen_qr") }
        compose.onNodeWithText("Pollo").assertExists()
        compose.onNodeWithTag("campo_qr_RAZA").performScrollTo().performClick()
        compose.onNodeWithTag("imagen_qr").assertExists()
    }
}

/** El resultado de leer un QR, sin cámara. */
class ResultadoQrTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun qrDePetcareConCodigoOfreceCuidar() {
        var pedido: String? = null
        compose.setContent {
            PetcareTheme {
                ResultadoQrScreen("PETCARE v1\nNombre: Luna\nAlergias: Pollo\nCodigo: ABCD2345", onVolver = {}, onQuieroCuidar = { pedido = it })
            }
        }
        compose.onNodeWithTag("dato_qr_Alergias").assertExists()
        compose.onNodeWithText("Quiero cuidar esta mascota").performClick()
        assertEquals("ABCD2345", pedido)
    }

    @Test
    fun qrAjenoMuestraElTextoCrudo() {
        compose.setContent { PetcareTheme { ResultadoQrScreen("https://ejemplo.com", onVolver = {}, onQuieroCuidar = {}) } }
        compose.onNodeWithTag("texto_crudo").assertExists()
    }
}
