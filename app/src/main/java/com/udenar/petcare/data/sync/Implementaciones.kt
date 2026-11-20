package com.udenar.petcare.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.udenar.petcare.data.alarmas.Notificador
import com.udenar.petcare.data.alarmas.RelojAlarmas
import com.udenar.petcare.data.cuidados.EventoDao
import com.udenar.petcare.data.cuidados.RegistroDao
import com.udenar.petcare.data.fotos.ArchivosFotos
import com.udenar.petcare.data.local.CursorDao
import com.udenar.petcare.data.local.CursorEntidad
import com.udenar.petcare.data.local.PetcareDb
import com.udenar.petcare.data.mascotas.MascotaDao
import com.udenar.petcare.domain.cuidados.ProgramadorRecordatorios
import com.udenar.petcare.domain.sync.AlmacenLocal
import com.udenar.petcare.domain.sync.EstadoSync
import com.udenar.petcare.domain.sync.ProgramadorSync
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Servidor real: Supabase (PostgREST). Las reglas RLS limitan lo que cada quien sube y baja. */
class RemotaSupabase @Inject constructor(private val supabase: SupabaseClient) : FuenteRemota {

    override suspend fun subir(tabla: String, filas: List<JsonObject>): List<JsonObject> =
        supabase.from(tabla)
            .upsert(JsonArray(filas)) {
                onConflict = "id"
                select()
            }
            .decodeList<JsonObject>()

    override suspend fun cambiosDesde(tabla: String, desde: String?, limite: Int): List<JsonObject> =
        supabase.from(tabla)
            .select {
                if (desde != null) filter { gt("actualizado_en", desde) }
                order("actualizado_en", Order.ASCENDING)
                limit(limite.toLong())
            }
            .decodeList<JsonObject>()
}

class CursoresRoom @Inject constructor(private val dao: CursorDao) : AlmacenCursores {
    override suspend fun cursor(tabla: String) = dao.cursor(tabla)
    override suspend fun guardar(tabla: String, cursor: String?, sincronizadoEn: Long) =
        dao.guardar(CursorEntidad(tabla, cursor, sincronizadoEn))
}

/** Trabajo de WorkManager: corre cuando hay red, aunque la app esté cerrada. */
@HiltWorker
class TrabajoSincronizacion @AssistedInject constructor(
    @Assisted contexto: Context,
    @Assisted params: WorkerParameters,
    private val sincronizador: Sincronizador,
    private val supabase: SupabaseClient,
    private val recordatorios: ProgramadorRecordatorios,
    private val compartir: com.udenar.petcare.domain.compartir.RepositorioCompartir,
) : CoroutineWorker(contexto, params) {

    override suspend fun doWork(): Result {
        // Sin sesión no hay nada que sincronizar (las reglas del servidor rechazarían todo)
        if (supabase.auth.currentSessionOrNull() == null) return Result.success()
        return try {
            // Primero: ¿empecé o dejé de cuidar alguna mascota? (RF-20)
            compartir.actualizarMembresias()
            sincronizador.sincronizar()
            // Lo que llegó de otros teléfonos también suena aquí (y se retira lo ya resuelto)
            recordatorios.reprogramar()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}

@Singleton
class ProgramadorWorkManager @Inject constructor(
    @ApplicationContext private val contexto: Context,
    mascotas: MascotaDao,
    eventos: EventoDao,
    registros: RegistroDao,
    pesos: com.udenar.petcare.data.salud.PesoDao,
    visitas: com.udenar.petcare.data.salud.VisitaDao,
    cursores: CursorDao,
) : ProgramadorSync {

    override fun solicitar() {
        val pedido = OneTimeWorkRequestBuilder<TrabajoSincronizacion>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // Si ya hay uno corriendo, este se encadena detrás: ningún cambio se queda sin subir
        WorkManager.getInstance(contexto).enqueueUniqueWork(NOMBRE, ExistingWorkPolicy.APPEND_OR_REPLACE, pedido)
    }

    override val estado: Flow<EstadoSync> = combine(
        combine(mascotas.contarPendientes(), eventos.contarPendientes(), registros.contarPendientes(), pesos.contarPendientes(), visitas.contarPendientes()) { a -> a.sum() },
        cursores.ultimaSincronizacion(),
    ) { pendientes, ultima ->
        EstadoSync(porSubir = pendientes, ultima = ultima?.let { Instant.ofEpochMilli(it) })
    }

    companion object {
        const val NOMBRE = "sincronizacion"
    }
}

class AlmacenLocalRoom @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val db: PetcareDb,
    private val fotos: ArchivosFotos,
    private val reloj: RelojAlarmas,
    private val notificador: Notificador,
) : AlmacenLocal {
    override suspend fun borrarTodo() {
        WorkManager.getInstance(contexto).cancelUniqueWork(ProgramadorWorkManager.NOMBRE)
        // Al cerrar sesión no queda ninguna alarma ni notificación de esta cuenta
        db.alarmas().todas().forEach { reloj.cancelar(it.clave) }
        notificador.retirarTodas()
        withContext(Dispatchers.IO) {
            db.clearAllTables()
            fotos.borrarTodo()
        }
    }
}
