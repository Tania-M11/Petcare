package com.udenar.petcare.ui.compartir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.udenar.petcare.domain.compartir.CompartirException
import com.udenar.petcare.domain.compartir.EstadoSolicitud
import com.udenar.petcare.domain.compartir.MascotaPorCodigo
import com.udenar.petcare.domain.compartir.RepositorioCompartir
import com.udenar.petcare.domain.compartir.ResultadoSolicitud
import com.udenar.petcare.domain.compartir.SolicitudCuidado
import com.udenar.petcare.domain.compartir.codigoValido
import com.udenar.petcare.domain.compartir.normalizarCodigo
import com.udenar.petcare.domain.sync.ProgramadorSync
import com.udenar.petcare.ui.componentes.AvatarMascota
import com.udenar.petcare.ui.componentes.BotonCircular
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.Seccion
import com.udenar.petcare.ui.theme.Kalnia
import com.udenar.petcare.ui.theme.Pc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

data class EstadoCuidar(
    val codigo: String = "",
    val buscando: Boolean = false,
    val encontrada: MascotaPorCodigo? = null,
    val mensaje: String? = null,
    val exito: Boolean = false,
    val bloqueadoHasta: Long = 0,
    val mias: List<SolicitudCuidado> = emptyList(),
)

/** RF-20: buscar por código (solo nombre y especie) y pedir cuidar; el dueño debe aprobar. */
@HiltViewModel
class CuidarViewModel @Inject constructor(
    private val repo: RepositorioCompartir,
    private val sync: ProgramadorSync,
    private val reloj: Clock,
) : ViewModel() {
    private val _e = MutableStateFlow(EstadoCuidar())
    val e: StateFlow<EstadoCuidar> = _e.asStateFlow()
    private var fallidos = 0

    init {
        cargarMias()
    }

    fun cargarMias() = viewModelScope.launch {
        runCatching { repo.misSolicitudes() }.onSuccess { l -> _e.update { it.copy(mias = l) } }
    }

    fun escribir(texto: String) = _e.update { it.copy(codigo = normalizarCodigo(texto), encontrada = null, mensaje = null, exito = false) }

    fun buscar() {
        val s = _e.value
        if (reloj.millis() < s.bloqueadoHasta) {
            _e.update { it.copy(mensaje = "Demasiados intentos. Espera un minuto.") }
            return
        }
        if (!codigoValido(s.codigo)) {
            _e.update { it.copy(mensaje = "El código tiene 8 letras y números (sin I, L, O, 0 ni 1).") }
            return
        }
        _e.update { it.copy(buscando = true, mensaje = null) }
        viewModelScope.launch {
            try {
                val m = repo.buscar(s.codigo)
                if (m == null) {
                    // Anti-abuso en el cliente: tras 5 búsquedas fallidas, un minuto de pausa
                    fallidos++
                    val bloqueo = if (fallidos >= 5) reloj.millis() + 60_000 else 0L
                    if (fallidos >= 5) fallidos = 0
                    _e.update { it.copy(mensaje = ResultadoSolicitud.NO_DISPONIBLE.mensaje, bloqueadoHasta = bloqueo) }
                } else {
                    _e.update { it.copy(encontrada = m) }
                }
            } catch (ex: CompartirException) {
                _e.update { it.copy(mensaje = ex.message) }
            } finally {
                _e.update { it.copy(buscando = false) }
            }
        }
    }

    fun solicitar() {
        _e.update { it.copy(buscando = true) }
        viewModelScope.launch {
            try {
                val r = repo.solicitar(_e.value.codigo)
                _e.update { it.copy(mensaje = r.mensaje, exito = r == ResultadoSolicitud.PENDIENTE || r == ResultadoSolicitud.YA_MIEMBRO) }
                if (r == ResultadoSolicitud.YA_MIEMBRO) sync.solicitar()
                cargarMias()
            } catch (ex: CompartirException) {
                _e.update { it.copy(mensaje = ex.message) }
            } finally {
                _e.update { it.copy(buscando = false) }
            }
        }
    }
}

@Composable
fun CuidarConCodigoScreen(onVolver: () -> Unit, viewModel: CuidarViewModel = hiltViewModel()) {
    val e by viewModel.e.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.cargarMias() }
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
            BotonCircular(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onVolver)
            Text("Cuidar con código", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp))
        }
        Text(
            "Escribe el código que te compartió el dueño. Verás solo el nombre de la mascota hasta que te apruebe.",
            style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave,
        )
        OutlinedTextField(
            value = e.codigo,
            onValueChange = viewModel::escribir,
            placeholder = { Text("ABCD2345", fontFamily = Kalnia, fontSize = 28.sp, color = Pc.Linea, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            singleLine = true,
            textStyle = TextStyle(fontFamily = Kalnia, fontSize = 28.sp, letterSpacing = 4.sp, textAlign = TextAlign.Center, color = Pc.Tinta),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Pc.Blanco, unfocusedContainerColor = Pc.Blanco,
                focusedBorderColor = Pc.Naranja, unfocusedBorderColor = Pc.Linea, cursorColor = Pc.Naranja,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("campo_codigo"),
        )
        val m = e.encontrada
        if (m == null) {
            BotonPrincipal("Buscar", onClick = viewModel::buscar, cargando = e.buscando, habilitado = e.codigo.length == 8)
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(Pc.Blanco)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarMascota(m.especie, null, tam = 64.dp)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(m.nombre, style = MaterialTheme.typography.headlineSmall)
                    Text(m.especie.etiqueta, style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
                }
            }
            if (!e.exito) BotonPrincipal("Quiero cuidar a ${m.nombre}", onClick = viewModel::solicitar, cargando = e.buscando)
        }
        e.mensaje?.let {
            Text(
                it, style = MaterialTheme.typography.bodyMedium,
                color = if (e.exito) Pc.CieloOscuro else MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("mensaje_cuidar"),
            )
        }
        if (e.mias.isNotEmpty()) {
            Seccion("Mis solicitudes") {
                e.mias.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.creadoEn.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString(), style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave, modifier = Modifier.weight(1f))
                        Text(
                            when (s.estado) {
                                EstadoSolicitud.PENDIENTE -> "Esperando aprobación"
                                EstadoSolicitud.APROBADA -> "Aprobada ✓"
                                EstadoSolicitud.RECHAZADA -> "Rechazada"
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
