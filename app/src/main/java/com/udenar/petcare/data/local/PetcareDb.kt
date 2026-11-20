package com.udenar.petcare.data.local

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.udenar.petcare.data.alarmas.AlarmaDao
import com.udenar.petcare.data.alarmas.AlarmaEntidad
import com.udenar.petcare.data.cuidados.EventoDao
import com.udenar.petcare.data.cuidados.EventoEntidad
import com.udenar.petcare.data.cuidados.RegistroDao
import com.udenar.petcare.data.cuidados.RegistroEntidad
import com.udenar.petcare.data.mascotas.MascotaDao
import com.udenar.petcare.data.salud.PesoDao
import com.udenar.petcare.data.salud.PesoEntidad
import com.udenar.petcare.data.salud.VisitaDao
import com.udenar.petcare.data.salud.VisitaEntidad
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

    /** Al empezar a cuidar una mascota nueva: la próxima sincronización descarga todo. */
    @Query("DELETE FROM cursores_sync")
    suspend fun reiniciar()

    @Query("SELECT MAX(sincronizadoEn) FROM cursores_sync")
    fun ultimaSincronizacion(): Flow<Long?>
}

/**
 * Base de datos del teléfono (Room): fuente de lo que se ve en pantalla y de las alarmas.
 * Cada parte agrega sus tablas subiendo la versión con una migración automática.
 */
@Database(
    entities = [MascotaEntidad::class, CursorEntidad::class, EventoEntidad::class, RegistroEntidad::class, AlarmaEntidad::class, PesoEntidad::class, VisitaEntidad::class, PreferenciaMascota::class, ConfigQrEntidad::class],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // 2.2: foto local de la mascota
        AutoMigration(from = 2, to = 3), // 2.3: cuidados y registros
        AutoMigration(from = 3, to = 4), // 2.4: alarmas programadas
        AutoMigration(from = 4, to = 5), // 2.8: pesos y visitas
        AutoMigration(from = 5, to = 6), // 3.2: recordatorios por mascota en este teléfono
        AutoMigration(from = 6, to = 7), // 3.3: configuración del QR y veterinario manual (RF-04)
    ],
)
abstract class PetcareDb : RoomDatabase() {
    abstract fun mascotas(): MascotaDao
    abstract fun cursores(): CursorDao
    abstract fun eventos(): EventoDao
    abstract fun registros(): RegistroDao
    abstract fun alarmas(): AlarmaDao
    abstract fun pesos(): PesoDao
    abstract fun visitas(): VisitaDao
    abstract fun preferencias(): PreferenciaDao
    abstract fun configQr(): ConfigQrDao
}
