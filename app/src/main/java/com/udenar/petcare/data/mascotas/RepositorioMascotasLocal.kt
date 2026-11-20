package com.udenar.petcare.data.mascotas

import com.udenar.petcare.data.fotos.AlmacenFotos
import com.udenar.petcare.data.fotos.ArchivosFotos
import com.udenar.petcare.domain.cuidados.ProgramadorRecordatorios
import com.udenar.petcare.domain.mascotas.CodigoMascota
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.domain.modelo.Sexo
import com.udenar.petcare.domain.modelo.Tamano
import com.udenar.petcare.domain.sync.ProgramadorSync
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class FilaCodigo(val codigo: String, val activo: Boolean)

/** Escribe primero en el teléfono (marcado como pendiente) y pide sincronizar. */
@Singleton
class RepositorioMascotasLocal @Inject constructor(
    private val dao: MascotaDao,
    private val sync: ProgramadorSync,
    private val reloj: Clock,
    private val fotos: AlmacenFotos,
    private val archivos: ArchivosFotos,
    private val supabase: SupabaseClient,
    private val recordatorios: ProgramadorRecordatorios,
) : RepositorioMascotas {

    override fun observar(): Flow<List<Mascota>> = dao.observarActivas().map { lista -> lista.map { it.comoDominio() } }

    override suspend fun obtener(id: String): Mascota? = dao.porId(id)?.takeIf { !it.eliminado }?.comoDominio()

    override suspend fun guardar(mascota: Mascota, fotoNueva: String?) {
        val anterior = dao.porId(mascota.id)
        var entidad = mascota.comoEntidad(anterior, reloj.millis())
        if (fotoNueva != null) {
            anterior?.fotoLocal?.takeIf { it != fotoNueva }?.let { File(it).delete() }
            entidad = entidad.copy(fotoLocal = fotoNueva, fotoPendiente = true)
        }
        dao.guardar(entidad)
        sync.solicitar()
    }

    override suspend fun asegurarFoto(id: String) {
        val m = dao.porId(id) ?: return
        val ruta = m.fotoUrl ?: return
        if (m.fotoLocal != null && File(m.fotoLocal).exists()) return
        val bytes = try {
            fotos.descargar(ruta)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return // sin internet: se intenta la próxima vez
        }
        val archivo = archivos.nuevo(id)
        withContext(Dispatchers.IO) { archivo.writeBytes(bytes) }
        dao.fotoDescargada(id, ruta, archivo.absolutePath)
    }

    override suspend fun codigo(id: String): CodigoMascota? =
        supabase.from("codigos_mascota")
            .select { filter { eq("mascota_id", id) } }
            .decodeSingleOrNull<FilaCodigo>()
            ?.let { CodigoMascota(it.codigo, it.activo) }

    override suspend fun eliminar(id: String) {
        val actual = dao.porId(id) ?: return
        dao.guardar(actual.copy(eliminado = true, pendiente = true, cambioLocal = maxOf(reloj.millis(), actual.cambioLocal + 1)))
        actual.fotoLocal?.let { File(it).delete() }
        // Sus cuidados dejan de sonar
        recordatorios.reprogramar()
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
    fotoUrl = anterior?.fotoUrl ?: fotoUrl,
    eliminado = false,
    actualizadoEn = anterior?.actualizadoEn,
    fotoLocal = anterior?.fotoLocal,
    fotoPendiente = anterior?.fotoPendiente ?: false,
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
    fotoLocal = fotoLocal?.takeIf { File(it).exists() },
    propietarioId = propietarioId,
    porSubir = pendiente,
)
