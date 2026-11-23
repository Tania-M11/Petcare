package com.udenar.petcare.data.clima

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.udenar.petcare.domain.recomendaciones.Clima
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class RespuestaClima(val current: Actual? = null) {
    @Serializable
    data class Actual(
        @SerialName("temperature_2m") val temperatura: Double = 0.0,
        val precipitation: Double = 0.0,
        @SerialName("weather_code") val codigo: Int = 0,
    )
}

/**
 * Clima actual con Open-Meteo (gratis, sin clave). Solo si la persona ya dio permiso de
 * ubicación (nunca se pide para esto) y con la posición redondeada a ~1 km. Se guarda 1 hora.
 */
@Singleton
class ServicioClima @Inject constructor(@ApplicationContext private val contexto: Context) {
    private var ultimo: Pair<Long, Clima>? = null
    private val json = Json { ignoreUnknownKeys = true }

    private fun conPermiso() = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        .any { ContextCompat.checkSelfPermission(contexto, it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    suspend fun actual(): Clima? {
        ultimo?.let { (t, c) -> if (System.currentTimeMillis() - t < 60 * 60 * 1000) return c }
        if (!conPermiso()) return null
        return runCatching {
            val l = LocationServices.getFusedLocationProviderClient(contexto).lastLocation.await() ?: return null
            val url = "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,precipitation,weather_code"
                .format(Locale.US, l.latitude, l.longitude)
            val texto = withContext(Dispatchers.IO) {
                (URL(url).openConnection() as HttpURLConnection).run {
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    try { inputStream.bufferedReader().readText() } finally { disconnect() }
                }
            }
            val a = json.decodeFromString(RespuestaClima.serializer(), texto).current ?: return null
            // Códigos WMO 51–67 y 80–82: llovizna y lluvia
            Clima(a.temperatura, a.precipitation > 0.1 || a.codigo in 51..67 || a.codigo in 80..82)
                .also { ultimo = System.currentTimeMillis() to it }
        }.getOrNull()
    }
}
