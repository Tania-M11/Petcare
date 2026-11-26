package com.udenar.petcare.ui.hoy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.udenar.petcare.domain.animo.Animo
import com.udenar.petcare.domain.modelo.Icono
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.domain.paseos.textoDistancia
import com.udenar.petcare.ui.componentes.AvatarMascota
import com.udenar.petcare.ui.componentes.BaldosaIcono
import com.udenar.petcare.ui.componentes.Simbolo
import com.udenar.petcare.ui.cuidados.icono
import com.udenar.petcare.ui.componentes.colores
import com.udenar.petcare.ui.componentes.grano
import com.udenar.petcare.ui.componentes.ilustracion
import com.udenar.petcare.ui.theme.Kalnia
import com.udenar.petcare.ui.theme.Pc
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Alto de cada página del carrusel: todas iguales para que no queden huecos al deslizar. */
val ALTO_CARRUSEL = 350.dp

private val HORA = DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("es-CO"))

/**
 * Píldoras de categoría con la foto de cada mascota (como un catálogo): "Todas" y cada una.
 * La elegida queda oscura; tocarla otra vez vuelve a "Todas".
 */
@Composable
fun ChipsMascotas(mascotas: List<Mascota>, filtro: String?, onElegir: (String?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PildoraMascota("Todas", filtro == null, onClick = { onElegir(null) }, modifier = Modifier.testTag("filtro_todas")) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Pc.Durazno), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Apps, contentDescription = null, tint = Pc.NaranjaOscuro, modifier = Modifier.size(18.dp))
            }
        }
        mascotas.forEach { m ->
            PildoraMascota(m.nombre, filtro == m.id, onClick = { onElegir(m.id) }, modifier = Modifier.testTag("filtro_${m.nombre}")) {
                AvatarMascota(m.especie, m.fotoLocal, tam = 34.dp)
            }
        }
    }
}

@Composable
private fun PildoraMascota(texto: String, elegida: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, imagen: @Composable () -> Unit) {
    val fondo by animateColorAsState(if (elegida) Pc.Tinta else Pc.Blanco, label = "fondo")
    val color by animateColorAsState(if (elegida) Pc.Crema else Pc.Tinta, label = "texto")
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(fondo)
            .border(1.dp, if (elegida) Color.Transparent else Pc.Linea, RoundedCornerShape(50))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(start = 5.dp, end = 16.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        imagen()
        Text(texto, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Puntos del carrusel: el actual se alarga. */
@Composable
fun IndicadorPaginas(estado: PagerState, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(estado.pageCount) { n ->
            val ancho by animateDpAsState(if (n == estado.currentPage) 20.dp else 7.dp, label = "ancho")
            val color by animateColorAsState(if (n == estado.currentPage) Pc.Naranja else Pc.Linea, label = "color")
            Box(Modifier.height(7.dp).width(ancho).clip(RoundedCornerShape(50)).background(color))
        }
    }
}

/**
 * Carrusel de Hoy: la primera página es el día de toda la casa ([portada]) y luego una tarjeta
 * grande por mascota. Deslizar cambia el filtro de la agenda de abajo.
 */
@Composable
fun CarruselHoy(estado: PagerState, resumenes: List<ResumenMascotaHoy>, onAnimo: (String, Animo) -> Unit, onAbrir: (String) -> Unit, portada: @Composable () -> Unit) {
    HorizontalPager(
        state = estado,
        pageSpacing = 12.dp,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxWidth().height(ALTO_CARRUSEL).testTag("carrusel_hoy"),
    ) { pagina ->
        if (pagina == 0) portada()
        else resumenes.getOrNull(pagina - 1)?.let { r -> TarjetaMascotaHoy(r, onAnimo = { onAnimo(r.mascota.id, it) }, onAbrir = { onAbrir(r.mascota.id) }) }
    }
}

/**
 * Tarjeta "Para Venus": su foto grande recortada, el nombre, cómo va su día y, abajo,
 * "¿Cómo está hoy?" para anotar su ánimo en un toque.
 */
@Composable
private fun TarjetaMascotaHoy(r: ResumenMascotaHoy, onAnimo: (Animo) -> Unit, onAbrir: () -> Unit) {
    val m = r.mascota
    val forma = RoundedCornerShape(30.dp)
    val colores = m.especie.colores()
    Column(
        Modifier
            .fillMaxWidth()
            .height(ALTO_CARRUSEL)
            .clip(forma)
            .background(Brush.linearGradient(listOf(colores.first(), colores.last().copy(alpha = 0.55f))))
            .grano(0.3f)
            .testTag("hero_${m.nombre}"),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).clickable(onClick = onAbrir)) {
            // Foto o ilustración grande, recortada contra la esquina
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 18.dp, y = (-10).dp)
                    .size(150.dp)
                    .rotate(8f)
                    .clip(RoundedCornerShape(44.dp))
                    .background(Pc.Blanco.copy(alpha = 0.35f)),
            )
            if (m.fotoLocal != null) {
                AvatarMascota(
                    m.especie, m.fotoLocal, tam = 118.dp, forma = RoundedCornerShape(36.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 14.dp).rotate(6f).border(4.dp, Pc.Blanco, RoundedCornerShape(36.dp)),
                )
            } else {
                Image(
                    painterResource(m.especie.ilustracion()), contentDescription = null,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp).size(124.dp),
                )
            }
            Column(Modifier.padding(start = 20.dp, top = 18.dp, end = 150.dp)) {
                Text("Para", style = MaterialTheme.typography.labelLarge, color = Pc.Tinta.copy(alpha = 0.6f))
                Text(m.nombre, fontFamily = Kalnia, fontSize = 32.sp, lineHeight = 36.sp, color = Pc.Tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DatoHero(
                        Icono.CHECK,
                        when {
                            r.total == 0 -> "Sin cuidados hoy"
                            r.hechos == r.total -> "Cuidados de hoy completos"
                            else -> "${r.hechos} de ${r.total} ${if (r.total == 1) "cuidado" else "cuidados"}"
                        },
                    )
                    r.proxima?.let { t ->
                        DatoHero(t.evento.tipo.icono, if (t.conHora) "${t.evento.titulo} · ${t.fechaHora.format(HORA)}" else t.evento.titulo)
                    }
                    DatoHero(Icono.PASEO, if (r.paseos == 0) "Aún sin paseos" else "${r.paseos} ${if (r.paseos == 1) "paseo" else "paseos"} · ${textoDistancia(r.metros)}")
                }
            }
        }
        SelectorAnimo(m.nombre, r.animo, r.semana.map { it.fecha to it.animo }, r.nota, onAnimo)
    }
}

@Composable
private fun DatoHero(icono: Icono, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Pc.Blanco.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
            Simbolo(icono, tam = 14.dp)
        }
        Text(texto, style = MaterialTheme.typography.labelLarge, color = Pc.Tinta, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * "¿Cómo está hoy?": cinco caritas en fila. La elegida crece y se pinta; debajo, la semana en
 * puntos de color para ver de un vistazo cómo ha estado.
 */
@Composable
private fun SelectorAnimo(nombre: String, hoy: Animo?, semana: List<Pair<LocalDate, Animo>>, nota: String?, onAnimo: (Animo) -> Unit) {
    Column(
        Modifier
            .padding(10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Pc.Blanco.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("animo_$nombre"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(hoy, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "pregunta") { a ->
                Text(
                    if (a == null) "¿Cómo está $nombre hoy?" else "Hoy: ${a.etiqueta.lowercase()}",
                    style = MaterialTheme.typography.titleSmall, color = Pc.Tinta,
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            SemanaAnimo(semana)
        }
        // Varios días decaído: una línea amable que remite a su veterinario
        nota?.let {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Simbolo(Icono.VETERINARIO, tam = 14.dp)
                Text(it, style = MaterialTheme.typography.labelSmall, color = Pc.TintaSuave, maxLines = 2, modifier = Modifier.padding(start = 6.dp).testTag("nota_animo"))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Animo.entries.forEach { a ->
                val elegido = a == hoy
                val escala by animateFloatAsState(if (elegido) 1.12f else 1f, spring(dampingRatio = 0.45f), label = "escala")
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.RadioButton) { onAnimo(a) }
                        .padding(4.dp)
                        .testTag("animo_${a.name}"),
                ) {
                    Box(
                        Modifier
                            .scale(escala)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (elegido) a.color() else Pc.Crema),
                        contentAlignment = Alignment.Center,
                    ) {
                        Simbolo(a.icono, tam = 20.dp, color = if (elegido) Pc.SobreAcento else Pc.TintaSuave)
                    }
                    Text(a.etiqueta, style = MaterialTheme.typography.labelSmall, color = if (elegido) Pc.Tinta else Pc.TintaSuave, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

/** Color de cada ánimo (cálidos para los buenos, fríos para los bajos). */
fun Animo.color(): Color = when (this) {
    Animo.FELIZ -> Pc.Naranja
    Animo.JUGUETON -> Pc.Lavanda
    Animo.TRANQUILO -> Pc.Menta
    Animo.CANSADO -> Pc.Cielo
    Animo.DECAIDO -> Pc.TintaSuave
}

/** Los últimos 7 días en puntitos (vacío = sin anotar). */
@Composable
private fun SemanaAnimo(semana: List<Pair<LocalDate, Animo>>) {
    val hoy = LocalDate.now()
    val porDia = semana.toMap()
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        (6 downTo 0).forEach { d ->
            val a = porDia[hoy.minusDays(d.toLong())]
            Box(
                Modifier
                    .size(if (d == 0) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(a?.color() ?: Pc.Linea),
            )
        }
    }
}

/** Accesos rápidos en círculos con su nombre debajo (como una fila de "transportes"). */
data class AccesoRapido(val icono: Icono, val texto: String, val etiqueta: String, val onClick: () -> Unit)

@Composable
fun AccesosRapidos(accesos: List<AccesoRapido>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        accesos.forEach { a ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = a.onClick)
                    .padding(6.dp)
                    .testTag(a.etiqueta),
            ) {
                Box(
                    Modifier
                        .size(60.dp)
                        .shadow(6.dp, CircleShape, ambientColor = Pc.Naranja.copy(alpha = 0.2f), spotColor = Pc.Naranja.copy(alpha = 0.2f))
                        .clip(CircleShape)
                        .background(Pc.Blanco)
                        .border(1.dp, Pc.Linea, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    BaldosaIcono(a.icono, tam = 44.dp, modifier = Modifier.clip(CircleShape))
                }
                Text(a.texto, style = MaterialTheme.typography.labelMedium, color = Pc.Tinta, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

