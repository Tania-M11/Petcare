package com.udenar.petcare.domain.sync

import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class EstadoSync(
    /** Cambios hechos en el teléfono que aún no llegan al servidor. */
    val porSubir: Int,
    /** Última sincronización completa (null = nunca en este teléfono). */
    val ultima: Instant?,
)

/** Pide sincronizar (cuando haya red) y expone el estado para la interfaz. */
interface ProgramadorSync {
    fun solicitar()
    val estado: Flow<EstadoSync>
}

/** Borra todo lo guardado en el teléfono (al cerrar sesión, RF-01). */
interface AlmacenLocal {
    suspend fun borrarTodo()
}
