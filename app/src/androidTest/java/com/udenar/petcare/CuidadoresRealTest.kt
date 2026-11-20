package com.udenar.petcare

import com.udenar.petcare.data.sync.Sincronizador
import com.udenar.petcare.domain.compartir.RepositorioCompartir
import com.udenar.petcare.domain.compartir.ResultadoSolicitud
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Parte 3.1 contra el Supabase real: dueña y cuidador con cuentas temporales.
 * Prueba en el servidor cada regla de la sección 5 y al final borra ambas cuentas.
 */
@HiltAndroidTest
class CuidadoresRealTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var supabase: SupabaseClient
    @Inject lateinit var compartir: RepositorioCompartir
    @Inject lateinit var mascotas: RepositorioMascotas
    @Inject lateinit var sincronizador: Sincronizador

    private val marca = System.currentTimeMillis()
    private val dueña = "duena.$marca@petcare.test" to "Prueba-${UUID.randomUUID()}"
    private val cuidador = "cuidador.$marca@petcare.test" to "Prueba-${UUID.randomUUID()}"
    private val creadas = mutableListOf<Pair<String, String>>()

    private suspend fun registrar(cuenta: Pair<String, String>, nombre: String) {
        supabase.auth.clearSession()
        supabase.auth.signUpWith(Email) {
            email = cuenta.first
            password = cuenta.second
            data = buildJsonObject {
                put("nombre", nombre)
                put("acepto_privacidad", true)
            }
        }
        creadas += cuenta
    }

    private suspend fun entrar(cuenta: Pair<String, String>) {
        supabase.auth.clearSession()
        supabase.auth.signInWith(Email) {
            email = cuenta.first
            password = cuenta.second
        }
    }

    @Before
    fun preparar() = runBlocking {
        hilt.inject()
        val aplicado = try {
            supabase.from("miembros_mascota").select { limit(0) }
            true
        } catch (e: PostgrestRestException) {
            e.code != "PGRST205"
        }
        assumeTrue("Falta aplicar supabase/07_cuidadores.sql", aplicado)
    }

    @After
    fun limpiar() = runBlocking {
        for (c in creadas.reversed()) {
            runCatching {
                entrar(c)
                supabase.postgrest.rpc("eliminar_mi_cuenta")
            }
        }
        supabase.auth.clearSession()
    }

    private suspend fun filas(tabla: String): List<JsonObject> = supabase.from(tabla).select().decodeList()

    @Test
    fun cicloCompletoDeUnCuidador(): Unit = runBlocking {
        // Dueña: mascota, un cuidado y una visita (directo al servidor)
        registrar(dueña, "Dueña Prueba")
        val mascotaId = UUID.randomUUID().toString()
        val eventoId = UUID.randomUUID().toString()
        supabase.from("mascotas").insert(buildJsonObject { put("id", mascotaId); put("nombre", "Nala"); put("especie", "GATO") })
        supabase.from("eventos_cuidado").insert(buildJsonObject {
            put("id", eventoId); put("mascota_id", mascotaId); put("tipo", "COMIDA"); put("titulo", "Cena")
            put("frecuencia_tipo", "DIARIO"); put("ancla", "CALENDARIO"); put("fecha_inicio", LocalDate.now().toString())
        })
        supabase.from("visitas").insert(buildJsonObject {
            put("id", UUID.randomUUID().toString()); put("mascota_id", mascotaId); put("fecha", LocalDate.now().toString()); put("motivo", "Privada")
        })
        val codigo = filas("codigos_mascota").single()["codigo"]!!.jsonPrimitive.content

        // Cuidador: el código solo revela nombre y especie; no da acceso
        registrar(cuidador, "Cuidador Prueba")
        val encontrada = compartir.buscar(codigo)
        assertEquals("Nala", encontrada!!.nombre)
        assertTrue(filas("mascotas").isEmpty())
        assertTrue("No puede listar códigos", filas("codigos_mascota").isEmpty())
        assertEquals(ResultadoSolicitud.PENDIENTE, compartir.solicitar(codigo))
        assertEquals(ResultadoSolicitud.PENDIENTE, compartir.solicitar(codigo)) // no duplica
        val solicitudId = compartir.misSolicitudes().single().id
        try {
            compartir.resolver(solicitudId, true)
            fail("El cuidador no puede aprobarse a sí mismo")
        } catch (_: Exception) {
        }

        // Dueña aprueba
        entrar(dueña)
        assertEquals("Cuidador Prueba", compartir.solicitudesRecibidas().single().solicitanteNombre)
        compartir.resolver(solicitudId, true)

        // Cuidador: ve la mascota y sus cuidados; registra; no edita ni ve visitas ni el código
        entrar(cuidador)
        assertEquals(1, filas("mascotas").size)
        assertEquals(1, filas("eventos_cuidado").size)
        assertTrue("Las visitas no se comparten", filas("visitas").isEmpty())
        assertTrue("El código no se comparte", filas("codigos_mascota").isEmpty())
        val registroId = UUID.randomUUID().toString()
        supabase.from("registros_cuidado").insert(buildJsonObject {
            put("id", registroId); put("mascota_id", mascotaId); put("evento_id", eventoId)
            put("fecha_programada", LocalDate.now().atTime(20, 0).toString()); put("estado", "REALIZADA")
            put("registrado_en", java.time.Instant.now().toString())
        })
        assertEquals("CUIDADOR", filas("registros_cuidado").single()["registrado_por_rol"]!!.jsonPrimitive.content)
        supabase.from("mascotas").update(buildJsonObject { put("nombre", "Hackeada") }) { filter { eq("id", mascotaId) } }
        assertEquals("Nala", filas("mascotas").single()["nombre"]!!.jsonPrimitive.content)
        try {
            supabase.from("eventos_cuidado").insert(buildJsonObject {
                put("id", UUID.randomUUID().toString()); put("mascota_id", mascotaId); put("tipo", "COMIDA"); put("titulo", "Intruso")
                put("frecuencia_tipo", "DIARIO"); put("ancla", "CALENDARIO"); put("fecha_inicio", LocalDate.now().toString())
            })
            fail("Un cuidador no crea cuidados")
        } catch (_: PostgrestRestException) {
        }

        // En su teléfono: al sincronizar, la mascota aparece como "Cuidas a…"
        compartir.actualizarMembresias()
        sincronizador.sincronizar()
        assertNotNull(mascotas.obtener(mascotaId))

        // Dueña la quita; en el teléfono del cuidador desaparece
        entrar(dueña)
        val idCuidador = compartir.miembros(mascotaId).single { it.nombre == "Cuidador Prueba" }.usuarioId
        compartir.quitar(mascotaId, idCuidador)
        entrar(cuidador)
        assertTrue(filas("mascotas").isEmpty())
        compartir.actualizarMembresias()
        assertNull(mascotas.obtener(mascotaId))

        // Regenerar invalida el anterior; desactivar deja el código sin uso
        entrar(dueña)
        val nuevo = compartir.regenerarCodigo(mascotaId)
        entrar(cuidador)
        assertNull(compartir.buscar(codigo))
        assertNotNull(compartir.buscar(nuevo))
        entrar(dueña)
        compartir.desactivarCodigo(mascotaId)
        entrar(cuidador)
        assertNull(compartir.buscar(nuevo))
        assertEquals(ResultadoSolicitud.NO_DISPONIBLE, compartir.solicitar(nuevo))
        assertEquals(codigo.length, 8)
        first()
    }

    private suspend fun first() = mascotas.observar().first()
}
