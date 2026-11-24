package com.udenar.petcare

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
    @Inject lateinit var veterinarios: VeterinariosFalso

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

    @Test
    fun capturasProfesionales() {
        hilt.inject()
        compose.runOnUiThread {
            compose.activity.setShowWhenLocked(true)
            compose.activity.setTurnScreenOn(true)
        }
        compose.esperarInterfaz()
        runBlocking { mascotas.guardar(Mascota("m1", "Venus", Especie.PERRO)) }
        val base = com.udenar.petcare.domain.profesionales.PerfilVeterinario(
            id = "v1", nombre = "Laura Gómez", especies = setOf(Especie.PERRO, Especie.GATO),
            servicios = setOf(com.udenar.petcare.domain.profesionales.ServicioVet.CONSULTA, com.udenar.petcare.domain.profesionales.ServicioVet.VACUNACION),
            telefono = "3001234567", ciudad = "Pasto", direccion = "Calle 18", lat = 1.2136, lon = -77.2811,
            verificacion = com.udenar.petcare.domain.profesionales.Verificacion.VERIFICADO, clinica = "Clínica Huellitas",
        )
        veterinarios.directorio += base
        veterinarios.directorio += base.copy(id = "v2", nombre = "Pedro Ruiz", lat = 1.2085, lon = -77.2760, domicilio = true, verificacion = com.udenar.petcare.domain.profesionales.Verificacion.SIN_CERTIFICADO, clinica = null)
        compose.onNodeWithTag("pestana_servicios").performClick()
        compose.onNodeWithTag("tarjeta_veterinarios").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("vet_Laura Gómez") }
        guardar("10_directorio")
        compose.onNodeWithTag("alternar_mapa").performClick()
        Thread.sleep(4_000)
        guardar("11_mapa")
        // La superficie del mapa no sale en la captura sobre la pantalla de bloqueo: se guardan los marcadores aparte
        val dir = File(compose.activity.getExternalFilesDir(null), "capturas")
        listOf(com.udenar.petcare.domain.modelo.Icono.VETERINARIO to 0xFF5B89BA.toInt(), com.udenar.petcare.domain.modelo.Icono.TIJERAS to 0xFFF08A4B.toInt(), com.udenar.petcare.domain.modelo.Icono.CASA to 0xFFA98EE0.toInt()).forEachIndexed { i, (ic, c) ->
            File(dir, "15_marcador_$i.png").outputStream().use { com.udenar.petcare.ui.mapas.imagenMarcador(compose.activity, ic, c).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithTag("alternar_mapa").performClick()
        compose.onNodeWithTag("vet_Laura Gómez").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("solicitar_servicio") }
        compose.onNodeWithTag("solicitar_servicio").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("servicio_CONSULTA") }
        compose.onNodeWithTag("servicio_CONSULTA").performClick()
        guardar("12_cita_servicio")
        compose.onNodeWithTag("continuar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("hora_9") }
        guardar("13_cita_cuando")
        compose.onNodeWithTag("continuar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("agregar_foto_solicitud") }
        guardar("14_cita_fotos")
    }

    @Test
    fun capturasPantallas() {
        hilt.inject()
        compose.runOnUiThread {
            compose.activity.setShowWhenLocked(true)
            compose.activity.setTurnScreenOn(true)
        }
        compose.esperarInterfaz()
        val hoy = LocalDate.now()
        runBlocking {
            mascotas.guardar(Mascota("m1", "Apolo", Especie.GATO, raza = "Criollo"))
            mascotas.guardar(Mascota("m2", "Venus", Especie.PERRO))
            cuidados.guardarEvento(EventoCuidado("e2", "m2", TipoCuidado.COMIDA, "Desayuno", Frecuencia(TipoFrecuencia.DIARIO, horas = listOf(LocalTime.of(7, 0))), fechaInicio = hoy.minusDays(10)))
            cuidados.guardarEvento(EventoCuidado("e3", "m2", TipoCuidado.VACUNA, "Rabia", Frecuencia(TipoFrecuencia.UNICO), fechaInicio = hoy.plusDays(3), avisoAnticipadoDias = 7))
        }
        compose.onNodeWithTag("pestana_mascotas").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("mascota_Venus") }
        guardar("20_mascotas")
        compose.onNodeWithTag("mascota_Venus").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("seccion_Cuidados") }
        guardar("21_ficha_resumen")
        compose.onRoot().performTouchInput { swipeUp() }
        guardar("22_ficha_resumen_abajo")
        compose.onNodeWithTag("seccion_Cuidados").performClick()
        guardar("23_ficha_cuidados")
        compose.onNodeWithTag("seccion_Salud").performClick()
        guardar("24_ficha_salud")
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("pestana_servicios").performClick()
        guardar("25_servicios")
        compose.onNodeWithTag("pestana_perfil").performClick()
        guardar("26_perfil")
    }
}

