package com.udenar.petcare.data.fotos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Foto guiada (Fase 6.3): recorta a la mascota del fondo con ML Kit (en el teléfono, sin costo)
 * y la convierte en "pegatina" con borde blanco sobre un fondo pastel, como los íconos de la app.
 * Se guarda en JPEG porque es el formato del bucket de fotos.
 */
@Singleton
class Pegatinas @Inject constructor(@ApplicationContext private val contexto: Context) {

    /** Archivo temporal para que la cámara guarde la foto (FileProvider "camara"). */
    fun nuevoArchivoCamara(): Pair<File, Uri> {
        val carpeta = File(contexto.cacheDir, "camara").apply { mkdirs() }
        val archivo = File(carpeta, "foto_${System.currentTimeMillis()}.jpg")
        return archivo to FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", archivo)
    }

    /**
     * Crea la pegatina a partir de [uri]. [colores] = degradado del fondo (ARGB).
     * Devuelve null si no se encontró a la mascota en la foto (entonces se usa la foto completa).
     */
    suspend fun crear(uri: Uri, mascotaId: String, colores: IntArray): File? = withContext(Dispatchers.Default) {
        val original = decodificar(uri) ?: return@withContext null
        val segmentador = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
        val recorte = try {
            segmentador.process(InputImage.fromBitmap(original, 0)).await().foregroundBitmap
        } catch (_: Exception) {
            null
        } finally {
            segmentador.close()
        } ?: return@withContext null
        val sujeto = recortarTransparente(recorte) ?: return@withContext null
        val lienzo = componer(sujeto, colores)
        withContext(Dispatchers.IO) {
            val carpeta = File(contexto.filesDir, "fotos").apply { mkdirs() }
            File(carpeta, "${mascotaId}_pegatina_${System.currentTimeMillis()}.jpg").apply {
                outputStream().use { lienzo.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            }
        }
    }

    private fun decodificar(uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contexto.contentResolver, uri)) { d, info, _ ->
                val lado = max(info.size.width, info.size.height)
                if (lado > LADO_TRABAJO) d.setTargetSampleSize(lado / LADO_TRABAJO)
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                d.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(contexto.contentResolver, uri)
        }
    }.getOrNull()?.let { b ->
        val escala = LADO_TRABAJO.toFloat() / max(b.width, b.height)
        if (escala < 1f) Bitmap.createScaledBitmap(b, (b.width * escala).toInt(), (b.height * escala).toInt(), true) else b
    }

    /** Recorta el área transparente alrededor de la mascota. */
    private fun recortarTransparente(b: Bitmap): Bitmap? {
        var izq = b.width; var arr = b.height; var der = -1; var aba = -1
        val pixeles = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        for (y in 0 until b.height) for (x in 0 until b.width) {
            if ((pixeles[y * b.width + x] ushr 24) > 40) {
                izq = min(izq, x); der = max(der, x); arr = min(arr, y); aba = max(aba, y)
            }
        }
        if (der < 0 || (der - izq) < 40 || (aba - arr) < 40) return null
        return Bitmap.createBitmap(b, izq, arr, der - izq + 1, aba - arr + 1)
    }

    /** Fondo pastel, borde blanco (la silueta dibujada alrededor) y la mascota encima. */
    private fun componer(sujeto: Bitmap, colores: IntArray): Bitmap {
        val lado = LADO_FINAL
        val salida = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
        val c = Canvas(salida)
        c.drawRect(0f, 0f, lado.toFloat(), lado.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, lado.toFloat(), lado.toFloat(), colores, null, Shader.TileMode.CLAMP)
        })
        val margen = lado * 0.12f
        val escala = min((lado - 2 * margen) / sujeto.width, (lado - 2 * margen) / sujeto.height)
        val ancho = sujeto.width * escala
        val alto = sujeto.height * escala
        val x = (lado - ancho) / 2
        val y = (lado - alto) / 2 + lado * 0.02f
        val escalado = Bitmap.createScaledBitmap(sujeto, ancho.toInt().coerceAtLeast(1), alto.toInt().coerceAtLeast(1), true)
        val blanco = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = PorterDuffColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN) }
        val sombra = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = PorterDuffColorFilter(0x22000000, PorterDuff.Mode.SRC_IN) }
        val borde = lado * 0.022f
        c.drawBitmap(escalado, x + borde * 0.6f, y + borde * 1.4f, sombra)
        for (i in 0 until 24) {
            val a = Math.PI * 2 * i / 24
            c.drawBitmap(escalado, x + (cos(a) * borde).toFloat(), y + (sin(a) * borde).toFloat(), blanco)
        }
        c.drawBitmap(escalado, x, y, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return salida
    }

    companion object {
        private const val LADO_TRABAJO = 1024
        private const val LADO_FINAL = 512
    }
}

/** Lee el texto de una foto con ML Kit (en el teléfono, sin internet) — Fase 6.3. */
@Singleton
class LectorEtiquetas @Inject constructor(@ApplicationContext private val contexto: Context) {
    suspend fun leer(uri: Uri): String = withContext(Dispatchers.Default) {
        val lector = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            lector.process(InputImage.fromFilePath(contexto, uri)).await().text
        } finally {
            lector.close()
        }
    }
}
