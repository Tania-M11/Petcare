package com.udenar.petcare

import com.udenar.petcare.domain.mascotas.CampoMascota
import com.udenar.petcare.domain.mascotas.FichaMascota
import com.udenar.petcare.domain.mascotas.UnidadPeso
import com.udenar.petcare.domain.modelo.Especie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FichaMascotaTest {
    private val hoy = LocalDate.of(2026, 11, 19)

    @Test
    fun edadEnAniosMesesYSemanas() {
        assertEquals("3 años", FichaMascota.edad(LocalDate.of(2023, 5, 1), false, hoy))
        assertEquals("1 año y 2 meses", FichaMascota.edad(LocalDate.of(2025, 9, 10), false, hoy))
        assertEquals("1 año", FichaMascota.edad(LocalDate.of(2025, 11, 19), false, hoy))
        assertEquals("5 meses", FichaMascota.edad(LocalDate.of(2026, 6, 1), false, hoy))
        assertEquals("3 semanas", FichaMascota.edad(LocalDate.of(2026, 10, 28), false, hoy))
        assertEquals("Recién nacido", FichaMascota.edad(hoy.minusDays(2), false, hoy))
        assertEquals("2 años aprox.", FichaMascota.edad(LocalDate.of(2024, 1, 1), true, hoy))
    }

    @Test
    fun pesoEnKgOGramos() {
        assertEquals(4200, FichaMascota.gramos("4,2", UnidadPeso.KG))
        assertEquals(350, FichaMascota.gramos(" 350 ", UnidadPeso.G))
        assertNull(FichaMascota.gramos("", UnidadPeso.KG))
        assertEquals(-1, FichaMascota.gramos("abc", UnidadPeso.KG))
        assertEquals(-1, FichaMascota.gramos("0", UnidadPeso.G))
        assertEquals("4,2 kg", FichaMascota.peso(4200))
        assertEquals("12 kg", FichaMascota.peso(12000))
        assertEquals("350 g", FichaMascota.peso(350))
        assertEquals("4,2" to UnidadPeso.KG, FichaMascota.pesoEditable(4200))
        assertEquals("350" to UnidadPeso.G, FichaMascota.pesoEditable(350))
    }

    @Test
    fun validacion() {
        assertTrue(FichaMascota.validar("Luna", Especie.GATO, "", null, "", UnidadPeso.KG, hoy).isEmpty())
        val e = FichaMascota.validar(" ", Especie.OTRO, "", hoy.plusDays(1), "x", UnidadPeso.KG, hoy)
        assertEquals(setOf(CampoMascota.NOMBRE, CampoMascota.ESPECIE_OTRO, CampoMascota.NACIMIENTO, CampoMascota.PESO), e.keys)
    }
}
