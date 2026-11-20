package com.udenar.petcare.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.udenar.petcare.domain.cuidados.DescripcionCuidados
import com.udenar.petcare.domain.cuidados.ModoFrecuencia
import com.udenar.petcare.ui.theme.Pc
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val ES = Locale.forLanguageTag("es-CO")
private val FECHA = DateTimeFormatter.ofPattern("EEE d 'de' MMMM 'de' yyyy", ES)

/** Campo de fecha que abre el calendario. [hasta]/[desde] limitan las fechas elegibles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorFecha(
    etiqueta: String,
    fecha: LocalDate?,
    onCambio: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    desde: LocalDate? = null,
    hasta: LocalDate? = null,
    textoVacio: String = "Sin fecha",
    permitirBorrar: Boolean = false,
    error: String? = null,
) {
    var abierto by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(Pc.Crema)
                .clickable { abierto = true }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = Pc.Naranja)
            Column(Modifier.padding(start = 12.dp)) {
                Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = Pc.TintaSuave)
                Text(
                    fecha?.format(FECHA)?.replaceFirstChar { it.uppercase() } ?: textoVacio,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) }
    }
    if (abierto) {
        val min = desde?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val max = hasta?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = fecha?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    (min == null || utcTimeMillis >= min) && (max == null || utcTimeMillis <= max)
            },
        )
        DatePickerDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { onCambio(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    abierto = false
                }) { Text("Listo") }
            },
            dismissButton = {
                if (permitirBorrar) {
                    TextButton(onClick = {
                        onCambio(null)
                        abierto = false
                    }) { Text("Quitar fecha") }
                } else {
                    TextButton(onClick = { abierto = false }) { Text("Cancelar") }
                }
            },
        ) { DatePicker(state = estado) }
    }
}

/** Siete círculos L M X J V S D para elegir días de la semana. */
@Composable
fun SelectorDias(dias: Set<DayOfWeek>, onCambio: (Set<DayOfWeek>) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { d ->
            val activo = d in dias
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (activo) Pc.Naranja else Pc.Crema)
                    .clickable { onCambio(if (activo) dias - d else dias + d) }
                    .testTag("dia_semana_${d.value}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    d.getDisplayName(TextStyle.NARROW, ES).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (activo) Pc.Blanco else Pc.TintaSuave,
                )
            }
        }
    }
}

/** Lista de horas como píldoras, con botón para agregar (reloj de Material 3). */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditorHoras(
    horas: List<LocalTime>,
    onCambio: (List<LocalTime>) -> Unit,
    modifier: Modifier = Modifier,
    horaSugerida: LocalTime = LocalTime.of(8, 0),
    error: String? = null,
) {
    var abierto by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            horas.sorted().forEach { h ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Pc.Durazno)
                        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(DescripcionCuidados.hora(h), style = MaterialTheme.typography.labelLarge)
                    Icon(
                        Icons.Rounded.Close, contentDescription = "Quitar ${DescripcionCuidados.hora(h)}", tint = Pc.TintaSuave,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable { onCambio(horas - h) },
                    )
                }
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.5.dp, Pc.NaranjaClaro, RoundedCornerShape(50))
                    .clickable { abierto = true }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag("agregar_hora"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = Pc.Naranja, modifier = Modifier.size(18.dp))
                Text("Agregar hora", style = MaterialTheme.typography.labelLarge, color = Pc.Naranja, modifier = Modifier.padding(start = 4.dp))
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
    }
    if (abierto) {
        val estado = rememberTimePickerState(initialHour = horaSugerida.hour, initialMinute = horaSugerida.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { abierto = false },
            containerColor = Pc.Crema,
            title = { Text("Elige la hora", style = MaterialTheme.typography.headlineSmall) },
            text = {
                TimePicker(
                    state = estado,
                    colors = TimePickerDefaults.colors(
                        selectorColor = Pc.Naranja,
                        timeSelectorSelectedContainerColor = Pc.Durazno,
                        periodSelectorSelectedContainerColor = Pc.Durazno,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val nueva = LocalTime.of(estado.hour, estado.minute)
                    if (nueva !in horas) onCambio(horas + nueva)
                    abierto = false
                }) { Text("Agregar") }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar") } },
        )
    }
}

/** Elección de repetición: modo + "cada N" + días de la semana. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectorFrecuencia(
    modo: ModoFrecuencia,
    n: String,
    dias: Set<DayOfWeek>,
    onModo: (ModoFrecuencia) -> Unit,
    onN: (String) -> Unit,
    onDias: (Set<DayOfWeek>) -> Unit,
    modifier: Modifier = Modifier,
    modos: List<ModoFrecuencia> = ModoFrecuencia.entries,
    errorN: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            modos.forEach { m ->
                ChipOpcion(m.etiqueta.replace(" N ", " … "), modo == m, onClick = { onModo(m) }, modifier = Modifier.testTag("modo_${m.name}"))
            }
        }
        if (modo.usaN) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cada", style = MaterialTheme.typography.bodyLarge)
                CampoTexto(
                    n, onN, "N", teclado = KeyboardType.Number, error = errorN,
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .width(96.dp),
                )
                Text(modo.unidad, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (modo == ModoFrecuencia.DIAS_SEMANA) SelectorDias(dias, onDias)
    }
}
