package com.udenar.petcare

import com.udenar.petcare.data.profesionales.PrestadoresSupabase
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.profesionales.FiltroPrestadores
import com.udenar.petcare.domain.profesionales.ModalidadPrestador
import com.udenar.petcare.domain.profesionales.ModoPrecio
import com.udenar.petcare.domain.profesionales.PerfilPrestador
import com.udenar.petcare.domain.profesionales.ServicioPrestador
import com.udenar.petcare.domain.profesionales.UnidadPrecio
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import javax.inject.Inject

/** Parte 4.4 contra el Supabase real: un prestador y una dueña temporales; al final se borra todo. */
@HiltAndroidTest
class PrestadoresRealTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var supabase: SupabaseClient
    private lateinit var repo: PrestadoresSupabase

    private val marca = System.currentTimeMillis()
    private val pres = "pres.$marca@petcare.test" to "Prueba-${UUID.randomUUID()}"
    private val duena = "duena.$marca@petcare.test" to "Prueba-${UUID.randomUUID()}"
    private val creadas = mutableListOf<Pair<String, String>>()

    private suspend fun registrar(c: Pair<String, String>, nombre: String): String {
        supabase.auth.clearSession()
        supabase.auth.signUpWith(Email) {
            email = c.first
            password = c.second
            data = buildJsonObject { put("nombre", nombre); put("acepto_privacidad", true) }
        }
        creadas += c
        return supabase.auth.currentUserOrNull()!!.id
    }

    private suspend fun entrar(c: Pair<String, String>) {
        supabase.auth.clearSession()
        supabase.auth.signInWith(Email) { email = c.first; password = c.second }
    }

    @Before
    fun preparar() = runBlocking {
        hilt.inject()
        repo = PrestadoresSupabase(supabase)
        val aplicado = try {
            supabase.from("prestadores").select { limit(0) }
            true
        } catch (e: PostgrestRestException) {
            e.code != "PGRST205"
        }
        assumeTrue("Falta aplicar supabase/14_prestadores.sql", aplicado)
    }

    @After
    fun limpiar() = runBlocking {
        for (c in creadas.reversed()) {
            runCatching {
                entrar(c)
                val id = supabase.auth.currentUserOrNull()!!.id
                val bucket = supabase.storage.from("galeria")
                val fotos = runCatching { bucket.list(id).map { "$id/${it.name}" } }.getOrDefault(emptyList())
                if (fotos.isNotEmpty()) bucket.delete(fotos)
                supabase.postgrest.rpc("eliminar_mi_cuenta")
            }
        }
        supabase.auth.clearSession()
    }

    @Test
    fun perfilGaleriaYDirectorio(): Unit = runBlocking {
        val presId = registrar(pres, "Prestador Prueba")
        assertEquals(listOf("PASEADOR", "BANO_PELUQUERIA", "CUIDADO_TEMPORAL"), repo.categorias().map { it.id })
        val png = android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==", 0)
        val foto = repo.subirFoto(png)
        assertTrue(foto.contains("/object/public/galeria/$presId/"))
        repo.guardar(
            PerfilPrestador(
                id = presId, nombre = "Paseos Prueba", descripcion = "Paseos grupales por el parque", telefono = "3001234567", ciudad = "Pastó",
                zona = "Centro y Las Cuadras", lat = 1.2136, lon = -77.2811, modalidades = setOf(ModalidadPrestador.DOMICILIO),
                especies = setOf(Especie.PERRO), categorias = listOf("PASEADOR"), galeria = listOf(foto),
                servicios = listOf(ServicioPrestador("s1", "PASEADOR", "Paseo de 1 hora", modo = ModoPrecio.FIJO, montoCop = 15000, unidad = UnidadPrecio.POR_PASEO)),
            ),
        )
        val mio = repo.cargarMiPerfil()!!
        assertEquals(15000, mio.servicios.single().montoCop)
        assertEquals(listOf(foto), mio.galeria)
        val usuario = supabase.from("usuarios").select { filter { eq("id", presId) } }.decodeSingle<JsonObject>()
        assertEquals("true", usuario["es_prestador"]!!.jsonPrimitive.content)

        // El servidor rechaza categorías inexistentes, servicios sin precio y cambios en las calificaciones
        for (cambio in listOf(
            buildJsonObject { put("categorias", JsonArray(listOf(JsonPrimitive("INVENTADA")))) },
            buildJsonObject { put("servicios", JsonArray(listOf(buildJsonObject { put("id", "x"); put("categoria", "PASEADOR"); put("nombre", "Gratis"); put("modo", "FIJO") }))) },
            buildJsonObject { put("rating_suma", 50) },
        )) {
            try {
                supabase.from("prestadores").update(cambio) { filter { eq("id", presId) } }
                fail("Debió rechazar $cambio")
            } catch (_: PostgrestRestException) {
            }
        }

        // La dueña lo encuentra por ciudad (sin tilde), zona, categoría, especie y modalidad
        registrar(duena, "Dueña Prueba")
        val encontrados = repo.buscar(
            FiltroPrestadores(ciudad = "pasto", zona = "cuadras", categoria = "PASEADOR", especie = Especie.PERRO, modalidad = ModalidadPrestador.DOMICILIO),
            lat = 1.2200, lon = -77.2811,
        )
        val p = encontrados.single { it.id == presId }
        assertNotNull(p.distanciaKm)
        assertEquals("$15.000 por paseo", com.udenar.petcare.domain.profesionales.Precios.texto(p.servicios.single()))
        assertTrue(repo.buscar(FiltroPrestadores(ciudad = "Pasto", categoria = "BANO_PELUQUERIA")).none { it.id == presId })
        assertTrue(repo.buscar(FiltroPrestadores(ciudad = "Pasto", modalidad = ModalidadPrestador.ESTABLECIMIENTO)).none { it.id == presId })
        // La dueña no puede borrar fotos ajenas
        repo.borrarFoto(foto)

        // Al ocultarlo desaparece del directorio; sus fotos se borran
        entrar(pres)
        assertEquals(1, supabase.storage.from("galeria").list(presId).size)
        repo.guardar(repo.cargarMiPerfil()!!.copy(activo = false))
        repo.borrarFoto(foto)
        assertTrue(supabase.storage.from("galeria").list(presId).isEmpty())
        entrar(duena)
        assertTrue(repo.buscar(FiltroPrestadores()).none { it.id == presId })
    }
}
