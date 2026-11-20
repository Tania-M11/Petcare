package com.udenar.petcare.data.fotos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** Fotos en Supabase Storage (bucket privado "mascotas", carpeta del usuario). */
interface AlmacenFotos {
    /** Sube el archivo y devuelve su ruta en el bucket. */
    suspend fun subir(mascotaId: String, archivo: File): String
    suspend fun borrar(ruta: String)
    suspend fun descargar(ruta: String): ByteArray
}

class FotosSupabase @Inject constructor(private val supabase: SupabaseClient) : AlmacenFotos {
    private val bucket get() = supabase.storage.from(BUCKET)

    override suspend fun subir(mascotaId: String, archivo: File): String {
        val usuario = supabase.auth.currentUserOrNull()?.id ?: error("Sin sesión")
        // Ruta nueva en cada cambio: los otros teléfonos notan que la foto cambió
        val ruta = "$usuario/$mascotaId/${System.currentTimeMillis()}.jpg"
        bucket.upload(ruta, withContext(Dispatchers.IO) { archivo.readBytes() }) {
            upsert = true
            contentType = ContentType.Image.JPEG
        }
        return ruta
    }

    override suspend fun borrar(ruta: String) = bucket.delete(ruta)

    override suspend fun descargar(ruta: String): ByteArray = bucket.downloadAuthenticated(ruta)

    companion object {
        const val BUCKET = "mascotas"
    }
}

/** Archivos de fotos dentro del almacenamiento privado de la app. */
@Singleton
class ArchivosFotos @Inject constructor(@ApplicationContext private val contexto: Context) {
    private val carpeta: File get() = File(contexto.filesDir, "fotos").apply { mkdirs() }

    fun nuevo(mascotaId: String): File = File(carpeta, "${mascotaId}_${System.currentTimeMillis()}.jpg")

    fun borrarTodo() {
        carpeta.deleteRecursively()
    }

    /**
     * Reduce la foto elegida a 512 px y ≤ ~100 KB en JPEG (spec 11.1).
     * ImageDecoder respeta la orientación de la cámara (Android 9+).
     */
    suspend fun comprimir(uri: Uri, mascotaId: String): File = withContext(Dispatchers.IO) {
        val bitmap = decodificar(uri)
        val escala = LADO_MAXIMO.toFloat() / max(bitmap.width, bitmap.height)
        val reducida = if (escala < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * escala).toInt(), (bitmap.height * escala).toInt(), true)
        } else bitmap
        var calidad = 85
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().use { s ->
                reducida.compress(Bitmap.CompressFormat.JPEG, calidad, s)
                s.toByteArray()
            }
            calidad -= 10
        } while (bytes.size > BYTES_MAXIMOS && calidad >= 45)
        nuevo(mascotaId).apply { writeBytes(bytes) }
    }

    private fun decodificar(uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contexto.contentResolver, uri)) { decoder, info, _ ->
                val lado = max(info.size.width, info.size.height)
                if (lado > LADO_MAXIMO * 2) {
                    val f = (LADO_MAXIMO * 2f) / lado
                    decoder.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contexto.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opciones) }
            var muestra = 1
            while (max(opciones.outWidth, opciones.outHeight) / (muestra * 2) >= LADO_MAXIMO) muestra *= 2
            contexto.contentResolver.openInputStream(uri).use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestra })
            } ?: error("No se pudo leer la imagen")
        }

    companion object {
        const val LADO_MAXIMO = 512
        const val BYTES_MAXIMOS = 100 * 1024
    }
}
