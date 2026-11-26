package com.udenar.petcare.ui.mascotas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.petcare.domain.cuidados.DescripcionCuidados
import com.udenar.petcare.domain.modelo.Icono
import com.udenar.petcare.domain.recomendaciones.PoseMichi
import com.udenar.petcare.ui.componentes.AsistentePasos
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.ChipOpcion
import com.udenar.petcare.ui.componentes.EditorHoras
import com.udenar.petcare.ui.componentes.FilaResumen
import com.udenar.petcare.ui.componentes.PreguntaMichi
import com.udenar.petcare.ui.componentes.SelectorFecha
import com.udenar.petcare.ui.componentes.TarjetaOpcionGrande
import com.udenar.petcare.ui.theme.Pc
import java.time.LocalDate
import java.time.LocalTime

private enum class PasoVisita { MOTIVO, CUANDO, INDICACIONES, PROXIMA, RESUMEN }

private val MOTIVOS = listOf(
    "Control" to Icono.VETERINARIO, "Vacunación" to Icono.VACUNA, "Desparasitación" to Icono.ESCUDO,
    "Malestar" to Icono.TERMOMETRO, "Urgencia" to Icono.HOSPITAL, "Otro" to Icono.NOTA,
)

/**
 * Nueva visita veterinaria por pasos (Fase 6.2). Solo guarda lo que la persona escribe; los
 * atajos de la próxima cita son fechas para anotar lo que dijo el veterinario, no sugerencias.
 */
@Composable
fun NuevaVisitaScreen(onCerrar: () -> Unit, onGuardada: () -> Unit, viewModel: FormVisitaViewModel = hiltViewModel()) {
    val e by viewModel.f.collectAsStateWithLifecycle()
    LaunchedEffect(e.listo) { if (e.listo) onGuardada() }
    var paso by rememberSaveable { mutableIntStateOf(0) }
    val pasos = PasoVisita.entries
    val actual = pasos[paso]
    val hoy = viewModel.hoy
    AsistentePasos(
        paso = paso, total = pasos.size,
        onAtras = { if (paso == 0) onCerrar() else paso-- },
        onCerrar = onCerrar,
        onSaltar = when (actual) {
            PasoVisita.INDICACIONES -> ({ paso++ })
            PasoVisita.PROXIMA -> ({ viewModel.editar { it.copy(proximaFecha = null) }; paso++ })
            else -> null
        },
        abajo = {
            when (actual) {
                PasoVisita.RESUMEN -> BotonPrincipal("Guardar visita", onClick = viewModel::guardar, modifier = Modifier.testTag("guardar_visita"))
                else -> BotonPrincipal(
                    "Continuar", habilitado = actual != PasoVisita.MOTIVO || e.motivo.isNotBlank(),
                    onClick = { paso++ }, modifier = Modifier.testTag("continuar"),
                )
            }
            e.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        },
    ) { n ->
        when (pasos[n]) {
            PasoVisita.MOTIVO -> {
                PreguntaMichi("¿Por qué fue la visita?", "Elige uno o escríbelo", PoseMichi.DOCTOR)
                MOTIVOS.chunked(3).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        fila.forEach { (m, icono) ->
                            TarjetaOpcionGrande(
                                m, e.motivo == m, onClick = { viewModel.editar { it.copy(motivo = if (m == "Otro") "" else m) } },
                                icono = icono, color = Pc.CieloClaro, modifier = Modifier.weight(1f).testTag("motivo_$m"),
                            )
                        }
                        repeat(3 - fila.size) { Box(Modifier.weight(1f)) }
                    }
                }
                CampoTexto(e.motivo, { v -> viewModel.editar { it.copy(motivo = v.take(120)) } }, "Motivo", modifier = Modifier.testTag("motivo_visita"))
            }
            PasoVisita.CUANDO -> {
                PreguntaMichi("¿Cuándo y con quién?", null, PoseMichi.AGENDA)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(hoy to "Hoy", hoy.minusDays(1) to "Ayer").forEach { (d, t) ->
                        TarjetaOpcionGrande(t, e.fecha == d, onClick = { viewModel.editar { it.copy(fecha = d) } }, icono = Icono.CALENDARIO, subtitulo = DescripcionCuidados.fecha(d), modifier = Modifier.weight(1f))
                    }
                }
                SelectorFecha("Otra fecha", e.fecha, { d -> d?.let { viewModel.editar { s -> s.copy(fecha = it) } } }, hasta = hoy)
                CampoTexto(e.veterinario, { v -> viewModel.editar { it.copy(veterinario = v) } }, "Veterinario (opcional)")
                CampoTexto(e.clinica, { v -> viewModel.editar { it.copy(clinica = v) } }, "Clínica (opcional)")
            }
            PasoVisita.INDICACIONES -> {
                PreguntaMichi("¿Qué indicó el veterinario?", "Escríbelo tal como lo indicó (opcional)", PoseMichi.DOCTOR)
                CampoTexto(e.tratamiento, { v -> viewModel.editar { it.copy(tratamiento = v) } }, "Tratamiento", lineas = 3)
                CampoTexto(e.medicamentos, { v -> viewModel.editar { it.copy(medicamentos = v) } }, "Medicamentos", lineas = 2)
                CampoTexto(e.notas, { v -> viewModel.editar { it.copy(notas = v) } }, "Notas", lineas = 2)
                Text("Para que te recuerde un medicamento, créalo en Cuidados con la dosis que te indicaron.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            }
            PasoVisita.PROXIMA -> {
                PreguntaMichi("¿Hay una próxima cita?", "Recibirás un aviso el día anterior y a la hora de la cita", PoseMichi.AGENDA)
                Text("Toca cuándo te dijo que volvieras", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("En 15 días" to e.fecha.plusDays(15), "En 1 mes" to e.fecha.plusMonths(1), "En 3 meses" to e.fecha.plusMonths(3), "En 6 meses" to e.fecha.plusMonths(6), "En 1 año" to e.fecha.plusYears(1))
                        .filter { !it.second.isBefore(hoy) }
                        .forEach { (t, d) -> ChipOpcion(t, e.proximaFecha == d, onClick = { viewModel.editar { it.copy(proximaFecha = d) } }, modifier = Modifier.testTag("proxima_$t")) }
                }
                SelectorFecha("Fecha", e.proximaFecha, { d -> viewModel.editar { it.copy(proximaFecha = d) } }, desde = hoy, textoVacio = "Sin próxima cita", permitirBorrar = true)
                if (e.proximaFecha != null) EditorHoras(e.proximaHora, { h -> viewModel.editar { it.copy(proximaHora = h.takeLast(1)) } }, horaSugerida = LocalTime.of(9, 0))
            }
            PasoVisita.RESUMEN -> {
                PreguntaMichi("Confirma los datos", "Toca un dato para cambiarlo", PoseMichi.LEYENDO)
                FilaResumen("Motivo", e.motivo, onEditar = { paso = PasoVisita.MOTIVO.ordinal })
                FilaResumen("Fecha", fechaLarga(e.fecha, hoy) + listOfNotNull(e.veterinario.ifBlank { null }, e.clinica.ifBlank { null }).joinToString("") { " · $it" }, onEditar = { paso = PasoVisita.CUANDO.ordinal })
                FilaResumen(
                    "Indicaciones",
                    listOf(e.tratamiento, e.medicamentos, e.notas).filter { it.isNotBlank() }.joinToString(" · ").take(80).ifEmpty { "Sin indicaciones" },
                    onEditar = { paso = PasoVisita.INDICACIONES.ordinal },
                )
                FilaResumen("Próxima cita", e.proximaFecha?.let { fechaLarga(it, hoy) } ?: "Sin próxima cita", onEditar = { paso = PasoVisita.PROXIMA.ordinal })
            }
        }
    }
}

private fun fechaLarga(d: LocalDate, hoy: LocalDate) = when (d) {
    hoy -> "Hoy"
    hoy.minusDays(1) -> "Ayer"
    else -> DescripcionCuidados.fecha(d) + if (d.year != hoy.year) " de ${d.year}" else ""
}
