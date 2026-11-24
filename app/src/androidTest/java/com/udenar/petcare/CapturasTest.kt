package com.udenar.petcare

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.Frecuencia
import com.udenar.petcare.domain.cuidados.RepositorioCuidados
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.cuidados.TipoFrecuencia
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.Mascota
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * Capturas de pantalla para revisar el diseño (no verifica nada). Se corre a mano con
 * `adb shell am instrument -e class com.udenar.petcare.CapturasTest ...` y las imágenes quedan en
 * Android/data/com.udenar.petcare/files/capturas.
 */
@HiltAndroidTest
class CapturasTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var mascotas: RepositorioMascotas
    @Inject lateinit var cuidados: RepositorioCuidados

    private fun guardar(nombre: String) {
        compose.waitForIdle()
        Thread.sleep(1_200)
        compose.waitForIdle()
        val img = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(compose.activity.getExternalFilesDir(null), "capturas").apply { mkdirs() }
        File(dir, "$nombre.png").outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun capturas() {
        hilt.inject()
        compose.runOnUiThread {
            compose.activity.setShowWhenLocked(true)
            compose.activity.setTurnScreenOn(true)
        }
        compose.esperarInterfaz()
        val hoy = LocalDate.now()
        runBlocking {
            mascotas.guardar(Mascota("m1", "Apolo", Especie.GATO))
            mascotas.guardar(Mascota("m2", "Venus", Especie.PERRO))
            cuidados.guardarEvento(EventoCuidado("e1", "m1", TipoCuidado.MEDICAMENTO, "Gotas", Frecuencia(TipoFrecuencia.DIARIO, horas = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))), fechaInicio = hoy))
            cuidados.guardarEvento(EventoCuidado("e2", "m2", TipoCuidado.COMIDA, "Desayuno", Frecuencia(TipoFrecuencia.DIARIO, horas = listOf(LocalTime.of(7, 0))), fechaInicio = hoy))
            cuidados.guardarEvento(EventoCuidado("e3", "m2", TipoCuidado.VACUNA, "Rabia", Frecuencia(TipoFrecuencia.UNICO), fechaInicio = hoy.plusDays(3), avisoAnticipadoDias = 7))
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("3 pendientes") }
        guardar("01_hoy")
        compose.onRoot().performTouchInput { swipeUp() }
        guardar("02_hoy_abajo")
        compose.onNodeWithTag("nuevo_cuidado").performClick()
        guardar("03_registrar")
    }
}
