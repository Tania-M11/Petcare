package com.udenar.petcare

import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.profesionales.ModalidadPrestador
import com.udenar.petcare.domain.profesionales.ModoPrecio
import com.udenar.petcare.domain.profesionales.PerfilPrestador
import com.udenar.petcare.domain.profesionales.Precios
import com.udenar.petcare.domain.profesionales.ServicioPrestador
import com.udenar.petcare.domain.profesionales.UnidadPrecio
import com.udenar.petcare.domain.profesionales.ValidadorPrestador
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrestadoresTest {
    private val paseo = ServicioPrestador("s1", "PASEADOR", "Paseo de 1 hora", modo = ModoPrecio.FIJO, montoCop = 15000, unidad = UnidadPrecio.POR_PASEO)
    private val perfil = PerfilPrestador(
        id = "p1", nombre = "Paseos Felices", descripcion = "Paseos grupales y personalizados", telefono = "3001234567", ciudad = "Pasto",
        zona = "Centro", lat = 1.21, lon = -77.28, modalidades = setOf(ModalidadPrestador.DOMICILIO), especies = setOf(Especie.PERRO),
        categorias = listOf("PASEADOR"), servicios = listOf(paseo),
    )

    @Test
    fun preciosOrientativos() {
        assertEquals("$15.000 por paseo", Precios.texto(paseo))
        assertEquals("Desde $40.000", Precios.texto(paseo.copy(modo = ModoPrecio.DESDE, montoCop = 40000, unidad = null)))
        assertEquals("A cotizar", Precios.texto(paseo.copy(modo = ModoPrecio.COTIZACION)))
        assertEquals("Desde $12.000 por paseo", perfil.copy(servicios = listOf(paseo, paseo.copy(id = "s2", montoCop = 12000))).precioDesde)
    }

    @Test
    fun validacion() {
        assertTrue(ValidadorPrestador.errores(perfil).isEmpty())
        val e = ValidadorPrestador.errores(perfil.copy(descripcion = "corta", categorias = emptyList(), especies = emptySet(), modalidades = emptySet(), zona = null, lat = 0.0, lon = 0.0))
        assertEquals(setOf("descripcion", "categorias", "especies", "modalidades", "zona", "ubicacion", "servicios"), e.keys)
        // Un servicio con precio fijo necesita monto; uno "a cotizar" no
        assertEquals(1, ValidadorPrestador.erroresServicio(paseo.copy(montoCop = null), listOf("PASEADOR")).size)
        assertTrue(ValidadorPrestador.erroresServicio(paseo.copy(modo = ModoPrecio.COTIZACION, montoCop = null), listOf("PASEADOR")).isEmpty())
        assertEquals("Centro · hasta 5 km", perfil.copy(radioKm = 5).cobertura)
    }
}
