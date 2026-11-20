package com.udenar.petcare.data.mascotas

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Fila local de una mascota. Fechas y enums como texto, igual que en el servidor. */
@Entity(tableName = "mascotas")
data class MascotaEntidad(
    @PrimaryKey val id: String,
    val propietarioId: String?,
    val nombre: String,
    val especie: String,
    val especieOtro: String?,
    val raza: String?,
    val sexo: String,
    val fechaNacimiento: String?,
    val nacimientoAproximado: Boolean,
    val color: String?,
    val tamano: String?,
    val pesoGramos: Int?,
    val esterilizado: Boolean?,
    val notas: String?,
    val alergias: String?,
    val condiciones: String?,
    val medicamentosActuales: String?,
    val observaciones: String?,
    val fotoUrl: String?,
    val eliminado: Boolean,
    /** Hora del servidor de la última versión conocida (null = nunca subida). */
    val actualizadoEn: String?,
    /** Hay cambios locales sin subir. */
    val pendiente: Boolean,
    /** Momento de la última edición local; evita confirmar una versión ya reemplazada. */
    val cambioLocal: Long,
)

@Dao
interface MascotaDao {
    @Query("SELECT * FROM mascotas WHERE eliminado = 0 ORDER BY nombre COLLATE NOCASE")
    fun observarActivas(): Flow<List<MascotaEntidad>>

    @Query("SELECT * FROM mascotas WHERE id = :id")
    suspend fun porId(id: String): MascotaEntidad?

    @Upsert
    suspend fun guardar(m: MascotaEntidad)

    @Query("SELECT * FROM mascotas WHERE pendiente = 1")
    suspend fun pendientes(): List<MascotaEntidad>

    @Query("SELECT COUNT(*) FROM mascotas WHERE pendiente = 1")
    fun contarPendientes(): Flow<Int>

    @Query("UPDATE mascotas SET pendiente = 0, actualizadoEn = :actualizadoEn WHERE id = :id AND cambioLocal = :cambioLocal")
    suspend fun confirmar(id: String, cambioLocal: Long, actualizadoEn: String)

    /** Una eliminación ya confirmada por el servidor no necesita seguir en el teléfono. */
    @Query("DELETE FROM mascotas WHERE eliminado = 1 AND pendiente = 0")
    suspend fun purgarEliminadas()

    @Query("DELETE FROM mascotas WHERE id = :id")
    suspend fun borrar(id: String)

    /** Aplica filas del servidor sin pisar cambios locales pendientes. */
    @Transaction
    suspend fun aplicarRemotas(filas: List<MascotaEntidad>) {
        for (r in filas) {
            val local = porId(r.id)
            if (local?.pendiente == true) continue
            if (r.eliminado) borrar(r.id) else guardar(r)
        }
    }
}
