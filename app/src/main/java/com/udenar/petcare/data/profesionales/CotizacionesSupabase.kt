package com.udenar.petcare.data.profesionales

import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.profesionales.ContactoCompartido
import com.udenar.petcare.domain.profesionales.DetalleSolicitudPres
import com.udenar.petcare.domain.profesionales.EstadoSolicitudPres
import com.udenar.petcare.domain.profesionales.ModalidadPrestador
import com.udenar.petcare.domain.profesionales.ModoPrecio
import com.udenar.petcare.domain.profesionales.PasoHistorial
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.Propuesta
import com.udenar.petcare.domain.profesionales.RepositorioCotizaciones
import com.udenar.petcare.domain.profesionales.ResumenMascota
import com.udenar.petcare.domain.profesionales.SaludCompartida
import com.udenar.petcare.domain.profesionales.SolicitudPrestador
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ResumenJson(
    val nombre: String = "",
    val especie: String? = null,
    val raza: String? = null,
    val sexo: String? = null,
    val edad: String? = null,
    val tamano: String? = null,
    @SerialName("peso_gramos") val pesoGramos: Int? = null,
)

@Serializable
private data class PasoJson(val actor: String = "", val estado: String = "", val fecha: String? = null, val nota: String? = null)

@Serializable
private data class FilaSolicitudPres(
    val id: String,
    @SerialName("mascota_id") val mascotaId: String? = null,
    @SerialName("propietario_nombre") val propietarioNombre: String? = null,
    @SerialName("prestador_id") val prestadorId: String,
    @SerialName("prestador_nombre") val prestadorNombre: String,
    @SerialName("categoria_id") val categoriaId: String,
    @SerialName("servicio_nombre") val servicioNombre: String,
    @SerialName("precio_modo") val precioModo: String = "COTIZACION",
    @SerialName("precio_referencia") val precioReferencia: Int? = null,
    val modalidad: String,
    @SerialName("fecha_inicio") val fechaInicio: String,
    @SerialName("fecha_fin") val fechaFin: String? = null,
    val descripcion: String,
    val mensaje: String? = null,
    val estado: String,
    val resumen: ResumenJson = ResumenJson(),
    @SerialName("cotizacion_precio") val cotizacionPrecio: Int? = null,
    @SerialName("cotizacion_observaciones") val cotizacionObservaciones: String? = null,
    @SerialName("cotizacion_vigencia") val cotizacionVigencia: String? = null,
    @SerialName("propuesta_inicio") val propuestaInicio: String? = null,
    @SerialName("propuesta_fin") val propuestaFin: String? = null,
    val motivo: String? = null,
    val historial: List<PasoJson> = emptyList(),
    @SerialName("creado_en") val creadoEn: String? = null,
) {
    fun comoSolicitud() = SolicitudPrestador(
        id = id, mascotaId = mascotaId, propietarioNombre = propietarioNombre, prestadorId = prestadorId, prestadorNombre = prestadorNombre,
        categoriaId = categoriaId, servicioNombre = servicioNombre,
        precioModo = ModoPrecio.entries.firstOrNull { it.name == precioModo } ?: ModoPrecio.COTIZACION, precioReferencia = precioReferencia,
        modalidad = ModalidadPrestador.entries.firstOrNull { it.name == modalidad } ?: ModalidadPrestador.DOMICILIO,
        inicio = LocalDateTime.parse(fechaInicio), fin = fechaFin?.let(LocalDateTime::parse),
        descripcion = descripcion, mensaje = mensaje, estado = EstadoSolicitudPres.desdeCodigo(estado),
        resumen = ResumenMascota(resumen.nombre, Especie.desdeCodigo(resumen.especie), resumen.raza, resumen.sexo, resumen.edad, resumen.tamano, resumen.pesoGramos),
        cotizacionPrecio = cotizacionPrecio, cotizacionObservaciones = cotizacionObservaciones,
        cotizacionVigencia = cotizacionVigencia?.let(LocalDate::parse),
        propuestaInicio = propuestaInicio?.let(LocalDateTime::parse), propuestaFin = propuestaFin?.let(LocalDateTime::parse),
        motivo = motivo,
        historial = historial.map { PasoHistorial(it.actor, EstadoSolicitudPres.desdeCodigo(it.estado), it.fecha?.let { f -> runCatching { OffsetDateTime.parse(f).toInstant() }.getOrNull() }, it.nota) },
        creadaEn = creadoEn?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() },
    )
}

@Serializable
private data class FilaCompartido(val tipo: String, val datos: JsonObject)

@Serializable
private data class SaludJson(val alergias: String? = null, val condiciones: String? = null, val comportamiento: String? = null, val indicaciones: String? = null)

@Serializable
private data class ContactoJson(val telefono: String? = null, val direccion: String? = null)

private fun LocalDateTime.sinSegundos() = withSecond(0).withNano(0).toString()

/** Cotizaciones y contratación con prestadores (supabase/15_cotizaciones.sql). */
@Singleton
class CotizacionesSupabase @Inject constructor(private val supabase: SupabaseClient) : RepositorioCotizaciones {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private fun yo() = supabase.auth.currentUserOrNull()?.id ?: throw ProfesionalException("Tu sesión terminó. Vuelve a entrar.")

    override suspend fun solicitar(
        mascotaId: String, prestadorId: String, categoriaId: String, servicioId: String?, modalidad: ModalidadPrestador,
        inicio: LocalDateTime, fin: LocalDateTime?, descripcion: String, mensaje: String?, salud: SaludCompartida?, contacto: ContactoCompartido?,
    ): String = red {
        if (descripcion.trim().length < 3) throw ProfesionalException("Cuéntale al prestador qué necesitas")
        supabase.postgrest.rpc(
            "crear_solicitud_prestador",
            buildJsonObject {
                put("p_mascota", mascotaId); put("p_prestador", prestadorId); put("p_categoria", categoriaId); put("p_servicio", servicioId)
                put("p_modalidad", modalidad.name); put("p_inicio", inicio.sinSegundos()); put("p_fin", fin?.sinSegundos())
                put("p_descripcion", descripcion.trim().take(1000)); put("p_mensaje", mensaje?.trim()?.take(500))
                if (salud != null && !salud.vacia) {
                    put("p_salud", buildJsonObject {
                        salud.alergias.limpio()?.let { put("alergias", it.take(500)) }
                        salud.condiciones.limpio()?.let { put("condiciones", it.take(500)) }
                        salud.comportamiento.limpio()?.let { put("comportamiento", it.take(500)) }
                        salud.indicaciones.limpio()?.let { put("indicaciones", it.take(500)) }
                    })
                }
                if (contacto != null && !contacto.vacio) {
                    put("p_contacto", buildJsonObject {
                        contacto.telefono.limpio()?.let { put("telefono", it.take(30)) }
                        contacto.direccion.limpio()?.let { put("direccion", it.take(200)) }
                    })
                }
            },
        ).data.trim('"', ' ')
    }

    private suspend fun lista(columna: String) = supabase.from(TABLA).select {
        filter { eq(columna, yo()) }
        order("creado_en", Order.DESCENDING)
        limit(100)
    }.decodeList<FilaSolicitudPres>().map { it.comoSolicitud() }

    override suspend fun misSolicitudes() = red { lista("propietario_id") }

    override suspend fun bandeja() = red { lista("prestador_id") }

    override suspend fun detalle(id: String): DetalleSolicitudPres = red {
        val s = supabase.from(TABLA).select { filter { eq("id", id) } }.decodeSingleOrNull<FilaSolicitudPres>()?.comoSolicitud()
            ?: throw ProfesionalException("Esta solicitud ya no está disponible.")
        // El servidor solo devuelve lo que el prestador puede leer en este estado (RF-36)
        val compartido = supabase.from("solicitudes_compartido").select { filter { eq("solicitud_id", id) } }.decodeList<FilaCompartido>()
        val salud = compartido.firstOrNull { it.tipo == "SALUD" }?.datos?.let { json.decodeFromJsonElement(SaludJson.serializer(), it) }
            ?.let { SaludCompartida(it.alergias, it.condiciones, it.comportamiento, it.indicaciones) }
        val contacto = compartido.firstOrNull { it.tipo == "CONTACTO" }?.datos?.let { json.decodeFromJsonElement(ContactoJson.serializer(), it) }
            ?.let { ContactoCompartido(it.telefono, it.direccion) }
        DetalleSolicitudPres(s, salud, contacto)
    }

    override suspend fun confirmar(id: String) = rpc("confirmar_cotizacion", id)

    override suspend fun dejarDeCompartir(id: String) = red {
        supabase.from("solicitudes_compartido").delete { filter { eq("solicitud_id", id) } }
        Unit
    }

    override suspend fun cotizar(id: String, propuesta: Propuesta) = red {
        if (propuesta.precioCop == null && propuesta.inicio == null) throw ProfesionalException("Escribe un precio o propone otra fecha")
        supabase.postgrest.rpc(
            "cotizar_solicitud",
            buildJsonObject {
                put("p_id", id); put("p_precio", propuesta.precioCop); put("p_observaciones", propuesta.observaciones?.trim()?.take(500))
                put("p_vigencia", propuesta.vigencia?.toString()); put("p_inicio", propuesta.inicio?.sinSegundos()); put("p_fin", propuesta.fin?.sinSegundos())
            },
        )
        Unit
    }

    override suspend fun aceptar(id: String) = rpc("aceptar_solicitud_prestador", id)

    override suspend fun rechazar(id: String, motivo: String?) = red {
        supabase.postgrest.rpc("rechazar_solicitud_prestador", buildJsonObject { put("p_id", id); put("p_motivo", motivo?.trim()?.take(300)) })
        Unit
    }

    override suspend fun cancelar(id: String, motivo: String?) = red {
        supabase.postgrest.rpc("cancelar_solicitud_prestador", buildJsonObject { put("p_id", id); put("p_motivo", motivo?.trim()?.take(300)) })
        Unit
    }

    override suspend fun completar(id: String) = rpc("completar_solicitud_prestador", id)

    private suspend fun rpc(funcion: String, id: String) = red {
        supabase.postgrest.rpc(funcion, buildJsonObject { put("p_id", id) })
        Unit
    }

    private fun String?.limpio(): String? = this?.trim()?.ifEmpty { null }

    private suspend fun <T> red(bloque: suspend () -> T): T = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ProfesionalException) {
        throw e
    } catch (e: HttpRequestException) {
        throw ProfesionalException("Sin conexión. Revisa tu internet e intenta de nuevo.", e)
    } catch (e: IOException) {
        throw ProfesionalException("Sin conexión. Revisa tu internet e intenta de nuevo.", e)
    } catch (e: PostgrestRestException) {
        throw ProfesionalException(
            when (e.code) {
                "42501" -> "No tienes permiso para hacer esto."
                "23505" -> "Ya tienes una solicitud abierta con este prestador para esta mascota."
                "53400" -> "Tienes 5 solicitudes abiertas. Espera a que te respondan."
                "P0001", "P0002" -> e.error.takeIf { it.length < 80 } ?: "La solicitud cambió. Actualiza la lista."
                "22023" -> e.error.takeIf { it.length < 80 } ?: "Revisa el servicio, la modalidad y la fecha."
                else -> "No se pudo completar. Intenta de nuevo."
            },
            e,
        )
    } catch (e: Exception) {
        throw ProfesionalException("No se pudo completar. Intenta de nuevo.", e)
    }

    companion object {
        const val TABLA = "solicitudes_prestador"
    }
}
