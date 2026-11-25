package com.udenar.petcare.domain.salud

import com.udenar.petcare.domain.cuidados.Autor
import com.udenar.petcare.domain.modelo.Icono
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/** Lo que el dueño o quien cuida observa (RF de síntomas y comportamiento). La app no lo interpreta. */
enum class TipoSintoma(val etiqueta: String, val icono: Icono) {
    FALTA_APETITO("Falta de apetito", Icono.COMIDA),
    VOMITO("Vómito", Icono.TERMOMETRO),
    TOS("Tos", Icono.NUBE),
    RASQUINA("Rasquiña", Icono.INSECTO),
    COMPORTAMIENTO("Cambio de comportamiento", Icono.PREGUNTA),
    OTRO("Otro", Icono.NOTA),
}

data class Sintoma(
    val id: String,
    val mascotaId: String,
    val tipo: TipoSintoma,
    val fechaHora: LocalDateTime,
    val descripcion: String? = null,
    /** Foto guardada como documento de la mascota (tipo FOTO). */
    val documentoId: String? = null,
    val registradoPor: Autor? = null,
    val porSubir: Boolean = false,
)

interface RepositorioSintomas {
    /** Del más reciente al más antiguo. */
    fun observar(mascotaId: String): Flow<List<Sintoma>>
    fun observarTodos(): Flow<List<Sintoma>>

    /** Lo puede anotar el dueño o quien la cuida. */
    suspend fun guardar(s: Sintoma)
    suspend fun eliminar(id: String)
}
