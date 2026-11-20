package com.udenar.petcare

import com.udenar.petcare.domain.paseos.Coordenada
import com.udenar.petcare.domain.paseos.FiltroGps
import com.udenar.petcare.domain.paseos.Geo
import com.udenar.petcare.domain.paseos.Polilinea
import com.udenar.petcare.domain.paseos.textoDistancia
import com.udenar.petcare.domain.paseos.textoDuracion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaseosTest {

    @Test
    fun polilineaDelEjemploDeGoogle() {
        val puntos = listOf(Coordenada(38.5, -120.2), Coordenada(40.7, -120.95), Coordenada(43.252, -126.453))
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", Polilinea.codificar(puntos))
        val vuelta = Polilinea.decodificar("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, vuelta.size)
        assertEquals(43.252, vuelta.last().lat, 1e-6)
        assertEquals(-126.453, vuelta.last().lon, 1e-6)
    }

    @Test
    fun distanciaHaversine() {
        // Un grado de latitud ≈ 111,2 km
        assertEquals(111_195.0, Geo.distanciaM(Coordenada(0.0, 0.0), Coordenada(1.0, 0.0)), 50.0)
        // Pasto: dos puntos a ~100 m
        val a = Coordenada(1.2136, -77.2811)
        val b = Coordenada(1.2145, -77.2811)
        assertEquals(100.0, Geo.distanciaM(a, b), 1.0)
        assertEquals(200.0, Geo.distanciaTotalM(listOf(a, b, a)), 2.0)
    }

    @Test
    fun filtroDescartaImprecisosYTemblores() {
        val a = Coordenada(1.2136, -77.2811)
        assertTrue(FiltroGps.aceptar(a, 8f, null))
        assertFalse("Precisión peor de 30 m", FiltroGps.aceptar(a, 45f, null))
        assertFalse("Se movió menos de 10 m", FiltroGps.aceptar(Coordenada(1.21365, -77.2811), 8f, a))
        assertTrue(FiltroGps.aceptar(Coordenada(1.2140, -77.2811), 8f, a))
    }

    @Test
    fun textos() {
        assertEquals("350 m", textoDistancia(350))
        assertEquals("1,2 km", textoDistancia(1234))
        assertEquals("25 min", textoDuracion(25 * 60 + 10))
        assertEquals("1 h 05 min", textoDuracion(3900))
    }
}
