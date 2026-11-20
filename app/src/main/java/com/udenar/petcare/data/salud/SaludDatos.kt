package com.udenar.petcare.data.salud

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.udenar.petcare.data.sync.Pendiente
import com.udenar.petcare.data.sync.TablaSincronizable
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import javax.inject.Inject

private val json = Json { ignoreUnknownKeys = true }

@Entity(tableName = "pesos", indices = [Index("mascotaId")])
data class PesoEntidad(
    @PrimaryKey val id: String,
    val mascotaId: String,
    val fecha: String,
    val gramos: Int,
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

@Entity(tableName = "visitas", indices = [Index("mascotaId")])
data class VisitaEntidad(
    @PrimaryKey val id: String,
    val mascotaId: String,
    val fecha: String,
    val motivo: String,
    val veterinario: String?,
    val clinica: String?,
    val tratamiento: String?,
    val medicamentos: String?,
    val notas: String?,
    val proximaCitaId: String?,
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
interface PesoDao {
    @Query("SELECT * FROM pesos WHERE mascotaId = :mascotaId AND eliminado = 0 ORDER BY fecha DESC, registradoEn DESC")
    fun observarDe(mascotaId: String): Flow<List<PesoEntidad>>

    @Query("SELECT MAX(fecha) FROM pesos WHERE mascotaId = :mascotaId AND eliminado = 0")
    suspend fun ultimaFecha(mascotaId: String): String?

    @Query("SELECT * FROM pesos WHERE id = :id")
    suspend fun porId(id: String): PesoEntidad?

    @Upsert suspend fun guardar(p: PesoEntidad)

    @Query("SELECT * FROM pesos WHERE pendiente = 1") suspend fun pendientes(): List<PesoEntidad>

    @Query("SELECT COUNT(*) FROM pesos WHERE pendiente = 1") fun contarPendientes(): Flow<Int>

    @Query("UPDATE pesos SET pendiente = 0, actualizadoEn = :a WHERE id = :id AND cambioLocal = :c")
    suspend fun confirmar(id: String, c: Long, a: String)

    @Query("DELETE FROM pesos WHERE eliminado = 1 AND pendiente = 0") suspend fun purgarEliminados()

    @Query("DELETE FROM pesos WHERE id = :id") suspend fun borrar(id: String)

    @Transaction
    suspend fun aplicarRemotos(filas: List<PesoEntidad>) {
        for (r in filas) {
            if (porId(r.id)?.pendiente == true) continue
            if (r.eliminado) borrar(r.id) else guardar(r)
        }
    }
}

@Dao
interface VisitaDao {
    @Query("SELECT * FROM visitas WHERE mascotaId = :mascotaId AND eliminado = 0 ORDER BY fecha DESC, registradoEn DESC")
    fun observarDe(mascotaId: String): Flow<List<VisitaEntidad>>

    @Query("SELECT * FROM visitas WHERE id = :id")
    suspend fun porId(id: String): VisitaEntidad?

    @Upsert suspend fun guardar(v: VisitaEntidad)

    @Query("SELECT * FROM visitas WHERE pendiente = 1") suspend fun pendientes(): List<VisitaEntidad>

    @Query("SELECT COUNT(*) FROM visitas WHERE pendiente = 1") fun contarPendientes(): Flow<Int>

    @Query("UPDATE visitas SET pendiente = 0, actualizadoEn = :a WHERE id = :id AND cambioLocal = :c")
    suspend fun confirmar(id: String, c: Long, a: String)

    @Query("DELETE FROM visitas WHERE eliminado = 1 AND pendiente = 0") suspend fun purgarEliminados()

    @Query("DELETE FROM visitas WHERE id = :id") suspend fun borrar(id: String)

    @Transaction
    suspend fun aplicarRemotos(filas: List<VisitaEntidad>) {
        for (r in filas) {
            if (porId(r.id)?.pendiente == true) continue
            if (r.eliminado) borrar(r.id) else guardar(r)
        }
    }
}

@Serializable
private data class PesoRemoto(
    val id: String,
    @SerialName("mascota_id") val mascotaId: String,
    val fecha: String,
    val gramos: Int,
    val notas: String? = null,
    @SerialName("registrado_por") val registradoPor: String? = null,
    @SerialName("registrado_por_nombre") val registradoPorNombre: String? = null,
    @SerialName("registrado_por_rol") val registradoPorRol: String? = null,
    @SerialName("registrado_en") val registradoEn: String,
    val eliminado: Boolean = false,
    @SerialName("actualizado_en") val actualizadoEn: String? = null,
) {
    fun comoEntidad() = PesoEntidad(id, mascotaId, fecha, gramos, notas, registradoPor, registradoPorNombre, registradoPorRol, registradoEn, eliminado, actualizadoEn, false, 0)
}

@Serializable
private data class VisitaRemota(
    val id: String,
    @SerialName("mascota_id") val mascotaId: String,
    val fecha: String,
    val motivo: String,
    val veterinario: String? = null,
    val clinica: String? = null,
    val tratamiento: String? = null,
    val medicamentos: String? = null,
    val notas: String? = null,
    @SerialName("proxima_cita_id") val proximaCitaId: String? = null,
    @SerialName("registrado_por") val registradoPor: String? = null,
    @SerialName("registrado_por_nombre") val registradoPorNombre: String? = null,
    @SerialName("registrado_por_rol") val registradoPorRol: String? = null,
    @SerialName("registrado_en") val registradoEn: String,
    val eliminado: Boolean = false,
    @SerialName("actualizado_en") val actualizadoEn: String? = null,
) {
    fun comoEntidad() = VisitaEntidad(
        id, mascotaId, fecha, motivo, veterinario, clinica, tratamiento, medicamentos, notas, proximaCitaId,
        registradoPor, registradoPorNombre, registradoPorRol, registradoEn, eliminado, actualizadoEn, false, 0,
    )
}

/** Depende de mascotas (orden 1, junto con los cuidados). */
class TablaPesos @Inject constructor(private val dao: PesoDao) : TablaSincronizable {
    override val tabla = "pesos"
    override val orden = 1
    override suspend fun pendientes() = dao.pendientes().map {
        Pendiente(it.id, it.cambioLocal, buildJsonObject {
            put("id", it.id); put("mascota_id", it.mascotaId); put("fecha", it.fecha); put("gramos", it.gramos)
            put("notas", it.notas); put("registrado_en", it.registradoEn); put("eliminado", it.eliminado)
        })
    }
    override suspend fun confirmar(id: String, cambioLocal: Long, actualizadoEn: String) {
        dao.confirmar(id, cambioLocal, actualizadoEn)
        dao.purgarEliminados()
    }
    override suspend fun aplicarRemotas(filas: List<JsonObject>) =
        dao.aplicarRemotos(filas.map { json.decodeFromJsonElement<PesoRemoto>(it).comoEntidad() })
}

/** Va después de los cuidados porque puede apuntar a la próxima cita (orden 2). */
class TablaVisitas @Inject constructor(private val dao: VisitaDao) : TablaSincronizable {
    override val tabla = "visitas"
    override val orden = 2
    override suspend fun pendientes() = dao.pendientes().map {
        Pendiente(it.id, it.cambioLocal, buildJsonObject {
            put("id", it.id); put("mascota_id", it.mascotaId); put("fecha", it.fecha); put("motivo", it.motivo)
            put("veterinario", it.veterinario); put("clinica", it.clinica); put("tratamiento", it.tratamiento)
            put("medicamentos", it.medicamentos); put("notas", it.notas); put("proxima_cita_id", it.proximaCitaId)
            put("registrado_en", it.registradoEn); put("eliminado", it.eliminado)
        })
    }
    override suspend fun confirmar(id: String, cambioLocal: Long, actualizadoEn: String) {
        dao.confirmar(id, cambioLocal, actualizadoEn)
        dao.purgarEliminados()
    }
    override suspend fun aplicarRemotas(filas: List<JsonObject>) =
        dao.aplicarRemotos(filas.map { json.decodeFromJsonElement<VisitaRemota>(it).comoEntidad() })
}
