package com.udenar.petcare

import com.udenar.petcare.data.local.PetcareDb
import com.udenar.petcare.data.sync.Sincronizador
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.Mascota
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import javax.inject.Inject

/**
 * Parte 2.1, contra el Supabase real (requiere internet y supabase/03_mascotas_sync.sql):
 * crea una cuenta temporal, sincroniza en ambos sentidos y al final la elimina con
 * eliminar_mi_cuenta(), así no queda ningún dato de prueba en el proyecto.
 */
@HiltAndroidTest
class SincronizacionRealTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var supabase: SupabaseClient
    @Inject lateinit var mascotas: RepositorioMascotas
    @Inject lateinit var sincronizador: Sincronizador
    @Inject lateinit var db: PetcareDb

    private val correo = "sync.${System.currentTimeMillis()}@petcare.test"
    private val clave = "Prueba-${UUID.randomUUID()}"

    @Before
    fun crearCuentaTemporal() = runBlocking {
        hilt.inject()
        // Antes de crear nada: si el SQL no está aplicado, la cuenta temporal no se podría borrar
        val sqlAplicado = try {
            supabase.postgrest.rpc("eliminar_mi_cuenta")
            true
        } catch (e: PostgrestRestException) {
            e.code != "PGRST202" // sin sesión responde "sin permiso" (existe) en vez de "no existe"
        }
        assumeTrue("Falta aplicar supabase/03_mascotas_sync.sql", sqlAplicado)
        supabase.auth.signUpWith(Email) {
            email = correo
            password = clave
            data = buildJsonObject {
                put("nombre", "Prueba sincronización")
                put("acepto_privacidad", true)
            }
        }
        assertTrue("La cuenta temporal no quedó con sesión", supabase.auth.currentSessionOrNull() != null)
    }

    @After
    fun eliminarCuentaTemporal() = runBlocking {
        if (supabase.auth.currentSessionOrNull() == null) return@runBlocking
        supabase.postgrest.rpc("eliminar_mi_cuenta")
        supabase.auth.clearSession()
        try {
            supabase.auth.signInWith(Email) {
                email = correo
                password = clave
            }
            fail("La cuenta temporal debió eliminarse")
        } catch (_: Exception) {
            // Esperado: la cuenta ya no existe
        }
    }

    private suspend fun remotas(): List<JsonObject> = supabase.from("mascotas").select().decodeList()

    @Test
    fun idaYVueltaConElServidor() = runBlocking {
        // 1) Se crea en el teléfono y queda pendiente
        val id = UUID.randomUUID().toString()
        mascotas.guardar(Mascota(id = id, nombre = "Luna", especie = Especie.GATO, pesoGramos = 4200))
        assertTrue(mascotas.observar().first().single().porSubir)

        // 2) Sube y el servidor la guarda con el propietario correcto
        sincronizador.sincronizar()
        val subida = remotas().single()
        assertEquals("Luna", subida["nombre"]!!.jsonPrimitive.content)
        assertEquals(supabase.auth.currentUserOrNull()!!.id, subida["propietario_id"]!!.jsonPrimitive.content)
        assertTrue(!mascotas.observar().first().single().porSubir)

        // 3) Un cambio hecho en otro dispositivo baja al teléfono
        supabase.from("mascotas").update(buildJsonObject { put("nombre", "Luna Lunera") }) { filter { eq("id", id) } }
        sincronizador.sincronizar()
        assertEquals("Luna Lunera", mascotas.obtener(id)!!.nombre)

        // 4) Teléfono nuevo: con la base vacía se descarga todo
        db.clearAllTables()
        sincronizador.sincronizar()
        assertEquals(listOf("Luna Lunera"), mascotas.observar().first().map { it.nombre })

        // 5) Eliminar se propaga como marca
        mascotas.eliminar(id)
        sincronizador.sincronizar()
        assertEquals("true", remotas().single()["eliminado"]!!.jsonPrimitive.content)
        assertTrue(mascotas.observar().first().isEmpty())
    }

    @Test
    fun nadieVeNiEditaMascotasAjenas() = runBlocking {
        // Una mascota con un id que no es suya: el servidor la rechaza al subir
        val ajena = UUID.randomUUID().toString()
        try {
            supabase.from("mascotas").insert(buildJsonObject {
                put("id", ajena)
                put("nombre", "Intrusa")
                put("especie", "PERRO")
                put("propietario_id", UUID.randomUUID().toString())
            })
            fail("No debería poder crear una mascota a nombre de otra persona")
        } catch (_: Exception) {
            // Esperado: propietario_id no se puede enviar (permiso de columna) ni ser otro (RLS)
        }
        assertTrue(remotas().isEmpty())
    }
}
