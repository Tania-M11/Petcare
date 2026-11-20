package com.udenar.petcare.data.mascotas

import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.domain.modelo.Sexo
import com.udenar.petcare.domain.modelo.Tamano
import com.udenar.petcare.domain.sync.ProgramadorSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Escribe primero en el teléfono (marcado como pendiente) y pide sincronizar. */
@Singleton
class RepositorioMascotasLocal @Inject constructor(
    private val dao: MascotaDao,
    private val sync: ProgramadorSync,
    private val reloj: Clock,
) : RepositorioMascotas {

    override fun observar(): Flow<List<Mascota>> = dao.observarActivas().map { lista -> lista.map { it.comoDominio() } }

    override suspend fun obtener(id: String): Mascota? = dao.porId(id)?.takeIf { !it.eliminado }?.comoDominio()

    override suspend fun guardar(mascota: Mascota) {
        val anterior = dao.porId(mascota.id)
        dao.guardar(mascota.comoEntidad(anterior, reloj.millis()))
        sync.solicitar()
    }

    override suspend fun eliminar(id: String) {
        val actual = dao.porId(id) ?: return
        dao.guardar(actual.copy(eliminado = true, pendiente = true, cambioLocal = reloj.millis()))
        sync.solicitar()
    }
}

private fun Mascota.comoEntidad(anterior: MascotaEntidad?, ahora: Long) = MascotaEntidad(
    id = id,
    propietarioId = anterior?.propietarioId ?: propietarioId,
    nombre = nombre.trim(),
    especie = especie.name,
    especieOtro = especieOtro?.trim()?.ifBlank { null },
    raza = raza?.trim()?.ifBlank { null },
    sexo = sexo.name,
    fechaNacimiento = fechaNacimiento?.toString(),
    nacimientoAproximado = nacimientoAproximado,
    color = color?.trim()?.ifBlank { null },
    tamano = tamano?.name,
    pesoGramos = pesoGramos,
    esterilizado = esterilizado,
    notas = notas?.trim()?.ifBlank { null },
    alergias = alergias?.trim()?.ifBlank { null },
    condiciones = condiciones?.trim()?.ifBlank { null },
    medicamentosActuales = medicamentosActuales?.trim()?.ifBlank { null },
    observaciones = observaciones?.trim()?.ifBlank { null },
    fotoUrl = fotoUrl,
    eliminado = false,
    actualizadoEn = anterior?.actualizadoEn,
    pendiente = true,
    // Siempre crece, aunque el reloj del teléfono retroceda
    cambioLocal = maxOf(ahora, (anterior?.cambioLocal ?: 0) + 1),
)

internal fun MascotaEntidad.comoDominio() = Mascota(
    id = id,
    nombre = nombre,
    especie = Especie.desdeCodigo(especie),
    especieOtro = especieOtro,
    raza = raza,
    sexo = Sexo.entries.firstOrNull { it.name == sexo } ?: Sexo.DESCONOCIDO,
    fechaNacimiento = fechaNacimiento?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    nacimientoAproximado = nacimientoAproximado,
    color = color,
    tamano = Tamano.entries.firstOrNull { it.name == tamano },
    pesoGramos = pesoGramos,
    esterilizado = esterilizado,
    notas = notas,
    alergias = alergias,
    condiciones = condiciones,
    medicamentosActuales = medicamentosActuales,
    observaciones = observaciones,
    fotoUrl = fotoUrl,
    propietarioId = propietarioId,
    porSubir = pendiente,
)
