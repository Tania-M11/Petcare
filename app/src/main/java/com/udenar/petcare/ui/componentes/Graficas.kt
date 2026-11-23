package com.udenar.petcare.ui.componentes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.udenar.petcare.domain.recomendaciones.ProgresoMascota
import com.udenar.petcare.ui.theme.Kalnia
import com.udenar.petcare.ui.theme.Pc
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Barras que crecen al aparecer; la última (la semana actual) va resaltada. */
@Composable
fun GraficaBarras(
    valores: List<Float>,
    etiquetas: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    maximo: Float = valores.maxOrNull()?.takeIf { it > 0 } ?: 1f,
    textoValor: (Float) -> String = { "" },
    alto: Dp = 120.dp,
) {
    val crecer = remember(valores) { Animatable(0f) }
    LaunchedEffect(valores) { crecer.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(alto), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            valores.forEachIndexed { i, v ->
                val ultima = i == valores.lastIndex
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (v > 0) Text(textoValor(v), style = MaterialTheme.typography.labelSmall, color = if (ultima) Pc.Tinta else Pc.TintaSuave, maxLines = 1)
                    Canvas(Modifier.fillMaxWidth().height(alto - 18.dp)) {
                        val h = (v / maximo).coerceIn(0f, 1f) * size.height * crecer.value
                        drawRoundRect(Pc.Linea, Offset.Zero, size, CornerRadius(10f, 10f))
                        if (h > 0f) {
                            drawRoundRect(
                                Brush.verticalGradient(listOf(color, color.copy(alpha = if (ultima) 1f else 0.55f))),
                                Offset(0f, size.height - h), Size(size.width, h), CornerRadius(10f, 10f),
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            etiquetas.forEachIndexed { i, e ->
                Text(e, style = MaterialTheme.typography.labelSmall, color = if (i == etiquetas.lastIndex) Pc.Tinta else Pc.TintaSuave, textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1)
            }
        }
    }
}

private val SEMANA = DateTimeFormatter.ofPattern("d/M", Locale.forLanguageTag("es-CO"))

/** Progreso de la mascota (Fase 6.4): racha, cumplimiento semanal, paseos, equipo y logros. */
@Composable
fun TarjetaProgreso(nombre: String, p: ProgresoMascota, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Pc.Blanco).padding(16.dp).testTag("tarjeta_progreso"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Progreso de $nombre", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        p.racha >= 7 -> "¡Una racha increíble! Sigue así."
                        p.racha > 0 -> "Cada día completo suma a la racha."
                        else -> "Completa todo lo de un día para empezar una racha."
                    },
                    style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Pc.Durazno).padding(horizontal = 14.dp, vertical = 6.dp)) {
                Text("🔥 ${p.racha}", fontFamily = Kalnia, fontSize = 24.sp, color = Pc.Tinta, modifier = Modifier.testTag("racha"))
                Text(if (p.racha == 1) "día" else "días", style = MaterialTheme.typography.labelSmall, color = Pc.TintaSuave)
            }
        }
        if (p.semanas.any { it.total > 0 }) {
            Text("Cuidados hechos por semana", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
            GraficaBarras(
                p.semanas.map { it.fraccion * 100f }, p.semanas.map { it.lunes.format(SEMANA) }, Pc.Naranja,
                maximo = 100f, textoValor = { "${it.toInt()}%" }, modifier = Modifier.testTag("grafica_cumplimiento"),
            )
        }
        if (p.paseos.any { it.cantidad > 0 }) {
            Text("Paseos por semana (km)", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
            GraficaBarras(
                p.paseos.map { it.km.toFloat() }, p.paseos.map { it.lunes.format(SEMANA) }, Pc.Sol,
                textoValor = { "%.1f".format(Locale.forLanguageTag("es-CO"), it) }, alto = 100.dp, modifier = Modifier.testTag("grafica_paseos"),
            )
        }
        if (p.porPersona.isNotEmpty()) {
            Text("Quién ayudó este mes", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
            val total = p.porPersona.values.sum().coerceAtLeast(1)
            p.porPersona.entries.sortedByDescending { it.value }.take(4).forEachIndexed { i, (persona, n) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(persona, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(90.dp), maxLines = 1)
                    Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(50)).background(Pc.Crema)) {
                        Box(Modifier.fillMaxWidth(n.toFloat() / total).height(12.dp).clip(RoundedCornerShape(50)).background(listOf(Pc.Lavanda, Pc.Menta, Pc.Cielo, Pc.Sol)[i % 4]))
                    }
                    Text(" $n", style = MaterialTheme.typography.labelMedium, color = Pc.TintaSuave)
                }
            }
        }
        Text("Logros", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            p.logros.forEach { l ->
                Column(
                    Modifier.width(96.dp).clip(RoundedCornerShape(18.dp)).background(if (l.conseguido) Pc.SolClaro else Pc.Crema).padding(10.dp).alpha(if (l.conseguido) 1f else 0.45f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(l.emoji, fontSize = 26.sp)
                    Text(l.titulo, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, color = Pc.Tinta)
                    Text(l.descripcion, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = Pc.TintaSuave, maxLines = 3)
                }
            }
        }
    }
}

@Composable
fun PuntoColor(color: Color, tam: Dp = 10.dp) = Box(Modifier.size(tam).clip(RoundedCornerShape(50)).background(color))
