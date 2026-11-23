package com.udenar.petcare

import com.udenar.petcare.data.profesionales.AtencionVetSupabase
import com.udenar.petcare.data.profesionales.VeterinariosSupabase
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.documentos.TipoDocumento
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.profesionales.EstadoSolicitudVet
import com.udenar.petcare.domain.profesionales.Modalidad
import com.udenar.petcare.domain.profesionales.NuevoCuidadoClinico
import com.udenar.petcare.domain.profesionales.PerfilVeterinario
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.ServicioVet
import com.udenar.petcare.domain.profesionales.esVeterinario
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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

/**
 * Parte 4.3 contra el Supabase real: dueña y veterinaria temporales recorren una cita completa
 * y se prueban en el servidor los permisos del veterinario vinculado. Al final se borra todo.
 */
@HiltAndroidTest
class SolicitudesVetRealTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var supabase: SupabaseClient
    private lateinit var vets: VeterinariosSupabase
    private lateinit var atencion: AtencionVetSupabase

    private val marca = System.currentTimeMillis()
    private val vet = "vet.$marca@petcare.test" to "Prueba-${UUID.randomUUID()}"
    private val duena = "duena.$marca@petcare.test" to "Prueba-${UUID.randomUUID()}"
    private val creadas = mutableListOf<Pair<String, String>>()
    private val archivosVet = mutableListOf<String>()

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
        vets = VeterinariosSupabase(supabase)
        atencion = AtencionVetSupabase(supabase)
        val aplicado = try {
            supabase.from("solicitudes_vet").select { limit(0) }
            true
        } catch (e: PostgrestRestException) {
            e.code != "PGRST205"
        }
        assumeTrue("Falta aplicar supabase/13_solicitudes_vet.sql", aplicado)
    }

    @After
    fun limpiar() = runBlocking {
        // Primero los archivos que subió la veterinaria (la base de datos no puede borrarlos)
        runCatching {
            entrar(vet)
            if (archivosVet.isNotEmpty()) supabase.storage.from("documentos").delete(archivosVet)
        }
        for (c in creadas.reversed()) {
            runCatching {
                entrar(c)
                supabase.postgrest.rpc("eliminar_mi_cuenta")
            }
        }
        supabase.auth.clearSession()
    }

    @Test
    fun citaCompletaConPermisosDelVeterinario(): Unit = runBlocking {
        val vetId = registrar(vet, "Vet Prueba")
        vets.guardar(
            PerfilVeterinario(
                id = vetId, nombre = "Dra. Prueba", especies = setOf(Especie.GATO), servicios = setOf(ServicioVet.CONSULTA),
                telefono = "3001234567", ciudad = "Pasto", direccion = "Calle 18", lat = 1.21, lon = -77.28,
            ),
        )

        val duenaId = registrar(duena, "Dueña Prueba")
        val mascotaId = UUID.randomUUID().toString()
        supabase.from("mascotas").insert(buildJsonObject { put("id", mascotaId); put("nombre", "Nala"); put("especie", "GATO"); put("alergias", "Polen") })
        val manana = LocalDate.now().plusDays(1)
        val id = atencion.solicitar(mascotaId, vetId, ServicioVet.CONSULTA, Modalidad.CONSULTORIO, manana.atTime(10, 0), "Revisión anual")
        // Una sola pendiente por mascota y veterinario; servicios y domicilio se validan en el servidor
        try {
            atencion.solicitar(mascotaId, vetId, ServicioVet.CONSULTA, Modalidad.CONSULTORIO, manana.atTime(11, 0), null)
            fail("No debería duplicarse")
        } catch (_: ProfesionalException) {
        }
        try {
            atencion.solicitar(mascotaId, vetId, ServicioVet.CIRUGIA, Modalidad.DOMICILIO, manana.atTime(11, 0), null)
            fail("Servicio y modalidad no ofrecidos")
        } catch (_: ProfesionalException) {
        }
        assertEquals(EstadoSolicitudVet.PENDIENTE, atencion.misSolicitudes().single().estado)

        // Veterinaria: ve la solicitud, pero no la mascota hasta aceptar
        entrar(vet)
        val pendiente = atencion.bandeja().single()
        assertEquals("Nala", pendiente.mascotaNombre)
        assertEquals("Dueña Prueba", pendiente.propietarioNombre)
        assertTrue(supabase.from("mascotas").select().decodeList<JsonObject>().isEmpty())
        atencion.resolver(id, aceptar = true, fecha = manana.atTime(LocalTime.of(15, 30)))
        assertEquals(EstadoSolicitudVet.ACEPTADA, atencion.bandeja().single().estado)
        assertEquals("Nala", atencion.pacientes().single().mascota.nombre)
        val ficha = atencion.ficha(mascotaId)
        assertEquals("Polen", ficha.mascota.alergias)
        // La cita quedó como recordatorio para los miembros, firmada por la veterinaria
        val cita = ficha.cuidados.single { it.tipo == TipoCuidado.CITA_VET }
        assertEquals(manana, cita.fechaInicio)
        assertEquals(listOf(LocalTime.of(15, 30)), cita.frecuencia.horas)
        assertTrue(cita.creadoPor.esVeterinario)

        // RF-27: registra visita, vacuna, peso y documento
        atencion.registrarVisita(mascotaId, LocalDate.now(), "Control", "Reposo", null, null)
        atencion.registrarCuidado(mascotaId, NuevoCuidadoClinico(TipoCuidado.VACUNA, "Triple felina", manana.plusMonths(1), dosis = "1 ml"))
        atencion.registrarPeso(mascotaId, 4200)
        val png = android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==", 0)
        atencion.subirDocumento(mascotaId, "Examen", TipoDocumento.EXAMEN, png, "image/png")
        val conRegistros = atencion.ficha(mascotaId)
        archivosVet += conRegistros.documentos.mapNotNull { it.ruta }
        assertEquals(1, archivosVet.size)
        assertTrue(conRegistros.visitas.single().registradoPor.esVeterinario)
        assertEquals("Dra. Prueba", conRegistros.visitas.single().registradoPor?.nombre)
        // No puede crear cuidados que no son clínicos ni editar la mascota
        try {
            supabase.from("eventos_cuidado").insert(buildJsonObject {
                put("id", UUID.randomUUID().toString()); put("mascota_id", mascotaId); put("tipo", "BANO"); put("titulo", "Baño")
                put("frecuencia_tipo", "UNICO"); put("ancla", "CALENDARIO"); put("fecha_inicio", manana.toString()); put("origen", "VETERINARIO")
            })
            fail("Un veterinario no crea baños")
        } catch (e: PostgrestRestException) {
            assertEquals("42501", e.code)
        }
        supabase.from("mascotas").update(buildJsonObject { put("nombre", "Otra") }) { filter { eq("id", mascotaId) } }
        atencion.completar(id)

        // Dueña: ve lo registrado por la veterinaria y su documento
        entrar(duena)
        val visita = supabase.from("visitas").select { filter { eq("mascota_id", mascotaId) } }.decodeList<JsonObject>().single()
        assertEquals("VETERINARIO", visita["registrado_por_rol"]!!.jsonPrimitive.content)
        assertEquals("Nala", supabase.from("mascotas").select { filter { eq("id", mascotaId) } }.decodeSingle<JsonObject>()["nombre"]!!.jsonPrimitive.content)
        assertTrue(supabase.storage.from("documentos").downloadAuthenticated(archivosVet.single()).isNotEmpty())
        assertEquals(EstadoSolicitudVet.COMPLETADA, atencion.misSolicitudes().single().estado)
        try {
            atencion.cancelar(id)
            fail("Una cita completada no se cancela")
        } catch (_: ProfesionalException) {
        }
        assertEquals("Dra. Prueba", atencion.vetsDe(mascotaId).single().nombre)
        atencion.desvincular(mascotaId, vetId)
        assertTrue(atencion.vetsDe(mascotaId).isEmpty())
        assertEquals(duenaId, supabase.auth.currentUserOrNull()!!.id)

        // Sin vínculo, la veterinaria ya no ve la ficha
        entrar(vet)
        assertTrue(atencion.pacientes().isEmpty())
        try {
            atencion.ficha(mascotaId)
            fail("Sin vínculo no hay acceso")
        } catch (_: ProfesionalException) {
        }
    }
}
