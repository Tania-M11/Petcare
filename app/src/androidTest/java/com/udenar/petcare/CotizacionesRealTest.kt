package com.udenar.petcare

import com.udenar.petcare.data.profesionales.CotizacionesSupabase
import com.udenar.petcare.data.profesionales.PrestadoresSupabase
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.profesionales.ContactoCompartido
import com.udenar.petcare.domain.profesionales.EstadoSolicitudPres
import com.udenar.petcare.domain.profesionales.ModalidadPrestador
import com.udenar.petcare.domain.profesionales.ModoPrecio
import com.udenar.petcare.domain.profesionales.PerfilPrestador
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.Propuesta
import com.udenar.petcare.domain.profesionales.SaludCompartida
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
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

/**
 * Parte 4.5 contra el Supabase real: dueña y prestador temporales recorren las transiciones de
 * RF-35 y se comprueba en el servidor que lo compartido expira según el estado (RF-36).
 */
@HiltAndroidTest
class CotizacionesRealTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var supabase: SupabaseClient
    private lateinit var prestadores: PrestadoresSupabase
    private lateinit var cot: CotizacionesSupabase

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

    private suspend fun esperaFalla(bloque: suspend () -> Unit) {
        try {
            bloque()
            fail("Debió fallar")
        } catch (_: ProfesionalException) {
        }
    }

    @Before
    fun preparar() = runBlocking {
        hilt.inject()
        prestadores = PrestadoresSupabase(supabase)
        cot = CotizacionesSupabase(supabase)
        val aplicado = try {
            supabase.from("solicitudes_prestador").select { limit(0) }
            true
        } catch (e: PostgrestRestException) {
            e.code != "PGRST205"
        }
        assumeTrue("Falta aplicar supabase/15_cotizaciones.sql", aplicado)
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

    @Test
    fun cotizacionContratacionYPrivacidad(): Unit = runBlocking {
        val presId = registrar(pres, "Prestador Prueba")
        prestadores.guardar(
            PerfilPrestador(
                id = presId, nombre = "Paseos Prueba", descripcion = "Paseos grupales por el parque", telefono = "3001234567", ciudad = "Pasto",
                zona = "Centro", lat = 1.21, lon = -77.28, modalidades = setOf(ModalidadPrestador.DOMICILIO), especies = setOf(Especie.PERRO),
                categorias = listOf("PASEADOR"),
                servicios = listOf(ServicioPrestador("s1", "PASEADOR", "Paseo de 1 hora", modo = ModoPrecio.FIJO, montoCop = 15000, unidad = UnidadPrecio.POR_PASEO)),
            ),
        )

        registrar(duena, "Dueña Prueba")
        val mascotaId = UUID.randomUUID().toString()
        supabase.from("mascotas").insert(buildJsonObject { put("id", mascotaId); put("nombre", "Rocky"); put("especie", "PERRO"); put("raza", "Criollo"); put("alergias", "Secreta") })
        val manana = LocalDate.now().plusDays(1).atTime(LocalTime.of(16, 0))
        val salud = SaludCompartida(alergias = "Pollo", comportamiento = "Tira de la correa")
        val contacto = ContactoCompartido("3009998877", "Calle 5 # 10-20")
        val id1 = cot.solicitar(mascotaId, presId, "PASEADOR", "s1", ModalidadPrestador.DOMICILIO, manana, null, "Paseo por la tarde", null, salud, contacto)
        // Una abierta por mascota y prestador; modalidad no ofrecida se rechaza
        esperaFalla { cot.solicitar(mascotaId, presId, "PASEADOR", "s1", ModalidadPrestador.DOMICILIO, manana, null, "Otra", null, null, null) }

        // Prestador, PENDIENTE: ve resumen y salud, no el contacto ni la ficha
        entrar(pres)
        val p1 = cot.detalle(id1)
        assertEquals("Rocky", p1.solicitud.resumen.nombre)
        assertEquals("Criollo", p1.solicitud.resumen.raza)
        assertEquals("Pollo", p1.salud?.alergias)
        assertNull(p1.contacto)
        assertTrue(supabase.from("mascotas").select().decodeList<JsonObject>().isEmpty())
        // Precio fijo y sin cambios: puede aceptar directo; entonces ve el contacto
        cot.aceptar(id1)
        val p2 = cot.detalle(id1)
        assertEquals(EstadoSolicitudPres.ACEPTADA, p2.solicitud.estado)
        assertEquals("3009998877", p2.contacto?.telefono)
        // No puede completar un servicio que aún no empieza
        esperaFalla { cot.completar(id1) }

        // Dueña: el servicio quedó como recordatorio; al cancelar se elimina
        entrar(duena)
        val evento = supabase.from("eventos_cuidado").select { filter { eq("mascota_id", mascotaId) } }.decodeList<JsonObject>().single()
        assertEquals("SERVICIO_CONTRATADO", evento["tipo"]!!.jsonPrimitive.content)
        cot.cancelar(id1, "Cambio de planes")
        val eliminado = supabase.from("eventos_cuidado").select { filter { eq("mascota_id", mascotaId) } }.decodeList<JsonObject>().single()
        assertEquals("true", eliminado["eliminado"]!!.jsonPrimitive.content)

        // Prestador: al cancelarse, lo compartido expira (el historial queda)
        entrar(pres)
        val p3 = cot.detalle(id1)
        assertEquals(EstadoSolicitudPres.CANCELADA, p3.solicitud.estado)
        assertNull(p3.salud)
        assertNull(p3.contacto)
        assertTrue(p3.solicitud.historial.size >= 3)

        // Segunda solicitud "a cotizar": requiere cotización y confirmación de la dueña
        entrar(duena)
        val id2 = cot.solicitar(mascotaId, presId, "PASEADOR", null, ModalidadPrestador.DOMICILIO, manana, null, "Paseo largo", null, null, contacto)
        entrar(pres)
        esperaFalla { cot.aceptar(id2) }
        val propuesta = manana.plusDays(1)
        cot.cotizar(id2, Propuesta(25000, "Incluye hidratación", LocalDate.now().plusDays(5), propuesta, null))
        assertEquals(EstadoSolicitudPres.COTIZADA, cot.bandeja().first { it.id == id2 }.estado)
        // El prestador no puede confirmar su propia cotización
        esperaFalla { cot.confirmar(id2) }
        entrar(duena)
        cot.confirmar(id2)
        val aceptada = cot.misSolicitudes().first { it.id == id2 }
        assertEquals(EstadoSolicitudPres.ACEPTADA, aceptada.estado)
        assertEquals(propuesta, aceptada.inicio)
        // La dueña deja de compartir: el prestador ya no ve su contacto aunque esté aceptada
        cot.dejarDeCompartir(id2)
        entrar(pres)
        assertNull(cot.detalle(id2).contacto)
        assertNotNull(cot.detalle(id2).solicitud.cotizacionPrecio)
        cot.cancelar(id2, null)
        assertEquals(EstadoSolicitudPres.CANCELADA, cot.bandeja().first { it.id == id2 }.estado)
    }
}
