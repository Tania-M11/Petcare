package com.udenar.petcare

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.domain.paseos.RepositorioPaseos
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

/** Parte 3.4: registrar un paseo sin GPS desde la ficha (el GPS se prueba en la calle). */
@HiltAndroidTest
class PaseoUiTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var mascotas: RepositorioMascotas
    @Inject lateinit var paseos: RepositorioPaseos

    @Before
    fun preparar() {
        hilt.inject()
        compose.esperarInterfaz()
        runBlocking { mascotas.guardar(Mascota("m1", "Max", Especie.PERRO)) }
    }

    @Test
    fun paseoManualQuedaEnLaFichaYElHistorial(): Unit = runBlocking {
        compose.onNodeWithTag("pestana_mascotas").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("mascota_Max") }
        compose.onNodeWithTag("mascota_Max").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("seccion_Cuidados") }
        compose.onNodeWithTag("seccion_Cuidados").performClick()
        compose.onNodeWithTag("pasear_ahora").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("paseo_manual") }
        compose.onNodeWithTag("paseo_manual").performClick()
        compose.onNodeWithText("Duración (minutos)").performTextInput("30")
        compose.onNodeWithText("Distancia en km (opcional)").performTextInput("2,5")
        compose.onNodeWithTag("guardar_paseo").performClick()

        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("paseo_registrado") }
        val p = paseos.observar("m1").first().single()
        assertEquals(1800, p.duracionS)
        assertEquals(2500, p.distanciaM)
        assertTrue(p.esManual)
        compose.onNodeWithText("2,5 km · 30 min").assertExists()
    }

    @Test
    fun elDetalleDeUnPaseoConGpsMuestraElMapa(): Unit = runBlocking {
        val ruta = listOf(com.udenar.petcare.domain.paseos.Coordenada(1.2136, -77.2811), com.udenar.petcare.domain.paseos.Coordenada(1.2150, -77.2800))
        paseos.guardarGps(com.udenar.petcare.domain.paseos.ResumenPaseo(listOf("m1"), java.time.Instant.now().minusSeconds(600), java.time.Instant.now(), 600, 180, ruta))
        compose.onNodeWithTag("pestana_mascotas").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("mascota_Max") }
        compose.onNodeWithTag("mascota_Max").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("seccion_Cuidados") }
        compose.onNodeWithTag("seccion_Cuidados").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("paseo_registrado") }
        compose.onNodeWithTag("paseo_registrado").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("mapa_paseo") }
        Thread.sleep(3_000) // deja que MapLibre cargue el estilo y dibuje la ruta
        compose.onNodeWithText("180 m").assertExists()
    }
}
