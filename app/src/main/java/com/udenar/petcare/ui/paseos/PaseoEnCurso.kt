package com.udenar.petcare.ui.paseos

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.udenar.petcare.data.paseos.EstadoSesion
import com.udenar.petcare.data.paseos.FaseSesion
import com.udenar.petcare.data.paseos.SesionPaseo
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.domain.paseos.textoDistancia
import com.udenar.petcare.ui.componentes.AvatarMascota
import com.udenar.petcare.ui.mapas.ATRIBUCION_MAPA
import com.udenar.petcare.ui.theme.Kalnia
import com.udenar.petcare.ui.theme.Pc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

/** El paseo en curso visto desde fuera de su pantalla: Hoy y la barra sobre las pestañas. */
@HiltViewModel
class PaseoEnCursoViewModel @Inject constructor(
    private val sesion: SesionPaseo,
    mascotasRepo: RepositorioMascotas,
) : ViewModel() {
    val estado: StateFlow<EstadoSesion> = sesion.estado
    val mascotas: StateFlow<List<Mascota>> = mascotasRepo.observar().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun pausar() = sesion.pausar()
    fun reanudar() = sesion.reanudar()
}

/** Segundos del paseo, que avanzan cada segundo en pantalla mientras está activo. */
@Composable
fun segundosPaseo(s: EstadoSesion): Long {
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.fase) {
        while (s.fase == FaseSesion.ACTIVO) {
            ahora = System.currentTimeMillis()
            delay(1_000)
        }
    }
    return s.duracionS(Instant.ofEpochMilli(ahora))
}

/** Cronómetro: "07:42" o "1:05:09". */
fun cronometro(segundos: Long): String {
    val h = segundos / 3600
    val m = (segundos % 3600) / 60
    val s = segundos % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Ritmo en min/km ("12:30 /km"), o null si aún no hay distancia suficiente. */
fun ritmo(segundos: Long, metros: Double): String? {
    if (metros < 100) return null
    val sPorKm = (segundos / (metros / 1000.0)).toLong()
    return "%d:%02d /km".format(sPorKm / 60, sPorKm % 60)
}

/** Punto que late: indica que algo está pasando en vivo. */
@Composable
fun PuntoEnVivo(color: Color = Pc.Menta, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "vivo")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alfa")
    val e by t.animateFloat(1f, 1.6f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "escala")
    Box(modifier.size(14.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(14.dp).scale(e).alpha(1.2f - a).clip(CircleShape).background(color.copy(alpha = 0.5f)))
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
    }
}

/** Avatares de las mascotas del paseo, encimados. */
@Composable
private fun AvataresPaseo(mascotas: List<Mascota>, tam: androidx.compose.ui.unit.Dp, borde: Color) {
    Box {
        mascotas.take(3).forEachIndexed { i, m ->
            AvatarMascota(
                m.especie, m.fotoLocal, tam = tam,
                modifier = Modifier.offset(x = tam * 0.6f * i).border(2.dp, borde, CircleShape),
            )
        }
        if (mascotas.size > 1) Spacer(Modifier.width(tam * 0.6f * (mascotas.take(3).size - 1) + tam))
    }
}

private fun nombres(mascotas: List<Mascota>) = when (mascotas.size) {
    0 -> "Paseo"
    1 -> mascotas[0].nombre
    2 -> "${mascotas[0].nombre} y ${mascotas[1].nombre}"
    else -> "${mascotas[0].nombre} y ${mascotas.size - 1} más"
}

/**
 * Barra compacta sobre las pestañas mientras hay un paseo: se ve desde cualquier parte de la
 * app y al tocarla vuelve al mapa del paseo.
 */
@Composable
fun BarraPaseoEnCurso(onAbrir: () -> Unit, modifier: Modifier = Modifier, viewModel: PaseoEnCursoViewModel = hiltViewModel()) {
    val s by viewModel.estado.collectAsStateWithLifecycle()
    if (s.fase == FaseSesion.INACTIVO) return
    val todas by viewModel.mascotas.collectAsStateWithLifecycle()
    val delPaseo = todas.filter { it.id in s.mascotaIds }
    val seg = segundosPaseo(s)
    Row(
        modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(Pc.Tinta)
            .clickable(onClick = onAbrir)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .testTag("barra_paseo"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvataresPaseo(delPaseo, 36.dp, Pc.Tinta)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PuntoEnVivo(if (s.fase == FaseSesion.ACTIVO) Pc.Menta else Pc.Sol)
                Text(
                    if (s.fase == FaseSesion.ACTIVO) "Paseando con ${nombres(delPaseo)}" else "Paseo en pausa",
                    style = MaterialTheme.typography.labelLarge, color = Pc.Crema, maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text("${cronometro(seg)} · ${textoDistancia(s.distanciaM.toInt())}", style = MaterialTheme.typography.bodySmall, color = Pc.Crema.copy(alpha = 0.7f))
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Pc.Naranja)
                .clickable { if (s.fase == FaseSesion.ACTIVO) viewModel.pausar() else viewModel.reanudar() }
                .testTag("barra_paseo_pausa"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (s.fase == FaseSesion.ACTIVO) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (s.fase == FaseSesion.ACTIVO) "Pausar" else "Reanudar",
                tint = Pc.SobreAcento,
            )
        }
    }
}

/**
 * Tarjeta grande del paseo en curso para Hoy: mapa en vivo con la ruta, el cronómetro y la
 * distancia en grande, y un botón para volver al paseo.
 */
@Composable
fun TarjetaPaseoEnVivo(onAbrir: () -> Unit, modifier: Modifier = Modifier, viewModel: PaseoEnCursoViewModel = hiltViewModel()) {
    val s by viewModel.estado.collectAsStateWithLifecycle()
    if (s.fase == FaseSesion.INACTIVO) return
    val todas by viewModel.mascotas.collectAsStateWithLifecycle()
    val delPaseo = todas.filter { it.id in s.mascotaIds }
    val seg = segundosPaseo(s)
    val forma = RoundedCornerShape(30.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(12.dp, forma, ambientColor = Pc.Naranja.copy(alpha = 0.3f), spotColor = Pc.Naranja.copy(alpha = 0.3f))
            .clip(forma)
            .background(Pc.Blanco)
            .clickable(onClick = onAbrir)
            .testTag("tarjeta_paseo_vivo"),
    ) {
        Box(Modifier.fillMaxWidth().height(170.dp)) {
            MapaPaseo(ruta = s.ruta, posicion = s.posicion, seguir = true, interactivo = false, modifier = Modifier.fillMaxSize())
            // Degradado para que la etiqueta se lea sobre el mapa
            Box(Modifier.fillMaxWidth().height(60.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent))))
            Row(
                Modifier.padding(12.dp).clip(RoundedCornerShape(50)).background(Pc.Tinta).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PuntoEnVivo(if (s.fase == FaseSesion.ACTIVO) Pc.Menta else Pc.Sol)
                Text(if (s.fase == FaseSesion.ACTIVO) "En vivo" else "En pausa", style = MaterialTheme.typography.labelMedium, color = Pc.Crema, modifier = Modifier.padding(start = 6.dp))
            }
            if (s.ruta.isEmpty()) {
                Text(
                    "Buscando GPS…", style = MaterialTheme.typography.labelMedium, color = Pc.Tinta,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(50)).background(Pc.Blanco).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AvataresPaseo(delPaseo, 44.dp, Pc.Blanco)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(nombres(delPaseo), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(cronometro(seg), style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
            }
            Text(textoDistancia(s.distanciaM.toInt()), fontFamily = Kalnia, fontSize = 28.sp, color = Pc.Naranja)
        }
    }
}

/**
 * Vista del paseo activo a pantalla completa (como una app de seguimiento): el mapa de fondo y
 * una tarjeta abajo con la distancia grande, el cronómetro, el ritmo y los controles.
 */
@Composable
fun PaseoActivo(
    s: EstadoSesion,
    mascotas: List<Mascota>,
    onVolver: () -> Unit,
    onPausar: () -> Unit,
    onReanudar: () -> Unit,
    onFinalizar: () -> Unit,
) {
    val seg = segundosPaseo(s)
    val delPaseo = mascotas.filter { it.id in s.mascotaIds }
    Box(Modifier.fillMaxSize().background(Pc.Crema)) {
        MapaPaseo(ruta = s.ruta, posicion = s.posicion, seguir = true, modifier = Modifier.fillMaxSize())
        Row(Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Pc.Blanco).clickable(onClick = onVolver),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = Pc.Tinta) }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.shadow(6.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(Pc.Tinta).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PuntoEnVivo(if (s.fase == FaseSesion.ACTIVO) Pc.Menta else Pc.Sol)
                Text(if (s.fase == FaseSesion.ACTIVO) "Paseo en curso" else "En pausa", style = MaterialTheme.typography.labelLarge, color = Pc.Crema, modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (s.ruta.isEmpty()) {
            Text(
                "Buscando señal de GPS…", style = MaterialTheme.typography.labelLarge, color = Pc.Tinta,
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(50)).background(Pc.Blanco).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Pc.Blanco)
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvataresPaseo(delPaseo, 44.dp, Pc.Blanco)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(nombres(delPaseo), style = MaterialTheme.typography.titleMedium)
                    Text(ritmo(seg, s.distanciaM) ?: "Calculando ritmo…", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(textoDistancia(s.distanciaM.toInt()), fontFamily = Kalnia, fontSize = 34.sp, color = Pc.Naranja, modifier = Modifier.testTag("paseo_distancia"))
                }
            }
            // Cronómetro grande en una franja de color
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.horizontalGradient(listOf(Pc.Durazno, Pc.LavandaClara))).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Tiempo", style = MaterialTheme.typography.labelMedium, color = Pc.TintaSuave)
                    Text(cronometro(seg), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Pc.Tinta, modifier = Modifier.testTag("paseo_tiempo"))
                }
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Pc.Blanco)
                        .clickable { if (s.fase == FaseSesion.ACTIVO) onPausar() else onReanudar() }
                        .testTag("paseo_pausa"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (s.fase == FaseSesion.ACTIVO) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (s.fase == FaseSesion.ACTIVO) "Pausar" else "Reanudar",
                        tint = Pc.Tinta, modifier = Modifier.size(28.dp),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(50)).background(Pc.Naranja).clickable(onClick = onFinalizar).padding(horizontal = 20.dp).testTag("paseo_finalizar"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.Flag, contentDescription = null, tint = Pc.SobreAcento)
                Text("Terminar paseo", style = MaterialTheme.typography.titleMedium, color = Pc.SobreAcento, modifier = Modifier.padding(start = 8.dp))
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = Pc.SobreAcento)
            }
            Text(ATRIBUCION_MAPA, style = MaterialTheme.typography.labelSmall, color = Pc.TintaSuave)
        }
    }
}
