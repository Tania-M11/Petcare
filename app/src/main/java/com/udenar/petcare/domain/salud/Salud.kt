package com.udenar.petcare.domain.salud

import com.udenar.petcare.domain.cuidados.Autor
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** Un pesaje (RF-10). */
data class RegistroPeso(
    val id: String,
    val mascotaId: String,
    val fecha: LocalDate,
    val gramos: Int,
    val notas: String? = null,
    val registradoPor: Autor? = null,
    val porSubir: Boolean = false,
)

/** Visita veterinaria (RF-09): todo lo escribe el usuario o el veterinario, la app no lo interpreta. */
data class Visita(
    val id: String,
    val mascotaId: String,
    val fecha: LocalDate,
    val motivo: String,
    val veterinario: String? = null,
    val clinica: String? = null,
    val tratamiento: String? = null,
    val medicamentos: String? = null,
    val notas: String? = null,
    /** Cuidado CITA_VET creado para la próxima cita. */
    val proximaCitaId: String? = null,
    val registradoPor: Autor? = null,
    val registradoEn: Instant? = null,
    val porSubir: Boolean = false,
)

interface RepositorioSalud {
    fun observarPesos(mascotaId: String): Flow<List<RegistroPeso>>
    fun observarVisitas(mascotaId: String): Flow<List<Visita>>

    /** Registra un peso; si es el más reciente, también actualiza el peso de la ficha. */
    suspend fun registrarPeso(mascotaId: String, fecha: LocalDate, gramos: Int, notas: String? = null)
    suspend fun eliminarPeso(id: String)

    /** Guarda la visita y crea (o actualiza/quita) el recordatorio de la próxima cita. */
    suspend fun guardarVisita(visita: Visita, proximaCita: LocalDateTime?)
    suspend fun eliminarVisita(id: String)
}
