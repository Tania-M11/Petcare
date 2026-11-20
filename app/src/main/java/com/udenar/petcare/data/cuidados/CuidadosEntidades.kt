package com.udenar.petcare.data.cuidados

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Cuidado programable en el teléfono (espejo de public.eventos_cuidado). */
@Entity(tableName = "eventos_cuidado", indices = [Index("mascotaId")])
data class EventoEntidad(
    @PrimaryKey val id: String,
    val mascotaId: String,
    val tipo: String,
    val titulo: String,
    val frecuenciaTipo: String,
    val frecuenciaN: Int,
    /** "1,4" (ISO: lunes = 1). */
    val diasSemana: String,
    /** "08:00,20:00". */
    val horas: String,
    val ancla: String,
    val fechaInicio: String,
    val fechaFin: String?,
    val avisoAnticipadoDias: Int?,
    /** Objeto JSON con los campos propios del tipo. */
    val detalle: String,
    val origen: String,
    val origenRef: String?,
    val creadoPor: String?,
    val creadoPorNombre: String?,
    val creadoPorRol: String?,
    val oculto: Boolean,
    val eliminado: Boolean,
    val actualizadoEn: String?,
    val pendiente: Boolean,
    val cambioLocal: Long,
)

/** Registro de cumplimiento en el teléfono (espejo de public.registros_cuidado). */
@Entity(tableName = "registros_cuidado", indices = [Index("eventoId"), Index("fechaProgramada")])
data class RegistroEntidad(
    @PrimaryKey val id: String,
    val mascotaId: String,
    val eventoId: String,
    /** "2026-11-19T08:00" (hora local). */
    val fechaProgramada: String,
    val estado: String,
    val registradoPor: String?,
    val registradoPorNombre: String?,
    val registradoPorRol: String?,
    /** Instante ISO (UTC). */
    val registradoEn: String,
    val notas: String?,
    val eliminado: Boolean,
    val actualizadoEn: String?,
    val pendiente: Boolean,
    val cambioLocal: Long,
)

@Dao
interface EventoDao {
    /** Cuidados vigentes de mascotas que siguen existiendo. */
    @Query(
        "SELECT e.* FROM eventos_cuidado e JOIN mascotas m ON m.id = e.mascotaId " +
            "WHERE e.eliminado = 0 AND m.eliminado = 0 ORDER BY e.titulo COLLATE NOCASE",
    )
    fun observarActivos(): Flow<List<EventoEntidad>>

    @Query("SELECT * FROM eventos_cuidado WHERE id = :id")
    suspend fun porId(id: String): EventoEntidad?

    @Upsert
    suspend fun guardar(e: EventoEntidad)

    @Query("SELECT * FROM eventos_cuidado WHERE pendiente = 1")
    suspend fun pendientes(): List<EventoEntidad>

    @Query("SELECT COUNT(*) FROM eventos_cuidado WHERE pendiente = 1")
    fun contarPendientes(): Flow<Int>

    @Query("UPDATE eventos_cuidado SET pendiente = 0, actualizadoEn = :actualizadoEn WHERE id = :id AND cambioLocal = :cambioLocal")
    suspend fun confirmar(id: String, cambioLocal: Long, actualizadoEn: String)

    @Query("DELETE FROM eventos_cuidado WHERE eliminado = 1 AND pendiente = 0")
    suspend fun purgarEliminados()

    @Query("DELETE FROM eventos_cuidado WHERE id = :id")
    suspend fun borrar(id: String)

    @Transaction
    suspend fun aplicarRemotos(filas: List<EventoEntidad>) {
        for (r in filas) {
            if (porId(r.id)?.pendiente == true) continue
            if (r.eliminado) borrar(r.id) else guardar(r)
        }
    }
}

@Dao
interface RegistroDao {
    @Query("SELECT * FROM registros_cuidado WHERE eliminado = 0")
    fun observarActivos(): Flow<List<RegistroEntidad>>

    @Query("SELECT * FROM registros_cuidado WHERE id = :id")
    suspend fun porId(id: String): RegistroEntidad?

    @Upsert
    suspend fun guardar(r: RegistroEntidad)

    @Query("SELECT * FROM registros_cuidado WHERE pendiente = 1")
    suspend fun pendientes(): List<RegistroEntidad>

    @Query("SELECT COUNT(*) FROM registros_cuidado WHERE pendiente = 1")
    fun contarPendientes(): Flow<Int>

    @Query("UPDATE registros_cuidado SET pendiente = 0, actualizadoEn = :actualizadoEn WHERE id = :id AND cambioLocal = :cambioLocal")
    suspend fun confirmar(id: String, cambioLocal: Long, actualizadoEn: String)

    @Query("DELETE FROM registros_cuidado WHERE eliminado = 1 AND pendiente = 0")
    suspend fun purgarEliminados()

    @Query("DELETE FROM registros_cuidado WHERE id = :id")
    suspend fun borrar(id: String)

    @Transaction
    suspend fun aplicarRemotos(filas: List<RegistroEntidad>) {
        for (r in filas) {
            if (porId(r.id)?.pendiente == true) continue
            if (r.eliminado) borrar(r.id) else guardar(r)
        }
    }
}
