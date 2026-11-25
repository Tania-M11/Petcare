package com.udenar.petcare.ui.hoy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.udenar.petcare.domain.recomendaciones.CabeceraHoy
import com.udenar.petcare.domain.recomendaciones.Consejo
import com.udenar.petcare.ui.componentes.BaldosaIcono
import com.udenar.petcare.ui.componentes.Simbolo
import com.udenar.petcare.ui.componentes.dibujo
import com.udenar.petcare.ui.componentes.grano
import com.udenar.petcare.ui.theme.Kalnia
import com.udenar.petcare.ui.theme.Pc
import kotlinx.coroutines.delay

/**
 * Portada del carrusel de Hoy (toda la casa): la fecha, un título grande, el estado del día en una línea con su
 * barra de progreso, Michi que respira y trucos que rotan solos (uno por línea).
 */
@Composable
fun TarjetaSaludo(
    etiqueta: String,
    titulo: String,
    cabecera: CabeceraHoy,
    hechos: Int,
    total: Int,
    consejos: List<Consejo>,
    modifier: Modifier = Modifier,
    proximos: List<ProximoHoy> = emptyList(),
) {
    val pulso = rememberInfiniteTransition(label = "michi")
    val escala by pulso.animateFloat(1f, 1.05f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "respira")
    val fraccion by animateFloatAsState(if (total == 0) 0f else hechos.toFloat() / total, spring(dampingRatio = 0.7f, stiffness = 120f), label = "progreso")
    val forma = RoundedCornerShape(30.dp)
    // Alto fijo (lo da quien la usa): el título arriba, el estado abajo y Michi grande en la esquina
    Column(
        modifier
            .fillMaxWidth()
            .clip(forma)
            .background(Brush.linearGradient(listOf(Pc.LavandaClara, Pc.Durazno, Pc.SolClaro)))
            .grano(0.35f)
            .testTag("mensaje_michi"),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AnimatedContent(
                cabecera.pose,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "pose",
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp),
            ) { p ->
                Image(painterResource(p.dibujo()), contentDescription = "Michi", modifier = Modifier.size(150.dp).offset(y = 8.dp).scale(escala))
            }
            Column(Modifier.fillMaxSize().padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 16.dp)) {
                Text(etiqueta, style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
                Text(titulo, fontFamily = Kalnia, fontSize = 30.sp, lineHeight = 34.sp, color = Pc.Tinta, maxLines = 2, overflow = TextOverflow.Ellipsis)
                // Lo que viene: los próximos cuidados del día en píldoras
                Column(Modifier.padding(top = 10.dp).fillMaxWidth(0.72f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    proximos.take(2).forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Pc.Blanco.copy(alpha = 0.75f)).padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Simbolo(p.icono, tam = 16.dp)
                            Text(p.texto, style = MaterialTheme.typography.labelLarge, color = Pc.Tinta, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            Text(p.hora, style = MaterialTheme.typography.labelMedium, color = Pc.TintaSuave, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.padding(end = 140.dp), verticalAlignment = Alignment.CenterVertically) {
                    AnimatedContent(cabecera.icono, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "icono") { i ->
                        BaldosaIcono(i, fondo = Pc.Blanco.copy(alpha = 0.75f), tam = 34.dp)
                    }
                    AnimatedContent(cabecera.estado, transitionSpec = { (fadeIn() + slideInVertically { it / 2 }) togetherWith fadeOut() }, label = "estado") { t ->
                        Text(t, style = MaterialTheme.typography.titleSmall, color = Pc.Tinta, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                if (total > 0) {
                    Row(Modifier.padding(top = 10.dp, end = 140.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(50)).background(Pc.Blanco.copy(alpha = 0.7f))) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraccion.coerceIn(0f, 1f))
                                    .clip(RoundedCornerShape(50))
                                    .background(Brush.horizontalGradient(listOf(Pc.NaranjaClaro, if (hechos == total) Pc.Menta else Pc.Naranja))),
                            )
                        }
                        Text("$hechos/$total", style = MaterialTheme.typography.labelLarge, color = Pc.Tinta, modifier = Modifier.padding(start = 10.dp).testTag("progreso_hoy"))
                    }
                }
            }
        }
        if (consejos.isNotEmpty()) FranjaConsejos(consejos)
    }
}

/** Próximo cuidado del día para la portada: "Desayuno · Venus" a las "7:00 a. m.". */
data class ProximoHoy(val icono: com.udenar.petcare.domain.modelo.Icono, val texto: String, val hora: String)

/** Una línea con el truco del momento; cambia sola cada pocos segundos o al tocarla. */
@Composable
private fun FranjaConsejos(consejos: List<Consejo>) {
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(consejos) {
        while (true) {
            delay(6_000)
            i = (i + 1) % consejos.size
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Pc.Blanco.copy(alpha = 0.6f))
            .clickable { i = (i + 1) % consejos.size }
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag("consejo"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            consejos[i % consejos.size],
            transitionSpec = { (fadeIn(tween(350)) + slideInVertically { it }) togetherWith (fadeOut(tween(200)) + slideOutVertically { -it }) },
            label = "consejo",
        ) { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Simbolo(c.icono, tam = 22.dp)
                Text(c.texto, style = MaterialTheme.typography.bodySmall, color = Pc.Tinta, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}
