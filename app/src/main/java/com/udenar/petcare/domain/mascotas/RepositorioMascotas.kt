package com.udenar.petcare.domain.mascotas

import com.udenar.petcare.domain.modelo.Mascota
import kotlinx.coroutines.flow.Flow

/**
 * Mascotas del usuario. Se leen y escriben en el teléfono (funciona sin internet);
 * la sincronización con el servidor ocurre en segundo plano.
 */
interface RepositorioMascotas {
    fun observar(): Flow<List<Mascota>>
    suspend fun obtener(id: String): Mascota?
    suspend fun guardar(mascota: Mascota)
    suspend fun eliminar(id: String)
}
