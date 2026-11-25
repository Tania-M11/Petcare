package com.udenar.petcare.ui.componentes

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.udenar.petcare.ui.theme.Pc

/**
 * Borde inferior en "nube" (ondas redondas), como la portada de PetCare. [ondas] es cuántos
 * bultos tiene el borde; [alto] cuánto bajan.
 */
fun formaNube(ondas: Int = 5, alto: Float = 0.08f) = GenericShape { size, _ ->
    val h = size.height
    val profundidad = h * alto
    val base = h - profundidad
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, base)
    val ancho = size.width / ondas
    for (i in ondas - 1 downTo 0) {
        val x0 = ancho * i
        // Medio óvalo hacia abajo: cada onda es un bulto redondo
        arcTo(Rect(x0, base - profundidad, x0 + ancho, base + profundidad), 0f, 180f, false)
    }
    lineTo(0f, 0f)
    close()
}

/** Huellas que flotan despacio, para decorar portadas y cabeceras. */
@Composable
fun BoxScope.HuellasFlotantes(color: Color = Pc.Blanco.copy(alpha = 0.55f)) {
    val t = rememberInfiniteTransition(label = "huellas")
    val sube by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sube")
    listOf(
        Triple(Alignment.TopEnd, 18.dp to 48.dp, 34.dp),
        Triple(Alignment.TopEnd, 64.dp to 92.dp, 22.dp),
        Triple(Alignment.CenterStart, 18.dp to 0.dp, 26.dp),
        Triple(Alignment.TopStart, 120.dp to 70.dp, 18.dp),
    ).forEachIndexed { i, (alin, desp, tam) ->
        val dy = (if (i % 2 == 0) sube else 1 - sube) * 8f
        Icon(
            Icons.Rounded.Pets, contentDescription = null, tint = color,
            modifier = Modifier
                .align(alin)
                .padding(
                    start = if (alin == Alignment.TopEnd) 0.dp else desp.first,
                    end = if (alin == Alignment.TopEnd) desp.first else 0.dp,
                    top = desp.second,
                )
                .offset(y = dy.dp)
                .rotate(-20f + i * 14f)
                .size(tam),
        )
    }
}

/**
 * Botón de entrada oscuro en píldora: círculo de color con la huella, el texto y una flecha.
 * Contrasta con la portada clara (y se invierte en modo oscuro).
 */
@Composable
fun BotonEmpezar(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, cargando: Boolean = false, alto: Dp = 64.dp) {
    Row(
        modifier
            .fillMaxWidth()
            .height(alto)
            .clip(RoundedCornerShape(50))
            .background(Pc.Tinta)
            .clickable(enabled = !cargando, onClick = onClick)
            .padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(alto - 14.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Pc.NaranjaClaro, Pc.Naranja))),
            contentAlignment = Alignment.Center,
        ) {
            if (cargando) CircularProgressIndicator(color = Pc.SobreAcento, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
            else Icon(Icons.Rounded.Pets, contentDescription = null, tint = Pc.SobreAcento, modifier = Modifier.size(24.dp))
        }
        Text(texto, style = MaterialTheme.typography.titleMedium, color = Pc.Crema, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = Pc.Crema.copy(alpha = 0.7f), modifier = Modifier.padding(end = 14.dp))
    }
}
