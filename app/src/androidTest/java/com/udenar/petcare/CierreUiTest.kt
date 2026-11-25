package com.udenar.petcare

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.udenar.petcare.data.avisos.Apertura
import com.udenar.petcare.data.avisos.AperturaAvisos
import com.udenar.petcare.data.avisos.DestinoAviso
import com.udenar.petcare.data.cuenta.RecuperacionClave
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.ModoUso
import com.udenar.petcare.domain.profesionales.EstadoSolicitudVet
import com.udenar.petcare.domain.profesionales.Modalidad
import com.udenar.petcare.domain.profesionales.PerfilVeterinario
import com.udenar.petcare.domain.profesionales.Resena
import com.udenar.petcare.domain.profesionales.ServicioVet
import com.udenar.petcare.domain.profesionales.SolicitudVet
import com.udenar.petcare.domain.profesionales.TipoReporte
import com.udenar.petcare.domain.profesionales.Verificacion
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** Fase 5: reseñas, reportes, avisos que abren el modo correcto y la cuenta (recuperar, confirmar, eliminar). */
@HiltAndroidTest
class CierreUiTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var cuenta: CuentaFalsa
    @Inject lateinit var atencion: AtencionVetFalsa
    @Inject lateinit var resenas: ResenasFalsas
    @Inject lateinit var veterinarios: VeterinariosFalso
    @Inject lateinit var apertura: AperturaAvisos
    @Inject lateinit var recuperacion: RecuperacionClave

    private val laura = PerfilVeterinario(
        id = "v1", nombre = "Laura Gómez", especies = setOf(Especie.PERRO), servicios = setOf(ServicioVet.CONSULTA),
        telefono = "3001234567", ciudad = "Pasto", direccion = "Calle 18", lat = 1.21, lon = -77.28, verificacion = Verificacion.VERIFICADO,
        ratingSuma = 9, ratingCantidad = 2,
    )

    @Before
    fun preparar() {
        hilt.inject()
        compose.esperarInterfaz()
    }

    @Test
    fun calificarUnaCitaCompletada() {
        atencion.solicitudes += SolicitudVet(
            "s1", "m1", "Max", Especie.PERRO, "Ana María", null, "v1", "Laura Gómez", ServicioVet.CONSULTA, Modalidad.CONSULTORIO,
            LocalDate.now().minusDays(1).atTime(LocalTime.of(10, 0)), estado = EstadoSolicitudVet.COMPLETADA,
        )
        compose.onNodeWithTag("pestana_servicios").performClick()
        // Completada y sin reseña: aparece en "Mis solicitudes" para calificarla
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("solicitud_Max") }
        compose.onNodeWithTag("solicitud_Max").performClick()
        compose.onNodeWithTag("calificar").performClick()
        compose.onNodeWithTag("estrella_4").performClick()
        compose.onNodeWithTag("comentario_resena").performTextInput("Muy amable")
        compose.onNodeWithTag("guardar_resena").performClick()
        compose.waitUntil(5_000) { resenas.resenas.isNotEmpty() }
        val r = resenas.resenas.single().second
        assertEquals(4, r.estrellas)
        assertEquals("Muy amable", r.comentario)
        // Ya calificada: ahora ofrece editar la reseña
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("solicitud_Max") }
        compose.onNodeWithTag("solicitud_Max").performClick()
        compose.onNodeWithText("Editar mi reseña").assertExists()
    }

    @Test
    fun elPerfilMuestraReseñasYSePuedeReportar() {
        veterinarios.directorio += laura
        resenas.resenas += "v1" to Resena("r1", "s9", "Pedro Ruiz", 5, "Excelente atención", LocalDate.now())
        compose.onNodeWithTag("pestana_servicios").performClick()
        compose.onNodeWithTag("tarjeta_veterinarios").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("vet_Laura Gómez") }
        compose.onNodeWithTag("vet_Laura Gómez").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("resena_Pedro Ruiz") }
        compose.onNodeWithText("4,5").assertExists()
        compose.onNodeWithText("Excelente atención").assertExists()
        compose.onNodeWithTag("reportar_perfil").performScrollTo().performClick()
        compose.onNodeWithTag("motivo_FALSO").performClick()
        compose.onNodeWithTag("enviar_reporte").performClick()
        compose.waitUntil(5_000) { resenas.reportes.isNotEmpty() }
        assertEquals(TipoReporte.PERFIL_VET to "v1", resenas.reportes.single())
    }

    @Test
    fun unAvisoTocadoAbreElModoYLaPestana() {
        // Aviso de una solicitud nueva para el veterinario
        veterinarios.miPerfil.value = laura
        apertura.abrir(Apertura(ModoUso.VETERINARIO, DestinoAviso.SOLICITUDES))
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("chip_modo_vet") }
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Aún no tienes solicitudes") }
        assertEquals(null, apertura.pendiente.value)
        // Aviso de una cita confirmada para el dueño: vuelve al modo personal, pestaña Servicios
        apertura.abrir(Apertura(ModoUso.PERSONAL, DestinoAviso.SERVICIOS))
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("tarjeta_veterinarios") }
    }

    @Test
    fun recuperarContraseñaYCrearUnaNueva() {
        cuenta.sinSesion()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("ya_tengo_cuenta") }
        compose.onNodeWithTag("ya_tengo_cuenta").performClick()
        compose.onNodeWithTag("olvide_clave").performScrollTo().performClick()
        compose.onNodeWithTag("correo_recuperar").performTextInput("ana@petcare.test")
        compose.onNodeWithTag("enviar_enlace").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("aviso_acceso") }
        assertEquals(listOf("ana@petcare.test"), cuenta.correosRecuperacion)

        // El enlace del correo abre la app con sesión: se pide la contraseña nueva
        recuperacion.pendiente.value = true
        cuenta.conAna()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Nueva contraseña") }
        compose.onNodeWithText("Contraseña nueva (mínimo 8)").performTextInput("nueva12345")
        compose.onNodeWithText("Repítela").performTextInput("nueva12345")
        compose.onNodeWithText("Guardar contraseña").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("pestana_hoy") }
        assertEquals("nueva12345", cuenta.claveCambiada)
    }

    @Test
    fun registroConConfirmacionDeCorreo() {
        cuenta.sinSesion()
        cuenta.exigeConfirmacion = true
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("crear_cuenta") }
        compose.onNodeWithTag("crear_cuenta").performClick()
        compose.onNodeWithText("Nombre").performTextInput("Leo")
        compose.onNodeWithText("Correo").performTextInput("leo@petcare.test")
        compose.onNodeWithText("Contraseña (mínimo 8)").performTextInput("clave1234")
        compose.onNodeWithTag("casilla_privacidad").performClick()
        compose.onNodeWithText("Crear cuenta").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("aviso_acceso") }
        compose.onNodeWithText("Te enviamos un correo a leo@petcare.test. Ábrelo en este teléfono para confirmar tu cuenta.").assertExists()
    }

    @Test
    fun eliminarLaCuentaPideConfirmacionEscrita() {
        compose.onNodeWithTag("pestana_perfil").performClick()
        compose.onNodeWithTag("eliminar_cuenta").performScrollTo().performClick()
        compose.onNodeWithTag("confirmar_eliminar").performTextInput("ELIMINAR")
        compose.onNodeWithTag("boton_eliminar_cuenta").performClick()
        compose.waitUntil(5_000) { cuenta.cuentaEliminada }
        assertTrue(cuenta.cuentaEliminada)
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("ya_tengo_cuenta") }
    }
}
