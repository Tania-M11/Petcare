package com.udenar.petcare.ui.historial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.udenar.petcare.domain.cuidados.RepositorioCuidados
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.domain.paseos.RepositorioPaseos
import com.udenar.petcare.domain.salud.RepositorioSalud
import com.udenar.petcare.domain.salud.RepositorioSintomas
import com.udenar.petcare.ui.hoy.ChipsMascotas
import com.udenar.petcare.ui.mascotas.ItemHistorial
import com.udenar.petcare.ui.mascotas.TarjetaHistorial
import com.udenar.petcare.ui.mascotas.construirHistorial
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class HistorialViewModel @Inject constructor(
    mascotasRepo: RepositorioMascotas,
    cuidados: RepositorioCuidados,
    salud: RepositorioSalud,
    paseos: RepositorioPaseos,
    sintomas: RepositorioSintomas,
    reloj: Clock,
) : ViewModel() {
    val mascotas: StateFlow<List<Mascota>> = mascotasRepo.observar().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Mascota elegida (null = todas). */
    private val _filtro = MutableStateFlow<String?>(null)
    val filtro = _filtro.asStateFlow()
    fun elegir(id: String?) { _filtro.value = if (_filtro.value == id) null else id }

    val items: StateFlow<List<ItemHistorial>> = combine(
        combine(cuidados.observarEventos(), cuidados.observarRegistros(), ::Pair),
        combine(salud.observarTodosPesos(), salud.observarTodasVisitas(), ::Pair),
        combine(paseos.observarDesde(Instant.EPOCH), sintomas.observarTodos(), ::Pair),
        mascotas,
        _filtro,
    ) { (ev, reg), (ps, vs), (pas, sin), lista, f ->
        val nombres = lista.associate { it.id to it.nombre }
        fun mia(id: String) = id in nombres && (f == null || id == f)
        construirHistorial(
            ev, reg.filter { mia(it.mascotaId) }, ps.filter { mia(it.mascotaId) }, vs.filter { mia(it.mascotaId) },
            pas.filter { mia(it.mascotaId) }, sin.filter { mia(it.mascotaId) }, reloj.zone,
            nombreDe = if (f == null) ({ id -> nombres[id] }) else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Pestaña Historial: la línea de tiempo de todas las mascotas (o de una), con filtros por tipo. */
@Composable
fun HistorialScreen(onVolver: () -> Unit, modifier: Modifier = Modifier, viewModel: HistorialViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val mascotas by viewModel.mascotas.collectAsStateWithLifecycle()
    val filtro by viewModel.filtro.collectAsStateWithLifecycle()
    Column(
        modifier.fillMaxSize().background(com.udenar.petcare.ui.theme.Pc.Crema).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            com.udenar.petcare.ui.componentes.BotonCircular(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onVolver)
            Text("Historial de tus mascotas", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 16.dp))
        }
        if (mascotas.isNotEmpty()) ChipsMascotas(mascotas, filtro, onElegir = viewModel::elegir)
        TarjetaHistorial(items)
        Spacer(Modifier.height(24.dp))
    }
}
