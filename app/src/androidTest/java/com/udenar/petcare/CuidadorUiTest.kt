package com.udenar.petcare

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.udenar.petcare.data.compartir.SoloPropietarioException
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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import javax.inject.Inject

/** Parte 3.1: lo que ve y puede hacer quien cuida una mascota ajena (sección 5). */
@HiltAndroidTest
class CuidadorUiTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var mascotas: RepositorioMascotas
    @Inject lateinit var cuidados: RepositorioCuidados
    @Inject lateinit var dao: com.udenar.petcare.data.mascotas.MascotaDao

    @Before
    fun preparar() {
        hilt.inject()
        compose.esperarInterfaz()
        runBlocking {
            // Mascota de otra persona (llegó por sincronización) que Ana cuida
            mascotas.guardar(Mascota("ajena", "Toby", Especie.PERRO, propietarioId = "dueno"))
            dao.guardar(dao.porId("ajena")!!.copy(pendiente = false))
            mascotas.guardar(Mascota("mia", "Luna", Especie.GATO))
        }
    }

    @Test
    fun laListaSeparaLasQueCuidoYLaFichaEsDeSoloLectura() {
        compose.onNodeWithTag("pestana_mascotas").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Cuidas a…") }
        compose.onNodeWithTag("mascota_Toby").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("dejar_de_cuidar") }
        // Sin editar, sin código, sin eliminar
        assertTrue(compose.onAllNodesWithTextExists("Editar ficha").not())
        assertTrue(compose.onAllNodesWithTextExists("Código para cuidadores").not())
        compose.onNodeWithTag("seccion_Cuidados").performClick()
        assertTrue(compose.onAllNodesWithTagExists("agregar_cuidado").not())
        compose.onNodeWithTag("seccion_Salud").performClick()
        assertTrue(compose.onAllNodesWithTagExists("registrar_peso").not())
        assertTrue(compose.onAllNodesWithTagExists("agregar_visita").not())
        assertTrue("Los cuidadores no ven documentos", compose.onAllNodesWithTagExists("agregar_pdf").not())
    }

    @Test
    fun unCuidadorNoPuedeCrearCuidadosNiEditarLaFicha(): Unit = runBlocking {
        try {
            cuidados.guardarEvento(EventoCuidado("x", "ajena", TipoCuidado.COMIDA, "Cena", Frecuencia(TipoFrecuencia.DIARIO), fechaInicio = LocalDate.now()))
            fail("Debió rechazarse")
        } catch (_: SoloPropietarioException) {
        }
        try {
            mascotas.guardar(Mascota("ajena", "Otro nombre", Especie.PERRO))
            fail("Debió rechazarse")
        } catch (_: SoloPropietarioException) {
        }
        // En su propia mascota sí
        cuidados.guardarEvento(EventoCuidado("y", "mia", TipoCuidado.COMIDA, "Cena", Frecuencia(TipoFrecuencia.DIARIO), fechaInicio = LocalDate.now()))
    }

    @Test
    fun elMenuMasAbreCuidarConCodigo() {
        compose.onNodeWithTag("pestana_mascotas").performClick()
        compose.onNodeWithTag("boton_mas").performClick()
        compose.onNodeWithText("Cuidar con código").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("campo_codigo") }
        compose.onNodeWithText("Buscar").performScrollTo()
    }
}
