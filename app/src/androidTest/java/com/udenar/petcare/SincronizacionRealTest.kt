package com.udenar.petcare

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.udenar.petcare.data.local.PetcareDb
import com.udenar.petcare.data.sync.Sincronizador
import com.udenar.petcare.domain.cuidados.EstadoRegistro
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.Frecuencia
import com.udenar.petcare.domain.cuidados.RepositorioCuidados
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.cuidados.TipoFrecuencia
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
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.cancel
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
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
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
    @Inject lateinit var cuidados: RepositorioCuidados
    @Inject lateinit var documentos: com.udenar.petcare.domain.documentos.RepositorioDocumentos
    @Inject lateinit var tiempoReal: com.udenar.petcare.data.sync.TiempoReal
    @Inject lateinit var programador: ProgramadorFalso
    @Inject lateinit var salud: com.udenar.petcare.domain.salud.RepositorioSalud

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
        val codigosAplicado = try {
            supabase.from("codigos_mascota").select { limit(0) }
            true
        } catch (e: PostgrestRestException) {
            e.code != "PGRST205" // tabla inexistente
        }
        assumeTrue("Falta aplicar supabase/04_fotos_y_codigos.sql", codigosAplicado)
        val cuidadosAplicado = try {
            supabase.from("registros_cuidado").select { limit(0) }
            true
        } catch (e: PostgrestRestException) {
            e.code != "PGRST205"
        }
        assumeTrue("Falta aplicar supabase/05_cuidados.sql", cuidadosAplicado)
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
        // Storage no permite borrar archivos desde SQL: se borra toda la carpeta de la cuenta por la API
        // (aunque la prueba haya fallado a mitad de camino)
        for (nombre in listOf("mascotas", "documentos")) runCatching {
            val bucket = supabase.storage.from(nombre)
            val raiz = supabase.auth.currentUserOrNull()!!.id
            val archivos = bucket.list(raiz).flatMap { carpeta -> bucket.list("$raiz/${carpeta.name}").map { "$raiz/${carpeta.name}/${it.name}" } }
            if (archivos.isNotEmpty()) bucket.delete(archivos)
        }
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

    /** Una foto JPEG pequeña creada en el momento. */
    private fun fotoDePrueba(): String {
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(240, 138, 75)) }
        val archivo = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "prueba_${UUID.randomUUID()}.jpg")
        archivo.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        return archivo.absolutePath
    }

    @Test
    fun cuidadosYRegistrosLleganConSuAutor() = runBlocking {
        val hoy = LocalDate.now()
        // Mascota, cuidado y registro creados sin sincronizar entre medio: el orden de subida evita errores
        val mascotaId = UUID.randomUUID().toString()
        val eventoId = UUID.randomUUID().toString()
        mascotas.guardar(Mascota(id = mascotaId, nombre = "Kira", especie = Especie.PERRO))
        cuidados.guardarEvento(
            EventoCuidado(
                eventoId, mascotaId, TipoCuidado.MEDICAMENTO, "Gotas",
                Frecuencia(TipoFrecuencia.DIARIO, horas = listOf(LocalTime.of(9, 0))), fechaInicio = hoy,
                detalle = mapOf("dosis" to "2 gotas"),
            ),
        )
        val toma = cuidados.agenda(hoy).first().single()
        cuidados.registrar(toma, EstadoRegistro.ADMINISTRADA)
        sincronizador.sincronizar()

        val yo = supabase.auth.currentUserOrNull()!!.id
        val evento = supabase.from("eventos_cuidado").select().decodeList<JsonObject>().single()
        assertEquals(yo, evento["creado_por"]!!.jsonPrimitive.content)
        assertEquals("Prueba sincronización", evento["creado_por_nombre"]!!.jsonPrimitive.content)
        assertEquals("PROPIETARIO", evento["creado_por_rol"]!!.jsonPrimitive.content)
        val registro = supabase.from("registros_cuidado").select().decodeList<JsonObject>().single()
        assertEquals("Prueba sincronización", registro["registrado_por_nombre"]!!.jsonPrimitive.content)

        // Otro teléfono marca la misma toma: mismo id, un solo registro (gana la última escritura)
        db.clearAllTables()
        sincronizador.sincronizar()
        val tomaOtroTelefono = cuidados.agenda(hoy).first().single()
        assertEquals("Prueba sincronización", tomaOtroTelefono.registro!!.registradoPor!!.nombre)
        cuidados.registrar(tomaOtroTelefono, EstadoRegistro.OMITIDA)
        sincronizador.sincronizar()
        val registros = supabase.from("registros_cuidado").select().decodeList<JsonObject>()
        assertEquals(1, registros.size)
        assertEquals("OMITIDA", registros.single()["estado"]!!.jsonPrimitive.content)

        // Nadie puede firmar por otro: el autor no se puede enviar desde la app
        try {
            supabase.from("registros_cuidado").update(buildJsonObject { put("registrado_por", UUID.randomUUID().toString()) }) {
                filter { eq("id", registros.single()["id"]!!.jsonPrimitive.content) }
            }
            fail("No debería poder cambiar el autor de un registro")
        } catch (_: PostgrestRestException) {
            // Esperado: sin permiso sobre esa columna
        }
        assertEquals(yo, supabase.from("registros_cuidado").select().decodeList<JsonObject>().single()["registrado_por"]!!.jsonPrimitive.content)
    }

    @Test
    fun pesoYVisitaSubenConSuAutor(): Unit = runBlocking {
        val mascotaId = UUID.randomUUID().toString()
        mascotas.guardar(Mascota(id = mascotaId, nombre = "Toby", especie = Especie.PERRO))
        salud.registrarPeso(mascotaId, LocalDate.now(), 12300)
        salud.guardarVisita(
            com.udenar.petcare.domain.salud.Visita(UUID.randomUUID().toString(), mascotaId, LocalDate.now(), "Control", tratamiento = "Ninguno"),
            LocalDate.now().plusDays(30).atTime(10, 0),
        )
        sincronizador.sincronizar()

        val peso = supabase.from("pesos").select().decodeList<JsonObject>().single()
        assertEquals("12300", peso["gramos"]!!.jsonPrimitive.content)
        assertEquals("Prueba sincronización", peso["registrado_por_nombre"]!!.jsonPrimitive.content)
        val visita = supabase.from("visitas").select().decodeList<JsonObject>().single()
        assertEquals("Control", visita["motivo"]!!.jsonPrimitive.content)
        // La próxima cita quedó como cuidado CITA_VET en el servidor
        val citaId = visita["proxima_cita_id"]!!.jsonPrimitive.content
        val cita = supabase.from("eventos_cuidado").select { filter { eq("id", citaId) } }.decodeList<JsonObject>().single()
        assertEquals("CITA_VET", cita["tipo"]!!.jsonPrimitive.content)
        // El peso de la ficha también subió
        assertEquals("12300", remotas().single()["peso_gramos"]!!.jsonPrimitive.content)
    }

    @Test
    fun unCambioEnElServidorLlegaEnVivo(): Unit = runBlocking {
        val antes = programador.solicitudes
        val alcance = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        tiempoReal.iniciar(alcance)
        kotlinx.coroutines.delay(4_000) // tiempo para suscribirse al canal
        // "Otro teléfono" crea una mascota directamente en el servidor
        supabase.from("mascotas").insert(buildJsonObject { put("id", UUID.randomUUID().toString()); put("nombre", "En vivo"); put("especie", "AVE") })
        var llego = false
        repeat(50) {
            if (!llego && programador.solicitudes > antes) llego = true
            if (!llego) kotlinx.coroutines.delay(200)
        }
        tiempoReal.detener()
        alcance.cancel()
        assertTrue("El cambio no llegó por Realtime", llego)
    }

    @Test
    fun documentosSubenBajanYSeBorran(): Unit = runBlocking {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val carpeta = File(contexto.cacheDir, "compartidos").apply { mkdirs() }
        val pdf = File(carpeta, "examen.pdf").apply { writeText(listOf("%PDF-1.4", "1 0 obj<<>>endobj", "trailer<<>>", "%%EOF").joinToString("\n")) }
        val uriPdf = androidx.core.content.FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", pdf)
        val uriImagen = androidx.core.content.FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", File(fotoDePrueba()).copyTo(File(carpeta, "carne.jpg"), overwrite = true))

        val mascotaId = UUID.randomUUID().toString()
        mascotas.guardar(Mascota(id = mascotaId, nombre = "Doc", especie = Especie.GATO))
        documentos.agregar(mascotaId, uriImagen.toString(), "Carné", com.udenar.petcare.domain.documentos.TipoDocumento.CARNE, LocalDate.now(), null)
        documentos.agregar(mascotaId, uriPdf.toString(), "Examen", com.udenar.petcare.domain.documentos.TipoDocumento.EXAMEN, LocalDate.now(), null)
        sincronizador.sincronizar()

        val remotos = supabase.from("documentos").select().decodeList<JsonObject>()
        assertEquals(setOf("image/jpeg", "application/pdf"), remotos.map { it["mime"]!!.jsonPrimitive.content }.toSet())
        assertTrue(remotos.all { it["ruta"]!!.jsonPrimitive.content.startsWith(supabase.auth.currentUserOrNull()!!.id + "/$mascotaId/") })

        // Teléfono nuevo: se descargan al abrirlos
        db.clearAllTables()
        sincronizador.sincronizar()
        val examen = documentos.observar(mascotaId).first().single { it.nombre == "Examen" }
        assertEquals(null, examen.archivoLocal)
        val ruta = documentos.archivo(examen.id)!!
        assertTrue(File(ruta).readText().startsWith("%PDF"))

        // Eliminar borra la fila (marca) y el archivo en Storage
        documentos.eliminar(examen.id)
        sincronizador.sincronizar()
        val rutaRemota = remotos.single { it["mime"]!!.jsonPrimitive.content == "application/pdf" }["ruta"]!!.jsonPrimitive.content
        assertTrue(supabase.storage.from("documentos").list("${supabase.auth.currentUserOrNull()!!.id}/$mascotaId").none { rutaRemota.endsWith(it.name) })
    }

    @Test
    fun fotoYCodigoLlegaAlServidorYAOtroTelefono() = runBlocking {
        val id = UUID.randomUUID().toString()
        mascotas.guardar(Mascota(id = id, nombre = "Max", especie = Especie.PERRO), fotoNueva = fotoDePrueba())
        sincronizador.sincronizar()

        // La foto quedó en Storage, en la carpeta del usuario, y la fila tiene su ruta
        val ruta = remotas().single()["foto_url"]!!.jsonPrimitive.content
        assertTrue(ruta.startsWith(supabase.auth.currentUserOrNull()!!.id + "/$id/"))
        assertTrue(supabase.storage.from("mascotas").downloadAuthenticated(ruta).isNotEmpty())

        // El servidor generó el código: 8 caracteres sin ambiguos
        val codigo = mascotas.codigo(id)!!
        assertTrue(codigo.codigo, Regex("^[A-HJKMNP-Z2-9]{8}$").matches(codigo.codigo))
        assertTrue(codigo.activo)

        // Teléfono nuevo: baja la fila sin foto local y la descarga al pedirla
        db.clearAllTables()
        sincronizador.sincronizar()
        assertEquals(null, mascotas.obtener(id)!!.fotoLocal)
        mascotas.asegurarFoto(id)
        assertTrue(File(mascotas.obtener(id)!!.fotoLocal!!).length() > 0)
    }

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
