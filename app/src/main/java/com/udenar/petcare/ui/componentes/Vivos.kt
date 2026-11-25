package com.udenar.petcare.ui.componentes

import com.udenar.petcare.domain.modelo.Icono
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.udenar.petcare.R
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.recomendaciones.PoseChopper
import com.udenar.petcare.domain.recomendaciones.Recomendacion
import com.udenar.petcare.domain.recomendaciones.Tono
import com.udenar.petcare.ui.theme.Kalnia
import com.udenar.petcare.ui.theme.Pc
import kotlin.random.Random

@DrawableRes
fun PoseChopper.dibujo(): Int = when (this) {
    PoseChopper.SALUDO -> R.drawable.chopper_saludo
    PoseChopper.COMIDA -> R.drawable.chopper_comida
    PoseChopper.AMIGOS -> R.drawable.chopper_amigos
    PoseChopper.ORGANIZADOR -> R.drawable.chopper_organizador
    PoseChopper.DOCTOR -> R.drawable.chopper_veterinario
}

/** Color propio de cada mascota (borde, franja de sus tarjetas, anillo del filtro). */
fun Especie.acento(): Color = colores().last()

/** Chopper habla: ilustración que respira y globo de diálogo que cambia con animación. */
@Composable
fun BurbujaChopper(texto: String, pose: PoseChopper, modifier: Modifier = Modifier, tamChopper: Dp = 84.dp) {
    // Respiración suave para que se sienta vivo
    val pulso = rememberInfiniteTransition(label = "chopper")
    val escala by pulso.animateFloat(1f, 1.04f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "respira")
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        AnimatedContent(pose, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "pose") { p ->
            Image(painterResource(p.dibujo()), contentDescription = "Chopper", modifier = Modifier.size(tamChopper).scale(escala))
        }
        AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { it / 2 }) {
            Box(
                Modifier
                    .padding(start = 6.dp, bottom = 18.dp)
                    .shadow(6.dp, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 4.dp), ambientColor = Pc.Naranja.suave(0.2f), spotColor = Pc.Naranja.suave(0.2f))
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 4.dp))
                    .background(Pc.Blanco)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("mensaje_chopper"),
            ) {
                AnimatedContent(texto, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "texto") { t ->
                    Text(t, style = MaterialTheme.typography.bodyMedium, color = Pc.Tinta)
                }
            }
        }
    }
}

/** Anillo del día: se llena con animación a medida que se marcan los cuidados. */
@Composable
fun AnilloProgreso(hechos: Int, total: Int, modifier: Modifier = Modifier, tam: Dp = 96.dp) {
    val fraccion = if (total == 0) 0f else hechos.toFloat() / total
    val animada by animateFloatAsState(fraccion, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "anillo")
    val completo = total > 0 && hechos == total
    val rebote by animateFloatAsState(if (completo) 1.08f else 1f, spring(dampingRatio = Spring.DampingRatioHighBouncy), label = "rebote")
    Box(modifier.size(tam).scale(rebote).testTag("anillo_dia"), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val grosor = size.minDimension * 0.12f
            val lado = size.minDimension - grosor
            val arriba = Offset(grosor / 2, grosor / 2)
            drawArc(Pc.Durazno, 0f, 360f, false, arriba, Size(lado, lado), style = Stroke(grosor, cap = StrokeCap.Round))
            drawArc(
                Brush.sweepGradient(listOf(Pc.NaranjaClaro, Pc.Naranja, if (completo) Pc.Menta else Pc.Naranja)),
                -90f, 360f * animada, false, arriba, Size(lado, lado), style = Stroke(grosor, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (total == 0) {
                Icono3D(Icono.HOJA, tam = tam * 0.34f)
            } else {
                Text("$hechos/$total", fontFamily = Kalnia, fontSize = (tam.value * 0.22f).sp, color = Pc.Tinta)
                Text(if (completo) "¡listo!" else "hoy", style = MaterialTheme.typography.labelSmall, color = Pc.TintaSuave)
            }
        }
    }
}

/** Confeti de colores de la app; se dispara cada vez que cambia [disparo]. */
@Composable
fun Confeti(disparo: Int, modifier: Modifier = Modifier) {
    if (disparo == 0) return
    val progreso = remember(disparo) { Animatable(0f) }
    val piezas = remember(disparo) {
        val colores = listOf(Pc.Naranja, Pc.Lavanda, Pc.Sol, Pc.Menta, Pc.Cielo, Pc.NaranjaClaro)
        List(70) {
            Pieza(Random.nextFloat(), -Random.nextFloat() * 0.3f, (Random.nextFloat() - 0.5f) * 0.6f, 0.7f + Random.nextFloat() * 0.8f, colores.random(), Random.nextFloat() * 360f)
        }
    }
    LaunchedEffect(disparo) { progreso.animateTo(1f, tween(2200, easing = LinearEasing)) }
    if (progreso.value >= 1f) return
    Canvas(modifier.fillMaxSize().graphicsLayer { alpha = 1f - progreso.value * progreso.value }) {
        val t = progreso.value
        piezas.forEach { p ->
            val x = (p.x + p.deriva * t) * size.width
            val y = (p.y + p.caida * t * 1.4f) * size.height
            rotate(p.giro + t * 720f, Offset(x, y)) {
                drawRect(p.color, Offset(x - 6f, y - 3f), Size(12f, 6f))
            }
        }
    }
}

private data class Pieza(val x: Float, val y: Float, val deriva: Float, val caida: Float, val color: Color, val giro: Float)

private fun Tono.colores(): Pair<Color, Color> = when (this) {
    Tono.URGENTE -> Pc.Durazno to Pc.Naranja
    Tono.ATENCION -> Pc.SolClaro to Pc.Sol
    Tono.SUGERENCIA -> Pc.LavandaClara to Pc.Lavanda
    Tono.CELEBRACION -> Pc.MentaClara to Pc.Menta
}

/** Tarjeta de recomendación: qué, por qué la ves (al tocar "¿Por qué?") y un botón para actuar. */
@Composable
fun TarjetaRecomendacion(r: Recomendacion, onAccion: () -> Unit, onDescartar: () -> Unit, modifier: Modifier = Modifier) {
    val (fondo, borde) = r.tono.colores()
    var porQue by remember { mutableStateOf(false) }
    Column(
        modifier
            .width(290.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(fondo, Pc.Blanco)))
            .padding(16.dp)
            .testTag("recomendacion_${r.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            BaldosaIcono(r.icono, fondo = Pc.Blanco, tam = 44.dp)
            Text(r.titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            Icon(
                Icons.Rounded.Close, contentDescription = "Ahora no", tint = Pc.TintaSuave,
                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(50)).clickable(onClick = onDescartar).testTag("descartar_${r.id}"),
            )
        }
        Text(r.mensaje, style = MaterialTheme.typography.bodySmall, color = Pc.Tinta)
        AnimatedVisibility(porQue, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(verticalAlignment = Alignment.Top) {
                Icono3D(Icono.IDEA, tam = 16.dp)
                Text(r.porque, style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave, modifier = Modifier.padding(start = 6.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                r.textoAccion,
                style = MaterialTheme.typography.labelLarge,
                color = Pc.SobreAcento,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(borde)
                    .clickable(onClick = onAccion)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("accion_${r.id}"),
            )
            Text(
                if (porQue) "Ocultar" else "¿Por qué?",
                style = MaterialTheme.typography.labelMedium,
                color = Pc.TintaSuave,
                modifier = Modifier.padding(start = 12.dp).clip(RoundedCornerShape(50)).clickable { porQue = !porQue }.padding(8.dp),
            )
        }
    }
}

/** Avisos de toda la app con Chopper (reemplaza el snackbar gris): éxito, error o información. */
@Composable
fun AvisoChopper(texto: String, modifier: Modifier = Modifier) {
    val error = listOf("No se pudo", "No pudimos", "Sin conexión", "Sin permiso", "No tienes", "no está", "Escribe", "Elige").any { texto.contains(it, ignoreCase = true) }
    val exito = !error && listOf("¡", "✓", "guard", "Listo", "enviad", "Gracias", "registrad", "actualizad").any { texto.contains(it, ignoreCase = true) }
    val pose = when {
        error -> PoseChopper.ORGANIZADOR
        exito -> PoseChopper.AMIGOS
        else -> PoseChopper.SALUDO
    }
    val entrada = remember { Animatable(0.6f) }
    LaunchedEffect(texto) { entrada.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    Row(
        modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .scale(entrada.value)
            .shadow(12.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(if (error) Pc.Durazno else Pc.Blanco)
            .padding(end = 18.dp)
            .testTag("aviso_chopper"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(pose.dibujo()), contentDescription = null, modifier = Modifier.size(56.dp).padding(6.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = Pc.Tinta, modifier = Modifier.padding(vertical = 14.dp))
    }
}
