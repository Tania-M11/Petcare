package com.udenar.petcare.data.documentos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.udenar.petcare.data.compartir.Propiedad
import com.udenar.petcare.data.sync.Pendiente
import com.udenar.petcare.data.sync.TablaSincronizable
import com.udenar.petcare.domain.cuenta.RepositorioCuenta
import com.udenar.petcare.domain.documentos.Documento
import com.udenar.petcare.domain.documentos.DocumentoException
import com.udenar.petcare.domain.documentos.RepositorioDocumentos
import com.udenar.petcare.domain.documentos.TipoDocumento
import com.udenar.petcare.domain.sync.ProgramadorSync
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Entity(tableName = "documentos", indices = [Index("mascotaId")])
data class DocumentoEntidad(
    @PrimaryKey val id: String,
    val mascotaId: String,
    val nombre: String,
    val tipo: String,
    val fecha: String,
    val mime: String,
    val tamanoBytes: Int,
    val ruta: String?,
    val visitaId: String?,
    val eventoId: String?,
    val registradoPor: String?,
    val registradoPorNombre: String?,
    val registradoEn: String,
    val eliminado: Boolean,
    val actualizadoEn: String?,
    val pendiente: Boolean,
    val cambioLocal: Long,
    val archivoLocal: String?,
    @ColumnInfo(defaultValue = "0") val archivoPendiente: Boolean,
)

@Dao
interface DocumentoDao {
    @Query("SELECT * FROM documentos WHERE mascotaId = :mascotaId AND eliminado = 0 ORDER BY fecha DESC, registradoEn DESC")
    fun observarDe(mascotaId: String): Flow<List<DocumentoEntidad>>

    @Query("SELECT * FROM documentos WHERE id = :id") suspend fun porId(id: String): DocumentoEntidad?

    @Upsert suspend fun guardar(d: DocumentoEntidad)

    @Query("SELECT * FROM documentos WHERE pendiente = 1") suspend fun pendientes(): List<DocumentoEntidad>

    @Query("SELECT * FROM documentos WHERE archivoPendiente = 1 AND eliminado = 0") suspend fun archivosPendientes(): List<DocumentoEntidad>

    @Query("SELECT COUNT(*) FROM documentos WHERE pendiente = 1") fun contarPendientes(): Flow<Int>

    @Query("UPDATE documentos SET pendiente = 0, actualizadoEn = :a WHERE id = :id AND cambioLocal = :c")
    suspend fun confirmar(id: String, c: Long, a: String)

    @Query("UPDATE documentos SET ruta = :ruta, archivoPendiente = 0 WHERE id = :id") suspend fun archivoSubido(id: String, ruta: String)

    @Query("UPDATE documentos SET archivoLocal = :local WHERE id = :id") suspend fun archivoDescargado(id: String, local: String)

    @Query("DELETE FROM documentos WHERE eliminado = 1 AND pendiente = 0") suspend fun purgarEliminados()

    @Query("DELETE FROM documentos WHERE id = :id") suspend fun borrar(id: String)

    @Query("DELETE FROM documentos WHERE mascotaId = :m") suspend fun borrarDeMascota(m: String)

    @Transaction
    suspend fun aplicarRemotos(filas: List<DocumentoEntidad>) {
        for (r in filas) {
            val local = porId(r.id)
            if (local?.pendiente == true) continue
            if (r.eliminado) borrar(r.id) else guardar(r.copy(archivoLocal = local?.archivoLocal?.takeIf { local.ruta == r.ruta }))
        }
    }
}

@Serializable
private data class DocumentoRemoto(
    val id: String,
    @SerialName("mascota_id") val mascotaId: String,
    val nombre: String,
    val tipo: String,
    val fecha: String,
    val mime: String,
    @SerialName("tamano_bytes") val tamanoBytes: Int,
    val ruta: String? = null,
    @SerialName("visita_id") val visitaId: String? = null,
    @SerialName("evento_id") val eventoId: String? = null,
    @SerialName("registrado_por") val registradoPor: String? = null,
    @SerialName("registrado_por_nombre") val registradoPorNombre: String? = null,
    @SerialName("registrado_en") val registradoEn: String,
    val eliminado: Boolean = false,
    @SerialName("actualizado_en") val actualizadoEn: String? = null,
) {
    fun comoEntidad() = DocumentoEntidad(
        id, mascotaId, nombre, tipo, fecha, mime, tamanoBytes, ruta, visitaId, eventoId, registradoPor, registradoPorNombre,
        registradoEn, eliminado, actualizadoEn, pendiente = false, cambioLocal = 0, archivoLocal = null, archivoPendiente = false,
    )
}

private val json = Json { ignoreUnknownKeys = true }
private const val BUCKET = "documentos"

/** Sube primero los archivos (para tener su ruta) y luego las filas; va después de visitas (orden 3). */
class TablaDocumentos @Inject constructor(
    private val dao: DocumentoDao,
    private val supabase: SupabaseClient,
    private val propiedad: Propiedad,
) : TablaSincronizable {
    override val tabla = "documentos"
    override val orden = 3

    override suspend fun antesDeSubir() {
        val yo = supabase.auth.currentUserOrNull()?.id ?: return
        for (d in dao.archivosPendientes()) {
            if (!propiedad.esMia(d.mascotaId)) continue
            val archivo = d.archivoLocal?.let { File(it) }?.takeIf { it.exists() } ?: continue
            val ext = if (d.mime == "application/pdf") "pdf" else "jpg"
            val ruta = "$yo/${d.mascotaId}/${d.id}.$ext"
            supabase.storage.from(BUCKET).upload(ruta, withContext(Dispatchers.IO) { archivo.readBytes() }) {
                upsert = true
                contentType = ContentType.parse(d.mime)
            }
            dao.archivoSubido(d.id, ruta)
        }
        // Documentos eliminados: se borra también el archivo (Storage no se borra desde SQL)
        for (d in dao.pendientes().filter { it.eliminado && it.ruta != null && propiedad.esMia(it.mascotaId) }) {
            runCatching { supabase.storage.from(BUCKET).delete(d.ruta!!) }
        }
    }

    override suspend fun pendientes() = dao.pendientes().filter { propiedad.esMia(it.mascotaId) && !it.archivoPendiente }.map {
        Pendiente(it.id, it.cambioLocal, buildJsonObject {
            put("id", it.id); put("mascota_id", it.mascotaId); put("nombre", it.nombre); put("tipo", it.tipo)
            put("fecha", it.fecha); put("mime", it.mime); put("tamano_bytes", it.tamanoBytes); put("ruta", it.ruta)
            put("visita_id", it.visitaId); put("evento_id", it.eventoId); put("registrado_en", it.registradoEn); put("eliminado", it.eliminado)
        })
    }

    override suspend fun confirmar(id: String, cambioLocal: Long, actualizadoEn: String) {
        dao.confirmar(id, cambioLocal, actualizadoEn)
        dao.purgarEliminados()
    }

    override suspend fun aplicarRemotas(filas: List<JsonObject>) =
        dao.aplicarRemotos(filas.map { json.decodeFromJsonElement<DocumentoRemoto>(it).comoEntidad() })
}

@Singleton
class RepositorioDocumentosLocal @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val dao: DocumentoDao,
    private val supabase: SupabaseClient,
    private val propiedad: Propiedad,
    private val cuenta: RepositorioCuenta,
    private val sync: ProgramadorSync,
    private val reloj: Clock,
) : RepositorioDocumentos {

    private val carpeta: File get() = File(contexto.filesDir, "documentos").apply { mkdirs() }

    override fun observar(mascotaId: String): Flow<List<Documento>> = dao.observarDe(mascotaId).map { l ->
        l.map {
            Documento(
                it.id, it.mascotaId, it.nombre, TipoDocumento.entries.firstOrNull { t -> t.name == it.tipo } ?: TipoDocumento.OTRO,
                LocalDate.parse(it.fecha), it.mime, it.tamanoBytes, it.visitaId,
                it.archivoLocal?.takeIf { f -> File(f).exists() }, it.registradoPorNombre, it.pendiente || it.archivoPendiente,
            )
        }
    }

    override suspend fun agregar(mascotaId: String, origen: String, nombre: String, tipo: TipoDocumento, fecha: LocalDate, visitaId: String?) {
        propiedad.exigir(mascotaId)
        val uri = Uri.parse(origen)
        val mimeOrigen = contexto.contentResolver.getType(uri).orEmpty()
        val id = UUID.randomUUID().toString()
        val (archivo, mime) = withContext(Dispatchers.IO) {
            if (mimeOrigen == "application/pdf") {
                val tam = contexto.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getLong(0) else -1L
                } ?: -1L
                if (tam > RepositorioDocumentos.MAXIMO_BYTES) throw DocumentoException("El PDF pesa más de 5 MB.")
                val destino = File(carpeta, "$id.pdf")
                contexto.contentResolver.openInputStream(uri)?.use { entrada -> destino.outputStream().use { entrada.copyTo(it) } }
                    ?: throw DocumentoException("No se pudo leer el archivo.")
                if (destino.length() > RepositorioDocumentos.MAXIMO_BYTES) {
                    destino.delete()
                    throw DocumentoException("El PDF pesa más de 5 MB.")
                }
                destino to "application/pdf"
            } else {
                File(carpeta, "$id.jpg").apply { writeBytes(comprimirImagen(uri)) } to "image/jpeg"
            }
        }
        val yo = cuenta.usuario.value
        dao.guardar(
            DocumentoEntidad(
                id, mascotaId, nombre.trim().ifBlank { tipo.etiqueta }.take(120), tipo.name, fecha.toString(), mime, archivo.length().toInt(),
                ruta = null, visitaId = visitaId, eventoId = null, registradoPor = yo?.id, registradoPorNombre = yo?.nombre,
                registradoEn = Instant.now(reloj).toString(), eliminado = false, actualizadoEn = null, pendiente = true,
                cambioLocal = reloj.millis(), archivoLocal = archivo.absolutePath, archivoPendiente = true,
            ),
        )
        sync.solicitar()
    }

    /** Imagen comprimida: lado mayor ≤ 1600 px, JPEG ~70 % (spec 11.1). */
    private fun comprimirImagen(uri: Uri): ByteArray {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contexto.contentResolver, uri)) { d, info, _ ->
                val lado = max(info.size.width, info.size.height)
                if (lado > LADO) {
                    val f = LADO.toFloat() / lado
                    d.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
                }
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            contexto.contentResolver.openInputStream(uri).use { android.graphics.BitmapFactory.decodeStream(it) }
                ?: throw DocumentoException("No se pudo leer la imagen.")
        }
        val escala = LADO.toFloat() / max(bitmap.width, bitmap.height)
        val final = if (escala < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * escala).toInt(), (bitmap.height * escala).toInt(), true) else bitmap
        return ByteArrayOutputStream().use { s ->
            final.compress(Bitmap.CompressFormat.JPEG, 70, s)
            s.toByteArray()
        }
    }

    override suspend fun eliminar(id: String) {
        val d = dao.porId(id) ?: return
        propiedad.exigir(d.mascotaId)
        d.archivoLocal?.let { File(it).delete() }
        dao.guardar(d.copy(eliminado = true, pendiente = true, archivoPendiente = false, archivoLocal = null, cambioLocal = maxOf(reloj.millis(), d.cambioLocal + 1)))
        sync.solicitar()
    }

    override suspend fun archivo(id: String): String? {
        val d = dao.porId(id) ?: return null
        d.archivoLocal?.takeIf { File(it).exists() }?.let { return it }
        val ruta = d.ruta ?: return null
        return try {
            val bytes = supabase.storage.from(BUCKET).downloadAuthenticated(ruta)
            val destino = File(carpeta, "${d.id}.${if (d.mime == "application/pdf") "pdf" else "jpg"}")
            withContext(Dispatchers.IO) { destino.writeBytes(bytes) }
            dao.archivoDescargado(d.id, destino.absolutePath)
            destino.absolutePath
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val LADO = 1600
    }
}
