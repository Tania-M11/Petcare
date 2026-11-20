package com.udenar.petcare.data.cuidados

import com.udenar.petcare.data.sync.Pendiente
import com.udenar.petcare.data.sync.TablaSincronizable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import javax.inject.Inject

private val json = Json { ignoreUnknownKeys = true }

@Serializable
data class EventoRemoto(
    val id: String,
    @SerialName("mascota_id") val mascotaId: String,
    val tipo: String,
    val titulo: String,
    @SerialName("frecuencia_tipo") val frecuenciaTipo: String,
    @SerialName("frecuencia_n") val frecuenciaN: Int = 1,
    @SerialName("dias_semana") val diasSemana: List<Int> = emptyList(),
    val horas: List<String> = emptyList(),
    val ancla: String,
    @SerialName("fecha_inicio") val fechaInicio: String,
    @SerialName("fecha_fin") val fechaFin: String? = null,
    @SerialName("aviso_anticipado_dias") val avisoAnticipadoDias: Int? = null,
    val detalle: JsonObject = JsonObject(emptyMap()),
    val origen: String = "MANUAL",
    @SerialName("origen_ref") val origenRef: String? = null,
    @SerialName("creado_por") val creadoPor: String? = null,
    @SerialName("creado_por_nombre") val creadoPorNombre: String? = null,
    @SerialName("creado_por_rol") val creadoPorRol: String? = null,
    val oculto: Boolean = false,
    val eliminado: Boolean = false,
    @SerialName("actualizado_en") val actualizadoEn: String? = null,
) {
    fun comoEntidad() = EventoEntidad(
        id, mascotaId, tipo, titulo, frecuenciaTipo, frecuenciaN, diasSemana.joinToString(","), horas.joinToString(","),
        ancla, fechaInicio, fechaFin, avisoAnticipadoDias, detalle.toString(), origen, origenRef, creadoPor,
        creadoPorNombre, creadoPorRol, oculto, eliminado, actualizadoEn, pendiente = false, cambioLocal = 0,
    )
}

@Serializable
data class RegistroRemoto(
    val id: String,
    @SerialName("mascota_id") val mascotaId: String,
    @SerialName("evento_id") val eventoId: String,
    @SerialName("fecha_programada") val fechaProgramada: String,
    val estado: String,
    @SerialName("registrado_por") val registradoPor: String? = null,
    @SerialName("registrado_por_nombre") val registradoPorNombre: String? = null,
    @SerialName("registrado_por_rol") val registradoPorRol: String? = null,
    @SerialName("registrado_en") val registradoEn: String,
    val notas: String? = null,
    val eliminado: Boolean = false,
    @SerialName("actualizado_en") val actualizadoEn: String? = null,
) {
    fun comoEntidad() = RegistroEntidad(
        id, mascotaId, eventoId, normalizarFecha(fechaProgramada), estado, registradoPor, registradoPorNombre,
        registradoPorRol, registradoEn, notas, eliminado, actualizadoEn, pendiente = false, cambioLocal = 0,
    )
}

/** Postgres devuelve "2026-11-19T08:00:00"; en el teléfono se guarda como LocalDateTime.toString() ("2026-11-19T08:00"). */
internal fun normalizarFecha(texto: String): String = java.time.LocalDateTime.parse(texto).toString()

private fun lista(texto: String, numeros: Boolean): JsonArray =
    JsonArray(texto.split(',').filter { it.isNotBlank() }.map { if (numeros) JsonPrimitive(it.trim().toInt()) else JsonPrimitive(it.trim()) })

fun EventoEntidad.comoFilaRemota(): JsonObject = buildJsonObject {
    put("id", id)
    put("mascota_id", mascotaId)
    put("tipo", tipo)
    put("titulo", titulo)
    put("frecuencia_tipo", frecuenciaTipo)
    put("frecuencia_n", frecuenciaN)
    put("dias_semana", lista(diasSemana, numeros = true))
    put("horas", lista(horas, numeros = false))
    put("ancla", ancla)
    put("fecha_inicio", fechaInicio)
    put("fecha_fin", fechaFin)
    put("aviso_anticipado_dias", avisoAnticipadoDias)
    put("detalle", json.parseToJsonElement(detalle) as JsonElement)
    put("origen", origen)
    put("origen_ref", origenRef)
    put("oculto", oculto)
    put("eliminado", eliminado)
}

fun RegistroEntidad.comoFilaRemota(): JsonObject = buildJsonObject {
    put("id", id)
    put("mascota_id", mascotaId)
    put("evento_id", eventoId)
    put("fecha_programada", fechaProgramada)
    put("estado", estado)
    put("registrado_en", registradoEn)
    put("notas", notas)
    put("eliminado", eliminado)
}

/** Va después de mascotas (la mascota debe existir en el servidor antes que sus cuidados). */
class TablaEventos @Inject constructor(private val dao: EventoDao) : TablaSincronizable {
    override val tabla = "eventos_cuidado"
    override val orden = 1
    override suspend fun pendientes() = dao.pendientes().map { Pendiente(it.id, it.cambioLocal, it.comoFilaRemota()) }
    override suspend fun confirmar(id: String, cambioLocal: Long, actualizadoEn: String) {
        dao.confirmar(id, cambioLocal, actualizadoEn)
        dao.purgarEliminados()
    }
    override suspend fun aplicarRemotas(filas: List<JsonObject>) =
        dao.aplicarRemotos(filas.map { json.decodeFromJsonElement<EventoRemoto>(it).comoEntidad() })
}

/** Va después de los cuidados (cada registro apunta a su cuidado). */
class TablaRegistros @Inject constructor(private val dao: RegistroDao) : TablaSincronizable {
    override val tabla = "registros_cuidado"
    override val orden = 2
    override suspend fun pendientes() = dao.pendientes().map { Pendiente(it.id, it.cambioLocal, it.comoFilaRemota()) }
    override suspend fun confirmar(id: String, cambioLocal: Long, actualizadoEn: String) {
        dao.confirmar(id, cambioLocal, actualizadoEn)
        dao.purgarEliminados()
    }
    override suspend fun aplicarRemotas(filas: List<JsonObject>) =
        dao.aplicarRemotos(filas.map { json.decodeFromJsonElement<RegistroRemoto>(it).comoEntidad() })
}
