package com.udenar.petcare.data.profesionales

import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.udenar.petcare.domain.paseos.Coordenada
import com.udenar.petcare.domain.paseos.Geo
import com.udenar.petcare.domain.profesionales.LugarVeterinario
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Ubica en el mapa las veterinarias que solo tienen dirección, con el geocodificador del
 * teléfono. El resultado se guarda para no repetirlo. Si cae lejos de su ciudad (más de 12 km
 * del centro) no se usa: mejor sin pin que con un pin equivocado.
 */
@Singleton
class GeocodificadorLugares @Inject constructor(@ApplicationContext private val contexto: Context) {
    private val prefs = contexto.getSharedPreferences("geo_lugares", Context.MODE_PRIVATE)

    suspend fun coordenadas(lugares: List<LugarVeterinario>): Map<String, Coordenada> {
        val res = mutableMapOf<String, Coordenada>()
        lugares.forEach { l -> if (l.lat != null && l.lon != null) res[l.id] = Coordenada(l.lat, l.lon) }
        if (!Geocoder.isPresent()) return res
        val geo = Geocoder(contexto, Locale.forLanguageTag("es-CO"))
        val centros = mutableMapOf<String, Coordenada?>()
        for (l in lugares) {
            if (l.id in res) continue
            val guardado = prefs.getString(l.id, null)
            if (guardado != null) {
                if (guardado != "x") guardado.split(',').let { res[l.id] = Coordenada(it[0].toDouble(), it[1].toDouble()) }
                continue
            }
            val centro = centros.getOrPut(l.ciudad) { buscar(geo, "${l.ciudad}, Nariño, Colombia") } ?: continue
            val c = buscar(geo, "${l.direccion}, ${l.ciudad}, Nariño, Colombia")
            val valida = c?.takeIf { Geo.distanciaM(it, centro) < 12_000 }
            prefs.edit().putString(l.id, valida?.let { "${it.lat},${it.lon}" } ?: "x").apply()
            valida?.let { res[l.id] = it }
        }
        return res
    }

    @Suppress("DEPRECATION")
    private suspend fun buscar(geo: Geocoder, texto: String): Coordenada? = withTimeoutOrNull(8_000) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geo.getFromLocationName(texto, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(lista: MutableList<android.location.Address>) {
                        cont.resume(lista.firstOrNull()?.let { Coordenada(it.latitude, it.longitude) })
                    }
                    override fun onError(mensaje: String?) { cont.resume(null) }
                })
            }
        } else {
            withContext(Dispatchers.IO) { runCatching { geo.getFromLocationName(texto, 1)?.firstOrNull()?.let { Coordenada(it.latitude, it.longitude) } }.getOrNull() }
        }
    }
}
