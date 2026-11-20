package com.udenar.petcare.ui.hoy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.udenar.petcare.domain.cuidados.DescripcionCuidados
import com.udenar.petcare.domain.cuidados.EstadoRegistro
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.cuidados.grupo
import com.udenar.petcare.ui.componentes.AvatarMascota
import com.udenar.petcare.ui.componentes.Chip
import com.udenar.petcare.ui.cuidados.color
import com.udenar.petcare.ui.cuidados.emoji
import com.udenar.petcare.ui.theme.Kalnia
import com.udenar.petcare.ui.theme.Pc
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val ES = Locale.forLanguageTag("es-CO")
private val CORTA = DateTimeFormatter.ofPattern("d MMM", ES)

/** Una toma del día: qué, de quién, a qué hora y su botón de un toque (RNF-01). */
@Composable
fun TarjetaToma(
    item: ItemAgenda,
    hoy: LocalDate,
    sePuedeMarcar: Boolean,
    onHecho: () -> Unit,
    onOmitir: () -> Unit,
    onDeshacer: () -> Unit,
) {
    val t = item.toma
    val e = t.evento
    val r = t.registro
    val (fondoIcono, _) = e.tipo.grupo.color()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Pc.Blanco)
            .padding(14.dp)
            .testTag("toma_${e.titulo}_${t.fechaHora.toLocalTime()}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(fondoIcono),
                contentAlignment = Alignment.Center,
            ) { Text(e.tipo.emoji, fontSize = 22.sp) }
            AvatarMascota(
                item.mascota.especie, item.mascota.fotoLocal, tam = 22.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .border(2.dp, Pc.Blanco, CircleShape),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(e.titulo, style = MaterialTheme.typography.titleSmall)
            val cuando = if (t.conHora) DescripcionCuidados.hora(t.fechaHora.toLocalTime()) else "Todo el día"
            Text(
                listOfNotNull(cuando, item.mascota.nombre, e.detalle["dosis"], e.detalle["texto"]).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Pc.TintaSuave,
            )
            when {
                r != null && r.estado != EstadoRegistro.POSPUESTA -> {
                    val quien = r.registradoPor?.nombre?.substringBefore(' ')
                    val hora = DescripcionCuidados.hora(r.registradoEn.atZone(ZoneId.systemDefault()).toLocalTime())
                    val verbo = when (r.estado) {
                        EstadoRegistro.OMITIDA -> "Omitido"
                        EstadoRegistro.ADMINISTRADA -> "Dado"
                        else -> if (e.tipo == TipoCuidado.COMIDA) "Dada" else "Hecho"
                    }
                    Text(
                        "$verbo${quien?.let { " por $it" } ?: ""}, $hora",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (r.estado == EstadoRegistro.OMITIDA) Pc.TintaSuave else Pc.CieloOscuro,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                !t.conHora && t.fechaHora.toLocalDate().isBefore(hoy) && t.pendiente ->
                    Chip("Vencido desde ${t.fechaHora.toLocalDate().format(CORTA)}", fondo = Pc.Durazno, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                r?.estado == EstadoRegistro.POSPUESTA -> Text("Pospuesto", style = MaterialTheme.typography.labelMedium, color = Pc.Naranja)
            }
        }
        when {
            !sePuedeMarcar -> Unit
            t.pendiente -> Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (e.tipo == TipoCuidado.COMIDA) "Dada" else "Hecho",
                    style = MaterialTheme.typography.labelLarge,
                    color = Pc.Blanco,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Pc.Naranja)
                        .clickable(onClick = onHecho)
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                        .testTag("hecho_${e.titulo}"),
                )
                Text(
                    "Omitir",
                    style = MaterialTheme.typography.labelMedium,
                    color = Pc.TintaSuave,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onOmitir)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (r?.estado == EstadoRegistro.OMITIDA) Pc.Linea else Pc.Menta),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = Pc.Blanco, modifier = Modifier.size(20.dp)) }
                TextButton(onClick = onDeshacer) { Text("Deshacer", style = MaterialTheme.typography.labelMedium, color = Pc.TintaSuave) }
            }
        }
    }
}

/** Calendario mensual (RF-15): puntos en los días con cuidados; naranja si quedó algo sin hacer. */
@Composable
fun CalendarioMes(
    mes: YearMonth,
    hoy: LocalDate,
    seleccionado: LocalDate,
    resumen: Map<LocalDate, ResumenDia>,
    onMes: (Long) -> Unit,
    onDia: (LocalDate) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMes(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Mes anterior") }
            Text(
                mes.month.getDisplayName(TextStyle.FULL_STANDALONE, ES).replaceFirstChar { it.uppercase() } + " ${mes.year}",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onMes(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Mes siguiente") }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            DayOfWeek.entries.forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.NARROW, ES).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Pc.TintaSuave,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val desfase = mes.atDay(1).dayOfWeek.value - 1
        val celdas = desfase + mes.lengthOfMonth()
        (0 until (celdas + 6) / 7).forEach { semana ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { col ->
                    val n = semana * 7 + col - desfase + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                        if (n in 1..mes.lengthOfMonth()) {
                            val dia = mes.atDay(n)
                            val r = resumen[dia]
                            val elegido = dia == seleccionado
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(CircleShape)
                                    .background(if (elegido) Pc.Naranja else if (dia == hoy) Pc.Durazno else Pc.Blanco.copy(alpha = 0f))
                                    .clickable { onDia(dia) }
                                    .aspectRatio(1f)
                                    .testTag("cal_$n"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text("$n", fontFamily = Kalnia, fontSize = 16.sp, color = if (elegido) Pc.Blanco else Pc.Tinta)
                                Spacer(Modifier.height(2.dp))
                                Box(
                                    Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                r == null || r.total == 0 -> Pc.Blanco.copy(alpha = 0f)
                                                elegido -> Pc.Blanco
                                                dia.isBefore(hoy) && r.pendientes > 0 -> Pc.Naranja
                                                else -> Pc.Lavanda
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            "● Lavanda: hay cuidados ese día · ● Naranja: quedó algo sin marcar",
            style = MaterialTheme.typography.bodySmall,
            color = Pc.TintaSuave,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Alertas de salud arriba de Hoy (no son diagnóstico: solo fechas escritas por el usuario). */
@Composable
fun TarjetaAlertas(alertas: List<Alerta>, onAbrir: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Pc.Durazno)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Atención", style = MaterialTheme.typography.titleMedium)
        alertas.take(5).forEach { a ->
            val texto = when (a.tipo) {
                TipoAlerta.VACUNA_VENCIDA -> "💉 ${a.titulo} de ${a.mascota.nombre} venció${a.fecha?.let { " el ${it.format(CORTA)}" } ?: ""}"
                TipoAlerta.VACUNA_PROXIMA -> "💉 ${a.titulo} de ${a.mascota.nombre}${a.fecha?.let { " el ${it.format(CORTA)}" } ?: ""}"
                TipoAlerta.TRATAMIENTO_TERMINA -> "💊 ${a.titulo} de ${a.mascota.nombre} termina${a.fecha?.let { " el ${it.format(CORTA)}" } ?: ""}"
            }
            Text(
                texto,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onAbrir(a.mascota.id) }
                    .padding(vertical = 4.dp),
            )
        }
        Text("Esto no constituye un diagnóstico médico.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
    }
}
