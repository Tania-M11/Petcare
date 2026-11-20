package com.udenar.petcare

import com.udenar.petcare.domain.compartir.codigoValido
import com.udenar.petcare.domain.compartir.normalizarCodigo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodigoTest {
    @Test
    fun normalizaLoQueEscribeLaPersona() {
        assertEquals("ABCD2345", normalizarCodigo("abcd 2345"))
        assertEquals("ABCD2345", normalizarCodigo("ABCD-2345-XYZ"))
    }

    @Test
    fun soloAlfabetoSinAmbiguos() {
        assertTrue(codigoValido("ABCD2345"))
        assertFalse(codigoValido("ABCD234"))   // corto
        assertFalse(codigoValido("ABCDO234"))  // O ambigua
        assertFalse(codigoValido("ABCD1234"))  // 1 ambiguo
        assertFalse(codigoValido("ILABCD23"))  // I y L ambiguas
    }
}
