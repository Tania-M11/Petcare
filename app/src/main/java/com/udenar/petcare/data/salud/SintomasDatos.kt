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
import com.udenar.petcare.domain.cuenta.RepositorioCuenta
import com.udenar.petcare.domain.cuidados.Autor
import com.udenar.petcare.domain.salud.RepositorioSintomas
import com.udenar.petcare.domain.salud.Sintoma
import com.udenar.petcare.domain.salud.TipoSintoma
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
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

private val jsonSintomas = Json { ignoreUnknownKeys = true }

@Entity(tableName = "sintomas", indices = [Index("mascotaId")])
data class SintomaEntidad(
    @PrimaryKey val id: String,
    val mascotaId: String,
    val tipo: String,
    /** Fecha y hora local ISO (yyyy-MM-ddTHH:mm). */
    val fechaHora: String,
    val descripcion: String?,
    val documentoId: String?,
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
interface SintomaDao {
    @Query("SELECT * FROM sintomas WHERE mascotaId = :mascotaId AND eliminado = 0 ORDER BY fechaHora DESC")
    fun observarDe(mascotaId: String): Flow<List<SintomaEntidad>>

    @Query("SELECT * FROM sintomas WHERE eliminado = 0 ORDER BY fechaHora DESC")
    fun observarTodos(): Flow<List<SintomaEntidad>>

    @Query("SELECT * FROM sintomas WHERE id = :id")
    suspend fun porId(id: String): SintomaEntidad?

    @Upsert suspend fun guardar(s: SintomaEntidad)

    @Query("SELECT * FROM sintomas WHERE pendiente = 1") suspend fun pendientes(): List<SintomaEntidad>

    @Query("UPDATE sintomas SET pendiente = 0, actualizadoEn = :a WHERE id = :id AND cambioLocal = :c")
    suspend fun confirmar(id: String, c: Long, a: String)

    @Query("DELETE FROM sintomas WHERE eliminado = 1 AND pendiente = 0") suspend fun purgarEliminados()

    @Query("DELETE FROM sintomas WHERE id = :id") suspend fun borrar(id: String)

    @Transaction
    suspend fun aplicarRemotos(filas: List<SintomaEntidad>) {
        for (r in filas) {
            if (porId(r.id)?.pendiente == true) continue
            if (r.eliminado) borrar(r.id) else guardar(r)
        }
    }
}

@Serializable
private data class SintomaRemoto(
    val id: String,
    @SerialName("mascota_id") val mascotaId: String,
    val tipo: String,
    @SerialName("fecha_hora") val fechaHora: String,
    val descripcion: String? = null,
    @SerialName("documento_id") val documentoId: String? = null,
    @SerialName("registrado_por") val registradoPor: String? = null,
    @SerialName("registrado_por_nombre") val registradoPorNombre: String? = null,
    @SerialName("registrado_por_rol") val registradoPorRol: String? = null,
    @SerialName("registrado_en") val registradoEn: String,
    val eliminado: Boolean = false,
    @SerialName("actualizado_en") val actualizadoEn: String? = null,
) {
    fun comoEntidad() = SintomaEntidad(
        id, mascotaId, tipo, fechaHora.take(16), descripcion, documentoId, registradoPor, registradoPorNombre, registradoPorRol,
        registradoEn, eliminado, actualizadoEn, false, 0,
    )
}

/** Depende de mascotas (orden 1). Lo sube quien lo anotó: dueño o cuidador. */
class TablaSintomas @Inject constructor(private val dao: SintomaDao) : TablaSincronizable {
    override val tabla = "sintomas"
    override val orden = 1
    override suspend fun pendientes() = dao.pendientes().map {
        Pendiente(it.id, it.cambioLocal, buildJsonObject {
            put("id", it.id); put("mascota_id", it.mascotaId); put("tipo", it.tipo); put("fecha_hora", it.fechaHora)
            put("descripcion", it.descripcion); put("documento_id", it.documentoId); put("registrado_en", it.registradoEn); put("eliminado", it.eliminado)
        })
    }
    override suspend fun confirmar(id: String, cambioLocal: Long, actualizadoEn: String) {
        dao.confirmar(id, cambioLocal, actualizadoEn)
        dao.purgarEliminados()
    }
    override suspend fun aplicarRemotas(filas: List<JsonObject>) =
        dao.aplicarRemotos(filas.map { jsonSintomas.decodeFromJsonElement<SintomaRemoto>(it).comoEntidad() })
}

@Singleton
class RepositorioSintomasLocal @Inject constructor(
    private val dao: SintomaDao,
    private val cuenta: RepositorioCuenta,
    private val sync: ProgramadorSync,
    private val reloj: Clock,
) : RepositorioSintomas {
    private fun SintomaEntidad.dominio() = Sintoma(
        id, mascotaId, TipoSintoma.entries.firstOrNull { it.name == tipo } ?: TipoSintoma.OTRO,
        LocalDateTime.parse(fechaHora), descripcion, documentoId, Autor(registradoPor, registradoPorNombre, registradoPorRol), pendiente,
    )

    override fun observar(mascotaId: String): Flow<List<Sintoma>> = dao.observarDe(mascotaId).map { l -> l.map { it.dominio() } }
    override fun observarTodos(): Flow<List<Sintoma>> = dao.observarTodos().map { l -> l.map { it.dominio() } }

    override suspend fun guardar(s: Sintoma) {
        val anterior = dao.porId(s.id)
        val yo = cuenta.usuario.value
        dao.guardar(
            SintomaEntidad(
                s.id, s.mascotaId, s.tipo.name, s.fechaHora.withSecond(0).withNano(0).toString().take(16), s.descripcion?.trim()?.ifBlank { null }?.take(1000),
                s.documentoId, anterior?.registradoPor ?: yo?.id, anterior?.registradoPorNombre ?: yo?.nombre, anterior?.registradoPorRol,
                anterior?.registradoEn ?: Instant.now(reloj).toString(), false, anterior?.actualizadoEn, true,
                maxOf(reloj.millis(), (anterior?.cambioLocal ?: 0) + 1),
            ),
        )
        sync.solicitar()
    }

    override suspend fun eliminar(id: String) {
        val s = dao.porId(id) ?: return
        dao.guardar(s.copy(eliminado = true, pendiente = true, cambioLocal = maxOf(reloj.millis(), s.cambioLocal + 1)))
        sync.solicitar()
    }
}
