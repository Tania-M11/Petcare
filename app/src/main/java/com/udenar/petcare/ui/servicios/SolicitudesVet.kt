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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.domain.profesionales.EstadoSolicitudVet
import com.udenar.petcare.domain.profesionales.Modalidad
import com.udenar.petcare.domain.profesionales.PerfilVeterinario
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.RepositorioAtencionVet
import com.udenar.petcare.domain.profesionales.RepositorioVeterinarios
import com.udenar.petcare.domain.profesionales.ServicioVet
import com.udenar.petcare.domain.profesionales.SolicitudVet
import com.udenar.petcare.ui.componentes.BotonCircular
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.Chip
import com.udenar.petcare.ui.componentes.ChipOpcion
import com.udenar.petcare.ui.componentes.EditorHoras
import com.udenar.petcare.ui.componentes.Seccion
import com.udenar.petcare.ui.componentes.SelectorFecha
import com.udenar.petcare.ui.componentes.TituloSeccion
import com.udenar.petcare.ui.navegacion.RutaSolicitarVet
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

private val FECHA_HORA = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.forLanguageTag("es-CO"))

fun LocalDateTime.textoCita(): String = format(FECHA_HORA).replaceFirstChar { it.uppercase() }.replace(' ', ' ').replace(' ', ' ')

/** Colores del estado de una solicitud (dueño y veterinario). */
fun EstadoSolicitudVet.colores(): Pair<Color, Color> = when (this) {
    EstadoSolicitudVet.PENDIENTE -> Pc.SolClaro to Pc.Tinta
    EstadoSolicitudVet.ACEPTADA -> Pc.MentaClara to Pc.Tinta
    EstadoSolicitudVet.COMPLETADA -> Pc.CieloClaro to Pc.CieloOscuro
    EstadoSolicitudVet.RECHAZADA, EstadoSolicitudVet.CANCELADA -> Pc.Crema to Pc.TintaSuave
}

@HiltViewModel
class SolicitarVetViewModel @Inject constructor(
    estado: SavedStateHandle,
    private val veterinarios: RepositorioVeterinarios,
    private val atencion: RepositorioAtencionVet,
    mascotasRepo: RepositorioMascotas,
    private val reloj: Clock,
) : ViewModel() {
    private val vetId = estado.toRoute<RutaSolicitarVet>().id
    val vet = MutableStateFlow<PerfilVeterinario?>(null)
    val mensaje = MutableStateFlow<String?>(null)
    val enviando = MutableStateFlow(false)

    /** Solo las mascotas propias: el cuidador no contrata servicios (sección 5). */
    val mascotas: StateFlow<List<Mascota>> = mascotasRepo.observar().map { l -> l.filter { it.esPropietario } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val hoy: LocalDate get() = LocalDate.now(reloj)

    init {
        viewModelScope.launch {
            try {
                vet.value = veterinarios.obtener(vetId) ?: run { mensaje.value = "Este veterinario ya no está disponible."; null }
            } catch (e: ProfesionalException) {
                mensaje.value = e.message
            }
        }
    }

    fun enviar(mascotaId: String, servicio: ServicioVet, modalidad: Modalidad, fecha: LocalDateTime, texto: String, onListo: () -> Unit) {
        if (fecha.isBefore(LocalDateTime.now(reloj))) {
            mensaje.value = "Elige una fecha y hora que no hayan pasado"
            return
        }
        enviando.value = true
        viewModelScope.launch {
            try {
                atencion.solicitar(mascotaId, vetId, servicio, modalidad, fecha, texto)
                onListo()
            } catch (e: ProfesionalException) {
                mensaje.value = e.message
            } finally {
                enviando.value = false
            }
        }
    }
}

/** Solicitud de servicio a un veterinario (RF-25): sin pagos ni chat. */
@Composable
fun SolicitarVetScreen(onVolver: () -> Unit, onEnviada: () -> Unit, avisar: (String) -> Unit, viewModel: SolicitarVetViewModel = hiltViewModel()) {
    val vet by viewModel.vet.collectAsStateWithLifecycle()
    val mascotas by viewModel.mascotas.collectAsStateWithLifecycle()
    val mensaje by viewModel.mensaje.collectAsStateWithLifecycle()
    val enviando by viewModel.enviando.collectAsStateWithLifecycle()
    LaunchedEffect(mensaje) {
        mensaje?.let {
            avisar(it)
            viewModel.mensaje.value = null
        }
    }
    var mascotaId by remember { mutableStateOf<String?>(null) }
    var servicio by remember { mutableStateOf<ServicioVet?>(null) }
    var modalidad by remember { mutableStateOf(Modalidad.CONSULTORIO) }
    var fecha by remember { mutableStateOf(viewModel.hoy.plusDays(1)) }
    var hora by remember { mutableStateOf(listOf(LocalTime.of(9, 0))) }
    var texto by remember { mutableStateOf("") }
    LaunchedEffect(mascotas) { if (mascotaId == null && mascotas.size == 1) mascotaId = mascotas.first().id }

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
            Text("Pedir una cita", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp))
        }
        val v = vet ?: return@Column
        Text("Con ${v.nombre}${if (v.verificacion.verificado) " ✓" else ""}", style = MaterialTheme.typography.titleMedium)
        if (mascotas.isEmpty()) {
            Text("Primero agrega una mascota propia: solo el dueño puede pedir servicios.", color = Pc.TintaSuave)
            return@Column
        }
        Seccion("¿Para quién?") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                mascotas.forEach { m -> ChipOpcion(m.nombre, mascotaId == m.id, onClick = { mascotaId = m.id }, modifier = Modifier.testTag("pedir_${m.nombre}")) }
            }
            val elegida = mascotas.firstOrNull { it.id == mascotaId }
            if (elegida != null && elegida.especie !in v.especies) {
                // Se advierte sin bloquear (RF-25)
                Text(
                    "${v.nombre} no indica que atienda ${elegida.especie.etiqueta.lowercase()}. Puedes enviar la solicitud de todas formas.",
                    style = MaterialTheme.typography.bodySmall, color = Pc.Naranja, modifier = Modifier.testTag("aviso_especie"),
                )
            }
        }
        Seccion("Servicio") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (v.servicios.takeIf { it.isNotEmpty() } ?: ServicioVet.entries.toSet()).sortedBy { it.ordinal }.forEach { s ->
                    ChipOpcion(s.etiqueta, servicio == s, onClick = { servicio = s }, modifier = Modifier.testTag("servicio_${s.name}"))
                }
            }
            if (v.domicilio) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Modalidad.entries.forEach { m -> ChipOpcion(m.etiqueta, modalidad == m, onClick = { modalidad = m }) }
                }
            }
        }
        Seccion("¿Cuándo te gustaría?") {
            SelectorFecha("Fecha", fecha, { it?.let { f -> fecha = f } }, desde = viewModel.hoy)
            EditorHoras(hora, { h -> hora = h.takeLast(1) })
            Text("El veterinario confirma o propone otra hora.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
        }
        CampoTexto(texto, { texto = it.take(500) }, "Mensaje (opcional)", lineas = 3)
        BotonPrincipal(
            "Enviar solicitud",
            habilitado = mascotaId != null && servicio != null && hora.isNotEmpty(),
            cargando = enviando,
            onClick = { viewModel.enviar(mascotaId!!, servicio!!, modalidad, fecha.atTime(hora.first()), texto, onEnviada) },
            modifier = Modifier.testTag("enviar_solicitud"),
        )
        Text("PetCare no cobra ni procesa pagos: el valor se acuerda directamente con el veterinario.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
    }
}

@HiltViewModel
class MisSolicitudesViewModel @Inject constructor(
    private val atencion: RepositorioAtencionVet,
    tiempoReal: com.udenar.petcare.data.sync.TiempoReal,
    private val cotizaciones: com.udenar.petcare.domain.profesionales.RepositorioCotizaciones,
    private val reloj: Clock,
) : ViewModel() {
    val solicitudes = MutableStateFlow<List<SolicitudVet>>(emptyList())
    val cotizacionesPres = MutableStateFlow<List<com.udenar.petcare.domain.profesionales.SolicitudPrestador>>(emptyList())
    val hoy: LocalDate get() = LocalDate.now(reloj)

    private fun accionPres(avisar: (String) -> Unit, ok: String, bloque: suspend () -> Unit) = viewModelScope.launch {
        try {
            bloque()
            avisar(ok)
        } catch (e: ProfesionalException) {
            avisar(e.message ?: "No se pudo completar")
        }
        cargar()
    }

    fun confirmarPres(id: String, avisar: (String) -> Unit) = accionPres(avisar, "¡Listo! El servicio quedó acordado y te lo recordaremos") { cotizaciones.confirmar(id) }
    fun cancelarPres(id: String, avisar: (String) -> Unit) = accionPres(avisar, "Solicitud cancelada") { cotizaciones.cancelar(id, null) }
    fun dejarDeCompartir(id: String, avisar: (String) -> Unit) = accionPres(avisar, "El prestador ya no ve tus datos compartidos") { cotizaciones.dejarDeCompartir(id) }

    init {
        cargar()
        viewModelScope.launch { tiempoReal.cambiosVet.collect { cargar() } }
    }

    fun cargar() = viewModelScope.launch {
        runCatching { atencion.misSolicitudes() }.onSuccess { solicitudes.value = it }
        runCatching { cotizaciones.misSolicitudes() }.onSuccess { cotizacionesPres.value = it }
    }

    fun cancelar(id: String, avisar: (String) -> Unit) = viewModelScope.launch {
        try {
            atencion.cancelar(id)
            avisar("Solicitud cancelada")
            cargar()
        } catch (e: ProfesionalException) {
            avisar(e.message ?: "No se pudo cancelar")
        }
    }
}

/** "Mis solicitudes" arriba en Servicios cuando hay alguna activa o reciente. */
@Composable
fun MisSolicitudes(avisar: (String) -> Unit, viewModel: MisSolicitudesViewModel = hiltViewModel()) {
    val todas by viewModel.solicitudes.collectAsStateWithLifecycle()
    val pres by viewModel.cotizacionesPres.collectAsStateWithLifecycle()
    var abierta by remember { mutableStateOf<SolicitudVet?>(null) }
    var abiertaPres by remember { mutableStateOf<com.udenar.petcare.domain.profesionales.SolicitudPrestador?>(null) }
    // Al volver a Servicios (p. ej. después de enviar una) se refresca
    LaunchedEffect(Unit) { viewModel.cargar() }
    val visibles = todas.filter { it.estado.activa }
    val visiblesPres = pres.filter { it.estado.abierta }
    val recientes = if (visibles.isEmpty() && visiblesPres.isEmpty()) todas.take(1) to pres.take(1) else visibles to visiblesPres
    if (recientes.first.isEmpty() && recientes.second.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TituloSeccion("Mis solicitudes")
        recientes.second.forEach { s -> FilaSolicitudPres(s, titulo = "${s.resumen.nombre} · ${s.prestadorNombre}", onClick = { abiertaPres = s }) }
        recientes.first.forEach { s -> FilaSolicitud(s, titulo = "${s.mascotaNombre} · ${s.veterinarioNombre}", onClick = { abierta = s }) }
    }
    abiertaPres?.let { s ->
        DialogoSolicitudPresDueno(
            s, viewModel.hoy,
            onCerrar = { abiertaPres = null },
            onConfirmar = { abiertaPres = null; viewModel.confirmarPres(s.id, avisar) },
            onCancelar = { abiertaPres = null; viewModel.cancelarPres(s.id, avisar) },
            onDejarDeCompartir = { abiertaPres = null; viewModel.dejarDeCompartir(s.id, avisar) },
        )
    }
    abierta?.let { s ->
        AlertDialog(
            onDismissRequest = { abierta = null },
            containerColor = Pc.Crema,
            title = { Text("${s.servicio.etiqueta} para ${s.mascotaNombre}", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Con ${s.veterinarioNombre}")
                    Text(
                        (if (s.estado == EstadoSolicitudVet.ACEPTADA) "Cita confirmada: " else "Pediste: ") + s.fecha.textoCita(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(s.modalidad.etiqueta, color = Pc.TintaSuave)
                    s.motivoRechazo?.let { Text("Motivo: $it", color = Pc.TintaSuave) }
                    Chip(s.estado.etiqueta, fondo = s.estado.colores().first, color = s.estado.colores().second)
                }
            },
            confirmButton = { TextButton(onClick = { abierta = null }) { Text("Cerrar") } },
            dismissButton = if (s.estado.activa) {
                {
                    TextButton(onClick = {
                        abierta = null
                        viewModel.cancelar(s.id, avisar)
                    }, modifier = Modifier.testTag("cancelar_solicitud")) { Text("Cancelar solicitud", color = MaterialTheme.colorScheme.error) }
                }
            } else {
                null
            },
        )
    }
}

/** Fila de una solicitud con su estado (se usa en ambos modos). */
@Composable
fun FilaSolicitud(s: SolicitudVet, titulo: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Pc.Blanco)
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag("solicitud_${s.mascotaNombre}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall)
            Text("${s.servicio.etiqueta} · ${s.fecha.textoCita()}", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
        }
        Chip(s.estado.etiqueta, fondo = s.estado.colores().first, color = s.estado.colores().second)
    }
}
