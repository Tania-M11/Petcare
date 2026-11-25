package com.udenar.petcare.ui.calendario

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.petcare.domain.cuidados.EstadoRegistro
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.modelo.Icono
import com.udenar.petcare.domain.recomendaciones.PoseMichi
import com.udenar.petcare.ui.componentes.ChipOpcion
import com.udenar.petcare.ui.componentes.VacioMichi
import com.udenar.petcare.ui.hoy.CalendarioMes
import com.udenar.petcare.ui.hoy.ChipsMascotas
import com.udenar.petcare.ui.hoy.HoyViewModel
import com.udenar.petcare.ui.hoy.TarjetaToma
import com.udenar.petcare.ui.theme.Pc
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Qué mostrar en el calendario: todo o un tipo de recordatorio. */
private enum class FiltroCalendario(val titulo: String, val icono: Icono?, val tipos: Set<TipoCuidado>?) {
    TODO("Todo", null, null),
    VACUNAS("Vacunas", Icono.VACUNA, setOf(TipoCuidado.VACUNA)),
    MEDICAMENTOS("Medicamentos", Icono.MEDICAMENTO, setOf(TipoCuidado.MEDICAMENTO, TipoCuidado.DESPARASITACION_INTERNA, TipoCuidado.DESPARASITACION_EXTERNA)),
    CUIDADOS(
        "Cuidados", Icono.BANO,
        setOf(
            TipoCuidado.COMIDA, TipoCuidado.BANO, TipoCuidado.CORTE_PELO, TipoCuidado.CEPILLADO, TipoCuidado.CORTE_UNAS, TipoCuidado.LIMPIEZA_DENTAL,
            TipoCuidado.LIMPIEZA_OIDOS, TipoCuidado.LIMPIEZA_OJOS, TipoCuidado.PERSONALIZADO, TipoCuidado.PASEO, TipoCuidado.PESAJE,
        ),
    ),
    CITAS("Citas", Icono.VETERINARIO, setOf(TipoCuidado.CITA_VET, TipoCuidado.SERVICIO_CONTRATADO)),
}

private val FORMATO_DIA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-CO"))

/**
 * Pestaña Calendario: el mes con los días que tienen algo, filtro por mascota y por tipo
 * (vacunas, medicamentos, cuidados, citas) y, para el día elegido, lo pendiente y lo realizado.
 */
@Composable
fun CalendarioScreen(onVolver: () -> Unit, modifier: Modifier = Modifier, viewModel: HoyViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val mes by viewModel.mes.collectAsStateWithLifecycle()
    val resumenMes by viewModel.resumenMes.collectAsStateWithLifecycle()
    val agenda by viewModel.agenda.collectAsStateWithLifecycle()
    val mascotas by viewModel.mascotas.collectAsStateWithLifecycle()
    val filtroMascota by viewModel.filtro.collectAsStateWithLifecycle()
    var filtro by rememberSaveable { mutableStateOf(FiltroCalendario.TODO) }

    Column(
        modifier.fillMaxSize().background(Pc.Crema).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            com.udenar.petcare.ui.componentes.BotonCircular(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onVolver)
            Text("Calendario", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp))
        }
        if (mascotas.isNotEmpty()) ChipsMascotas(mascotas, filtroMascota, onElegir = { id -> if (id == null) viewModel.verMascota(null) else viewModel.verMascota(id) })
        Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Pc.Blanco).padding(top = 12.dp)) {
            CalendarioMes(mes = mes, hoy = estado.hoy, seleccionado = estado.seleccionado, resumen = resumenMes, onMes = viewModel::cambiarMes, onDia = viewModel::elegirDia)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FiltroCalendario.entries.forEach { f ->
                ChipOpcion(f.titulo, filtro == f, onClick = { filtro = f }, icono = f.icono, modifier = Modifier.testTag("cal_filtro_${f.name}"))
            }
        }
        Text(
            if (estado.seleccionado == estado.hoy) "Hoy" else estado.seleccionado.format(FORMATO_DIA).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleLarge,
        )
        val delDia = agenda.orEmpty().filter { filtro.tipos == null || it.toma.evento.tipo in filtro.tipos!! }
        val pendientes = delDia.filter { it.toma.pendiente }
        val hechas = delDia.filter { !it.toma.pendiente }
        if (delDia.isEmpty()) {
            VacioMichi(PoseMichi.DORMIDO, "Nada programado este día${if (filtro != FiltroCalendario.TODO) " en ${filtro.titulo.lowercase()}" else ""}.", fondo = Pc.Blanco)
        }
        if (pendientes.isNotEmpty()) {
            Text("Pendientes (${pendientes.size})", style = MaterialTheme.typography.titleSmall, color = Pc.Naranja, modifier = Modifier.testTag("cal_pendientes"))
            pendientes.forEach { item ->
                TarjetaToma(
                    item = item, hoy = estado.hoy, sePuedeMarcar = viewModel.sePuedeMarcar(item.toma),
                    onHecho = { viewModel.marcar(item.toma, item.toma.evento.tipo.estadoHecho) },
                    onOmitir = { viewModel.marcar(item.toma, EstadoRegistro.OMITIDA) },
                    onDeshacer = { viewModel.deshacer(item.toma) },
                )
            }
        }
        if (hechas.isNotEmpty()) {
            Text("Realizadas (${hechas.size})", style = MaterialTheme.typography.titleSmall, color = Pc.MentaOscura, modifier = Modifier.testTag("cal_realizadas"))
            hechas.forEach { item ->
                TarjetaToma(
                    item = item, hoy = estado.hoy, sePuedeMarcar = viewModel.sePuedeMarcar(item.toma),
                    onHecho = {}, onOmitir = {}, onDeshacer = { viewModel.deshacer(item.toma) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
