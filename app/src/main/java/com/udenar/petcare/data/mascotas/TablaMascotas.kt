package com.udenar.petcare.data.mascotas

import com.udenar.petcare.data.sync.Pendiente
import com.udenar.petcare.data.sync.TablaSincronizable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import javax.inject.Inject

/** Fila tal como la devuelve Supabase (supabase/03_mascotas_sync.sql). */
@Serializable
data class MascotaRemota(
    val id: String,
    @SerialName("propietario_id") val propietarioId: String? = null,
    val nombre: String,
    val especie: String,
    @SerialName("especie_otro") val especieOtro: String? = null,
    val raza: String? = null,
    val sexo: String = "DESCONOCIDO",
    @SerialName("fecha_nacimiento") val fechaNacimiento: String? = null,
    @SerialName("nacimiento_aproximado") val nacimientoAproximado: Boolean = false,
    val color: String? = null,
    val tamano: String? = null,
    @SerialName("peso_gramos") val pesoGramos: Int? = null,
    val esterilizado: Boolean? = null,
    val notas: String? = null,
    val alergias: String? = null,
    val condiciones: String? = null,
    @SerialName("medicamentos_actuales") val medicamentosActuales: String? = null,
    val observaciones: String? = null,
    @SerialName("foto_url") val fotoUrl: String? = null,
    val eliminado: Boolean = false,
    @SerialName("actualizado_en") val actualizadoEn: String? = null,
) {
    fun comoEntidad() = MascotaEntidad(
        id, propietarioId, nombre, especie, especieOtro, raza, sexo, fechaNacimiento, nacimientoAproximado, color,
        tamano, pesoGramos, esterilizado, notas, alergias, condiciones, medicamentosActuales, observaciones, fotoUrl,
        eliminado, actualizadoEn, pendiente = false, cambioLocal = 0,
    )
}

/** Lo que se sube: sin propietario ni fechas del servidor (los pone la base de datos). */
fun MascotaEntidad.comoFilaRemota(): JsonObject = buildJsonObject {
    put("id", id)
    put("nombre", nombre)
    put("especie", especie)
    put("especie_otro", especieOtro)
    put("raza", raza)
    put("sexo", sexo)
    put("fecha_nacimiento", fechaNacimiento)
    put("nacimiento_aproximado", nacimientoAproximado)
    put("color", color)
    put("tamano", tamano)
    put("peso_gramos", pesoGramos)
    put("esterilizado", esterilizado)
    put("notas", notas)
    put("alergias", alergias)
    put("condiciones", condiciones)
    put("medicamentos_actuales", medicamentosActuales)
    put("observaciones", observaciones)
    put("foto_url", fotoUrl)
    put("eliminado", eliminado)
}

class TablaMascotas @Inject constructor(private val dao: MascotaDao) : TablaSincronizable {
    override val tabla = "mascotas"
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun pendientes() = dao.pendientes().map { Pendiente(it.id, it.cambioLocal, it.comoFilaRemota()) }

    override suspend fun confirmar(id: String, cambioLocal: Long, actualizadoEn: String) {
        dao.confirmar(id, cambioLocal, actualizadoEn)
        dao.purgarEliminadas()
    }

    override suspend fun aplicarRemotas(filas: List<JsonObject>) =
        dao.aplicarRemotas(filas.map { json.decodeFromJsonElement<MascotaRemota>(it).comoEntidad() })
}
