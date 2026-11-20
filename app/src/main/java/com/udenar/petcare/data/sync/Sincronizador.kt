package com.udenar.petcare.data.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Cambio local listo para subir. [cambioLocal] identifica la versión editada. */
data class Pendiente(val id: String, val cambioLocal: Long, val fila: JsonObject)

/** Una tabla que se guarda en el teléfono y se sincroniza (implementada por cada entidad). */
interface TablaSincronizable {
    val tabla: String

    /** Orden de subida y bajada: las tablas de las que otras dependen van primero. */
    val orden: Int get() = 0

    /** Trabajo previo a subir filas (p. ej. subir fotos para tener su ruta). */
    suspend fun antesDeSubir() {}

    suspend fun pendientes(): List<Pendiente>

    /** Marca como subido solo si no se volvió a editar mientras se subía. */
    suspend fun confirmar(id: String, cambioLocal: Long, actualizadoEn: String)

    /** Aplica filas del servidor; los cambios locales pendientes no se pisan. */
    suspend fun aplicarRemotas(filas: List<JsonObject>)
}

/** Acceso al servidor (Supabase en la app; una versión falsa en las pruebas). */
interface FuenteRemota {
    suspend fun subir(tabla: String, filas: List<JsonObject>): List<JsonObject>
    suspend fun cambiosDesde(tabla: String, desde: String?, limite: Int): List<JsonObject>
}

/** Hasta dónde se ha descargado cada tabla (marca de tiempo del servidor). */
interface AlmacenCursores {
    suspend fun cursor(tabla: String): String?
    suspend fun guardar(tabla: String, cursor: String?, sincronizadoEn: Long)
}

data class ResultadoSync(val subidas: Int, val bajadas: Int)

/**
 * Motor de sincronización (RF-21): primero sube lo pendiente, luego baja lo nuevo.
 * Conflictos: gana la última escritura que llega al servidor; un cambio local
 * pendiente nunca se pisa con datos del servidor (se sube en la siguiente vuelta).
 */
class Sincronizador(
    private val tablas: Set<TablaSincronizable>,
    private val remota: FuenteRemota,
    private val cursores: AlmacenCursores,
    private val reloj: Clock,
) {
    private val candado = Mutex()

    suspend fun sincronizar(): ResultadoSync = candado.withLock {
        var subidas = 0
        var bajadas = 0
        for (t in tablas.sortedWith(compareBy({ it.orden }, { it.tabla }))) {
            subidas += subir(t)
            bajadas += bajar(t)
        }
        ResultadoSync(subidas, bajadas)
    }

    private suspend fun subir(t: TablaSincronizable): Int {
        var total = 0
        t.antesDeSubir()
        t.pendientes().chunked(LOTE_SUBIDA).forEach { lote ->
            val porId = lote.associateBy { it.id }
            remota.subir(t.tabla, lote.map { it.fila }).forEach { fila ->
                val id = fila.texto("id") ?: return@forEach
                val enviado = porId[id] ?: return@forEach
                val actualizado = fila.texto("actualizado_en") ?: return@forEach
                t.confirmar(id, enviado.cambioLocal, actualizado)
                total++
            }
        }
        return total
    }

    private suspend fun bajar(t: TablaSincronizable): Int {
        val guardado = cursores.cursor(t.tabla)
        // Margen hacia atrás: una transacción lenta pudo confirmar filas con hora anterior al cursor
        var desde = guardado?.let { restarMargen(it) }
        var cursor = guardado
        var total = 0
        while (true) {
            val filas = remota.cambiosDesde(t.tabla, desde, LIMITE_BAJADA)
            if (filas.isNotEmpty()) {
                t.aplicarRemotas(filas)
                total += filas.size
                val maximo = filas.mapNotNull { it.texto("actualizado_en") }.maxByOrNull { instante(it) }
                if (maximo != null && (cursor == null || instante(maximo) > instante(cursor))) cursor = maximo
            }
            if (filas.size < LIMITE_BAJADA) break
            desde = cursor
        }
        cursores.guardar(t.tabla, cursor, reloj.millis())
        return total
    }

    companion object {
        const val LOTE_SUBIDA = 50
        const val LIMITE_BAJADA = 500
        const val MARGEN_MINUTOS = 2L

        private fun instante(texto: String) = OffsetDateTime.parse(texto).toInstant()

        fun restarMargen(texto: String): String =
            OffsetDateTime.parse(texto).minusMinutes(MARGEN_MINUTOS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}

internal fun JsonObject.texto(clave: String): String? = this[clave]?.jsonPrimitive?.contentOrNull
