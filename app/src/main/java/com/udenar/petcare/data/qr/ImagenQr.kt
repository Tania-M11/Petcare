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
