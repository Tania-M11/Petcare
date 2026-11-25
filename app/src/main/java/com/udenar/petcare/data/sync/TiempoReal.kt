package com.udenar.petcare.data.sync

import com.udenar.petcare.domain.sync.ProgramadorSync
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cambios en vivo entre miembros (RF-21) mientras la app está abierta: cuando otra persona
 * marca una toma, cambia un cuidado o aprueba una solicitud, este teléfono sincroniza en
 * segundos (y se retiran las notificaciones ya resueltas). Realtime respeta las reglas RLS.
 */
@Singleton
class TiempoReal @Inject constructor(
    private val supabase: SupabaseClient,
    private val sync: ProgramadorSync,
    private val avisos: com.udenar.petcare.data.avisos.CentroAvisos,
) {
    private val _solicitudes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Avisa cuando llegan o cambian solicitudes para cuidar (para refrescar Hoy). */
    val cambiosSolicitudes: SharedFlow<Unit> = _solicitudes.asSharedFlow()

    private val _vet = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Avisa cuando cambian solicitudes a veterinarios o vínculos (bandeja, agenda y "Mis solicitudes"). */
    val cambiosVet: SharedFlow<Unit> = _vet.asSharedFlow()

    private var trabajo: Job? = null
    private var canal: RealtimeChannel? = null

    @OptIn(FlowPreview::class)
    fun iniciar(alcance: CoroutineScope) {
        if (trabajo?.isActive == true) return
        trabajo = alcance.launch {
            runCatching {
                val c = supabase.channel("petcare-cambios")
                canal = c
                val tablas = listOf("mascotas", "eventos_cuidado", "registros_cuidado", "pesos", "paseos", "sintomas", "miembros_mascota")
                val flujos = tablas.map { t -> c.postgresChangeFlow<PostgresAction>(schema = "public") { table = t } }
                val solicitudes = c.postgresChangeFlow<PostgresAction>(schema = "public") { table = "solicitudes_cuidado" }
                val vet = listOf("solicitudes_vet", "vinculos_vet", "solicitudes_prestador").map { t -> c.postgresChangeFlow<PostgresAction>(schema = "public") { table = t } }
                launch { merge(*vet.toTypedArray()).debounce(500).collect { _vet.tryEmit(Unit) } }
                // Avisos nuevos para esta cuenta (RF-29) mientras la app está abierta
                val notificaciones = c.postgresChangeFlow<PostgresAction>(schema = "public") { table = "notificaciones" }
                launch { notificaciones.debounce(300).collect { avisos.revisar() } }
                // Varios cambios seguidos se agrupan en una sola sincronización
                launch { merge(*flujos.toTypedArray()).debounce(1_500).collect { sync.solicitar() } }
                launch {
                    solicitudes.collect {
                        _solicitudes.tryEmit(Unit)
                        sync.solicitar()
                    }
                }
                c.subscribe()
            }
        }
    }

    suspend fun detener() {
        trabajo?.cancel()
        trabajo = null
        canal?.let { runCatching { supabase.realtime.removeChannel(it) } }
        canal = null
    }
}
