package com.udenar.petcare.data.profesionales

import com.udenar.petcare.data.cuidados.EventoRemoto
import com.udenar.petcare.data.cuidados.comoDominio
import com.udenar.petcare.data.mascotas.MascotaRemota
import com.udenar.petcare.data.mascotas.comoDominio
import com.udenar.petcare.domain.cuidados.Autor
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.documentos.TipoDocumento
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.profesionales.DocumentoClinico
import com.udenar.petcare.domain.profesionales.EstadoSolicitudVet
import com.udenar.petcare.domain.profesionales.FichaClinica
import com.udenar.petcare.domain.profesionales.Modalidad
import com.udenar.petcare.domain.profesionales.NuevoCuidadoClinico
import com.udenar.petcare.domain.profesionales.Paciente
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.RepositorioAtencionVet
import com.udenar.petcare.domain.profesionales.ServicioVet
import com.udenar.petcare.domain.profesionales.SolicitudVet
import com.udenar.petcare.domain.profesionales.TIPOS_CLINICOS
import com.udenar.petcare.domain.profesionales.ValidadorVeterinario
import com.udenar.petcare.domain.profesionales.VetVinculado
import com.udenar.petcare.domain.profesionales.Verificacion
import com.udenar.petcare.domain.salud.RegistroPeso
import com.udenar.petcare.domain.salud.Visita
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class FilaSolicitud(
    val id: String,
    @SerialName("mascota_id") val mascotaId: String,
    @SerialName("veterinario_id") val veterinarioId: String,
    val servicio: String,
    val modalidad: String,
    @SerialName("fecha_preferida") val fechaPreferida: String,
    @SerialName("fecha_confirmada") val fechaConfirmada: String? = null,
    val mensaje: String? = null,
    @SerialName("motivo_rechazo") val motivoRechazo: String? = null,
    val estado: String,
    @SerialName("mascota_nombre") val mascotaNombre: String,
    @SerialName("mascota_especie") val mascotaEspecie: String,
    @SerialName("propietario_nombre") val propietarioNombre: String? = null,
    @SerialName("propietario_telefono") val propietarioTelefono: String? = null,
    @SerialName("veterinario_nombre") val veterinarioNombre: String,
    @SerialName("creado_en") val creadoEn: String? = null,
    val fotos: List<String> = emptyList(),
) {
    fun comoSolicitud() = SolicitudVet(
        id, mascotaId, mascotaNombre, Especie.desdeCodigo(mascotaEspecie), propietarioNombre, propietarioTelefono, veterinarioId, veterinarioNombre,
        ServicioVet.entries.firstOrNull { it.name == servicio } ?: ServicioVet.OTROS,
        Modalidad.entries.firstOrNull { it.name == modalidad } ?: Modalidad.CONSULTORIO,
        LocalDateTime.parse(fechaPreferida), fechaConfirmada?.let(LocalDateTime::parse), mensaje, motivoRechazo,
        EstadoSolicitudVet.desdeCodigo(estado), creadoEn?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() },
        fotos = fotos,
    )
}

@Serializable
private data class FilaVinculo(@SerialName("mascota_id") val mascotaId: String, @SerialName("veterinario_id") val veterinarioId: String)

@Serializable
private data class FilaVetCorta(
    val id: String,
    val nombre: String,
    val clinica: String? = null,
    val telefono: String? = null,
    val verificacion: String = "SIN_CERTIFICADO",
)

@Serializable
private data class FilaVisita(
    val id: String,
    @SerialName("mascota_id") val mascotaId: String,
    val fecha: String,
    val motivo: String,
    val veterinario: String? = null,
    val clinica: String? = null,
    val tratamiento: String? = null,
    val medicamentos: String? = null,
    val notas: String? = null,
    @SerialName("registrado_por") val registradoPor: String? = null,
    @SerialName("registrado_por_nombre") val registradoPorNombre: String? = null,
    @SerialName("registrado_por_rol") val registradoPorRol: String? = null,
    val eliminado: Boolean = false,
)

@Serializable
private data class FilaPeso(
    val id: String,
    @SerialName("mascota_id") val mascotaId: String,
    val fecha: String,
    val gramos: Int,
    val notas: String? = null,
    @SerialName("registrado_por") val registradoPor: String? = null,
    @SerialName("registrado_por_nombre") val registradoPorNombre: String? = null,
    @SerialName("registrado_por_rol") val registradoPorRol: String? = null,
    val eliminado: Boolean = false,
)

@Serializable
private data class FilaDocumento(
    val id: String,
    val nombre: String,
    val tipo: String,
    val fecha: String,
    val mime: String,
    val ruta: String? = null,
    @SerialName("registrado_por_nombre") val registradoPorNombre: String? = null,
    val eliminado: Boolean = false,
)

/** Solicitudes, vínculos y ficha clínica contra Supabase (supabase/13_solicitudes_vet.sql). */
@Singleton
class AtencionVetSupabase @Inject constructor(private val supabase: SupabaseClient) : RepositorioAtencionVet {

    private fun yo() = supabase.auth.currentUserOrNull()?.id ?: throw ProfesionalException("Tu sesión terminó. Vuelve a entrar.")

    override suspend fun solicitar(
        mascotaId: String, veterinarioId: String, servicio: ServicioVet, modalidad: Modalidad, fecha: LocalDateTime, mensaje: String?,
    ): String = red {
        supabase.postgrest.rpc(
            "crear_solicitud_vet",
            buildJsonObject {
                put("p_mascota", mascotaId); put("p_veterinario", veterinarioId); put("p_servicio", servicio.name)
                put("p_modalidad", modalidad.name); put("p_fecha", fecha.withSecond(0).withNano(0).toString()); put("p_mensaje", mensaje?.trim()?.take(500))
            },
        ).data.trim('"', ' ')
    }

    private suspend fun solicitudes(columna: String): List<SolicitudVet> = supabase.from(SOLICITUDES)
        .select {
            filter { eq(columna, yo()) }
            order("creado_en", Order.DESCENDING)
            limit(100)
        }.decodeList<FilaSolicitud>().map { it.comoSolicitud() }

    override suspend fun misSolicitudes() = red { solicitudes("propietario_id") }

    override suspend fun bandeja() = red { solicitudes("veterinario_id") }

    override suspend fun cancelar(id: String) = red {
        supabase.postgrest.rpc("cancelar_solicitud_vet", buildJsonObject { put("p_id", id) })
        Unit
    }

    override suspend fun resolver(id: String, aceptar: Boolean, fecha: LocalDateTime?, motivo: String?) = red {
        supabase.postgrest.rpc(
            "resolver_solicitud_vet",
            buildJsonObject {
                put("p_id", id); put("p_aceptar", aceptar)
                put("p_fecha", fecha?.withSecond(0)?.withNano(0)?.toString()); put("p_motivo", motivo?.trim()?.take(300))
            },
        )
        Unit
    }

    override suspend fun completar(id: String) = red {
        supabase.postgrest.rpc("completar_solicitud_vet", buildJsonObject { put("p_id", id) })
        Unit
    }

    override suspend fun desvincular(mascotaId: String, veterinarioId: String) = red {
        supabase.postgrest.rpc("desvincular_vet", buildJsonObject { put("p_mascota", mascotaId); put("p_veterinario", veterinarioId) })
        Unit
    }

    override suspend fun vetsDe(mascotaId: String): List<VetVinculado> = red {
        val ids = supabase.from(VINCULOS).select { filter { eq("mascota_id", mascotaId) } }.decodeList<FilaVinculo>().map { it.veterinarioId }
        if (ids.isEmpty()) return@red emptyList()
        val perfiles = supabase.from("veterinarios").select { filter { isIn("id", ids) } }.decodeList<FilaVetCorta>().associateBy { it.id }
        ids.map { id ->
            perfiles[id]?.let { VetVinculado(it.id, it.nombre, Verificacion.desdeCodigo(it.verificacion).verificado, it.clinica, it.telefono) }
                ?: VetVinculado(id, "Veterinario (perfil oculto)", false)
        }
    }

    override suspend fun pacientes(): List<Paciente> = red {
        val ids = supabase.from(VINCULOS).select { filter { eq("veterinario_id", yo()) } }.decodeList<FilaVinculo>().map { it.mascotaId }
        if (ids.isEmpty()) return@red emptyList()
        supabase.from("mascotas").select { filter { isIn("id", ids); eq("eliminado", false) } }
            .decodeList<MascotaRemota>().map { Paciente(it.comoEntidad().comoDominio()) }.sortedBy { it.mascota.nombre }
    }

    override suspend fun ficha(mascotaId: String): FichaClinica = red {
        val mascota = supabase.from("mascotas").select { filter { eq("id", mascotaId) } }.decodeSingleOrNull<MascotaRemota>()
            ?: throw ProfesionalException("Ya no tienes acceso a esta mascota.")
        val cuidados = supabase.from("eventos_cuidado").select { filter { eq("mascota_id", mascotaId); eq("eliminado", false) } }
            .decodeList<EventoRemoto>().map { it.comoEntidad().comoDominio() }
            .filter { it.tipo in TIPOS_CLINICOS || it.tipo == TipoCuidado.CITA_VET }
            .sortedByDescending { it.fechaInicio }
        val visitas = supabase.from("visitas").select { filter { eq("mascota_id", mascotaId); eq("eliminado", false) }; order("fecha", Order.DESCENDING) }
            .decodeList<FilaVisita>().map {
                Visita(it.id, it.mascotaId, LocalDate.parse(it.fecha), it.motivo, it.veterinario, it.clinica, it.tratamiento, it.medicamentos, it.notas,
                    registradoPor = Autor(it.registradoPor, it.registradoPorNombre, it.registradoPorRol))
            }
        val pesos = supabase.from("pesos").select { filter { eq("mascota_id", mascotaId); eq("eliminado", false) }; order("fecha", Order.DESCENDING); limit(20) }
            .decodeList<FilaPeso>().map { RegistroPeso(it.id, it.mascotaId, LocalDate.parse(it.fecha), it.gramos, it.notas, Autor(it.registradoPor, it.registradoPorNombre, it.registradoPorRol)) }
        val documentos = supabase.from("documentos").select { filter { eq("mascota_id", mascotaId); eq("eliminado", false) }; order("fecha", Order.DESCENDING) }
            .decodeList<FilaDocumento>().map {
                DocumentoClinico(it.id, it.nombre, TipoDocumento.entries.firstOrNull { t -> t.name == it.tipo } ?: TipoDocumento.OTRO, LocalDate.parse(it.fecha), it.mime, it.ruta, it.registradoPorNombre)
            }
        FichaClinica(mascota.comoEntidad().comoDominio(), cuidados, visitas, pesos, documentos)
    }

    override suspend fun registrarVisita(mascotaId: String, fecha: LocalDate, motivo: String, tratamiento: String?, medicamentos: String?, notas: String?) = red {
        if (motivo.isBlank()) throw ProfesionalException("Escribe el motivo de la visita")
        val perfil = supabase.from("veterinarios").select { filter { eq("id", yo()) } }.decodeSingleOrNull<FilaVetCorta>()
        supabase.from("visitas").insert(
            buildJsonObject {
                put("id", UUID.randomUUID().toString()); put("mascota_id", mascotaId); put("fecha", fecha.toString()); put("motivo", motivo.trim().take(200))
                put("veterinario", perfil?.nombre); put("clinica", perfil?.clinica)
                put("tratamiento", tratamiento.limpio()); put("medicamentos", medicamentos.limpio()); put("notas", notas.limpio())
                put("registrado_en", Instant.now().toString())
            },
        )
        Unit
    }

    override suspend fun registrarCuidado(mascotaId: String, cuidado: NuevoCuidadoClinico) = red {
        if (cuidado.tipo !in TIPOS_CLINICOS) throw ProfesionalException("Tipo de cuidado no permitido")
        if (cuidado.titulo.isBlank()) throw ProfesionalException("Escribe el nombre del producto o medicamento")
        val medicamento = cuidado.tipo == TipoCuidado.MEDICAMENTO
        if (medicamento && cuidado.horas.isEmpty()) throw ProfesionalException("Agrega al menos una hora")
        val detalle = buildJsonObject {
            cuidado.dosis.limpio()?.let { put("dosis", it) }
            cuidado.indicaciones.limpio()?.let { put("indicaciones", it) }
            if (cuidado.tipo != TipoCuidado.VACUNA && !medicamento) put("producto", cuidado.titulo.trim())
        }
        supabase.from("eventos_cuidado").insert(
            buildJsonObject {
                put("id", UUID.randomUUID().toString()); put("mascota_id", mascotaId); put("tipo", cuidado.tipo.name); put("titulo", cuidado.titulo.trim().take(80))
                put("frecuencia_tipo", if (medicamento) "DIARIO" else "UNICO")
                put("horas", JsonArray(cuidado.horas.sorted().map { JsonPrimitive(it.toString()) }))
                put("ancla", "CALENDARIO"); put("fecha_inicio", cuidado.fecha.toString())
                put("fecha_fin", if (medicamento) cuidado.fechaFin?.toString() else null)
                put("aviso_anticipado_dias", if (medicamento) null else 3)
                put("detalle", detalle); put("origen", "VETERINARIO")
            },
        )
        Unit
    }

    override suspend fun registrarPeso(mascotaId: String, gramos: Int) = red {
        if (gramos !in 1..200_000) throw ProfesionalException("Escribe un peso válido")
        supabase.from("pesos").insert(
            buildJsonObject {
                put("id", UUID.randomUUID().toString()); put("mascota_id", mascotaId); put("fecha", LocalDate.now().toString()); put("gramos", gramos)
                put("registrado_en", Instant.now().toString())
            },
        )
        Unit
    }

    override suspend fun subirDocumento(mascotaId: String, nombre: String, tipo: TipoDocumento, bytes: ByteArray, mime: String) = red {
        if (bytes.size > ValidadorVeterinario.MAX_CERTIFICADO) throw ProfesionalException("El archivo pesa más de 5 MB.")
        val extension = when (mime) {
            "application/pdf" -> "pdf"
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            else -> throw ProfesionalException("Sube una imagen (JPG o PNG) o un PDF.")
        }
        val id = UUID.randomUUID().toString()
        // Carpeta del autor; el dueño lo ve porque la mascota es suya (13_solicitudes_vet.sql)
        val ruta = "${yo()}/$mascotaId/$id.$extension"
        supabase.storage.from("documentos").upload(ruta, bytes) { contentType = ContentType.parse(mime) }
        supabase.from("documentos").insert(
            buildJsonObject {
                put("id", id); put("mascota_id", mascotaId); put("nombre", nombre.trim().ifBlank { "Documento" }.take(120)); put("tipo", tipo.name)
                put("fecha", LocalDate.now().toString()); put("mime", mime); put("tamano_bytes", bytes.size); put("ruta", ruta)
                put("registrado_en", Instant.now().toString())
            },
        )
        Unit
    }

    override suspend fun descargarDocumento(ruta: String): ByteArray = red { supabase.storage.from("documentos").downloadAuthenticated(ruta) }

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
                "23505" -> "Ya enviaste una solicitud a este veterinario para esta mascota."
                "53400" -> "Tienes 5 solicitudes pendientes. Espera a que te respondan."
                "P0001" -> e.error.takeIf { it.length < 80 } ?: "La solicitud cambió. Actualiza la lista."
                "P0002" -> "Este veterinario ya no está disponible."
                "22023" -> "Revisa el servicio, la modalidad y la fecha."
                else -> "No se pudo completar. Intenta de nuevo."
            },
            e,
        )
    } catch (e: Exception) {
        throw ProfesionalException("No se pudo completar. Intenta de nuevo.", e)
    }

    companion object {
        const val SOLICITUDES = "solicitudes_vet"
        const val VINCULOS = "vinculos_vet"
    }
}
