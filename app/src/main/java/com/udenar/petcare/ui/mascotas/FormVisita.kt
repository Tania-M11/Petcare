package com.udenar.petcare.ui.mascotas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.udenar.petcare.domain.cuidados.RepositorioCuidados
import com.udenar.petcare.domain.salud.RepositorioSalud
import com.udenar.petcare.domain.salud.Visita
import com.udenar.petcare.ui.componentes.BotonCircular
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.EditorHoras
import com.udenar.petcare.ui.componentes.Seccion
import com.udenar.petcare.ui.componentes.SelectorFecha
import com.udenar.petcare.ui.navegacion.RutaFormVisita
import com.udenar.petcare.ui.theme.Pc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

data class FormVisitaEstado(
    val id: String,
    val mascotaId: String,
    val esNueva: Boolean,
    val fecha: LocalDate,
    val motivo: String = "",
    val veterinario: String = "",
    val clinica: String = "",
    val tratamiento: String = "",
    val medicamentos: String = "",
    val notas: String = "",
    val proximaFecha: LocalDate? = null,
    val proximaHora: List<LocalTime> = emptyList(),
    val error: String? = null,
    val listo: Boolean = false,
)

@HiltViewModel
class FormVisitaViewModel @Inject constructor(
    estado: SavedStateHandle,
    private val salud: RepositorioSalud,
    private val cuidados: RepositorioCuidados,
    private val reloj: Clock,
) : ViewModel() {
    private val ruta = estado.toRoute<RutaFormVisita>()
    val hoy: LocalDate get() = LocalDate.now(reloj)

    private val _f = MutableStateFlow(FormVisitaEstado(ruta.id ?: UUID.randomUUID().toString(), ruta.mascotaId, ruta.id == null, hoy))
    val f: StateFlow<FormVisitaEstado> = _f.asStateFlow()

    init {
        ruta.id?.let { id ->
            viewModelScope.launch {
                val v = salud.observarVisitas(ruta.mascotaId).first().firstOrNull { it.id == id } ?: return@launch
                val cita = v.proximaCitaId?.let { cid -> cuidados.observarEventos().first().firstOrNull { it.id == cid } }
                _f.update {
                    it.copy(
                        fecha = v.fecha, motivo = v.motivo, veterinario = v.veterinario.orEmpty(), clinica = v.clinica.orEmpty(),
                        tratamiento = v.tratamiento.orEmpty(), medicamentos = v.medicamentos.orEmpty(), notas = v.notas.orEmpty(),
                        proximaFecha = cita?.fechaInicio, proximaHora = cita?.frecuencia?.horas.orEmpty(),
                    )
                }
            }
        }
    }

    fun editar(cambio: (FormVisitaEstado) -> FormVisitaEstado) = _f.update { cambio(it).copy(error = null) }

    fun guardar() {
        val e = _f.value
        if (e.motivo.isBlank()) {
            _f.update { it.copy(error = "Escribe el motivo de la visita") }
            return
        }
        viewModelScope.launch {
            val cita = e.proximaFecha?.atTime(e.proximaHora.firstOrNull() ?: LocalTime.of(9, 0))
            salud.guardarVisita(
                Visita(e.id, e.mascotaId, e.fecha, e.motivo, e.veterinario, e.clinica, e.tratamiento, e.medicamentos, e.notas),
                cita,
            )
            _f.update { it.copy(listo = true) }
        }
    }

    fun eliminar() {
        viewModelScope.launch {
            salud.eliminarVisita(_f.value.id)
            _f.update { it.copy(listo = true) }
        }
    }
}

/** Visita veterinaria (RF-09): la app guarda lo que escribe el usuario, sin interpretar. */
@Composable
fun FormVisitaScreen(onListo: () -> Unit, viewModel: FormVisitaViewModel = hiltViewModel()) {
    val e by viewModel.f.collectAsStateWithLifecycle()
    LaunchedEffect(e.listo) { if (e.listo) onListo() }
    Column(
        Modifier
            .fillMaxSize()
            .background(Pc.Crema)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotonCircular(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onListo)
            Text(if (e.esNueva) "Nueva visita" else "Editar visita", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp))
        }
        Seccion("Consulta") {
            SelectorFecha("Fecha", e.fecha, { d -> d?.let { viewModel.editar { s -> s.copy(fecha = it) } } }, hasta = viewModel.hoy)
            CampoTexto(e.motivo, { v -> viewModel.editar { it.copy(motivo = v) } }, "Motivo", error = e.error)
            CampoTexto(e.veterinario, { v -> viewModel.editar { it.copy(veterinario = v) } }, "Veterinario (opcional)")
            CampoTexto(e.clinica, { v -> viewModel.editar { it.copy(clinica = v) } }, "Clínica (opcional)")
        }
        Seccion("Lo que indicó el veterinario") {
            CampoTexto(e.tratamiento, { v -> viewModel.editar { it.copy(tratamiento = v) } }, "Tratamiento", lineas = 3)
            CampoTexto(e.medicamentos, { v -> viewModel.editar { it.copy(medicamentos = v) } }, "Medicamentos", lineas = 2)
            CampoTexto(e.notas, { v -> viewModel.editar { it.copy(notas = v) } }, "Notas", lineas = 2)
            Text("Para que te recuerde un medicamento, créalo en Cuidados con la dosis que te indicaron.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
        }
        Seccion("Próxima cita (opcional)") {
            SelectorFecha("Fecha", e.proximaFecha, { d -> viewModel.editar { it.copy(proximaFecha = d) } }, desde = viewModel.hoy, textoVacio = "Sin próxima cita", permitirBorrar = true)
            if (e.proximaFecha != null) {
                EditorHoras(e.proximaHora, { h -> viewModel.editar { it.copy(proximaHora = h.takeLast(1)) } }, horaSugerida = LocalTime.of(9, 0))
                Text("Te avisaremos el día anterior y a la hora de la cita.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            }
        }
        BotonPrincipal(if (e.esNueva) "Guardar visita" else "Guardar cambios", onClick = viewModel::guardar)
        if (!e.esNueva) {
            TextButton(onClick = viewModel::eliminar, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Eliminar visita", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
