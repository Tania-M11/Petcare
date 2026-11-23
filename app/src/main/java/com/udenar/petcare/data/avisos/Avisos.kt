package com.udenar.petcare.data.avisos

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.udenar.petcare.data.alarmas.Notificador
import com.udenar.petcare.domain.modelo.ModoUso
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Pestaña que abre un aviso al tocarlo (RF-29: "el push abre el modo correcto"). */
enum class DestinoAviso { HOY, MASCOTAS, SERVICIOS, PERFIL, SOLICITUDES }

data class Aviso(
    val id: String,
    val tipo: String,
    val titulo: String,
    val cuerpo: String,
    val modo: ModoUso,
    val destino: DestinoAviso,
    val creadoEn: Instant? = null,
) {
    /** Canal Android: los de cuidadores van aparte de las solicitudes (RF-29). */
    val canal: String get() = if (tipo.startsWith("CUIDADO_")) Notificador.CANAL_CUIDADORES else Notificador.CANAL_SOLICITUDES

    companion object {
        fun desde(datos: Map<String, String>): Aviso? {
            val id = datos["id"] ?: return null
            return Aviso(
                id, datos["tipo"].orEmpty(), datos["titulo"] ?: return null, datos["cuerpo"].orEmpty(),
                ModoUso.entries.firstOrNull { it.name == datos["modo"] } ?: ModoUso.PERSONAL,
                DestinoAviso.entries.firstOrNull { it.name == datos["destino"] } ?: DestinoAviso.HOY,
            )
        }
    }
}

data class Apertura(val modo: ModoUso, val destino: DestinoAviso)

/** El aviso que el usuario tocó; la raíz cambia de modo y cada estructura abre su pestaña. */
@Singleton
class AperturaAvisos @Inject constructor() {
    private val _pendiente = MutableStateFlow<Apertura?>(null)
    val pendiente: StateFlow<Apertura?> = _pendiente.asStateFlow()

    fun abrir(a: Apertura) {
        _pendiente.value = a
    }

    fun consumir() {
        _pendiente.value = null
    }
}

@Serializable
private data class FilaAviso(
    val id: String,
    val tipo: String,
    val titulo: String,
    val cuerpo: String,
    val modo: String = "PERSONAL",
    val destino: String = "HOY",
    @SerialName("creado_en") val creadoEn: String,
)

/**
 * Muestra los avisos de la tabla `notificaciones` una sola vez por teléfono, venga de donde venga:
 * Realtime con la app abierta, la revisión periódica (cada 15 min) o un push de FCM.
 */
@Singleton
class CentroAvisos @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val supabase: SupabaseClient,
    private val notificador: Notificador,
) {
    private val prefs = contexto.getSharedPreferences("petcare_avisos", Context.MODE_PRIVATE)
    private val candado = Mutex()

    /** Muestra el aviso si este teléfono no lo había mostrado antes. */
    fun mostrar(a: Aviso) {
        synchronized(this) {
            val vistos = prefs.getStringSet(VISTOS, emptySet()).orEmpty()
            if (a.id in vistos) return
            // Se guardan los últimos 200 para no crecer sin límite
            prefs.edit().putStringSet(VISTOS, (vistos.toList().takeLast(199) + a.id).toSet()).apply()
        }
        notificador.mostrarAvisoRemoto(a)
    }

    /** Trae los avisos nuevos desde la última revisión y los muestra. */
    suspend fun revisar() = candado.withLock {
        if (supabase.auth.currentSessionOrNull() == null) return@withLock
        val cursor = prefs.getString(CURSOR, null)
        if (cursor == null) {
            // Primera vez en este teléfono: no se muestran avisos viejos
            prefs.edit().putString(CURSOR, Instant.now().toString()).apply()
            return@withLock
        }
        try {
            val nuevos = supabase.from("notificaciones").select {
                filter { gt("creado_en", cursor) }
                order("creado_en", Order.ASCENDING)
                limit(30)
            }.decodeList<FilaAviso>()
            nuevos.forEach { f ->
                mostrar(
                    Aviso(
                        f.id, f.tipo, f.titulo, f.cuerpo, ModoUso.entries.firstOrNull { it.name == f.modo } ?: ModoUso.PERSONAL,
                        DestinoAviso.entries.firstOrNull { it.name == f.destino } ?: DestinoAviso.HOY,
                        runCatching { OffsetDateTime.parse(f.creadoEn).toInstant() }.getOrNull(),
                    ),
                )
            }
            nuevos.lastOrNull()?.let { prefs.edit().putString(CURSOR, it.creadoEn).apply() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sin conexión: se intenta en la próxima revisión
        }
    }

    /** Revisión periódica para cuando la app está cerrada y no hay push (WorkManager, cada 15 min). */
    fun programar() {
        val pedido = PeriodicWorkRequestBuilder<TrabajoAvisos>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        runCatching { WorkManager.getInstance(contexto).enqueueUniquePeriodicWork(TRABAJO, ExistingPeriodicWorkPolicy.KEEP, pedido) }
    }

    /** Al cerrar sesión o eliminar la cuenta. */
    fun reiniciar() {
        prefs.edit().clear().apply()
        runCatching { WorkManager.getInstance(contexto).cancelUniqueWork(TRABAJO) }
    }

    companion object {
        private const val VISTOS = "vistos"
        private const val CURSOR = "cursor"
        private const val TRABAJO = "revisar-avisos"
    }
}

@HiltWorker
class TrabajoAvisos @AssistedInject constructor(
    @Assisted contexto: Context,
    @Assisted params: WorkerParameters,
    private val centro: CentroAvisos,
) : CoroutineWorker(contexto, params) {
    override suspend fun doWork(): Result {
        centro.revisar()
        return Result.success()
    }
}

/**
 * Push con FCM (opcional): solo funciona si el proyecto tiene `app/google-services.json`.
 * Sin Firebase, los avisos llegan igual por Realtime y por la revisión periódica.
 */
@Singleton
class PushFcm @Inject constructor(@ApplicationContext private val contexto: Context, private val supabase: SupabaseClient) {
    val disponible: Boolean get() = runCatching { FirebaseApp.getApps(contexto).isNotEmpty() }.getOrDefault(false)

    suspend fun registrar() {
        if (!disponible || supabase.auth.currentSessionOrNull() == null) return
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            supabase.postgrest.rpc("registrar_dispositivo", buildJsonObject { put("p_token", token) })
        }
    }

    /** Antes de cerrar sesión: este teléfono deja de recibir los avisos de la cuenta. */
    suspend fun olvidar() {
        if (!disponible || supabase.auth.currentSessionOrNull() == null) return
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            supabase.postgrest.rpc("olvidar_dispositivo", buildJsonObject { put("p_token", token) })
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DependenciasPush {
    fun centro(): CentroAvisos
    fun push(): PushFcm
}

/** Recibe los mensajes de datos que envía la Edge Function `notificar`. */
class ServicioMensajes : FirebaseMessagingService() {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val deps get() = EntryPointAccessors.fromApplication(applicationContext, DependenciasPush::class.java)

    override fun onMessageReceived(mensaje: RemoteMessage) {
        Aviso.desde(mensaje.data)?.let { deps.centro().mostrar(it) }
    }

    override fun onNewToken(token: String) {
        alcance.launch { deps.push().registrar() }
    }
}
