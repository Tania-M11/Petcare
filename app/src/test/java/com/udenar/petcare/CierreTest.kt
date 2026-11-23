package com.udenar.petcare

import com.udenar.petcare.data.alarmas.Notificador
import com.udenar.petcare.data.avisos.Aviso
import com.udenar.petcare.data.avisos.DestinoAviso
import com.udenar.petcare.domain.cuenta.Campo
import com.udenar.petcare.domain.cuenta.ValidadorCuenta
import com.udenar.petcare.domain.modelo.ModoUso
import com.udenar.petcare.domain.profesionales.ValidadorResena
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fase 5: avisos (RF-29), reseñas (RF-28) y cuenta (5.2). */
class CierreTest {
    @Test
    fun avisoDesdeDatosDelPush() {
        val a = Aviso.desde(mapOf("id" to "n1", "tipo" to "VET_NUEVA", "titulo" to "Nueva solicitud", "cuerpo" to "Ana pide cita", "modo" to "VETERINARIO", "destino" to "SOLICITUDES"))!!
        assertEquals(ModoUso.VETERINARIO, a.modo)
        assertEquals(DestinoAviso.SOLICITUDES, a.destino)
        assertEquals(Notificador.CANAL_SOLICITUDES, a.canal)
        assertEquals(Notificador.CANAL_CUIDADORES, a.copy(tipo = "CUIDADO_APROBADA").canal)
        // Datos incompletos o desconocidos: no se muestra o se usa lo seguro
        assertNull(Aviso.desde(mapOf("titulo" to "x")))
        assertEquals(ModoUso.PERSONAL, Aviso.desde(mapOf("id" to "n2", "titulo" to "x", "modo" to "RARO"))!!.modo)
    }

    @Test
    fun resenasDeUnoACincoEstrellas() {
        assertTrue(ValidadorResena.valida(5, "Excelente"))
        assertFalse(ValidadorResena.valida(0, null))
        assertFalse(ValidadorResena.valida(6, null))
        assertFalse(ValidadorResena.valida(4, "x".repeat(501)))
    }

    @Test
    fun recuperarYNuevaClave() {
        assertTrue(ValidadorCuenta.recuperar("ana@petcare.test").isEmpty())
        assertEquals(setOf(Campo.CORREO), ValidadorCuenta.recuperar("ana").keys)
        assertTrue(ValidadorCuenta.nuevaClave("clave1234", "clave1234").isEmpty())
        assertEquals("Las contraseñas no coinciden", ValidadorCuenta.nuevaClave("clave1234", "clave12345")[Campo.CLAVE])
        assertEquals(setOf(Campo.CLAVE), ValidadorCuenta.nuevaClave("corta", "corta").keys)
    }
}
