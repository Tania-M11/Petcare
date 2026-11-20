package com.udenar.petcare.data.mascotas

import androidx.room.ColumnInfo
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
    /** Copia de la foto en el teléfono (la elegida aquí o la descargada). */
    val fotoLocal: String? = null,
    /** La foto elegida aún no se sube a Storage. */
    @ColumnInfo(defaultValue = "0") val fotoPendiente: Boolean = false,
    /** Veterinario manual como objeto JSON (RF-04). */
    val veterinario: String? = null,
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

    @Query("SELECT * FROM mascotas WHERE fotoPendiente = 1 AND eliminado = 0")
    suspend fun fotosPendientes(): List<MascotaEntidad>

    @Query("UPDATE mascotas SET fotoUrl = :fotoUrl, fotoPendiente = 0 WHERE id = :id AND fotoLocal = :fotoLocal")
    suspend fun fotoSubida(id: String, fotoLocal: String, fotoUrl: String)

    @Query("UPDATE mascotas SET fotoLocal = :fotoLocal WHERE id = :id AND fotoUrl = :fotoUrl")
    suspend fun fotoDescargada(id: String, fotoUrl: String, fotoLocal: String)

    @Query("SELECT COUNT(*) FROM mascotas WHERE pendiente = 1")
    fun contarPendientes(): Flow<Int>

    @Query("UPDATE mascotas SET pendiente = 0, actualizadoEn = :actualizadoEn WHERE id = :id AND cambioLocal = :cambioLocal")
    suspend fun confirmar(id: String, cambioLocal: Long, actualizadoEn: String)

    /** Una eliminación ya confirmada por el servidor no necesita seguir en el teléfono. */
    @Query("DELETE FROM mascotas WHERE eliminado = 1 AND pendiente = 0")
    suspend fun purgarEliminadas()

    @Query("DELETE FROM mascotas WHERE id = :id")
    suspend fun borrar(id: String)

    /** Mascotas de otra persona guardadas en el teléfono (las que cuido). */
    @Query("SELECT id FROM mascotas")
    suspend fun ids(): List<String>

    @Query("SELECT id FROM mascotas WHERE propietarioId IS NOT NULL AND propietarioId != :yo")
    suspend fun ajenas(yo: String): List<String>

    /** Aplica filas del servidor sin pisar cambios locales pendientes. */
    @Transaction
    suspend fun aplicarRemotas(filas: List<MascotaEntidad>) {
        for (r in filas) {
            val local = porId(r.id)
            if (local?.pendiente == true) continue
            if (r.eliminado) {
                borrar(r.id)
            } else {
                // La copia local de la foto sirve mientras sea la misma foto del servidor
                val mismaFoto = local != null && local.fotoUrl == r.fotoUrl
                guardar(r.copy(fotoLocal = if (mismaFoto) local!!.fotoLocal else null))
            }
        }
    }
}
