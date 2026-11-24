package com.udenar.petcare.domain.paseos

import com.udenar.petcare.domain.modelo.Icono
import com.udenar.petcare.domain.cuidados.Autor
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

data class Coordenada(val lat: Double, val lon: Double)

enum class TipoPaseo(val etiqueta: String, val icono: Icono) {
    PASEO("Paseo", Icono.PASEO),
    JUEGO("Juego", Icono.JUEGO),
    EJERCICIO("Ejercicio", Icono.EJERCICIO),
    ENTRENAMIENTO("Entrenamiento", Icono.ENTRENAMIENTO),
}

/** Paseo o actividad (RF-12): manual o con la ruta GPS. */
data class Paseo(
    val id: String,
    val mascotaId: String,
    val tipo: TipoPaseo,
    val inicio: Instant,
    val fin: Instant,
    val duracionS: Int,
    val distanciaM: Int?,
    val ruta: List<Coordenada> = emptyList(),
    val esManual: Boolean,
    val notas: String? = null,
    val registradoPor: Autor? = null,
    val porSubir: Boolean = false,
)

/** Lo que deja un paseo con GPS al finalizar. */
data class ResumenPaseo(
    val mascotaIds: List<String>,
    val inicio: Instant,
    val fin: Instant,
    val duracionS: Int,
    val distanciaM: Int,
    val ruta: List<Coordenada>,
)

interface RepositorioPaseos {
    fun observar(mascotaId: String): Flow<List<Paseo>>

    /** Paseos de todas las mascotas desde [desde] (recomendaciones). */
    fun observarDesde(desde: java.time.Instant): Flow<List<Paseo>>
    suspend fun obtener(id: String): Paseo?

    /** Un registro por mascota (comparten la ruta). */
    suspend fun guardarGps(resumen: ResumenPaseo, tipo: TipoPaseo = TipoPaseo.PASEO)
    suspend fun guardarManual(mascotaIds: List<String>, tipo: TipoPaseo, inicio: Instant, duracionS: Int, distanciaM: Int?, notas: String?)
    suspend fun eliminar(id: String)
}

object Geo {
    private const val RADIO_TIERRA_M = 6_371_000.0

    /** Distancia en metros entre dos puntos (fórmula del haversine). */
    fun distanciaM(a: Coordenada, b: Coordenada): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * RADIO_TIERRA_M * asin(sqrt(h))
    }

    fun distanciaTotalM(ruta: List<Coordenada>): Double = ruta.zipWithNext { a, b -> distanciaM(a, b) }.sum()
}

/**
 * Filtro de puntos GPS (RF-12): se descartan los imprecisos (peor de ~30 m) y los que
 * apenas se movieron (< 10 m) para que la distancia no crezca por el "temblor" del GPS.
 */
object FiltroGps {
    const val PRECISION_MAXIMA_M = 30f
    const val DESPLAZAMIENTO_MINIMO_M = 10.0

    fun aceptar(nuevo: Coordenada, precisionM: Float, ultimo: Coordenada?): Boolean {
        if (precisionM > PRECISION_MAXIMA_M) return false
        return ultimo == null || Geo.distanciaM(ultimo, nuevo) >= DESPLAZAMIENTO_MINIMO_M
    }
}

/** Polilínea codificada (algoritmo estándar de Google, precisión 1e-5) para guardar la ruta como texto. */
object Polilinea {

    fun codificar(puntos: List<Coordenada>): String {
        val sb = StringBuilder()
        var latAnterior = 0L
        var lonAnterior = 0L
        for (p in puntos) {
            val lat = (p.lat * 1e5).roundToLong()
            val lon = (p.lon * 1e5).roundToLong()
            valor(lat - latAnterior, sb)
            valor(lon - lonAnterior, sb)
            latAnterior = lat
            lonAnterior = lon
        }
        return sb.toString()
    }

    private fun valor(v: Long, sb: StringBuilder) {
        var x = if (v < 0) (v shl 1).inv() else v shl 1
        while (x >= 0x20) {
            sb.append(((0x20 or (x and 0x1f).toInt()) + 63).toChar())
            x = x shr 5
        }
        sb.append((x + 63).toInt().toChar())
    }

    fun decodificar(texto: String): List<Coordenada> {
        val puntos = mutableListOf<Coordenada>()
        var i = 0
        var lat = 0L
        var lon = 0L
        while (i < texto.length) {
            val (dLat, i1) = leer(texto, i)
            val (dLon, i2) = leer(texto, i1)
            lat += dLat
            lon += dLon
            i = i2
            puntos += Coordenada(lat / 1e5, lon / 1e5)
        }
        return puntos
    }

    private fun leer(t: String, desde: Int): Pair<Long, Int> {
        var resultado = 0L
        var corrimiento = 0
        var i = desde
        var b: Int
        do {
            b = t[i++].code - 63
            resultado = resultado or ((b and 0x1f).toLong() shl corrimiento)
            corrimiento += 5
        } while (b >= 0x20)
        val v = if (resultado and 1L != 0L) (resultado shr 1).inv() else resultado shr 1
        return v to i
    }
}

/** "1,2 km" o "350 m". */
fun textoDistancia(m: Int): String = if (m >= 1000) String.format(java.util.Locale.forLanguageTag("es-CO"), "%.1f km", m / 1000.0) else "$m m"

/** "1 h 05 min", "25 min", "45 s". */
fun textoDuracion(s: Int): String = when {
    s >= 3600 -> "${s / 3600} h ${"%02d".format((s % 3600) / 60)} min"
    s >= 60 -> "${s / 60} min"
    else -> "$s s"
}
