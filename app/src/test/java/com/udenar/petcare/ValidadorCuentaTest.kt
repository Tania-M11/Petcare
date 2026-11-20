package com.udenar.petcare

import com.udenar.petcare.domain.cuenta.Campo
import com.udenar.petcare.domain.cuenta.ValidadorCuenta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidadorCuentaTest {

    @Test
    fun registroCompletoNoTieneErrores() {
        assertTrue(ValidadorCuenta.registro("Ana", "ana@correo.com", "clave1234", true).isEmpty())
    }

    @Test
    fun registroVacioMarcaTodosLosCampos() {
        val e = ValidadorCuenta.registro(" ", "", "", false)
        assertEquals(setOf(Campo.NOMBRE, Campo.CORREO, Campo.CLAVE, Campo.PRIVACIDAD), e.keys)
    }

    @Test
    fun claveCortaYCorreoInvalido() {
        val e = ValidadorCuenta.registro("Ana", "ana@correo", "1234567", true)
        assertEquals(setOf(Campo.CORREO, Campo.CLAVE), e.keys)
    }

    @Test
    fun sinAceptarPrivacidadNoSePuedeRegistrar() {
        assertEquals(setOf(Campo.PRIVACIDAD), ValidadorCuenta.registro("Ana", "ana@correo.com", "clave1234", false).keys)
    }

    @Test
    fun inicioSesionPideCorreoYClave() {
        assertEquals(setOf(Campo.CORREO, Campo.CLAVE), ValidadorCuenta.inicioSesion("", "").keys)
        assertTrue(ValidadorCuenta.inicioSesion(" ana@correo.com ", "x").isEmpty())
    }
}
