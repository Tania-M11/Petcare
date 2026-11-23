package com.udenar.petcare.data.profesionales

import com.udenar.petcare.domain.profesionales.MotivoReporte
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.RepositorioResenas
import com.udenar.petcare.domain.profesionales.Resena
import com.udenar.petcare.domain.profesionales.TipoReporte
import com.udenar.petcare.domain.profesionales.TipoResena
import com.udenar.petcare.domain.profesionales.ValidadorResena
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class FilaResena(
    val id: String,
    @SerialName("solicitud_id") val solicitudId: String,
    @SerialName("autor_id") val autorId: String,
    @SerialName("autor_nombre") val autorNombre: String? = null,
    val estrellas: Int,
    val comentario: String? = null,
    @SerialName("creado_en") val creadoEn: String,
)

/** Reseñas y reportes (supabase/16_notificaciones_resenas.sql). El promedio lo calcula el servidor. */
@Singleton
class ResenasSupabase @Inject constructor(private val supabase: SupabaseClient) : RepositorioResenas {

    private fun yo() = supabase.auth.currentUserOrNull()?.id

    private fun FilaResena.comoResena() = Resena(
        id, solicitudId, autorNombre, estrellas, comentario,
        OffsetDateTime.parse(creadoEn).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate(), mia = autorId == yo(),
    )

    override suspend fun de(profesionalId: String, tipo: TipoResena, pagina: Int, tamano: Int): List<Resena> = red {
        supabase.from(TABLA).select {
            filter { eq("profesional_id", profesionalId); eq("tipo", tipo.name); eq("oculta", false) }
            order("creado_en", Order.DESCENDING)
            range((pagina * tamano).toLong(), (pagina * tamano + tamano - 1).toLong())
        }.decodeList<FilaResena>().map { it.comoResena() }
    }

    override suspend fun mias(): Map<String, Resena> = red {
        val id = yo() ?: return@red emptyMap()
        supabase.from(TABLA).select { filter { eq("autor_id", id) } }.decodeList<FilaResena>().associate { it.solicitudId to it.comoResena() }
    }

    override suspend fun guardar(solicitudId: String, tipo: TipoResena, estrellas: Int, comentario: String?) = red {
        if (!ValidadorResena.valida(estrellas, comentario)) throw ProfesionalException("Elige de 1 a 5 estrellas (comentario de hasta 500 caracteres)")
        supabase.postgrest.rpc(
            "guardar_resena",
            buildJsonObject { put("p_solicitud", solicitudId); put("p_tipo", tipo.name); put("p_estrellas", estrellas); put("p_comentario", comentario?.trim()) },
        )
        Unit
    }

    override suspend fun eliminar(solicitudId: String) = red {
        supabase.postgrest.rpc("eliminar_resena", buildJsonObject { put("p_solicitud", solicitudId) })
        Unit
    }

    override suspend fun reportar(tipo: TipoReporte, refId: String, motivo: MotivoReporte, detalle: String?) = red {
        val id = yo() ?: throw ProfesionalException("Tu sesión terminó. Vuelve a entrar.")
        supabase.from("reportes").insert(
            buildJsonObject { put("autor_id", id); put("tipo", tipo.name); put("ref_id", refId); put("motivo", motivo.name); put("detalle", detalle?.trim()?.take(500)?.ifEmpty { null }) },
        )
        Unit
    }

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
                "42501" -> "Solo puedes calificar un servicio completado."
                "23505" -> "Ya enviaste un reporte de esto. Gracias, lo revisaremos."
                else -> "No se pudo completar. Intenta de nuevo."
            },
            e,
        )
    } catch (e: Exception) {
        throw ProfesionalException("No se pudo completar. Intenta de nuevo.", e)
    }

    companion object {
        const val TABLA = "resenas"
    }
}
