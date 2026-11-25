package com.udenar.petcare.ui.profesional

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.Icono
import com.udenar.petcare.ui.componentes.BaldosaIcono
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.ChipOpcion
import com.udenar.petcare.ui.componentes.TarjetaOpcionGrande
import com.udenar.petcare.ui.componentes.ilustracion
import com.udenar.petcare.ui.theme.Pc
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private val LOCALE_ES = Locale.forLanguageTag("es-CO")

/** Especies en tarjetas grandes con su ilustración, tres por fila (se pueden elegir varias). */
@Composable
fun ElegirEspecies(elegidas: Set<Especie>, onAlternar: (Especie) -> Unit, prefijoTag: String) {
    Especie.entries.chunked(3).forEach { fila ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            fila.forEach { esp ->
                TarjetaOpcionGrande(
                    esp.etiqueta, esp in elegidas, onClick = { onAlternar(esp) },
                    dibujo = esp.ilustracion(), modifier = Modifier.weight(1f).testTag("${prefijoTag}_especie_${esp.name}"),
                )
            }
            repeat(3 - fila.size) { Box(Modifier.weight(1f)) }
        }
    }
}

/** Interruptor dentro de una tarjeta con ícono (p. ej. "Atiendo a domicilio"). */
@Composable
fun TarjetaInterruptor(icono: Icono, titulo: String, ayuda: String?, valor: Boolean, onCambio: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Pc.Blanco).clickable { onCambio(!valor) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BaldosaIcono(icono, tam = 42.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall)
            ayuda?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave) }
        }
        Switch(valor, onCambio, colors = SwitchDefaults.colors(checkedTrackColor = Pc.Naranja))
    }
}

private val DIAS_SEMANA = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val TURNOS = listOf(
    "Mañana" to "8:00-12:00",
    "Tarde" to "14:00-18:00",
    "Mañana y tarde" to "8:00-12:00, 14:00-18:00",
    "Todo el día" to "8:00-18:00",
)

/**
 * Horario sin escribir de más: se eligen los días (o un atajo como "Lun a vie") y el turno con
 * un toque; cada día se puede ajustar a mano tocándolo.
 */
@Composable
fun EditorHorarioAmigable(horario: Map<DayOfWeek, String>, onCambio: (Map<DayOfWeek, String>) -> Unit) {
    val activos = horario.filterValues { it.isNotBlank() }
    var dias by remember { mutableStateOf(activos.keys.ifEmpty { DIAS_SEMANA.toSet() }) }
    var editando by remember { mutableStateOf<DayOfWeek?>(null) }
    // El turno se aplica a los días elegidos; los demás quedan sin atención
    fun aplicar(rango: String) = onCambio(DayOfWeek.entries.associateWith { d -> if (d in dias) rango else "" })

    Text("1. ¿Qué días?", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "Lun a vie" to DIAS_SEMANA.toSet(),
            "Lun a sáb" to (DIAS_SEMANA + DayOfWeek.SATURDAY).toSet(),
            "Todos" to DayOfWeek.entries.toSet(),
        ).forEach { (t, set) -> ChipOpcion(t, dias == set, onClick = { dias = set }) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { d ->
            val on = d in dias
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (on) Pc.Naranja else Pc.Blanco)
                    .clickable { dias = if (on) dias - d else dias + d }.testTag("dia_${d.name}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    d.getDisplayName(TextStyle.NARROW, LOCALE_ES).uppercase(),
                    style = MaterialTheme.typography.labelLarge, color = if (on) Pc.SobreAcento else Pc.TintaSuave,
                )
            }
        }
    }
    Text("2. ¿En qué horario?", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TURNOS.forEach { (t, rango) ->
            ChipOpcion(t, dias.isNotEmpty() && dias.all { horario[it] == rango } && activos.keys == dias, onClick = { aplicar(rango) }, icono = Icono.RELOJ, modifier = Modifier.testTag("turno_$t"))
        }
    }
    if (activos.isNotEmpty()) {
        Text("Así queda (toca un día para ajustarlo)", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Pc.Blanco).padding(vertical = 4.dp)) {
            DayOfWeek.entries.filter { it in activos }.forEach { d ->
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable { editando = if (editando == d) null else d }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(d.getDisplayName(TextStyle.FULL, LOCALE_ES).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(100.dp))
                        Text(activos[d].orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(if (editando == d) "Listo" else "Ajustar", style = MaterialTheme.typography.labelMedium, color = Pc.Naranja)
                    }
                    AnimatedVisibility(editando == d) {
                        CampoTexto(
                            horario[d].orEmpty(), { v -> onCambio(horario + (d to v.take(60))) }, "Horario (p. ej. 8:00-12:00)",
                            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Texto corto del horario para el resumen ("Lun a vie · 8:00-12:00"). */
fun resumenHorario(horario: Map<DayOfWeek, String>): String {
    val activos = horario.filterValues { it.isNotBlank() }
    if (activos.isEmpty()) return "Sin horario"
    val rangos = activos.values.toSet()
    val dias = when (activos.keys) {
        DIAS_SEMANA.toSet() -> "Lun a vie"
        (DIAS_SEMANA + DayOfWeek.SATURDAY).toSet() -> "Lun a sáb"
        DayOfWeek.entries.toSet() -> "Todos los días"
        else -> activos.keys.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, LOCALE_ES) }
    }
    return if (rangos.size == 1) "$dias · ${rangos.first()}" else "$dias · horario por día"
}
