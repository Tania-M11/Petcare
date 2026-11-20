package com.udenar.petcare.data.compartir

import com.udenar.petcare.data.alarmas.PlanificadorAlarmas
import com.udenar.petcare.data.cuidados.EventoDao
import com.udenar.petcare.data.cuidados.RegistroDao
import com.udenar.petcare.data.local.CursorDao
import com.udenar.petcare.data.mascotas.MascotaDao
import com.udenar.petcare.data.salud.PesoDao
import com.udenar.petcare.data.salud.VisitaDao
import com.udenar.petcare.domain.compartir.CompartirException
import com.udenar.petcare.domain.compartir.EstadoSolicitud
import com.udenar.petcare.domain.compartir.MascotaPorCodigo
import com.udenar.petcare.domain.compartir.Miembro
import com.udenar.petcare.domain.compartir.RepositorioCompartir
import com.udenar.petcare.domain.compartir.ResultadoSolicitud
import com.udenar.petcare.domain.compartir.RolMascota
import com.udenar.petcare.domain.compartir.SolicitudCuidado
import com.udenar.petcare.domain.compartir.normalizarCodigo
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.sync.ProgramadorSync
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class FilaSolicitud(
    val id: String,
    @SerialName("mascota_id") val mascotaId: String,
    @SerialName("solicitante_id") val solicitanteId: String,
    @SerialName("solicitante_nombre") val solicitanteNombre: String? = null,
    val estado: String,
    @SerialName("creado_en") val creadoEn: String,
) {
    fun comoDominio() = SolicitudCuidado(
        id, mascotaId, solicitanteId, solicitanteNombre ?: "Alguien",
        EstadoSolicitud.entries.firstOrNull { it.name == estado } ?: EstadoSolicitud.PENDIENTE,
        OffsetDateTime.parse(creadoEn).toInstant(),
    )
}

@Serializable
private data class FilaMiembro(
    @SerialName("mascota_id") val mascotaId: String,
    @SerialName("usuario_id") val usuarioId: String,
    val rol: String,
    val nombre: String? = null,
)

@Singleton
class RepositorioCompartirSupabase @Inject constructor(
    private val supabase: SupabaseClient,
    private val mascotas: MascotaDao,
    private val eventos: EventoDao,
    private val registros: RegistroDao,
    private val pesos: PesoDao,
    private val visitas: VisitaDao,
    private val paseos: com.udenar.petcare.data.paseos.PaseoDao,
    private val cursores: CursorDao,
    private val alarmas: PlanificadorAlarmas,
    private val sync: ProgramadorSync,
) : RepositorioCompartir {

    private val yo: String get() = supabase.auth.currentUserOrNull()?.id ?: throw CompartirException("Tu sesión terminó. Vuelve a entrar.")

    override suspend fun buscar(codigo: String): MascotaPorCodigo? = red {
        val r = supabase.postgrest.rpc("buscar_mascota_por_codigo", buildJsonObject { put("p_codigo", normalizarCodigo(codigo)) })
        val datos = r.data.trim()
        if (datos.isEmpty() || datos == "null") return@red null
        val o = Json.parseToJsonElement(datos) as JsonObject
        MascotaPorCodigo(
            mascotaId = o["mascota_id"]!!.jsonPrimitive.content,
            nombre = o["nombre"]!!.jsonPrimitive.content,
            especie = Especie.desdeCodigo(o["especie"]?.jsonPrimitive?.contentOrNull),
        )
    }

    override suspend fun solicitar(codigo: String): ResultadoSolicitud = red {
        val r = supabase.postgrest.rpc("solicitar_cuidado", buildJsonObject { put("p_codigo", normalizarCodigo(codigo)) })
        ResultadoSolicitud.entries.firstOrNull { it.name == r.data.trim('"', ' ') } ?: ResultadoSolicitud.NO_DISPONIBLE
    }

    override suspend fun misSolicitudes(): List<SolicitudCuidado> = red {
        supabase.from("solicitudes_cuidado").select {
            filter { eq("solicitante_id", yo) }
            order("creado_en", Order.DESCENDING)
            limit(20)
        }.decodeList<FilaSolicitud>().map { it.comoDominio() }
    }

    override suspend fun solicitudesRecibidas(): List<SolicitudCuidado> = red {
        val id = yo
        supabase.from("solicitudes_cuidado").select {
            filter {
                eq("estado", "PENDIENTE")
                neq("solicitante_id", id)
            }
            order("creado_en", Order.ASCENDING)
        }.decodeList<FilaSolicitud>().map { it.comoDominio() }
    }

    override suspend fun resolver(solicitudId: String, aprobar: Boolean) = red {
        supabase.postgrest.rpc("resolver_solicitud", buildJsonObject { put("p_id", solicitudId); put("p_aprobar", aprobar) })
        Unit
    }

    override suspend fun miembros(mascotaId: String): List<Miembro> = red {
        supabase.from("miembros_mascota").select { filter { eq("mascota_id", mascotaId) } }
            .decodeList<FilaMiembro>()
            .map { Miembro(it.usuarioId, it.nombre ?: "Sin nombre", if (it.rol == "PROPIETARIO") RolMascota.PROPIETARIO else RolMascota.CUIDADOR) }
            .sortedBy { it.rol }
    }

    override suspend fun quitar(mascotaId: String, usuarioId: String) = red {
        supabase.postgrest.rpc("quitar_miembro", buildJsonObject { put("p_mascota", mascotaId); put("p_usuario", usuarioId) })
        Unit
    }

    override suspend fun salir(mascotaId: String) {
        quitar(mascotaId, yo)
        borrarDelTelefono(mascotaId)
    }

    override suspend fun regenerarCodigo(mascotaId: String): String = red {
        supabase.postgrest.rpc("regenerar_codigo", buildJsonObject { put("p_mascota", mascotaId) }).data.trim('"', ' ')
    }

    override suspend fun desactivarCodigo(mascotaId: String) = red {
        supabase.postgrest.rpc("desactivar_codigo", buildJsonObject { put("p_mascota", mascotaId) })
        Unit
    }

    override suspend fun actualizarMembresias() {
        val id = supabase.auth.currentUserOrNull()?.id ?: return
        val visibles = supabase.from("miembros_mascota").select { filter { eq("usuario_id", id) } }
            .decodeList<FilaMiembro>().map { it.mascotaId }.toSet()
        // Dejé de cuidar (o me quitaron): fuera del teléfono
        for (ajena in mascotas.ajenas(id).toSet() - visibles) borrarDelTelefono(ajena)
        // Puedo ver una mascota que no está en el teléfono: descargar todo de nuevo
        if ((visibles - mascotas.ids().toSet()).isNotEmpty()) {
            cursores.reiniciar()
            sync.solicitar()
        }
    }

    private suspend fun borrarDelTelefono(mascotaId: String) {
        registros.borrarDeMascota(mascotaId)
        eventos.borrarDeMascota(mascotaId)
        pesos.borrarDeMascota(mascotaId)
        visitas.borrarDeMascota(mascotaId)
        paseos.borrarDeMascota(mascotaId)
        mascotas.borrar(mascotaId)
        alarmas.reprogramar()
    }

    private suspend fun <T> red(bloque: suspend () -> T): T = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (e: CompartirException) {
        throw e
    } catch (e: HttpRequestException) {
        throw CompartirException("Sin conexión. Revisa tu internet e intenta de nuevo.", e)
    } catch (e: IOException) {
        throw CompartirException("Sin conexión. Revisa tu internet e intenta de nuevo.", e)
    } catch (e: PostgrestRestException) {
        throw CompartirException(if (e.code == "42501") "No tienes permiso para hacer esto." else "No se pudo completar. Intenta de nuevo.", e)
    } catch (e: Exception) {
        throw CompartirException("No se pudo completar. Intenta de nuevo.", e)
    }

}
