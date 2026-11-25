package com.udenar.petcare.ui.mascotas

import com.udenar.petcare.ui.componentes.Simbolo
import com.udenar.petcare.domain.modelo.Icono
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.udenar.petcare.domain.cuidados.AreaCuidado
import com.udenar.petcare.domain.cuidados.NivelCuidado
import com.udenar.petcare.domain.mascotas.FichaMascota
import com.udenar.petcare.domain.mascotas.UnidadPeso
import com.udenar.petcare.domain.salud.RegistroPeso
import com.udenar.petcare.domain.salud.Visita
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.ChipOpcion
import com.udenar.petcare.ui.componentes.SelectorFecha
import com.udenar.petcare.ui.theme.Kalnia
import com.udenar.petcare.ui.theme.Pc
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale.forLanguageTag("es-CO")
private val CORTA = DateTimeFormatter.ofPattern("d MMM yyyy", ES)

@Composable
private fun Tarjeta(titulo: String, accion: (@Composable () -> Unit)? = null, contenido: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Pc.Blanco)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.udenar.petcare.ui.componentes.TituloConIcono(titulo, Modifier.weight(1f))
            accion?.invoke()
        }
        contenido()
    }
}

@Composable
fun BotonAgregar(texto: String, etiqueta: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Pc.Durazno)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(etiqueta),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = Pc.Naranja, modifier = Modifier.size(18.dp))
        Text(texto, style = MaterialTheme.typography.labelLarge, color = Pc.Naranja)
    }
}

/** Estado de cuidado (RF-16): solo fechas y registros, nunca un diagnóstico. */
@Composable
fun TarjetaEstadoCuidado(estado: Map<AreaCuidado, NivelCuidado>) {
    Tarjeta("Estado de cuidado") {
        if (estado.isEmpty()) {
            Text("Cuando agregues cuidados verás aquí si están al día.", style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                estado.forEach { (area, nivel) ->
                    val (fondo, color) = when (nivel) {
                        NivelCuidado.AL_DIA -> Pc.MentaClara to Pc.Tinta
                        NivelCuidado.PROXIMO -> Pc.SolClaro to Pc.Tinta
                        NivelCuidado.PENDIENTE -> Pc.Durazno to MaterialTheme.colorScheme.error
                    }
                    Column(
                        Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .background(fondo)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("estado_${area.name}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Simbolo(area.icono, tam = 30.dp)
                        Text(area.titulo, style = MaterialTheme.typography.labelMedium)
                        Text(nivel.etiqueta, style = MaterialTheme.typography.labelLarge, color = color)
                    }
                }
            }
        }
        Text("Esto no constituye un diagnóstico médico.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
    }
}

/** Peso con su gráfica de evolución (RF-10). */
@Composable
fun TarjetaPeso(pesos: List<RegistroPeso>, hoy: LocalDate, onRegistrar: (LocalDate, Int) -> Unit, onEliminar: (String) -> Unit, editable: Boolean = true) {
    var abierto by remember { mutableStateOf(false) }
    Tarjeta("Peso", accion = if (editable) ({ BotonAgregar("Registrar", "registrar_peso") { abierto = true } }) else null) {
        val ultimo = pesos.firstOrNull()
        if (ultimo == null) {
            com.udenar.petcare.ui.componentes.VacioMichi(com.udenar.petcare.domain.recomendaciones.PoseMichi.SALUD, "Aún no hay pesajes. Registra el primero y aquí verás cómo cambia.")
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(FichaMascota.peso(ultimo.gramos), fontFamily = Kalnia, fontSize = 30.sp, color = Pc.Tinta)
                Text("  ${ultimo.fecha.format(CORTA)}", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            }
            if (pesos.size >= 2) GraficaPeso(pesos.sortedBy { it.fecha })
            else com.udenar.petcare.ui.componentes.VacioMichi(com.udenar.petcare.domain.recomendaciones.PoseMichi.AGENDA, "Con un pesaje más aparece su gráfica.", tam = 52.dp)
            pesos.take(6).forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.fecha.format(CORTA), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(FichaMascota.peso(p.gramos), style = MaterialTheme.typography.titleSmall)
                    if (editable) TextButton(onClick = { onEliminar(p.id) }) { Text("Quitar", style = MaterialTheme.typography.labelMedium, color = Pc.TintaSuave) }
                }
            }
        }
    }
    if (abierto) {
        // Parte del último peso: con − / + basta para la mayoría de los pesajes
        var texto by remember { mutableStateOf(pesos.firstOrNull()?.let { FichaMascota.pesoEditable(it.gramos).first } ?: "") }
        var unidad by remember { mutableStateOf(UnidadPeso.KG) }
        var fecha by remember { mutableStateOf(hoy) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { abierto = false },
            containerColor = Pc.Crema,
            title = { Text("Registrar peso", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CampoTexto(texto, { texto = it; error = null }, "Peso medido", teclado = KeyboardType.Decimal, error = error, modifier = Modifier.weight(1f))
                        ChipOpcion("kg", unidad == UnidadPeso.KG, onClick = { unidad = UnidadPeso.KG })
                        ChipOpcion("g", unidad == UnidadPeso.G, onClick = { unidad = UnidadPeso.G })
                    }
                    val actual = FichaMascota.gramos(texto, unidad)
                    if (unidad == UnidadPeso.KG) {
                        com.udenar.petcare.ui.componentes.Stepper(
                            (actual ?: 0) / 1000.0,
                            { v -> texto = "%.1f".format(java.util.Locale.forLanguageTag("es-CO"), v); error = null },
                            paso = 0.1, unidad = "kg",
                        )
                    }
                    val anterior = pesos.firstOrNull()
                    if (anterior != null && actual != null && actual > 0 && actual != anterior.gramos) {
                        val dif = actual - anterior.gramos
                        Text(
                            (if (dif > 0) "▲ " else "▼ ") + FichaMascota.peso(kotlin.math.abs(dif)) + " desde el último pesaje",
                            style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave,
                        )
                    }
                    SelectorFecha("Fecha", fecha, { it?.let { f -> fecha = f } }, hasta = hoy)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val g = FichaMascota.gramos(texto, unidad)
                    if (g == null || g <= 0) error = "Escribe un número mayor que 0" else {
                        onRegistrar(fecha, g)
                        abierto = false
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar") } },
        )
    }
}

/** Línea de evolución dibujada con Canvas (sin librerías). */
@Composable
private fun GraficaPeso(pesos: List<RegistroPeso>) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(Pc.Crema)
            .testTag("grafica_peso"),
    ) {
        val margen = 16.dp.toPx()
        val min = pesos.minOf { it.gramos }.toFloat()
        val max = pesos.maxOf { it.gramos }.toFloat()
        val rango = (max - min).takeIf { it > 0 } ?: 1f
        val d0 = pesos.first().fecha.toEpochDay().toFloat()
        val dias = (pesos.last().fecha.toEpochDay() - pesos.first().fecha.toEpochDay()).toFloat().takeIf { it > 0 } ?: 1f
        val puntos = pesos.map { p ->
            Offset(
                margen + (p.fecha.toEpochDay() - d0) / dias * (size.width - 2 * margen),
                size.height - margen - (p.gramos - min) / rango * (size.height - 2 * margen),
            )
        }
        val linea = Path().apply {
            moveTo(puntos.first().x, puntos.first().y)
            puntos.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val area = Path().apply {
            addPath(linea)
            lineTo(puntos.last().x, size.height)
            lineTo(puntos.first().x, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(Pc.Lavanda.copy(alpha = 0.35f), Pc.Lavanda.copy(alpha = 0f))))
        drawPath(linea, Pc.LavandaOscura, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        puntos.forEach { drawCircle(Pc.Naranja, radius = 4.dp.toPx(), center = it) }
    }
}

/** Visitas veterinarias (RF-09). */
@Composable
fun TarjetaVisitas(visitas: List<Visita>, onNueva: () -> Unit, onAbrir: (Visita) -> Unit) {
    Tarjeta("Visitas veterinarias", accion = { BotonAgregar("Agregar", "agregar_visita", onNueva) }) {
        if (visitas.isEmpty()) {
            Text("Registra cada consulta: motivo, lo que indicó el veterinario y la próxima cita.", style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
        }
        visitas.forEach { v ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onAbrir(v) }
                    .padding(vertical = 6.dp)
                    .testTag("visita_${v.motivo}"),
            ) {
                Text(v.motivo, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(v.fecha.format(CORTA), v.veterinario, v.clinica).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Pc.TintaSuave,
                )
                v.tratamiento?.let { Text("Tratamiento: $it", style = MaterialTheme.typography.bodySmall) }
                if (v.registradoPor?.rol == "VETERINARIO") {
                    com.udenar.petcare.ui.componentes.Chip("Registrado por veterinario · ${v.registradoPor.nombre.orEmpty()}", fondo = Pc.CieloClaro, color = Pc.CieloOscuro, icono = Icono.VETERINARIO)
                }
            }
        }
    }
}

/** Elemento de la línea de tiempo (RF-15). */
data class ItemHistorial(
    val instante: java.time.LocalDateTime,
    val categoria: CategoriaHistorial,
    val icono: Icono,
    val titulo: String,
    val detalle: String,
)

enum class CategoriaHistorial(val titulo: String) { CUIDADOS("Cuidados"), PASEOS("Paseos"), PESO("Peso"), VISITAS("Visitas"), SINTOMAS("Síntomas") }

@Composable
fun TarjetaHistorial(items: List<ItemHistorial>) {
    var filtro by remember { mutableStateOf<CategoriaHistorial?>(null) }
    Tarjeta("Historial") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipOpcion("Todo", filtro == null, onClick = { filtro = null })
            CategoriaHistorial.entries.forEach { c -> ChipOpcion(c.titulo, filtro == c, onClick = { filtro = c }, modifier = Modifier.testTag("historial_${c.name}")) }
        }
        val visibles = items.filter { filtro == null || it.categoria == filtro }
        if (visibles.isEmpty()) com.udenar.petcare.ui.componentes.VacioMichi(com.udenar.petcare.domain.recomendaciones.PoseMichi.LEYENDO, "Aún no hay registros.")
        var diaAnterior: LocalDate? = null
        visibles.take(100).forEach { i ->
            val dia = i.instante.toLocalDate()
            if (dia != diaAnterior) {
                Text(dia.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave, modifier = Modifier.padding(top = 6.dp))
                diaAnterior = dia
            }
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Pc.Crema), contentAlignment = Alignment.Center) { Simbolo(i.icono, tam = 22.dp) }
                Column(Modifier.padding(start = 10.dp)) {
                    Text(i.titulo, style = MaterialTheme.typography.titleSmall)
                    Text(i.detalle, style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                }
            }
        }
    }
}

@Composable
fun ChipsSeccion(actual: Int, onElegir: (Int) -> Unit) {
    val nombres = listOf("Resumen", "Cuidados", "Salud", "Historial")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        nombres.forEachIndexed { i, n -> ChipOpcion(n, actual == i, onClick = { onElegir(i) }, modifier = Modifier.testTag("seccion_$n")) }
    }
}

