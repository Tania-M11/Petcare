package com.udenar.petcare

import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.profesionales.Contacto
import com.udenar.petcare.domain.profesionales.PerfilVeterinario
import com.udenar.petcare.domain.profesionales.ValidadorVeterinario
import com.udenar.petcare.domain.profesionales.Verificacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VeterinariosTest {
    private val valido = PerfilVeterinario(
        id = "v1", nombre = "Laura Gómez", especies = setOf(Especie.PERRO), telefono = "+57 300 123 4567",
        ciudad = "Pasto", direccion = "Calle 18 # 25-30", lat = 1.21, lon = -77.28,
    )

    @Test
    fun perfilCompletoEsValido() = assertTrue(ValidadorVeterinario.errores(valido).isEmpty())

    @Test
    fun camposObligatorios() {
        val e = ValidadorVeterinario.errores(valido.copy(nombre = " ", especies = emptySet(), telefono = "abc", ciudad = "", direccion = "x", lat = 0.0, lon = 0.0))
        assertEquals(setOf("nombre", "especies", "telefono", "ciudad", "direccion", "ubicacion"), e.keys)
        assertFalse(ValidadorVeterinario.errores(valido.copy(whatsapp = "12")).isEmpty())
        assertTrue(ValidadorVeterinario.errores(valido.copy(whatsapp = "")).isEmpty())
    }

    @Test
    fun enlacesDeContacto() {
        assertEquals("tel:+573001234567", Contacto.enlaceLlamar("+57 300 123 4567"))
        assertEquals("tel:7231234", Contacto.enlaceLlamar("723 1234"))
        // Celular colombiano sin indicativo: se completa con 57
        assertEquals("https://wa.me/573001234567", Contacto.enlaceWhatsapp("300 123 4567"))
        assertEquals("https://wa.me/573001234567?text=Hola%20PetCare", Contacto.enlaceWhatsapp("+57 300 123 4567", "Hola PetCare"))
        assertEquals("geo:1.21,-77.28?q=1.21,-77.28(Cl%C3%ADnica%20Sur)", Contacto.enlaceComoLlegar(1.21, -77.28, "Clínica Sur"))
        assertEquals("a 850 m", Contacto.textoDistancia(0.85))
        assertEquals("a 2,4 km", Contacto.textoDistancia(2.37))
    }

    @Test
    fun insigniaSoloSiEstaVerificado() {
        assertEquals("Verificado ✓", Verificacion.VERIFICADO.insignia)
        Verificacion.entries.filter { it != Verificacion.VERIFICADO }.forEach { assertEquals("No verificado", it.insignia) }
        assertEquals(Verificacion.SIN_CERTIFICADO, Verificacion.desdeCodigo("RARO"))
    }
}
