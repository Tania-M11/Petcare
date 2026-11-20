package com.udenar.petcare.domain.mascotas

import com.udenar.petcare.domain.modelo.Mascota
import kotlinx.coroutines.flow.Flow

data class CodigoMascota(val codigo: String, val activo: Boolean)

/**
 * Mascotas del usuario. Se leen y escriben en el teléfono (funciona sin internet);
 * la sincronización con el servidor ocurre en segundo plano.
 */
interface RepositorioMascotas {
    fun observar(): Flow<List<Mascota>>
    suspend fun obtener(id: String): Mascota?
    /** [fotoNueva]: archivo ya comprimido en el teléfono, si se eligió una foto. */
    suspend fun guardar(mascota: Mascota, fotoNueva: String? = null)
    suspend fun eliminar(id: String)

    /** Descarga la foto del servidor si este teléfono aún no la tiene. */
    suspend fun asegurarFoto(id: String)

    /** Código para cuidadores (lo genera el servidor). null = aún no sincronizada. Requiere internet. */
    suspend fun codigo(id: String): CodigoMascota?
}
