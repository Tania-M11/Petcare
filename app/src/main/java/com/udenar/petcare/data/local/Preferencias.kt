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
