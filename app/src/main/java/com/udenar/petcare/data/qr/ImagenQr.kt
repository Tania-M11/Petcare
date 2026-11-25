package com.udenar.petcare.data.qr

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File

/** QR como imagen (ZXing core): funciona sin internet y lo lee cualquier cámara (RF-18). */
object ImagenQr {

    fun generar(texto: String, lado: Int = 720): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2,
        )
        val matriz = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, lado, lado, hints)
        val pixeles = IntArray(lado * lado) { i -> if (matriz[i % lado, i / lado]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixeles, lado, lado, Bitmap.Config.ARGB_8888)
    }

    /**
     * Placa para compartir o imprimir: la foto de la mascota en círculo, su nombre, el contacto
     * (si se eligió mostrarlo) y el QR debajo. El QR en sí solo lleva texto; la foto va en la placa.
     */
    fun placa(qr: Bitmap, nombre: String, fotoLocal: String?, contacto: String?): Bitmap {
        val ancho = 900
        val alto = 1260
        val b = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        c.drawColor(0xFFFBF6F1.toInt())
        val franja = android.graphics.Paint().apply { shader = android.graphics.LinearGradient(0f, 0f, ancho.toFloat(), 360f, 0xFFECE6FB.toInt(), 0xFFFCDCC6.toInt(), android.graphics.Shader.TileMode.CLAMP) }
        c.drawRoundRect(android.graphics.RectF(30f, 30f, ancho - 30f, 400f), 50f, 50f, franja)
        // Foto en círculo
        val foto = fotoLocal?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it) }.getOrNull() }
        val cx = ancho / 2f
        val radio = 120f
        val blanco = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawCircle(cx, 190f, radio + 8f, blanco)
        if (foto != null) {
            c.save()
            val ruta = android.graphics.Path().apply { addCircle(cx, 190f, radio, android.graphics.Path.Direction.CW) }
            c.clipPath(ruta)
            val esc = maxOf(radio * 2 / foto.width, radio * 2 / foto.height)
            val w = foto.width * esc
            val h = foto.height * esc
            c.drawBitmap(foto, null, android.graphics.RectF(cx - w / 2, 190f - h / 2, cx + w / 2, 190f + h / 2), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            c.restore()
        }
        val titulo = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2D2433.toInt(); textSize = 64f; typeface = android.graphics.Typeface.DEFAULT_BOLD; textAlign = android.graphics.Paint.Align.CENTER
        }
        val texto = android.graphics.Paint(titulo).apply { textSize = 34f; typeface = android.graphics.Typeface.DEFAULT; color = 0xFF8C8394.toInt() }
        c.drawText(nombre, cx, 480f, titulo)
        contacto?.takeIf { it.isNotBlank() }?.let { c.drawText(it, cx, 530f, texto) }
        c.drawBitmap(qr, null, android.graphics.RectF(cx - 300f, 570f, cx + 300f, 1170f), null)
        c.drawText("Escanéame con la cámara · PetCare", cx, 1220f, texto)
        return b
    }

    /** Archivo temporal para compartir con otras apps (sin exponer rutas internas). */
    fun paraCompartir(contexto: Context, bitmap: Bitmap, nombre: String): Uri {
        val carpeta = File(contexto.cacheDir, "compartidos").apply { mkdirs() }
        val archivo = File(carpeta, "qr_${nombre.filter { it.isLetterOrDigit() }.ifBlank { "mascota" }}.png")
        archivo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", archivo)
    }

    /** Guarda en Imágenes/PetCare (Android 10+ no necesita permisos). */
    fun guardarEnGaleria(contexto: Context, bitmap: Bitmap, nombre: String): Boolean {
        val valores = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "PetCare_QR_${nombre.filter { it.isLetterOrDigit() }}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PetCare")
        }
        val uri = contexto.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, valores) ?: return false
        return contexto.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
    }
}
