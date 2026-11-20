package com.udenar.petcare

import com.udenar.petcare.data.paseos.FaseSesion
import com.udenar.petcare.data.paseos.SesionPaseo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class SesionPaseoTest {
    /** Reloj que la prueba adelanta a mano. */
    private class RelojManual(var ahora: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?) = this
        override fun instant() = ahora
    }

    @Test
    fun distanciaYTiempoSinContarPausas() {
        val reloj = RelojManual(Instant.parse("2026-11-20T08:00:00Z"))
        val s = SesionPaseo(reloj)
        s.iniciar(listOf("m1", "m2"))
        s.nuevaUbicacion(1.2136, -77.2811, 5f)
        s.nuevaUbicacion(1.2145, -77.2811, 5f)   // +100 m
        s.nuevaUbicacion(1.2146, -77.2811, 60f)  // imprecisa: descartada
        reloj.ahora += Duration.ofMinutes(10)
        s.pausar()
        s.nuevaUbicacion(1.2200, -77.2811, 5f)   // en pausa: no suma
        reloj.ahora += Duration.ofMinutes(5)
        s.reanudar()
        reloj.ahora += Duration.ofMinutes(2)
        assertEquals(FaseSesion.ACTIVO, s.estado.value.fase)

        val r = s.finalizar()!!
        assertEquals(listOf("m1", "m2"), r.mascotaIds)
        assertEquals(12 * 60, r.duracionS)
        assertEquals(100.0, r.distanciaM.toDouble(), 2.0)
        assertEquals(2, r.ruta.size)
        assertEquals(FaseSesion.INACTIVO, s.estado.value.fase)
        assertNull(s.finalizar())
    }
}
