package com.udenar.petcare.ui.componentes

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Mosaico de ruido (se genera una vez) que da el acabado "granulado" de la referencia visual. */
private val ruido: ImageBitmap by lazy {
    val lado = 128
    val azar = Random(7)
    val pixeles = IntArray(lado * lado) {
        val claro = azar.nextBoolean()
        val alfa = azar.nextInt(0, 46)
        val c = if (claro) 255 else 40
        (alfa shl 24) or (c shl 16) or (c shl 8) or c
    }
    Bitmap.createBitmap(pixeles, lado, lado, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Pincel con el mosaico de ruido, para pintar grano dentro de cualquier forma. */
fun brushGrano(): ShaderBrush = ShaderBrush(ImageShader(ruido, TileMode.Repeated, TileMode.Repeated))

/** Superpone el grano sobre el contenido (degradados, tarjetas ya recortadas). */
fun Modifier.grano(intensidad: Float = 0.55f): Modifier = drawWithContent {
    drawContent()
    drawRect(brush = brushGrano(), alpha = intensidad)
}

/**
 * Forma orgánica ("blob") inscrita en un rectángulo: un círculo deformado con
 * curvas suaves. [semilla] cambia la silueta; misma semilla, misma forma.
 */
fun pathBlob(ancho: Float, alto: Float, semilla: Int, puntos: Int = 7): Path {
    val azar = Random(semilla)
    val cx = ancho / 2f
    val cy = alto / 2f
    val radios = List(puntos) { 0.80f + azar.nextFloat() * 0.20f }
    val vertices = List(puntos) { i ->
        val ang = 2 * PI * i / puntos
        Pair(cx + cos(ang).toFloat() * cx * radios[i], cy + sin(ang).toFloat() * cy * radios[i])
    }
    fun medio(a: Pair<Float, Float>, b: Pair<Float, Float>) = Pair((a.first + b.first) / 2, (a.second + b.second) / 2)
    // Curva cerrada que pasa por los puntos medios entre vértices (Bézier cuadráticas)
    val path = Path()
    val inicio = medio(vertices.last(), vertices.first())
    path.moveTo(inicio.first, inicio.second)
    for (i in vertices.indices) {
        val v = vertices[i]
        val m = medio(v, vertices[(i + 1) % puntos])
        path.quadraticTo(v.first, v.second, m.first, m.second)
    }
    path.close()
    return path
}

/** Color con transparencia, para brillos sobre degradados. */
fun Color.suave(alfa: Float) = copy(alpha = alfa)
