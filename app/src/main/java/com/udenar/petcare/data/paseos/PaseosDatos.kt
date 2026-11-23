package com.udenar.petcare.data.paseos

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.udenar.petcare.data.sync.Pendiente
import com.udenar.petcare.data.sync.TablaSincronizable
import com.udenar.petcare.domain.cuenta.RepositorioCuenta
import com.udenar.petcare.domain.cuidados.Autor
import com.udenar.petcare.domain.paseos.Paseo
import com.udenar.petcare.domain.paseos.Polilinea
import com.udenar.petcare.domain.paseos.RepositorioPaseos
import com.udenar.petcare.domain.paseos.ResumenPaseo
import com.udenar.petcare.domain.paseos.TipoPaseo
import com.udenar.petcare.domain.sync.ProgramadorSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "paseos", indices = [Index("mascotaId")])
data class PaseoEntidad(
    @PrimaryKey val id: String,
    val mascotaId: String,
    val tipo: String,
    /** Instantes ISO (UTC). */
    val inicio: String,
    val fin: String,
    val duracionS: Int,
    val distanciaM: Int?,
    val rutaPolyline: String?,
    val esManual: Boolean,
    val notas: String?,
    val registradoPor: String?,
    val registradoPorNombre: String?,
    val registradoPorRol: String?,
    val registradoEn: String,
    val eliminado: Boolean,
    val actualizadoEn: String?,
    val pendiente: Boolean,
    val cambioLocal: Long,
)

@Dao
interface PaseoDao {
    @Query("SELECT * FROM paseos WHERE mascotaId = :mascotaId AND eliminado = 0 ORDER BY inicio DESC")
    fun observarDe(mascotaId: String): Flow<List<PaseoEntidad>>

    @Query("SELECT * FROM paseos WHERE id = :id") suspend fun porId(id: String): PaseoEntidad?

    @Query("SELECT * FROM paseos WHERE eliminado = 0 AND inicio >= :desde ORDER BY inicio DESC")
    fun observarDesde(desde: String): Flow<List<PaseoEntidad>>

    @Upsert suspend fun guardar(p: PaseoEntidad)

    @Query("SELECT * FROM paseos WHERE pendiente = 1") suspend fun pendientes(): List<PaseoEntidad>

    @Query("SELECT COUNT(*) FROM paseos WHERE pendiente = 1") fun contarPendientes(): Flow<Int>

    @Query("UPDATE paseos SET pendiente = 0, actualizadoEn = :a WHERE id = :id AND cambioLocal = :c")
    suspend fun confirmar(id: String, c: Long, a: String)

    @Query("DELETE FROM paseos WHERE eliminado = 1 AND pendiente = 0") suspend fun purgarEliminados()

    @Query("DELETE FROM paseos WHERE id = :id") suspend fun borrar(id: String)

    @Query("DELETE FROM paseos WHERE mascotaId = :m") suspend fun borrarDeMascota(m: String)

    @Transaction
    suspend fun aplicarRemotos(filas: List<PaseoEntidad>) {
        for (r in filas) {
            if (porId(r.id)?.pendiente == true) continue
            if (r.eliminado) borrar(r.id) else guardar(r)
        }
    }
}

@Serializable
private data class PaseoRemoto(
    val id: String,
    @SerialName("mascota_id") val mascotaId: String,
    val tipo: String,
    val inicio: String,
    val fin: String,
    @SerialName("duracion_s") val duracionS: Int,
    @SerialName("distancia_m") val distanciaM: Int? = null,
    @SerialName("ruta_polyline") val rutaPolyline: String? = null,
    @SerialName("es_manual") val esManual: Boolean = false,
    val notas: String? = null,
    @SerialName("registrado_por") val registradoPor: String? = null,
    @SerialName("registrado_por_nombre") val registradoPorNombre: String? = null,
    @SerialName("registrado_por_rol") val registradoPorRol: String? = null,
    @SerialName("registrado_en") val registradoEn: String,
    val eliminado: Boolean = false,
    @SerialName("actualizado_en") val actualizadoEn: String? = null,
) {
    fun comoEntidad() = PaseoEntidad(
        id, mascotaId, tipo, OffsetDateTime.parse(inicio).toInstant().toString(), OffsetDateTime.parse(fin).toInstant().toString(),
        duracionS, distanciaM, rutaPolyline, esManual, notas, registradoPor, registradoPorNombre, registradoPorRol, registradoEn,
        eliminado, actualizadoEn, pendiente = false, cambioLocal = 0,
    )
}

private val json = Json { ignoreUnknownKeys = true }

/** Depende de mascotas (orden 1). Propietario y cuidadores registran paseos. */
class TablaPaseos @Inject constructor(private val dao: PaseoDao) : TablaSincronizable {
    override val tabla = "paseos"
    override val orden = 1
    override suspend fun pendientes() = dao.pendientes().map {
        Pendiente(it.id, it.cambioLocal, buildJsonObject {
            put("id", it.id); put("mascota_id", it.mascotaId); put("tipo", it.tipo); put("inicio", it.inicio); put("fin", it.fin)
            put("duracion_s", it.duracionS); put("distancia_m", it.distanciaM); put("ruta_polyline", it.rutaPolyline)
            put("es_manual", it.esManual); put("notas", it.notas); put("registrado_en", it.registradoEn); put("eliminado", it.eliminado)
        })
    }
    override suspend fun confirmar(id: String, cambioLocal: Long, actualizadoEn: String) {
        dao.confirmar(id, cambioLocal, actualizadoEn)
        dao.purgarEliminados()
    }
    override suspend fun aplicarRemotas(filas: List<JsonObject>) =
        dao.aplicarRemotos(filas.map { json.decodeFromJsonElement<PaseoRemoto>(it).comoEntidad() })
}

@Singleton
class RepositorioPaseosLocal @Inject constructor(
    private val dao: PaseoDao,
    private val cuenta: RepositorioCuenta,
    private val sync: ProgramadorSync,
    private val reloj: Clock,
) : RepositorioPaseos {

    override fun observar(mascotaId: String): Flow<List<Paseo>> = dao.observarDe(mascotaId).map { l -> l.map { it.comoDominio() } }

    override fun observarDesde(desde: Instant): Flow<List<Paseo>> = dao.observarDesde(desde.toString()).map { l -> l.map { it.comoDominio() } }

    override suspend fun obtener(id: String): Paseo? = dao.porId(id)?.takeIf { !it.eliminado }?.comoDominio()

    override suspend fun guardarGps(resumen: ResumenPaseo, tipo: TipoPaseo) {
        val ruta = Polilinea.codificar(resumen.ruta)
        resumen.mascotaIds.forEach { m ->
            guardar(m, tipo, resumen.inicio, resumen.fin, resumen.duracionS, resumen.distanciaM, ruta, esManual = false, notas = null)
        }
        sync.solicitar()
    }

    override suspend fun guardarManual(mascotaIds: List<String>, tipo: TipoPaseo, inicio: Instant, duracionS: Int, distanciaM: Int?, notas: String?) {
        mascotaIds.forEach { m ->
            guardar(m, tipo, inicio, inicio.plusSeconds(duracionS.toLong()), duracionS, distanciaM, null, esManual = true, notas = notas?.trim()?.ifBlank { null })
        }
        sync.solicitar()
    }

    private suspend fun guardar(
        mascotaId: String, tipo: TipoPaseo, inicio: Instant, fin: Instant, duracionS: Int, distanciaM: Int?, ruta: String?, esManual: Boolean, notas: String?,
    ) {
        val yo = cuenta.usuario.value
        dao.guardar(
            PaseoEntidad(
                UUID.randomUUID().toString(), mascotaId, tipo.name, inicio.toString(), fin.toString(), duracionS, distanciaM, ruta, esManual,
                notas, yo?.id, yo?.nombre, null, Instant.now(reloj).toString(), false, null, true, reloj.millis(),
            ),
        )
    }

    override suspend fun eliminar(id: String) {
        val p = dao.porId(id) ?: return
        dao.guardar(p.copy(eliminado = true, pendiente = true, cambioLocal = maxOf(reloj.millis(), p.cambioLocal + 1)))
        sync.solicitar()
    }
}

private fun PaseoEntidad.comoDominio() = Paseo(
    id = id,
    mascotaId = mascotaId,
    tipo = TipoPaseo.entries.firstOrNull { it.name == tipo } ?: TipoPaseo.PASEO,
    inicio = Instant.parse(inicio),
    fin = Instant.parse(fin),
    duracionS = duracionS,
    distanciaM = distanciaM,
    ruta = rutaPolyline?.let { runCatching { Polilinea.decodificar(it) }.getOrDefault(emptyList()) }.orEmpty(),
    esManual = esManual,
    notas = notas,
    registradoPor = Autor(registradoPor, registradoPorNombre, registradoPorRol),
    porSubir = pendiente,
)
