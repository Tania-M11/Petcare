package com.udenar.petcare

import com.udenar.petcare.domain.modelo.Especie
import org.junit.Assert.assertEquals
import org.junit.Test

class EspecieTest {
    @Test
    fun codigoConocidoSeConvierte() {
        assertEquals(Especie.GATO, Especie.desdeCodigo("GATO"))
    }

    @Test
    fun codigoDesconocidoOVacioEsOtro() {
        assertEquals(Especie.OTRO, Especie.desdeCodigo("DRAGON"))
        assertEquals(Especie.OTRO, Especie.desdeCodigo(null))
    }
}
