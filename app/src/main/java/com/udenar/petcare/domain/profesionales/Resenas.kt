package com.udenar.petcare.domain.profesionales

import java.time.LocalDate

/** A quién se califica (RF-28): se reutiliza el mismo componente para ambos. */
enum class TipoResena { VET, PRESTADOR }

data class Resena(
    val id: String,
    val solicitudId: String,
    val autorNombre: String?,
    val estrellas: Int,
    val comentario: String?,
    val fecha: LocalDate,
    val mia: Boolean = false,
)

enum class MotivoReporte(val etiqueta: String) {
    SPAM("Spam o publicidad"), OFENSIVO("Ofensivo o irrespetuoso"), FALSO("Falso o engañoso"), OTRO("Otro motivo"),
}

enum class TipoReporte { PERFIL_VET, PERFIL_PRESTADOR, RESENA }

interface RepositorioResenas {
    /** Reseñas visibles de un profesional, de la más nueva a la más vieja (paginadas). */
    suspend fun de(profesionalId: String, tipo: TipoResena, pagina: Int = 0, tamano: Int = 20): List<Resena>

    /** Mis reseñas por solicitud (para saber cuáles servicios ya califiqué). */
    suspend fun mias(): Map<String, Resena>

    /** Crea o edita mi reseña de una solicitud COMPLETADA (1–5 estrellas, comentario ≤ 500). */
    suspend fun guardar(solicitudId: String, tipo: TipoResena, estrellas: Int, comentario: String?)
    suspend fun eliminar(solicitudId: String)
    suspend fun reportar(tipo: TipoReporte, refId: String, motivo: MotivoReporte, detalle: String?)
}

object ValidadorResena {
    const val MAX_COMENTARIO = 500
    fun valida(estrellas: Int, comentario: String?) = estrellas in 1..5 && (comentario?.length ?: 0) <= MAX_COMENTARIO
}
