package com.udenar.petcare

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Parte 0.2: las 4 pestañas del modo personal y los accesos de Hoy. */
@HiltAndroidTest
class NavegacionTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun inyectar() = hilt.inject()

    @Test
    fun abreEnHoy() {
        compose.onNodeWithTag("pestana_hoy").assertIsSelected()
        compose.onNodeWithText("Pendientes de hoy").assertExists()
        compose.onNodeWithText("Agrega a tu primera mascota").assertExists()
    }

    @Test
    fun cadaPestanaMuestraSuSeccion() {
        compose.onNodeWithTag("pestana_mascotas").performClick()
        compose.onNodeWithText("Aún no hay mascotas").assertExists()

        compose.onNodeWithTag("pestana_servicios").performClick()
        compose.onNodeWithText("Veterinarios").assertExists()

        compose.onNodeWithTag("pestana_perfil").performClick()
        compose.onNodeWithText("Trabaja con PetCare").assertExists()

        compose.onNodeWithTag("pestana_hoy").performClick()
        compose.onNodeWithText("Pendientes de hoy").assertExists()
    }

    @Test
    fun tarjetaDePrimerosPasosLlevaAMascotas() {
        compose.onNodeWithText("Agrega a tu primera mascota").performClick()
        compose.onNodeWithTag("pestana_mascotas").assertIsSelected()
    }

    @Test
    fun menuMasDeMascotasMuestraLasTresOpciones() {
        compose.onNodeWithTag("pestana_mascotas").performClick()
        compose.onNodeWithText("Aún no hay mascotas").performClick()
        compose.onNodeWithText("Agregar mascota").assertExists()
        compose.onNodeWithText("Cuidar con código").assertExists()
        compose.onNodeWithText("Escanear QR").assertExists()
    }
}
