package com.udenar.petcare

import com.udenar.petcare.data.cuenta.RepositorioCuentaSupabase
import com.udenar.petcare.data.profesionales.AtencionVetSupabase
import com.udenar.petcare.data.profesionales.ResenasSupabase
import com.udenar.petcare.data.profesionales.VeterinariosSupabase
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.profesionales.Modalidad
import com.udenar.petcare.domain.profesionales.MotivoReporte
import com.udenar.petcare.domain.profesionales.PerfilVeterinario
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.ServicioVet
import com.udenar.petcare.domain.profesionales.TipoReporte
import com.udenar.petcare.domain.profesionales.TipoResena
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** Fase 5 contra el Supabase real: avisos, push, reseñas, reportes y eliminación de cuenta. */
@HiltAndroidTest
class CierreRealTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var supabase: SupabaseClient
    @Inject lateinit var cuenta: RepositorioCuentaSupabase

    private val marca = System.currentTimeMillis()
    private val vet = "vet.$marca@petcare.test" to "Prueba-${UUID.randomUUID()}"
    private val duena = "duena.$marca@petcare.test" to "Prueba-${UUID.randomUUID()}"
    private val creadas = mutableListOf<Pair<String, String>>()
    private val png = android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==", 0)

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

    private suspend fun avisos(): List<JsonObject> = supabase.from("notificaciones").select().decodeList()

    @Before
    fun preparar() = runBlocking {
        hilt.inject()
        val aplicado = try {
            supabase.from("resenas").select { limit(0) }
            true
        } catch (e: PostgrestRestException) {
            e.code != "PGRST205"
        }
        assumeTrue("Falta aplicar supabase/16_notificaciones_resenas.sql", aplicado)
    }

    @After
    fun limpiar() = runBlocking {
        for (c in creadas.reversed()) runCatching {
            entrar(c)
            supabase.postgrest.rpc("eliminar_mi_cuenta")
        }
        supabase.auth.clearSession()
    }

    @Test
    fun avisosResenasYReportes(): Unit = runBlocking {
        val vets = VeterinariosSupabase(supabase)
        val atencion = AtencionVetSupabase(supabase)
        val resenas = ResenasSupabase(supabase)
        val vetId = registrar(vet, "Vet Prueba")
        vets.guardar(
            PerfilVeterinario(
                id = vetId, nombre = "Dra. Prueba", especies = setOf(Especie.PERRO), servicios = setOf(ServicioVet.CONSULTA),
                telefono = "3001234567", ciudad = "Pasto", direccion = "Calle 18", lat = 1.21, lon = -77.28,
            ),
        )
        registrar(duena, "Dueña Prueba")
        val mascotaId = UUID.randomUUID().toString()
        supabase.from("mascotas").insert(buildJsonObject { put("id", mascotaId); put("nombre", "Toby"); put("especie", "PERRO") })
        val id = atencion.solicitar(mascotaId, vetId, ServicioVet.CONSULTA, Modalidad.CONSULTORIO, LocalDate.now().plusDays(1).atTime(9, 0), null)
        // Aún no se puede calificar: no está completada
        try {
            resenas.guardar(id, TipoResena.VET, 5, null)
            fail("Solo se califica lo completado")
        } catch (_: ProfesionalException) {
        }

        // El servidor avisa a la veterinaria (RF-29) y llama a la función de push una sola vez
        entrar(vet)
        val nueva = avisos().single()
        assertEquals("VET_NUEVA", nueva["tipo"]!!.jsonPrimitive.content)
        assertEquals("VETERINARIO", nueva["modo"]!!.jsonPrimitive.content)
        var enviado = "null"
        repeat(20) {
            if (enviado == "null") {
                delay(500)
                enviado = avisos().single()["push_enviado_en"]!!.toString()
            }
        }
        assertNotEquals("La Edge Function 'notificar' no procesó el aviso", "null", enviado)
        atencion.resolver(id, aceptar = true)
        atencion.completar(id)

        // La dueña recibió la confirmación y el cierre; ahora califica
        entrar(duena)
        val tipos = avisos().map { it["tipo"]!!.jsonPrimitive.content }.toSet()
        assertEquals(setOf("VET_ACEPTADA", "VET_COMPLETADA"), tipos)
        resenas.guardar(id, TipoResena.VET, 4, "Muy amable")
        assertEquals("4", supabase.from("veterinarios").select { filter { eq("id", vetId) } }.decodeSingle<JsonObject>()["rating_suma"]!!.jsonPrimitive.content)
        resenas.guardar(id, TipoResena.VET, 5, "Excelente") // editar: sigue siendo una
        val perfil = vets.obtener(vetId)!!
        assertEquals(5, perfil.ratingSuma)
        assertEquals(1, perfil.ratingCantidad)
        val lista = resenas.de(vetId, TipoResena.VET)
        assertEquals("Excelente", lista.single().comentario)
        assertTrue(lista.single().mia)
        assertEquals(setOf(id), resenas.mias().keys)
        // La app no puede escribir las calificaciones directamente
        try {
            supabase.from("veterinarios").update(buildJsonObject { put("rating_suma", 99) }) { filter { eq("id", vetId) } }
            fail("Las calificaciones solo las cambia el servidor")
        } catch (_: PostgrestRestException) {
        }

        // La veterinaria recibe aviso de la reseña, la ve y la puede reportar (una vez)
        entrar(vet)
        assertTrue(avisos().any { it["tipo"]!!.jsonPrimitive.content == "RESENA_NUEVA" })
        val resena = resenas.de(vetId, TipoResena.VET).single()
        assertTrue(!resena.mia)
        resenas.reportar(TipoReporte.RESENA, resena.id, MotivoReporte.FALSO, "Prueba")
        try {
            resenas.reportar(TipoReporte.RESENA, resena.id, MotivoReporte.SPAM, null)
            fail("Un reporte por persona")
        } catch (_: ProfesionalException) {
        }
        // Nadie más puede calificar esa solicitud
        try {
            resenas.guardar(id, TipoResena.VET, 1, "Falsa")
            fail("Solo el dueño de la solicitud califica")
        } catch (_: ProfesionalException) {
        }

        // La dueña elimina su reseña: el promedio vuelve a cero
        entrar(duena)
        resenas.eliminar(id)
        assertEquals(0, vets.obtener(vetId)!!.ratingCantidad)
    }

    @Test
    fun eliminarCuentaBorraArchivosYDatos(): Unit = runBlocking {
        val id = registrar(duena, "Dueña Prueba")
        val mascotaId = UUID.randomUUID().toString()
        supabase.from("mascotas").insert(buildJsonObject { put("id", mascotaId); put("nombre", "Luna"); put("especie", "GATO") })
        // Un archivo en cada carpeta propia
        supabase.storage.from("mascotas").upload("$id/$mascotaId/foto.jpg", png) { contentType = ContentType.Image.JPEG }
        supabase.storage.from("documentos").upload("$id/$mascotaId/${UUID.randomUUID()}.png", png) { contentType = ContentType.Image.PNG }
        supabase.storage.from("galeria").upload("$id/${UUID.randomUUID()}.jpg", png) { contentType = ContentType.Image.JPEG }
        assertEquals(1, supabase.storage.from("mascotas").list("$id/$mascotaId").size)

        cuenta.eliminarCuenta()
        creadas.clear() // ya no existe
        assertEquals(null, supabase.auth.currentSessionOrNull())
        // Ya no puede entrar
        try {
            entrar(duena)
            fail("La cuenta debería estar eliminada")
        } catch (_: Exception) {
        }
    }
}
