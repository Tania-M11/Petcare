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
import com.udenar.petcare.domain.modelo.ModoUso
import com.udenar.petcare.domain.profesionales.EstadoSolicitudVet
import com.udenar.petcare.domain.profesionales.Modalidad
import com.udenar.petcare.domain.profesionales.PerfilVeterinario
import com.udenar.petcare.domain.profesionales.ServicioVet
import com.udenar.petcare.domain.profesionales.SolicitudVet
import com.udenar.petcare.domain.profesionales.Verificacion
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** Parte 4.3: el dueño pide y cancela una cita; el veterinario la acepta y registra una visita. */
@HiltAndroidTest
class SolicitudVetUiTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var veterinarios: VeterinariosFalso
    @Inject lateinit var atencion: AtencionVetFalsa
    @Inject lateinit var modo: ModoMemoria
    @Inject lateinit var mascotas: RepositorioMascotas

    private val laura = PerfilVeterinario(
        id = "v1", nombre = "Laura Gómez", especies = setOf(Especie.PERRO), servicios = setOf(ServicioVet.CONSULTA, ServicioVet.VACUNACION),
        telefono = "3001234567", ciudad = "Pasto", direccion = "Calle 18", lat = 1.21, lon = -77.28, verificacion = Verificacion.VERIFICADO,
    )
    private val max = Mascota("m1", "Max", Especie.PERRO)

    @Before
    fun preparar() {
        hilt.inject()
        compose.esperarInterfaz()
    }

    @Test
    fun elDuenoPideUnaCitaYLaCancela(): Unit = runBlocking {
        mascotas.guardar(max)
        veterinarios.directorio += laura
        compose.onNodeWithTag("pestana_servicios").performClick()
        compose.onNodeWithTag("tarjeta_veterinarios").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("vet_Laura Gómez") }
        compose.onNodeWithTag("vet_Laura Gómez").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("solicitar_servicio") }
        compose.onNodeWithTag("solicitar_servicio").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("servicio_CONSULTA") }
        compose.onNodeWithTag("servicio_CONSULTA").performClick()
        compose.onNodeWithTag("enviar_solicitud").performScrollTo().performClick()

        // De vuelta en Servicios: aparece en "Mis solicitudes"
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("solicitud_Max") }
        val s = atencion.solicitudes.single()
        assertEquals("m1", s.mascotaId)
        assertEquals(ServicioVet.CONSULTA, s.servicio)
        compose.onNodeWithTag("solicitud_Max").performClick()
        compose.onNodeWithTag("cancelar_solicitud").performClick()
        compose.waitUntil(5_000) { atencion.solicitudes.single().estado == EstadoSolicitudVet.CANCELADA }
    }

    @Test
    fun elVeterinarioAceptaVeLaAgendaYRegistraUnaVisita() {
        veterinarios.miPerfil.value = laura
        atencion.pacientes += max
        atencion.solicitudes += SolicitudVet(
            "s1", "m1", "Max", Especie.PERRO, "Ana María", "3001112233", "v1", "Laura Gómez", ServicioVet.CONSULTA, Modalidad.CONSULTORIO,
            LocalDate.now().plusDays(1).atTime(LocalTime.of(10, 0)), mensaje = "Tiene tos",
        )
        modo.cambiar(ModoUso.VETERINARIO)
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("chip_modo_vet") }

        compose.onNodeWithTag("pestana_solicitudes").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("solicitud_Max") }
        compose.onNodeWithTag("solicitud_Max").performClick()
        compose.onNodeWithText("“Tiene tos”").assertExists()
        compose.onNodeWithTag("aceptar_solicitud").performScrollTo().performClick()
        compose.onNodeWithTag("confirmar_aceptar").performClick()
        compose.waitUntil(5_000) { atencion.solicitudes.single().estado == EstadoSolicitudVet.ACEPTADA }

        compose.onNodeWithTag("pestana_agenda").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("cita_Max") }

        compose.onNodeWithTag("pestana_pacientes").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("paciente_Max") }
        compose.onNodeWithTag("paciente_Max").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("nueva_visita_vet") }
        compose.onNodeWithTag("nueva_visita_vet").performClick()
        compose.onNodeWithTag("visita_motivo").performTextInput("Control de tos")
        compose.onNodeWithTag("guardar_visita_vet").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Control de tos") }
        assertEquals(listOf("Control de tos"), atencion.visitas)
    }
}
