package com.udenar.petcare

import com.udenar.petcare.domain.animo.Animo
import com.udenar.petcare.domain.animo.LecturaAnimo
import com.udenar.petcare.domain.animo.RegistroAnimo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnimoTest {
    private val hoy = LocalDate.of(2026, 11, 25)
    private fun r(diasAtras: Long, a: Animo) = RegistroAnimo("m1", hoy.minusDays(diasAtras), a)

    @Test
    fun dosDiasDecaidoSugiereConsultarSinDiagnosticar() {
        val nota = LecturaAnimo.nota("Venus", listOf(r(1, Animo.DECAIDO), r(0, Animo.DECAIDO)), hoy)!!
        assertTrue(nota.startsWith("Venus lleva 2 días decaído"))
        assertTrue("consulta a su veterinario" in nota)
    }

    @Test
    fun unSoloDiaOCortadoNoMuestraNota() {
        assertNull(LecturaAnimo.nota("Venus", listOf(r(0, Animo.DECAIDO)), hoy))
        assertNull(LecturaAnimo.nota("Venus", listOf(r(2, Animo.DECAIDO), r(1, Animo.FELIZ), r(0, Animo.DECAIDO)), hoy))
    }

    @Test
    fun rachaCuentaDiasSeguidosAunqueHoyFalte() {
        assertEquals(3, LecturaAnimo.racha(listOf(r(2, Animo.FELIZ), r(1, Animo.TRANQUILO), r(0, Animo.JUGUETON)), hoy))
        assertEquals(2, LecturaAnimo.racha(listOf(r(2, Animo.FELIZ), r(1, Animo.TRANQUILO)), hoy))
        assertEquals(0, LecturaAnimo.racha(listOf(r(3, Animo.FELIZ)), hoy))
    }
}
