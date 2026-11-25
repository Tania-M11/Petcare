package com.udenar.petcare.data.animo

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.udenar.petcare.domain.animo.Animo
import com.udenar.petcare.domain.animo.RegistroAnimo
import com.udenar.petcare.domain.animo.RepositorioAnimo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Ánimo anotado por día (solo en este teléfono). La clave une mascota y fecha. */
@Entity(tableName = "animos", indices = [Index("mascotaId")])
data class AnimoEntidad(
    @PrimaryKey val id: String,
    val mascotaId: String,
    /** ISO yyyy-MM-dd: ordena bien como texto. */
    val fecha: String,
    val animo: String,
)

@Dao
interface AnimoDao {
    @Query("SELECT * FROM animos WHERE mascotaId = :mascotaId AND fecha >= :desde ORDER BY fecha")
    fun observar(mascotaId: String, desde: String): Flow<List<AnimoEntidad>>

    @Query("SELECT * FROM animos WHERE fecha >= :desde ORDER BY fecha")
    fun observarDesde(desde: String): Flow<List<AnimoEntidad>>

    @Upsert
    suspend fun guardar(a: AnimoEntidad)
}

private fun AnimoEntidad.dominio() = Animo.entries.firstOrNull { it.name == animo }?.let { RegistroAnimo(mascotaId, LocalDate.parse(fecha), it) }

@Singleton
class RepositorioAnimoLocal @Inject constructor(private val dao: AnimoDao) : RepositorioAnimo {
    override fun observar(mascotaId: String, desde: LocalDate): Flow<List<RegistroAnimo>> =
        dao.observar(mascotaId, desde.toString()).map { l -> l.mapNotNull { it.dominio() } }

    override fun observarDesde(desde: LocalDate): Flow<List<RegistroAnimo>> =
        dao.observarDesde(desde.toString()).map { l -> l.mapNotNull { it.dominio() } }

    override suspend fun guardar(r: RegistroAnimo) =
        dao.guardar(AnimoEntidad("${r.mascotaId}_${r.fecha}", r.mascotaId, r.fecha.toString(), r.animo.name))
}
