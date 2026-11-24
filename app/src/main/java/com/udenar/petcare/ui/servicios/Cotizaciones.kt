package com.udenar.petcare.ui.servicios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.domain.profesionales.Categoria
import com.udenar.petcare.domain.profesionales.ContactoCompartido
import com.udenar.petcare.domain.profesionales.EstadoSolicitudPres
import com.udenar.petcare.domain.profesionales.ModalidadPrestador
import com.udenar.petcare.domain.profesionales.PerfilPrestador
import com.udenar.petcare.domain.profesionales.Precios
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.RepositorioCotizaciones
import com.udenar.petcare.domain.profesionales.RepositorioPrestadores
import com.udenar.petcare.domain.profesionales.SaludCompartida
import com.udenar.petcare.domain.profesionales.SolicitudPrestador
import com.udenar.petcare.ui.componentes.BotonCircular
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.Chip
import com.udenar.petcare.ui.componentes.ChipOpcion
import com.udenar.petcare.ui.componentes.EditorHoras
import com.udenar.petcare.ui.componentes.Seccion
import com.udenar.petcare.ui.componentes.SelectorFecha
import com.udenar.petcare.ui.navegacion.RutaSolicitarPrestador
import com.udenar.petcare.ui.theme.Pc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

fun EstadoSolicitudPres.colores(): Pair<Color, Color> = when (this) {
    EstadoSolicitudPres.PENDIENTE -> Pc.SolClaro to Pc.Tinta
    EstadoSolicitudPres.COTIZADA -> Pc.Durazno to Pc.Tinta
    EstadoSolicitudPres.ACEPTADA -> Pc.MentaClara to Pc.Tinta
    EstadoSolicitudPres.COMPLETADA -> Pc.CieloClaro to Pc.CieloOscuro
    EstadoSolicitudPres.RECHAZADA, EstadoSolicitudPres.CANCELADA -> Pc.Crema to Pc.TintaSuave
}

private val FECHA_CORTA = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO"))

/** Fila de una solicitud a prestador (en "Mis solicitudes" y en la bandeja del prestador). */
@Composable
fun FilaSolicitudPres(s: SolicitudPrestador, titulo: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Pc.Blanco).clickable(onClick = onClick).padding(14.dp)
            .testTag("solicitud_pres_${s.resumen.nombre}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall)
            Text(
                listOfNotNull(s.servicioNombre, s.fecha.textoCita(), s.cotizacionPrecio?.let { Precios.pesos(it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave,
            )
        }
        Chip(s.estado.etiqueta, fondo = s.estado.colores().first, color = s.estado.colores().second)
    }
}

@HiltViewModel
class SolicitarPrestadorViewModel @Inject constructor(
    estado: SavedStateHandle,
    private val prestadores: RepositorioPrestadores,
    private val cotizaciones: RepositorioCotizaciones,
    mascotasRepo: RepositorioMascotas,
    private val reloj: Clock,
    private val fotosSolicitudes: com.udenar.petcare.domain.profesionales.FotosSolicitudes,
    private val archivos: com.udenar.petcare.data.fotos.ArchivosFotos,
) : ViewModel() {
    private val prestadorId = estado.toRoute<RutaSolicitarPrestador>().id
    val prestador = MutableStateFlow<PerfilPrestador?>(null)
    val categorias = MutableStateFlow<List<Categoria>>(emptyList())
    val mensaje = MutableStateFlow<String?>(null)
    val enviando = MutableStateFlow(false)
    val mascotas: StateFlow<List<Mascota>> = mascotasRepo.observar().map { l -> l.filter { it.esPropietario } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val hoy: LocalDate get() = LocalDate.now(reloj)

    init {
        viewModelScope.launch {
            try {
                categorias.value = prestadores.categorias()
                prestador.value = prestadores.obtener(prestadorId) ?: run { mensaje.value = "Este prestador ya no está disponible."; null }
            } catch (e: ProfesionalException) {
                mensaje.value = e.message
            }
        }
    }

    fun enviar(
        mascotaId: String, categoriaId: String, servicioId: String?, modalidad: ModalidadPrestador, inicio: LocalDateTime, fin: LocalDateTime?,
        descripcion: String, texto: String, salud: SaludCompartida?, contacto: ContactoCompartido?, fotos: List<android.net.Uri>, onListo: () -> Unit,
    ) {
        when {
            inicio.isBefore(LocalDateTime.now(reloj)) -> mensaje.value = "Elige una fecha y hora que no hayan pasado"
            fin != null && fin.isBefore(inicio) -> mensaje.value = "La fecha de fin no puede ser antes del inicio"
            descripcion.isBlank() -> mensaje.value = "Cuéntale qué necesitas"
            else -> {
                enviando.value = true
                viewModelScope.launch {
                    try {
                        val id = cotizaciones.solicitar(mascotaId, prestadorId, categoriaId, servicioId, modalidad, inicio, fin, descripcion, texto, salud, contacto)
                        adjuntarFotos(archivos, fotosSolicitudes, com.udenar.petcare.domain.profesionales.TipoSolicitud.PRESTADOR, id, fotos)?.let { mensaje.value = it }
                        onListo()
                    } catch (e: ProfesionalException) {
                        mensaje.value = e.message
                    } finally {
                        enviando.value = false
                    }
                }
            }
        }
    }
}

@Composable
internal fun FilaCasilla(titulo: String, subtitulo: String, marcada: Boolean, onCambio: (Boolean) -> Unit, etiqueta: String) {
    Row(Modifier.fillMaxWidth().clickable { onCambio(!marcada) }.testTag(etiqueta), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(marcada, onCambio, colors = CheckboxDefaults.colors(checkedColor = Pc.Naranja))
        Column {
            Text(titulo, style = MaterialTheme.typography.titleSmall)
            Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
        }
    }
}

/** Detalle para el dueño: propuesta, confirmar, cancelar y dejar de compartir. */
@Composable
fun DialogoSolicitudPresDueno(
    s: SolicitudPrestador,
    hoy: LocalDate,
    onCerrar: () -> Unit,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    onDejarDeCompartir: () -> Unit,
    yaCalificada: Boolean = false,
    onCalificar: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = Pc.Crema,
        title = { Text("${s.servicioNombre} · ${s.resumen.nombre}", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Con ${s.prestadorNombre} · ${s.modalidad.etiqueta.lowercase()}")
                Text("Pediste: ${s.inicio.textoCita()}" + (s.fin?.let { " → ${it.textoCita()}" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                Chip(s.estado.etiqueta, fondo = s.estado.colores().first, color = s.estado.colores().second)
                if (s.hayPropuesta && s.estado != EstadoSolicitudPres.PENDIENTE) {
                    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Pc.Blanco).padding(12.dp).testTag("propuesta")) {
                        Text("Propuesta de ${s.prestadorNombre}", style = MaterialTheme.typography.titleSmall)
                        s.cotizacionPrecio?.let { Text("Precio: ${Precios.pesos(it)}", style = MaterialTheme.typography.titleMedium) }
                        s.propuestaInicio?.let { Text("Nueva fecha: ${it.textoCita()}" + (s.propuestaFin?.let { f -> " → ${f.textoCita()}" } ?: "")) }
                        s.cotizacionObservaciones?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        s.cotizacionVigencia?.let { Text((if (s.cotizacionVencida(hoy)) "Venció el " else "Válida hasta ") + it.format(FECHA_CORTA), style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave) }
                    }
                }
                s.motivo?.let { Text("Motivo: $it", color = Pc.TintaSuave) }
                if (s.historial.size > 1) {
                    Text("Historial", style = MaterialTheme.typography.titleSmall)
                    s.historial.forEach { h ->
                        Text("• ${h.estado.etiqueta} (${if (h.actor == "PRESTADOR") s.prestadorNombre else "tú"})" + (h.nota?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                    }
                }
                if (s.estado == EstadoSolicitudPres.COTIZADA && !s.cotizacionVencida(hoy)) {
                    BotonPrincipal("Aceptar propuesta", onClick = onConfirmar, modifier = Modifier.testTag("confirmar_cotizacion"))
                }
                if (s.estado == EstadoSolicitudPres.COMPLETADA) {
                    BotonPrincipal(if (yaCalificada) "Editar mi reseña" else "Calificar a ${s.prestadorNombre}", onClick = onCalificar, modifier = Modifier.testTag("calificar"))
                }
                if (s.estado.abierta) {
                    TextButton(onClick = onDejarDeCompartir) { Text("Dejar de compartir mis datos", color = Pc.TintaSuave) }
                }
                Text("El pago se acuerda directamente con el prestador.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
        dismissButton = if (s.estado.abierta) {
            { TextButton(onClick = onCancelar, modifier = Modifier.testTag("cancelar_cotizacion")) { Text("Cancelar solicitud", color = MaterialTheme.colorScheme.error) } }
        } else {
            null
        },
    )
}
