package com.udenar.petcare.data.profesionales

import android.content.Context
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.ModoUso
import com.udenar.petcare.domain.profesionales.FiltroVeterinarios
import com.udenar.petcare.domain.profesionales.PerfilVeterinario
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.RepositorioModo
import com.udenar.petcare.domain.profesionales.RepositorioVeterinarios
import com.udenar.petcare.domain.profesionales.ServicioVet
import com.udenar.petcare.domain.profesionales.ValidadorVeterinario
import com.udenar.petcare.domain.profesionales.Verificacion
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class FilaVeterinario(
    val id: String,
    val nombre: String,
    @SerialName("foto_url") val fotoUrl: String? = null,
    val especies: List<String> = emptyList(),
    val especialidades: List<String> = emptyList(),
    val servicios: List<String> = emptyList(),
    val clinica: String? = null,
    val telefono: String,
    val whatsapp: String? = null,
    val correo: String? = null,
    val descripcion: String? = null,
    val domicilio: Boolean = false,
    val horario: Map<String, String> = emptyMap(),
    val tarifa: String? = null,
    val ciudad: String,
    val direccion: String,
    val lat: Double,
    val lon: Double,
    val verificacion: String = "SIN_CERTIFICADO",
    val activo: Boolean = true,
    @SerialName("rating_suma") val ratingSuma: Int = 0,
    @SerialName("rating_cantidad") val ratingCantidad: Int = 0,
    @SerialName("distancia_km") val distanciaKm: Double? = null,
) {
    fun comoPerfil() = PerfilVeterinario(
        id = id, nombre = nombre,
        especies = especies.map { Especie.desdeCodigo(it) }.toSet(),
        especialidades = especialidades,
        servicios = servicios.mapNotNull { s -> ServicioVet.entries.firstOrNull { it.name == s } }.toSet(),
        clinica = clinica, telefono = telefono, whatsapp = whatsapp, correo = correo, descripcion = descripcion,
        domicilio = domicilio,
        horario = horario.mapNotNull { (k, v) -> k.toIntOrNull()?.takeIf { it in 1..7 }?.let { DayOfWeek.of(it) to v } }.toMap(),
        tarifa = tarifa, ciudad = ciudad, direccion = direccion, lat = lat, lon = lon,
        verificacion = Verificacion.desdeCodigo(verificacion), activo = activo,
        ratingSuma = ratingSuma, ratingCantidad = ratingCantidad, distanciaKm = distanciaKm, foto = fotoUrl,
    )
}

/** Perfil, verificación y directorio de veterinarios (supabase/12_veterinarios.sql). */
@Singleton
class VeterinariosSupabase @Inject constructor(private val supabase: SupabaseClient) : RepositorioVeterinarios {

    private val _miPerfil = MutableStateFlow<PerfilVeterinario?>(null)
    override val miPerfil: StateFlow<PerfilVeterinario?> = _miPerfil.asStateFlow()

    private fun yo() = supabase.auth.currentUserOrNull()?.id ?: throw ProfesionalException("Tu sesión terminó. Vuelve a entrar.")

    override suspend fun cargarMiPerfil(): PerfilVeterinario? = red {
        val id = yo()
        supabase.from(TABLA).select { filter { eq("id", id) } }.decodeSingleOrNull<FilaVeterinario>()?.comoPerfil()
            .also { _miPerfil.value = it }
    }

    override suspend fun guardar(perfil: PerfilVeterinario) = red {
        val errores = ValidadorVeterinario.errores(perfil)
        if (errores.isNotEmpty()) throw ProfesionalException(errores.values.first())
        val id = yo()
        val datos = buildJsonObject {
            put("nombre", perfil.nombre.trim())
            put("especies", JsonArray(perfil.especies.map { JsonPrimitive(it.name) }))
            put("especialidades", JsonArray(perfil.especialidades.map { it.trim() }.filter { it.isNotEmpty() }.take(10).map { JsonPrimitive(it) }))
            put("servicios", JsonArray(perfil.servicios.map { JsonPrimitive(it.name) }))
            put("clinica", perfil.clinica.limpio())
            put("telefono", perfil.telefono.trim())
            put("whatsapp", perfil.whatsapp.limpio())
            put("correo", perfil.correo.limpio())
            put("descripcion", perfil.descripcion.limpio())
            put("domicilio", perfil.domicilio)
            put("horario", JsonObject(perfil.horario.filterValues { it.isNotBlank() }.map { (d, h) -> d.value.toString() to JsonPrimitive(h.trim()) }.toMap()))
            put("tarifa", perfil.tarifa.limpio())
            put("ciudad", perfil.ciudad.trim())
            put("direccion", perfil.direccion.trim())
            put("lat", perfil.lat)
            put("lon", perfil.lon)
            put("activo", perfil.activo)
            put("foto_url", perfil.foto)
        }
        // insert u update por separado: el id y la verificación no son editables (permisos por columna)
        val existe = supabase.from(TABLA).select { filter { eq("id", id) } }.decodeSingleOrNull<FilaVeterinario>() != null
        val fila = if (existe) {
            supabase.from(TABLA).update(datos) { select(); filter { eq("id", id) } }.decodeSingle<FilaVeterinario>()
        } else {
            supabase.from(TABLA).insert(JsonObject(datos + ("id" to JsonPrimitive(id)))) { select() }.decodeSingle<FilaVeterinario>()
        }
        _miPerfil.value = fila.comoPerfil()
    }

    override suspend fun subirFoto(bytes: ByteArray): String = red {
        // Bucket público de la galería, en la carpeta del usuario (las políticas solo dejan escribir ahí)
        val ruta = "${yo()}/perfil_${System.currentTimeMillis()}.jpg"
        val bucket = supabase.storage.from("galeria")
        bucket.upload(ruta, bytes) { contentType = io.ktor.http.ContentType.Image.JPEG }
        bucket.publicUrl(ruta)
    }

    override suspend fun subirCertificado(bytes: ByteArray, tipoMime: String) = red {
        if (bytes.size > ValidadorVeterinario.MAX_CERTIFICADO) throw ProfesionalException("El archivo pesa más de 5 MB.")
        val extension = when (tipoMime) {
            "application/pdf" -> "pdf"
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            else -> throw ProfesionalException("Sube una imagen (JPG o PNG) o un PDF.")
        }
        val id = yo()
        val bucket = supabase.storage.from(BUCKET)
        // Un solo certificado vigente: se borra el de otro formato, si había
        val anteriores = bucket.list(id).map { "$id/${it.name}" }.filter { !it.endsWith(".$extension") }
        if (anteriores.isNotEmpty()) bucket.delete(anteriores)
        bucket.upload("$id/certificado.$extension", bytes) {
            upsert = true
            contentType = ContentType.parse(if (extension == "jpg") "image/jpeg" else tipoMime)
        }
        supabase.postgrest.rpc("enviar_certificado")
        _miPerfil.value = _miPerfil.value?.copy(verificacion = Verificacion.EN_REVISION)
    }

    override suspend fun buscar(filtro: FiltroVeterinarios, lat: Double?, lon: Double?): List<PerfilVeterinario> = red {
        supabase.postgrest.rpc(
            "buscar_veterinarios",
            buildJsonObject {
                put("p_ciudad", filtro.ciudad.trim().ifEmpty { null })
                put("p_especie", filtro.especie?.name)
                put("p_domicilio", filtro.domicilio)
                put("p_servicio", filtro.servicio?.name)
                put("p_solo_verificados", filtro.soloVerificados)
                put("p_lat", lat)
                put("p_lon", lon)
            },
        ).decodeList<FilaVeterinario>().map { it.comoPerfil() }
    }

    override suspend fun obtener(id: String): PerfilVeterinario? = red {
        supabase.from(TABLA).select { filter { eq("id", id) } }.decodeSingleOrNull<FilaVeterinario>()?.comoPerfil()
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
        throw ProfesionalException(if (e.code == "42501") "No tienes permiso para hacer esto." else "No se pudo guardar. Revisa los datos e intenta de nuevo.", e)
    } catch (e: Exception) {
        throw ProfesionalException("No se pudo completar. Intenta de nuevo.", e)
    }

    companion object {
        const val TABLA = "veterinarios"
        const val BUCKET = "certificados"
    }
}

/** Modo activo guardado en el teléfono (RF-30). */
@Singleton
class ModoLocal @Inject constructor(@ApplicationContext contexto: Context) : RepositorioModo {
    private val prefs = contexto.getSharedPreferences("petcare_modo", Context.MODE_PRIVATE)
    private val _modo = MutableStateFlow(ModoUso.entries.firstOrNull { it.name == prefs.getString("modo", null) } ?: ModoUso.PERSONAL)
    override val modo: StateFlow<ModoUso> = _modo.asStateFlow()

    override fun cambiar(modo: ModoUso) {
        prefs.edit().putString("modo", modo.name).apply()
        _modo.value = modo
    }
}
