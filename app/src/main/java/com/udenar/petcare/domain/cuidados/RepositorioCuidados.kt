package com.udenar.petcare.domain.cuidados

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Cuidados y su cumplimiento. Todo se guarda en el teléfono y se sincroniza en segundo plano. */
interface RepositorioCuidados {
    fun observarEventos(): Flow<List<EventoCuidado>>
    fun observarRegistros(): Flow<List<RegistroCuidado>>

    /** Tomas de un día (Hoy, calendario y alarmas), calculadas con [MotorCuidados]. */
    fun agenda(fecha: LocalDate): Flow<List<Toma>>

    suspend fun guardarEvento(evento: EventoCuidado)
    suspend fun eliminarEvento(id: String)

    /** Marca una toma (hecha, omitida o pospuesta) a nombre del usuario actual. */
    suspend fun registrar(toma: Toma, estado: EstadoRegistro, notas: String? = null)

    /** Deshace un registro: la toma vuelve a quedar pendiente. */
    suspend fun deshacer(toma: Toma)
}
