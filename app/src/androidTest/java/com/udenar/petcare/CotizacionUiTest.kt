package com.udenar.petcare

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.domain.modelo.ModoUso
import com.udenar.petcare.domain.profesionales.ContactoCompartido
import com.udenar.petcare.domain.profesionales.EstadoSolicitudPres
import com.udenar.petcare.domain.profesionales.ModalidadPrestador
import com.udenar.petcare.domain.profesionales.ModoPrecio
import com.udenar.petcare.domain.profesionales.PerfilPrestador
import com.udenar.petcare.domain.profesionales.Propuesta
import com.udenar.petcare.domain.profesionales.SaludCompartida
import com.udenar.petcare.domain.profesionales.ServicioPrestador
import com.udenar.petcare.domain.profesionales.UnidadPrecio
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** Parte 4.5: el dueño pide una cotización compartiendo datos y la acepta; el prestador cotiza. */
@HiltAndroidTest
class CotizacionUiTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var prestadores: PrestadoresFalso
    @Inject lateinit var cotizaciones: CotizacionesFalsas
    @Inject lateinit var modo: ModoMemoria
    @Inject lateinit var mascotas: RepositorioMascotas

    private val paseos = PerfilPrestador(
        id = "p1", nombre = "Paseos Felices", descripcion = "Paseos grupales", telefono = "3001234567", ciudad = "Pasto", zona = "Centro",
        lat = 1.21, lon = -77.28, modalidades = setOf(ModalidadPrestador.DOMICILIO), especies = setOf(Especie.PERRO), categorias = listOf("PASEADOR"),
        servicios = listOf(ServicioPrestador("s1", "PASEADOR", "Paseo de 1 hora", modo = ModoPrecio.FIJO, montoCop = 15000, unidad = UnidadPrecio.POR_PASEO)),
    )

    @Before
    fun preparar() {
        hilt.inject()
        compose.esperarInterfaz()
    }

    @Test
    fun elDuenoPideCotizacionCompartiendoSaludYAceptaLaPropuesta(): Unit = runBlocking {
        mascotas.guardar(Mascota("m1", "Max", Especie.PERRO, alergias = "Pollo"))
        prestadores.directorio += paseos
        compose.onNodeWithTag("pestana_servicios").performClick()
        compose.onNodeWithTag("tarjeta_prestadores").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("pres_Paseos Felices") }
        compose.onNodeWithTag("pres_Paseos Felices").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("solicitar_cotizacion") }
        compose.onNodeWithTag("solicitar_cotizacion").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("cotizar_servicio_Paseo de 1 hora") }
        compose.onNodeWithTag("cotizar_servicio_Paseo de 1 hora").performClick()
        compose.onNodeWithTag("cotizar_descripcion").performTextInput("Paseo por la tarde")
        compose.onNodeWithTag("compartir_salud").performScrollTo().performClick()
        compose.onNodeWithTag("enviar_cotizacion").performScrollTo().performClick()

        compose.waitUntil(5_000) { cotizaciones.solicitudes.size == 1 }
        val id = cotizaciones.solicitudes.single().id
        // Se compartió la alergia precargada de la ficha; el contacto no se marcó
        assertEquals("Pollo", cotizaciones.salud[id]?.alergias)
        assertFalse(cotizaciones.contacto.containsKey(id))

        // El prestador responde con una propuesta; el dueño la ve al volver a Servicios
        cotizaciones.cotizar(id, Propuesta(20000, "Incluye hidratación", null, null, null))
        compose.onNodeWithTag("pestana_hoy").performClick()
        compose.onNodeWithTag("pestana_servicios").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("solicitud_pres_Max") }
        compose.onNodeWithTag("solicitud_pres_Max").performScrollTo().performClick()
        compose.onNodeWithTag("propuesta").assertExists()
        compose.onNodeWithTag("confirmar_cotizacion").performClick()
        compose.waitUntil(5_000) { cotizaciones.solicitudes.single().estado == EstadoSolicitudPres.ACEPTADA }
    }

    @Test
    fun elPrestadorVeSoloLoCompartidoYCotiza(): Unit = runBlocking {
        prestadores.miPerfil.value = paseos
        val id = cotizaciones.solicitar(
            "m1", "p1", "PASEADOR", "s1", ModalidadPrestador.DOMICILIO, LocalDate.now().plusDays(1).atTime(LocalTime.of(16, 0)), null,
            "Paseo por la tarde", null, SaludCompartida(comportamiento = "Tira de la correa"), ContactoCompartido("3009998877", "Calle 5"),
        )
        modo.cambiar(ModoUso.PRESTADOR)
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("chip_modo_pres") }
        compose.onNodeWithTag("pestana_solicitudes_pres").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("solicitud_pres_Max") }
        compose.onNodeWithTag("solicitud_pres_Max").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("salud_compartida") }
        // Pendiente: ve la salud pero todavía no el contacto
        assertFalse(compose.onAllNodesWithTagExists("llamar_dueno"))
        compose.onNodeWithTag("cotizar_solicitud").performScrollTo().performClick()
        compose.onNodeWithTag("cotizacion_precio").performTextReplacement("20000")
        compose.onNodeWithTag("enviar_propuesta").performClick()
        compose.waitUntil(5_000) { cotizaciones.solicitudes.single().estado == EstadoSolicitudPres.COTIZADA }
        assertEquals(20000, cotizaciones.solicitudes.single().cotizacionPrecio)

        // Cuando el dueño acepta, aparece su contacto
        cotizaciones.confirmar(id)
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("pestana_agenda_pres") }
        compose.onNodeWithTag("pestana_agenda_pres").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("agenda_pres_Max") }
        compose.onNodeWithTag("agenda_pres_Max").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("llamar_dueno") }
    }
}
