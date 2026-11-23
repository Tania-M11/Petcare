package com.udenar.petcare

import com.udenar.petcare.domain.escaneo.AnalizadorEtiqueta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Fase 6.3: interpretar el texto leído de una etiqueta de vacuna o medicamento. */
class EtiquetasTest {
    private val hoy = LocalDate.of(2026, 11, 23)

    @Test
    fun etiquetaDeVacuna() {
        val texto = """
            NOBIVAC RABIES
            Vacuna antirrábica
            Lote: A123-45
            Fab. 15/03/2026
            Vence 09/2028
            Uso veterinario
        """.trimIndent()
        val l = AnalizadorEtiqueta.analizar(texto, hoy)
        assertEquals("NOBIVAC RABIES", l.nombres.first())
        assertEquals("A123-45", l.lote)
        assertEquals(listOf(LocalDate.of(2026, 3, 15), LocalDate.of(2028, 9, 1)), l.fechas)
        assertTrue(l.nombres.none { it.startsWith("Lote") || it.startsWith("Uso") })
    }

    @Test
    fun anioCortoYTextoSinDatos() {
        assertEquals(listOf(LocalDate.of(2026, 11, 2)), AnalizadorEtiqueta.analizar("Aplicada 2-11-26", hoy).fechas)
        val vacia = AnalizadorEtiqueta.analizar("12 34", hoy)
        assertTrue(vacia.nombres.isEmpty() && vacia.fechas.isEmpty() && vacia.lote == null)
    }
}
