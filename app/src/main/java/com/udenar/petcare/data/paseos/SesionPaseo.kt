package com.udenar.petcare.data.paseos

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.udenar.petcare.MainActivity
import com.udenar.petcare.R
import com.udenar.petcare.domain.paseos.Coordenada
import com.udenar.petcare.domain.paseos.FiltroGps
import com.udenar.petcare.domain.paseos.Geo
import com.udenar.petcare.domain.paseos.ResumenPaseo
import com.udenar.petcare.domain.paseos.textoDistancia
import com.udenar.petcare.domain.paseos.textoDuracion
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class FaseSesion { INACTIVO, ACTIVO, PAUSADO }

data class EstadoSesion(
    val fase: FaseSesion = FaseSesion.INACTIVO,
    val mascotaIds: List<String> = emptyList(),
    val inicio: Instant? = null,
    val ruta: List<Coordenada> = emptyList(),
    val distanciaM: Double = 0.0,
    /** Segundos acumulados en tramos ya cerrados (sin contar pausas). */
    val segundosPrevios: Long = 0,
    /** Inicio del tramo activo actual (null si está en pausa). */
    val tramoDesde: Instant? = null,
    val posicion: Coordenada? = null,
) {
    fun duracionS(ahora: Instant): Long = segundosPrevios + (tramoDesde?.let { ahora.epochSecond - it.epochSecond } ?: 0)
}

/**
 * Paseo en curso (RF-12). Vive mientras la app esté viva; el servicio en primer plano lo
 * alimenta con ubicaciones aunque la pantalla esté apagada. En pausa no suma distancia ni tiempo.
 */
@Singleton
class SesionPaseo @Inject constructor(private val reloj: Clock) {
    private val _estado = MutableStateFlow(EstadoSesion())
    val estado: StateFlow<EstadoSesion> = _estado.asStateFlow()

    fun iniciar(mascotaIds: List<String>) {
        val ahora = Instant.now(reloj)
        _estado.value = EstadoSesion(FaseSesion.ACTIVO, mascotaIds, ahora, tramoDesde = ahora)
    }

    fun nuevaUbicacion(lat: Double, lon: Double, precisionM: Float) = _estado.update { e ->
        val punto = Coordenada(lat, lon)
        if (e.fase != FaseSesion.ACTIVO) return@update e.copy(posicion = punto)
        val ultimo = e.ruta.lastOrNull()
        if (!FiltroGps.aceptar(punto, precisionM, ultimo)) return@update e.copy(posicion = punto)
        e.copy(ruta = e.ruta + punto, distanciaM = e.distanciaM + (ultimo?.let { Geo.distanciaM(it, punto) } ?: 0.0), posicion = punto)
    }

    fun pausar() = _estado.update { e ->
        if (e.fase != FaseSesion.ACTIVO) e else e.copy(fase = FaseSesion.PAUSADO, segundosPrevios = e.duracionS(Instant.now(reloj)), tramoDesde = null)
    }

    fun reanudar() = _estado.update { e ->
        if (e.fase != FaseSesion.PAUSADO) e else e.copy(fase = FaseSesion.ACTIVO, tramoDesde = Instant.now(reloj))
    }

    fun finalizar(): ResumenPaseo? {
        val e = _estado.value
        val inicio = e.inicio ?: return null
        val fin = Instant.now(reloj)
        _estado.value = EstadoSesion()
        return ResumenPaseo(e.mascotaIds, inicio, fin, e.duracionS(fin).toInt(), e.distanciaM.toInt(), e.ruta)
    }

    fun cancelar() {
        _estado.value = EstadoSesion()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DependenciasServicioPaseo {
    fun sesion(): SesionPaseo
}

/**
 * Servicio en primer plano de tipo "location" (RF-12): sigue recibiendo el GPS con la pantalla
 * apagada y muestra una notificación persistente con la distancia y el tiempo.
 */
class ServicioPaseo : Service() {
    private var cliente: com.google.android.gms.location.FusedLocationProviderClient? = null
    private lateinit var sesion: SesionPaseo

    private val callback = object : LocationCallback() {
        override fun onLocationResult(resultado: LocationResult) {
            resultado.locations.forEach { l -> sesion.nuevaUbicacion(l.latitude, l.longitude, if (l.hasAccuracy()) l.accuracy else 999f) }
            actualizarNotificacion()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sesion = EntryPointAccessors.fromApplication(applicationContext, DependenciasServicioPaseo::class.java).sesion()
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == DETENER) {
            detener()
            return START_NOT_STICKY
        }
        crearCanal()
        val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, ID, notificacion(), tipo)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            detener()
            return START_NOT_STICKY
        }
        // Cada 5 s o al moverse 10 m (RF-12)
        val pedido = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000).setMinUpdateDistanceMeters(10f).build()
        cliente = LocationServices.getFusedLocationProviderClient(this).also {
            it.requestLocationUpdates(pedido, callback, Looper.getMainLooper())
        }
        return START_STICKY
    }

    private fun detener() {
        cliente?.removeLocationUpdates(callback)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        cliente?.removeLocationUpdates(callback)
        super.onDestroy()
    }

    private fun crearCanal() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CANAL, "Paseos", NotificationManager.IMPORTANCE_LOW).apply { description = "Paseo con GPS en curso" },
        )
    }

    private fun notificacion(): Notification {
        val e = sesion.estado.value
        val texto = "${textoDistancia(e.distanciaM.toInt())} · ${textoDuracion(e.duracionS(Instant.now()).toInt())}" +
            if (e.fase == FaseSesion.PAUSADO) " · en pausa" else ""
        return NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(0xFFF08A4B.toInt())
            .setContentTitle("Paseo en curso")
            .setContentText(texto)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE),
            )
            .build()
    }

    private fun actualizarNotificacion() = getSystemService(NotificationManager::class.java).notify(ID, notificacion())

    companion object {
        private const val CANAL = "paseos"
        private const val ID = 7
        private const val DETENER = "com.udenar.petcare.DETENER_PASEO"

        fun iniciar(contexto: Context) = ContextCompat.startForegroundService(contexto, Intent(contexto, ServicioPaseo::class.java))

        fun detener(contexto: Context) {
            contexto.startService(Intent(contexto, ServicioPaseo::class.java).setAction(DETENER))
        }
    }
}
