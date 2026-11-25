package com.udenar.petcare

import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.ModoUso
import com.udenar.petcare.domain.profesionales.PerfilVeterinario
import com.udenar.petcare.domain.profesionales.ServicioVet
import com.udenar.petcare.domain.profesionales.Verificacion
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/** Partes 4.1 y 4.2: activar el perfil, cambiar de modo y buscar en el directorio. */
@HiltAndroidTest
class VeterinarioUiTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var veterinarios: VeterinariosFalso
    @Inject lateinit var modo: ModoMemoria

    @Before
    fun preparar() {
        hilt.inject()
        compose.esperarInterfaz()
    }

    @Test
    fun activarPerfilLlevaAlModoVeterinarioYSePuedeVolver() {
        compose.onNodeWithTag("pestana_perfil").performClick()
        compose.onNodeWithTag("perfil_pro_vet").performScrollTo().performClick()
        // Paso 1: nombre y foto, con la vista previa de la tarjeta del directorio (Fase 6.7)
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("vet_nombre") }
        compose.onNodeWithTag("vista_previa").assertExists()
        compose.onNodeWithTag("elegir_foto_perfil").assertExists()
        compose.onNodeWithTag("vet_nombre").performTextClearance()
        compose.onNodeWithTag("vet_nombre").performTextInput("Ana María Paz")
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("vet_Ana María Paz") } // la tarjeta cambia al escribir
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("continuar").performClick()
        // Especies: sin elegir no deja avanzar
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("vet_especie_PERRO") }
        compose.onNodeWithTag("continuar").performClick()
        compose.onAllNodesWithText("Elige al menos una especie")[0].assertExists()
        compose.onNodeWithTag("vet_especie_PERRO").performClick()
        compose.onNodeWithTag("continuar").performClick()
        // Servicios (consulta ya marcada)
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("vet_servicio_CONSULTA") }
        compose.onNodeWithTag("continuar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("vet_telefono") }
        compose.onNodeWithTag("vet_telefono").performTextInput("300 123 4567")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("continuar").performClick()
        // Horario con dos toques
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("turno_Mañana") }
        compose.onNodeWithTag("turno_Mañana").performScrollTo().performClick()
        compose.onNodeWithTag("continuar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("vet_ciudad") }
        compose.onNodeWithTag("vet_ciudad").performTextInput("Pasto")
        compose.onNodeWithTag("vet_direccion").performTextInput("Calle 18 # 25-30")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("vet_ubicar").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("vet_usar_punto") }
        compose.onNodeWithTag("vet_usar_punto").performClick()
        compose.onNodeWithText("Consultorio ubicado en el mapa ✓").assertExists()
        compose.onNodeWithTag("continuar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("asistente_saltar") }
        compose.onNodeWithTag("continuar").performClick()
        // Resumen: horario resumido y activar
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("vet_guardar") }
        compose.onNodeWithText("Lun a vie · 8:00-12:00").assertExists()
        compose.onNodeWithTag("vet_guardar").performClick()

        // Modo veterinario: pestaña Perfil con la verificación pendiente
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("chip_modo_vet") }
        assertEquals(ModoUso.VETERINARIO, modo.modo.value)
        assertEquals("Ana María Paz", veterinarios.miPerfil.value!!.nombre)
        compose.onNodeWithTag("mensaje_verificacion").assertExists()
        compose.onNodeWithText(Verificacion.SIN_CERTIFICADO.mensaje).assertExists()
        compose.onNodeWithTag("pestana_solicitudes").performClick()
        compose.onNodeWithText("Aún no tienes solicitudes").assertExists()

        // De vuelta al modo personal, Perfil ofrece cambiar de modo
        compose.onNodeWithTag("pestana_perfil_vet").performClick()
        compose.onNodeWithTag("volver_personal").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("pestana_hoy") }
        compose.onNodeWithTag("pestana_perfil").performClick()
        compose.onNodeWithTag("tarjeta_modo_vet").assertExists()
    }

    @Test
    fun directorioFiltraPorVerificadosYMuestraElDetalle() {
        val base = PerfilVeterinario(
            id = "v1", nombre = "Laura Gómez", especies = setOf(Especie.PERRO, Especie.GATO), servicios = setOf(ServicioVet.CONSULTA),
            telefono = "3001234567", ciudad = "Pasto", direccion = "Calle 18", lat = 1.21, lon = -77.28, verificacion = Verificacion.VERIFICADO,
        )
        veterinarios.directorio += base
        veterinarios.directorio += base.copy(id = "v2", nombre = "Pedro Ruiz", ciudad = "Ipiales", verificacion = Verificacion.EN_REVISION)

        compose.onNodeWithTag("pestana_servicios").performClick()
        compose.onNodeWithTag("tarjeta_veterinarios").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("vet_Pedro Ruiz") }
        compose.onNodeWithTag("vet_Laura Gómez").assertExists()
        compose.onNodeWithText("No verificado").assertExists()

        compose.onNodeWithTag("filtro_verificados").performClick()
        compose.waitUntil(5_000) { !compose.onAllNodesWithTagExists("vet_Pedro Ruiz") }
        compose.onNodeWithTag("vet_Laura Gómez").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("cabecera_vet") }
        compose.onNodeWithText("Verificado ✓").assertExists()
        compose.onNodeWithTag("llamar_vet").assertExists()
    }
}
