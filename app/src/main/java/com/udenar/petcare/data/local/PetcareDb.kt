package com.udenar.petcare.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.udenar.petcare.data.mascotas.MascotaDao
import com.udenar.petcare.data.mascotas.MascotaEntidad
import kotlinx.coroutines.flow.Flow

/** Hasta dónde se descargó cada tabla del servidor y cuándo terminó la última sincronización. */
@Entity(tableName = "cursores_sync")
data class CursorEntidad(
    @PrimaryKey val tabla: String,
    val cursor: String?,
    val sincronizadoEn: Long,
)

@Dao
interface CursorDao {
    @Query("SELECT cursor FROM cursores_sync WHERE tabla = :tabla")
    suspend fun cursor(tabla: String): String?

    @Upsert
    suspend fun guardar(c: CursorEntidad)

    @Query("SELECT MAX(sincronizadoEn) FROM cursores_sync")
    fun ultimaSincronizacion(): Flow<Long?>
}

/**
 * Base de datos del teléfono (Room): fuente de lo que se ve en pantalla y de las alarmas.
 * Cada parte agrega sus tablas subiendo la versión con una migración automática.
 */
@Database(
    entities = [MascotaEntidad::class, CursorEntidad::class],
    version = 1,
    exportSchema = true,
)
abstract class PetcareDb : RoomDatabase() {
    abstract fun mascotas(): MascotaDao
    abstract fun cursores(): CursorDao
}
