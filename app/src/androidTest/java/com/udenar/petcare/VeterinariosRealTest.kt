package com.udenar.petcare

import com.udenar.petcare.data.profesionales.VeterinariosSupabase
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.profesionales.FiltroVeterinarios
import com.udenar.petcare.domain.profesionales.PerfilVeterinario
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.ServicioVet
import com.udenar.petcare.domain.profesionales.Verificacion
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
import kotlinx.serialization.json.JsonObject
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
import java.time.DayOfWeek
import java.util.UUID
import javax.inject.Inject

/**
 * Partes 4.1 y 4.2 contra el Supabase real: una veterinaria y una dueña temporales.
 * Al final borra el certificado y ambas cuentas.
 */
@HiltAndroidTest
class VeterinariosRealTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var supabase: SupabaseClient
    private lateinit var repo: VeterinariosSupabase

    private val marca = System.currentTimeMillis()
    private val vet = "vet.$marca@petcare.test" to "Prueba-${UUID.randomUUID()}"
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
        repo = VeterinariosSupabase(supabase)
        val aplicado = try {
            supabase.from("veterinarios").select { limit(0) }
            true
        } catch (e: PostgrestRestException) {
            e.code != "PGRST205"
        }
        assumeTrue("Falta aplicar supabase/12_veterinarios.sql", aplicado)
    }

    @After
    fun limpiar() = runBlocking {
        for (c in creadas.reversed()) {
            runCatching {
                entrar(c)
                val id = supabase.auth.currentUserOrNull()!!.id
                val bucket = supabase.storage.from("certificados")
                val archivos = runCatching { bucket.list(id).map { "$id/${it.name}" } }.getOrDefault(emptyList())
                if (archivos.isNotEmpty()) bucket.delete(archivos)
                supabase.postgrest.rpc("eliminar_mi_cuenta")
            }
        }
        supabase.auth.clearSession()
    }

    @Test
    fun perfilVerificacionYDirectorio(): Unit = runBlocking {
        val vetId = registrar(vet, "Vet Prueba")
        repo.guardar(
            PerfilVeterinario(
                id = vetId, nombre = "Vet Prueba", especies = setOf(Especie.PERRO, Especie.GATO), servicios = setOf(ServicioVet.CONSULTA, ServicioVet.VACUNACION),
                telefono = "300 123 4567", ciudad = "Pastó", direccion = "Calle 18 # 25-30", lat = 1.2136, lon = -77.2811,
                horario = mapOf(DayOfWeek.MONDAY to "8:00-12:00"), domicilio = true,
            ),
        )
        val mio = repo.cargarMiPerfil()!!
        assertEquals(Verificacion.SIN_CERTIFICADO, mio.verificacion)
        assertEquals("8:00-12:00", mio.horario[DayOfWeek.MONDAY])
        // El perfil activo marca al usuario como veterinario
        val usuario = supabase.from("usuarios").select { filter { eq("id", vetId) } }.decodeSingle<JsonObject>()
        assertEquals("true", usuario["es_veterinario"]!!.jsonPrimitive.content)

        // RF-23: la app no puede asignarse VERIFICADO
        try {
            supabase.from("veterinarios").update(buildJsonObject { put("verificacion", "VERIFICADO") }) { filter { eq("id", vetId) } }
            fail("No debería poder verificarse sola")
        } catch (e: PostgrestRestException) {
            assertEquals("42501", e.code)
        }
        // Un certificado con un formato no permitido se rechaza antes de subir
        try {
            repo.subirCertificado(byteArrayOf(1, 2, 3), "text/plain")
            fail("Formato no permitido")
        } catch (_: ProfesionalException) {
        }
        val png = android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==", 0)
        repo.subirCertificado(png, "image/png")
        assertEquals(Verificacion.EN_REVISION, repo.cargarMiPerfil()!!.verificacion)
        assertEquals(listOf("certificado.png"), supabase.storage.from("certificados").list(vetId).map { it.name })

        // Dueña: encuentra a la veterinaria por ciudad (sin tildes ni mayúsculas) y con distancia
        registrar(duena, "Dueña Prueba")
        val encontrados = repo.buscar(FiltroVeterinarios(ciudad = "PASTO", especie = Especie.GATO, domicilio = true), lat = 1.2200, lon = -77.2811)
        val v = encontrados.single { it.id == vetId }
        assertNotNull(v.distanciaKm)
        assertTrue(v.distanciaKm!! in 0.5..1.0)
        assertTrue(repo.buscar(FiltroVeterinarios(ciudad = "Pasto", soloVerificados = true)).none { it.id == vetId })
        assertTrue(repo.buscar(FiltroVeterinarios(ciudad = "Pasto", especie = Especie.AVE)).none { it.id == vetId })
        assertEquals("Vet Prueba", repo.obtener(vetId)!!.nombre)
        // El certificado es privado: la dueña no lo ve
        assertTrue(supabase.storage.from("certificados").list(vetId).isEmpty())

        // Al ocultarlo desaparece del directorio
        entrar(vet)
        repo.guardar(repo.cargarMiPerfil()!!.copy(activo = false))
        entrar(duena)
        assertTrue(repo.buscar(FiltroVeterinarios()).none { it.id == vetId })
    }
}
