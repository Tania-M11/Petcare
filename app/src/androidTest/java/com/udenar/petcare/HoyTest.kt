package com.udenar.petcare

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** Parte 2.7: Hoy con pendientes, un toque para marcar, filtro, alertas y calendario. */
@HiltAndroidTest
class HoyTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var mascotas: RepositorioMascotas
    @Inject lateinit var cuidados: RepositorioCuidados

    @Before
    fun preparar() {
        hilt.inject()
        compose.esperarInterfaz()
        val hoy = LocalDate.now()
        runBlocking {
            mascotas.guardar(Mascota("m1", "Luna", Especie.GATO))
            mascotas.guardar(Mascota("m2", "Max", Especie.PERRO))
            cuidados.guardarEvento(
                EventoCuidado("e1", "m1", TipoCuidado.MEDICAMENTO, "Gotas", Frecuencia(TipoFrecuencia.DIARIO, horas = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))), fechaInicio = hoy),
            )
            cuidados.guardarEvento(EventoCuidado("e2", "m2", TipoCuidado.COMIDA, "Desayuno", Frecuencia(TipoFrecuencia.DIARIO, horas = listOf(LocalTime.of(7, 0))), fechaInicio = hoy))
            cuidados.guardarEvento(EventoCuidado("e3", "m2", TipoCuidado.VACUNA, "Rabia", Frecuencia(TipoFrecuencia.UNICO), fechaInicio = hoy.plusDays(3), avisoAnticipadoDias = 7))
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("3 pendientes") }
    }

    @Test
    fun unToqueMarcaLaTomaConQuienLaHizo() {
        compose.onNodeWithTag("hecho_Desayuno").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("2 pendientes") }
        compose.onNodeWithText("Dada por Ana", substring = true).assertExists()
        compose.onNodeWithText("Deshacer").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("3 pendientes") }
    }

    @Test
    fun filtrarPorMascotaYAlertaDeVacuna() {
        // La vacuna próxima aparece como recomendación con su porqué
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("carrusel_recomendaciones") }
        compose.onNodeWithTag("carrusel_recomendaciones").performScrollToNode(androidx.compose.ui.test.hasText("Se acerca: Rabia"))
        compose.onNodeWithTag("filtro_Luna").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("2 pendientes") }
        compose.onAllNodesWithTag("hecho_Desayuno").fetchSemanticsNodes().let { assert(it.isEmpty()) }
        compose.onNodeWithTag("filtro_Luna").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("3 pendientes") }
    }

    @Test
    fun calendarioDelMesLlevaAOtroDia() {
        compose.onNodeWithTag("ver_mes").performClick()
        val manana = LocalDate.now().plusDays(1)
        if (manana.month != LocalDate.now().month) compose.onNodeWithText("Mes siguiente", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("cal_${manana.dayOfMonth}").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Volver a hoy") }
        // Las tomas de mañana se ven pero no se pueden marcar todavía
        compose.onAllNodesWithTag("hecho_Gotas").fetchSemanticsNodes().let { assert(it.isEmpty()) }
        compose.onNodeWithText("Volver a hoy").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("hecho_Gotas") }
    }
}
