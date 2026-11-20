package com.udenar.petcare.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Preferencias de este teléfono por mascota (no se sincronizan: cada miembro decide, RF-14). */
@Entity(tableName = "preferencias_mascota")
data class PreferenciaMascota(
    @PrimaryKey val mascotaId: String,
    val recordatorios: Boolean,
)

@Dao
interface PreferenciaDao {
    @Query("SELECT mascotaId FROM preferencias_mascota WHERE recordatorios = 0")
    fun silenciadas(): Flow<List<String>>

    @Query("SELECT mascotaId FROM preferencias_mascota WHERE recordatorios = 0")
    suspend fun silenciadasAhora(): List<String>

    @Upsert
    suspend fun guardar(p: PreferenciaMascota)
}

/** Qué muestra el QR de cada mascota (RF-18). Lo decide el propietario en este teléfono. */
@Entity(tableName = "config_qr")
data class ConfigQrEntidad(
    @PrimaryKey val mascotaId: String,
    /** Nombres de CampoQr separados por coma. */
    val campos: String,
    val contacto: String,
)

@Dao
interface ConfigQrDao {
    @Query("SELECT * FROM config_qr WHERE mascotaId = :mascotaId")
    fun observar(mascotaId: String): Flow<ConfigQrEntidad?>

    @Upsert
    suspend fun guardar(c: ConfigQrEntidad)
}
