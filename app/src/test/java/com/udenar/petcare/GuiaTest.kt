package com.udenar.petcare

import com.udenar.petcare.domain.guia.EstadoPaso
import com.udenar.petcare.domain.guia.Guia
import com.udenar.petcare.domain.guia.Intencion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuiaTest {

    @Test
    fun cadaIntencionAportaSusPasos() {
        assertEquals(3, Guia.pasos(setOf(Intencion.MASCOTAS)).size)
        assertEquals(2, Guia.pasos(setOf(Intencion.CUIDAR)).size)
        assertEquals(6, Guia.pasos(setOf(Intencion.VETERINARIO, Intencion.SERVICIOS)).size)
        assertTrue(Guia.pasos(emptySet()).isEmpty())
    }

    @Test
    fun elOrdenSigueLasIntencionesNoLaSeleccion() {
        val pasos = Guia.pasos(setOf(Intencion.SERVICIOS, Intencion.MASCOTAS))
        assertEquals(Intencion.MASCOTAS, pasos.first().intencion)
        assertEquals(Intencion.SERVICIOS, pasos.last().intencion)
    }

    @Test
    fun progresoCuentaHechosYOmitidos() {
        val p = Guia.progreso(setOf(Intencion.MASCOTAS), hechos = setOf(Guia.NOTIFICACIONES), omitidos = setOf("crear_mascota"))
        assertEquals(2, p.resueltos)
        assertFalse(p.completa)
        assertEquals(EstadoPaso.OMITIDO, p.pasos.first { it.first.id == "crear_mascota" }.second)

        val completa = Guia.progreso(setOf(Intencion.CUIDAR), emptySet(), setOf("codigo_cuidar", "aprobacion"))
        assertTrue(completa.completa)
    }

    @Test
    fun codigosDesconocidosSeIgnoran() {
        assertEquals(setOf(Intencion.CUIDAR), Intencion.desdeCodigos(listOf("CUIDAR", "OTRA")))
    }
}
