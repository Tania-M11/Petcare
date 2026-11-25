package com.udenar.petcare

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.ModoUso
import com.udenar.petcare.domain.profesionales.ModalidadPrestador
import com.udenar.petcare.domain.profesionales.ModoPrecio
import com.udenar.petcare.domain.profesionales.PerfilPrestador
import com.udenar.petcare.domain.profesionales.ServicioPrestador
import com.udenar.petcare.domain.profesionales.UnidadPrecio
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/** Parte 4.4: activar el perfil de prestador y buscar en su directorio. */
@HiltAndroidTest
class PrestadorUiTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var prestadores: PrestadoresFalso
    @Inject lateinit var modo: ModoMemoria

    @Before
    fun preparar() {
        hilt.inject()
        compose.esperarInterfaz()
    }

    @Test
    fun activarPerfilConUnServicioYPausarSolicitudes() {
        compose.onNodeWithTag("pestana_perfil").performClick()
        // Desde "Cuidador y paseador": la categoría llega ya elegida
        compose.onNodeWithTag("perfil_pro_cuidador").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("pres_nombre") }
        compose.onNodeWithTag("continuar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("pres_categoria_PASEADOR") }
        compose.onNodeWithText("Principal").assertExists()
        compose.onNodeWithTag("continuar").performClick()
        // Servicios y precios
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("pres_agregar_servicio") }
        compose.onNodeWithTag("pres_agregar_servicio").performClick()
        compose.onNodeWithTag("servicio_nombre").performTextInput("Paseo de 1 hora")
        compose.onNodeWithTag("modo_FIJO").performClick()
        compose.onNodeWithTag("servicio_monto").performTextInput("15000")
        compose.onNodeWithTag("servicio_guardar").performClick()
        compose.onNodeWithTag("pres_servicio_Paseo de 1 hora").assertExists()
        compose.onNodeWithTag("continuar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("pres_especie_PERRO") }
        compose.onNodeWithTag("pres_especie_PERRO").performClick()
        compose.onNodeWithTag("continuar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("pres_telefono") }
        compose.onNodeWithTag("pres_telefono").performTextInput("300 123 4567")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("continuar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("pres_ciudad") }
        compose.onNodeWithTag("pres_ciudad").performTextInput("Pasto")
        compose.onNodeWithTag("pres_zona").performTextInput("Centro")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("pres_ubicar").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("vet_usar_punto") }
        compose.onNodeWithTag("vet_usar_punto").performClick()
        compose.onNodeWithTag("continuar").performClick()
        // Horario: se puede saltar
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("asistente_saltar") }
        compose.onNodeWithTag("asistente_saltar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("pres_descripcion") }
        compose.onNodeWithTag("pres_descripcion").performTextInput("Paseos tranquilos por el parque")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("continuar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("pres_guardar") }
        compose.onNodeWithTag("pres_guardar").performClick()

        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("chip_modo_pres") }
        assertEquals(ModoUso.PRESTADOR, modo.modo.value)
        val p = prestadores.miPerfil.value!!
        assertEquals(listOf("PASEADOR"), p.categorias)
        assertEquals(15000, p.servicios.single().montoCop)
        compose.onNodeWithText("$15.000").assertExists()

        compose.onNodeWithTag("interruptor_recibiendo").performClick()
        compose.waitUntil(5_000) { prestadores.miPerfil.value?.recibiendo == false }
        compose.onNodeWithTag("volver_personal_pres").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("pestana_hoy") }
        compose.onNodeWithTag("pestana_perfil").performClick()
        compose.onNodeWithTag("tarjeta_modo_pres").assertExists()
        assertFalse(prestadores.miPerfil.value!!.recibiendo)
    }

    @Test
    fun directorioFiltraPorCategoriaYMuestraElPerfil() {
        val base = PerfilPrestador(
            id = "p1", nombre = "Paseos Felices", descripcion = "Paseos grupales", telefono = "3001234567", ciudad = "Pasto", zona = "Centro",
            lat = 1.21, lon = -77.28, modalidades = setOf(ModalidadPrestador.DOMICILIO), especies = setOf(Especie.PERRO), categorias = listOf("PASEADOR"),
            servicios = listOf(ServicioPrestador("s1", "PASEADOR", "Paseo de 1 hora", modo = ModoPrecio.FIJO, montoCop = 15000, unidad = UnidadPrecio.POR_PASEO)),
        )
        prestadores.directorio += base
        prestadores.directorio += base.copy(id = "p2", nombre = "Spa Canino", categorias = listOf("BANO_PELUQUERIA"), servicios = emptyList(), recibiendo = false)

        compose.onNodeWithTag("pestana_servicios").performClick()
        compose.onNodeWithTag("tarjeta_prestadores").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("pres_Spa Canino") }
        compose.onNodeWithText("En pausa").assertExists()
        compose.onNodeWithTag("filtro_cat_PASEADOR").performClick()
        compose.waitUntil(5_000) { !compose.onAllNodesWithTagExists("pres_Spa Canino") }
        compose.onNodeWithTag("pres_Paseos Felices").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("cabecera_pres") }
        compose.onNodeWithText("$15.000 por paseo").performScrollTo().assertExists()
        compose.onNodeWithTag("llamar_pres").assertExists()
    }
}
