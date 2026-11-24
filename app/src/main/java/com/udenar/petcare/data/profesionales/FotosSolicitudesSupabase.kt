package com.udenar.petcare.data.profesionales

import com.udenar.petcare.domain.profesionales.FotosSolicitudes
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.TipoSolicitud
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/** Fotos de solicitudes en el bucket privado "solicitudes": {propietario}/{solicitud}/{n}.jpg */
class FotosSolicitudesSupabase @Inject constructor(private val supabase: SupabaseClient) : FotosSolicitudes {
    private val bucket get() = supabase.storage.from("solicitudes")

    override suspend fun adjuntar(tipo: TipoSolicitud, solicitudId: String, fotos: List<ByteArray>) {
        if (fotos.isEmpty()) return
        try {
            val yo = supabase.auth.currentUserOrNull()?.id ?: throw ProfesionalException("Tu sesión terminó. Vuelve a entrar.")
            val rutas = fotos.take(FotosSolicitudes.MAXIMO).mapIndexed { i, bytes ->
                "$yo/$solicitudId/${i + 1}.jpg".also { ruta -> bucket.upload(ruta, bytes) { upsert = true; contentType = ContentType.Image.JPEG } }
            }
            supabase.postgrest.rpc(
                "adjuntar_fotos_solicitud",
                buildJsonObject {
                    put("p_tipo", tipo.name); put("p_id", solicitudId)
                    put("p_fotos", JsonArray(rutas.map { JsonPrimitive(it) }))
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProfesionalException) {
            throw e
        } catch (e: Exception) {
            throw ProfesionalException("La solicitud se envió, pero no pudimos subir las fotos.", e)
        }
    }

    override suspend fun descargar(ruta: String): ByteArray = bucket.downloadAuthenticated(ruta)
}
