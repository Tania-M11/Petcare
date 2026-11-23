package com.udenar.petcare.data.profesionales

import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.profesionales.Categoria
import com.udenar.petcare.domain.profesionales.FiltroPrestadores
import com.udenar.petcare.domain.profesionales.ModalidadPrestador
import com.udenar.petcare.domain.profesionales.ModoPrecio
import com.udenar.petcare.domain.profesionales.PerfilPrestador
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.RepositorioPrestadores
import com.udenar.petcare.domain.profesionales.ServicioPrestador
import com.udenar.petcare.domain.profesionales.TipoPrestador
import com.udenar.petcare.domain.profesionales.UnidadPrecio
import com.udenar.petcare.domain.profesionales.ValidadorPrestador
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
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ServicioJson(
    val id: String,
    val categoria: String,
    val nombre: String,
    val descripcion: String? = null,
    val modo: String = "COTIZACION",
    val monto: Int? = null,
    val unidad: String? = null,
    val especies: List<String> = emptyList(),
)

@Serializable
private data class FilaPrestador(
    val id: String,
    val nombre: String,
    val tipo: String = "PERSONA",
    val descripcion: String,
    val telefono: String,
    val whatsapp: String? = null,
    val correo: String? = null,
    val ciudad: String,
    val direccion: String? = null,
    val zona: String? = null,
    @SerialName("radio_km") val radioKm: Int? = null,
    val lat: Double,
    val lon: Double,
    val modalidades: List<String> = emptyList(),
    val especies: List<String> = emptyList(),
    val categorias: List<String> = emptyList(),
    val servicios: List<ServicioJson> = emptyList(),
    val horario: Map<String, String> = emptyMap(),
    @SerialName("no_disponible") val noDisponible: List<String> = emptyList(),
    val recibiendo: Boolean = true,
    val galeria: List<String> = emptyList(),
    val activo: Boolean = true,
    @SerialName("rating_suma") val ratingSuma: Int = 0,
    @SerialName("rating_cantidad") val ratingCantidad: Int = 0,
    @SerialName("distancia_km") val distanciaKm: Double? = null,
) {
    fun comoPerfil() = PerfilPrestador(
        id = id, nombre = nombre, tipo = TipoPrestador.entries.firstOrNull { it.name == tipo } ?: TipoPrestador.PERSONA,
        descripcion = descripcion, telefono = telefono, whatsapp = whatsapp, correo = correo, ciudad = ciudad,
        direccion = direccion, zona = zona, radioKm = radioKm, lat = lat, lon = lon,
        modalidades = modalidades.mapNotNull { m -> ModalidadPrestador.entries.firstOrNull { it.name == m } }.toSet(),
        especies = especies.map { Especie.desdeCodigo(it) }.toSet(),
        categorias = categorias,
        servicios = servicios.map { s ->
            ServicioPrestador(
                s.id, s.categoria, s.nombre, s.descripcion, ModoPrecio.entries.firstOrNull { it.name == s.modo } ?: ModoPrecio.COTIZACION,
                s.monto, UnidadPrecio.entries.firstOrNull { it.name == s.unidad }, s.especies.map { Especie.desdeCodigo(it) }.toSet(),
            )
        },
        horario = horario.mapNotNull { (k, v) -> k.toIntOrNull()?.takeIf { it in 1..7 }?.let { DayOfWeek.of(it) to v } }.toMap(),
        noDisponible = noDisponible.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted(),
        recibiendo = recibiendo, galeria = galeria, activo = activo,
        ratingSuma = ratingSuma, ratingCantidad = ratingCantidad, distanciaKm = distanciaKm,
    )
}

@Serializable
private data class FilaCategoria(val id: String, val nombre: String, val icono: String, val orden: Int = 0)

/** Perfil, galería y directorio de prestadores (supabase/14_prestadores.sql). */
@Singleton
class PrestadoresSupabase @Inject constructor(private val supabase: SupabaseClient) : RepositorioPrestadores {

    private val _miPerfil = MutableStateFlow<PerfilPrestador?>(null)
    override val miPerfil: StateFlow<PerfilPrestador?> = _miPerfil.asStateFlow()
    private var cacheCategorias: List<Categoria>? = null

    private fun yo() = supabase.auth.currentUserOrNull()?.id ?: throw ProfesionalException("Tu sesión terminó. Vuelve a entrar.")

    override suspend fun cargarMiPerfil(): PerfilPrestador? = red {
        supabase.from(TABLA).select { filter { eq("id", yo()) } }.decodeSingleOrNull<FilaPrestador>()?.comoPerfil().also { _miPerfil.value = it }
    }

    override suspend fun categorias(): List<Categoria> = cacheCategorias ?: red {
        supabase.from("categorias_servicio").select { filter { eq("activa", true) }; order("orden", Order.ASCENDING) }
            .decodeList<FilaCategoria>().map { Categoria(it.id, it.nombre, it.icono, it.orden) }.also { cacheCategorias = it }
    }

    override suspend fun guardar(perfil: PerfilPrestador) = red {
        val errores = ValidadorPrestador.errores(perfil)
        if (errores.isNotEmpty()) throw ProfesionalException(errores.values.first())
        val id = yo()
        val datos = buildJsonObject {
            put("nombre", perfil.nombre.trim()); put("tipo", perfil.tipo.name); put("descripcion", perfil.descripcion.trim())
            put("telefono", perfil.telefono.trim()); put("whatsapp", perfil.whatsapp.limpio()); put("correo", perfil.correo.limpio())
            put("ciudad", perfil.ciudad.trim()); put("direccion", perfil.direccion.limpio()); put("zona", perfil.zona.limpio()); put("radio_km", perfil.radioKm)
            put("lat", perfil.lat); put("lon", perfil.lon)
            put("modalidades", JsonArray(perfil.modalidades.sortedBy { it.ordinal }.map { JsonPrimitive(it.name) }))
            put("especies", JsonArray(perfil.especies.map { JsonPrimitive(it.name) }))
            put("categorias", JsonArray(perfil.categorias.map { JsonPrimitive(it) }))
            put(
                "servicios",
                JsonArray(
                    perfil.servicios.map { s ->
                        buildJsonObject {
                            put("id", s.id); put("categoria", s.categoriaId); put("nombre", s.nombre.trim().take(80)); put("descripcion", s.descripcion.limpio()?.take(300))
                            put("modo", s.modo.name); put("monto", if (s.modo == ModoPrecio.COTIZACION) null else s.montoCop); put("unidad", s.unidad?.name)
                            put("especies", JsonArray(s.especies.map { JsonPrimitive(it.name) }))
                        }
                    },
                ),
            )
            put("horario", JsonObject(perfil.horario.filterValues { it.isNotBlank() }.map { (d, h) -> d.value.toString() to JsonPrimitive(h.trim()) }.toMap()))
            put("no_disponible", JsonArray(perfil.noDisponible.distinct().sorted().map { JsonPrimitive(it.toString()) }))
            put("recibiendo", perfil.recibiendo)
            put("galeria", JsonArray(perfil.galeria.take(ValidadorPrestador.MAX_GALERIA).map { JsonPrimitive(it) }))
            put("activo", perfil.activo)
        }
        val existe = supabase.from(TABLA).select { filter { eq("id", id) } }.decodeSingleOrNull<FilaPrestador>() != null
        val fila = if (existe) {
            supabase.from(TABLA).update(datos) { select(); filter { eq("id", id) } }.decodeSingle<FilaPrestador>()
        } else {
            supabase.from(TABLA).insert(JsonObject(datos + ("id" to JsonPrimitive(id)))) { select() }.decodeSingle<FilaPrestador>()
        }
        _miPerfil.value = fila.comoPerfil()
    }

    override suspend fun subirFoto(bytes: ByteArray): String = red {
        val ruta = "${yo()}/${UUID.randomUUID()}.jpg"
        val bucket = supabase.storage.from(BUCKET)
        bucket.upload(ruta, bytes) { contentType = ContentType.Image.JPEG }
        bucket.publicUrl(ruta)
    }

    override suspend fun borrarFoto(url: String) = red {
        val ruta = url.substringAfter("/object/public/$BUCKET/", "")
        if (ruta.startsWith(yo() + "/")) supabase.storage.from(BUCKET).delete(ruta)
    }

    override suspend fun buscar(filtro: FiltroPrestadores, lat: Double?, lon: Double?): List<PerfilPrestador> = red {
        supabase.postgrest.rpc(
            "buscar_prestadores",
            buildJsonObject {
                put("p_ciudad", filtro.ciudad.trim().ifEmpty { null }); put("p_zona", filtro.zona.trim().ifEmpty { null })
                put("p_categoria", filtro.categoria); put("p_especie", filtro.especie?.name); put("p_modalidad", filtro.modalidad?.name)
                put("p_lat", lat); put("p_lon", lon)
            },
        ).decodeList<FilaPrestador>().map { it.comoPerfil() }
    }

    override suspend fun obtener(id: String): PerfilPrestador? = red {
        supabase.from(TABLA).select { filter { eq("id", id) } }.decodeSingleOrNull<FilaPrestador>()?.comoPerfil()
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
                "23503" -> "Una de las categorías ya no existe. Actualiza y vuelve a elegir."
                "22023" -> "Revisa tus servicios: nombre, categoría y precio."
                else -> "No se pudo guardar. Revisa los datos e intenta de nuevo."
            },
            e,
        )
    } catch (e: Exception) {
        throw ProfesionalException("No se pudo completar. Intenta de nuevo.", e)
    }

    companion object {
        const val TABLA = "prestadores"
        const val BUCKET = "galeria"
    }
}
