package com.udenar.petcare

import com.udenar.petcare.domain.mascotas.CampoQr
import com.udenar.petcare.domain.mascotas.ConfigQr
import com.udenar.petcare.domain.mascotas.QrMascota
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.Mascota
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrMascotaTest {
    private val luna = Mascota("m", "Luna", Especie.GATO, raza = "Criolla", alergias = "Pollo\nPenicilina")

    @Test
    fun formatoLegibleYCodigoApagadoPorDefecto() {
        val t = QrMascota.texto(luna, ConfigQr(contacto = "Tania 300 000 0000"), codigo = "ABCD2345")
        assertEquals(
            "PETCARE v1\nNombre: Luna\nEspecie: Gato\nRaza: Criolla\nAlergias: Pollo; Penicilina\nContacto: Tania 300 000 0000",
            t,
        )
        assertFalse(t.contains("Codigo"))
    }

    @Test
    fun soloLosCamposElegidosYElCodigoSiSeActiva() {
        val t = QrMascota.texto(luna, ConfigQr(campos = setOf(CampoQr.NOMBRE, CampoQr.CODIGO)), codigo = "ABCD2345")
        assertEquals("PETCARE v1\nNombre: Luna\nCodigo: ABCD2345", t)
        assertEquals("ABCD2345", QrMascota.leer(t)!!.codigo)
    }

    @Test
    fun nuncaPasaDe800Caracteres() {
        val larga = luna.copy(alergias = "x".repeat(500), condiciones = "y".repeat(500))
        assertTrue(QrMascota.texto(larga, ConfigQr(), null).length <= QrMascota.MAXIMO)
    }

    @Test
    fun unQrAjenoNoSeInterpreta() {
        assertNull(QrMascota.leer("https://ejemplo.com"))
        assertEquals("Luna", QrMascota.leer(" PETCARE v1\nNombre: Luna ")!!.nombre)
    }
}
