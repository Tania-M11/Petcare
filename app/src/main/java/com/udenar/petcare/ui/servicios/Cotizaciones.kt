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
        descripcion: String, texto: String, salud: SaludCompartida?, contacto: ContactoCompartido?, onListo: () -> Unit,
    ) {
        when {
            inicio.isBefore(LocalDateTime.now(reloj)) -> mensaje.value = "Elige una fecha y hora que no hayan pasado"
            fin != null && fin.isBefore(inicio) -> mensaje.value = "La fecha de fin no puede ser antes del inicio"
            descripcion.isBlank() -> mensaje.value = "Cuéntale qué necesitas"
            else -> {
                enviando.value = true
                viewModelScope.launch {
                    try {
                        cotizaciones.solicitar(mascotaId, prestadorId, categoriaId, servicioId, modalidad, inicio, fin, descripcion, texto, salud, contacto)
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

/** Solicitud de cotización o contratación (RF-34) con lo que el dueño decide compartir (RF-36). */
@Composable
fun SolicitarPrestadorScreen(onVolver: () -> Unit, onEnviada: () -> Unit, avisar: (String) -> Unit, viewModel: SolicitarPrestadorViewModel = hiltViewModel()) {
    val p by viewModel.prestador.collectAsStateWithLifecycle()
    val categorias by viewModel.categorias.collectAsStateWithLifecycle()
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
    var categoria by remember { mutableStateOf<String?>(null) }
    var servicio by remember { mutableStateOf<String?>(null) }
    var modalidad by remember { mutableStateOf<ModalidadPrestador?>(null) }
    var fecha by remember { mutableStateOf(viewModel.hoy.plusDays(1)) }
    var hora by remember { mutableStateOf(listOf(LocalTime.of(9, 0))) }
    var fechaFin by remember { mutableStateOf<LocalDate?>(null) }
    var descripcion by remember { mutableStateOf("") }
    var texto by remember { mutableStateOf("") }
    var compartirSalud by remember { mutableStateOf(false) }
    var alergias by remember { mutableStateOf("") }
    var condiciones by remember { mutableStateOf("") }
    var comportamiento by remember { mutableStateOf("") }
    var indicaciones by remember { mutableStateOf("") }
    var compartirContacto by remember { mutableStateOf(false) }
    var telefono by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    LaunchedEffect(mascotas) { if (mascotaId == null && mascotas.size == 1) mascotaId = mascotas.first().id }
    LaunchedEffect(p) {
        p?.let {
            if (categoria == null) categoria = it.categoriaPrincipal
            if (modalidad == null) modalidad = it.modalidades.minByOrNull { m -> m.ordinal }
        }
    }
    // Al elegir otra mascota se precargan sus datos de salud (se pueden editar antes de compartir)
    LaunchedEffect(mascotaId) {
        mascotas.firstOrNull { it.id == mascotaId }?.let { m ->
            alergias = m.alergias.orEmpty()
            condiciones = m.condiciones.orEmpty()
        }
    }

    Column(
        Modifier.fillMaxSize().background(Pc.Crema).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotonCircular(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onVolver)
            Text("Pedir cotización", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp))
        }
        val pr = p ?: return@Column
        Text("A ${pr.nombre}", style = MaterialTheme.typography.titleMedium)
        if (mascotas.isEmpty()) {
            Text("Primero agrega una mascota propia: solo el dueño puede pedir servicios.", color = Pc.TintaSuave)
            return@Column
        }
        Seccion("¿Para quién?") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                mascotas.forEach { m -> ChipOpcion(m.nombre, mascotaId == m.id, onClick = { mascotaId = m.id }, modifier = Modifier.testTag("cotizar_${m.nombre}")) }
            }
            mascotas.firstOrNull { it.id == mascotaId }?.takeIf { it.especie !in pr.especies }?.let {
                Text("${pr.nombre} no indica que atienda ${it.especie.etiqueta.lowercase()}. Puedes enviarla de todas formas.", style = MaterialTheme.typography.bodySmall, color = Pc.Naranja)
            }
        }
        Seccion("¿Qué necesitas?") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pr.categorias.forEach { cid ->
                    val c = categorias.firstOrNull { it.id == cid }
                    ChipOpcion("${c?.icono.orEmpty()} ${c?.nombre ?: cid}", categoria == cid, onClick = { categoria = cid; servicio = null })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pr.servicios.filter { it.categoriaId == categoria }.forEach { s ->
                    ChipOpcion("${s.nombre} · ${Precios.texto(s)}", servicio == s.id, onClick = { servicio = if (servicio == s.id) null else s.id }, modifier = Modifier.testTag("cotizar_servicio_${s.nombre}"))
                }
            }
            if (pr.modalidades.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pr.modalidades.sortedBy { it.ordinal }.forEach { m -> ChipOpcion(m.etiqueta, modalidad == m, onClick = { modalidad = m }) }
                }
            } else {
                Text(pr.modalidades.firstOrNull()?.etiqueta.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            }
            CampoTexto(descripcion, { descripcion = it.take(1000) }, "Describe lo que necesitas *", lineas = 3, modifier = Modifier.testTag("cotizar_descripcion"))
        }
        Seccion("¿Cuándo?") {
            SelectorFecha(if (categoria == "CUIDADO_TEMPORAL") "Desde" else "Fecha", fecha, { it?.let { f -> fecha = f } }, desde = viewModel.hoy)
            EditorHoras(hora, { h -> hora = h.takeLast(1) })
            if (categoria == "CUIDADO_TEMPORAL") SelectorFecha("Hasta", fechaFin, { fechaFin = it }, desde = fecha, permitirBorrar = true)
            if (fecha in pr.noDisponible) Text("${pr.nombre} marcó ese día como no disponible; quizá te proponga otra fecha.", style = MaterialTheme.typography.bodySmall, color = Pc.Naranja)
        }
        Seccion("¿Qué compartes?") {
            Text("El prestador siempre ve nombre, especie, raza, sexo, edad, tamaño y peso aproximados. Nunca ve la ficha completa.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            FilaCasilla("Datos de salud y comportamiento", "Los ve mientras la solicitud esté abierta", compartirSalud, { compartirSalud = it }, "compartir_salud")
            if (compartirSalud) {
                CampoTexto(alergias, { alergias = it.take(500) }, "Alergias")
                CampoTexto(condiciones, { condiciones = it.take(500) }, "Condiciones relevantes")
                CampoTexto(comportamiento, { comportamiento = it.take(500) }, "Comportamiento (p. ej. tira de la correa, teme a otros perros)")
                CampoTexto(indicaciones, { indicaciones = it.take(500) }, "Indicaciones especiales para el servicio")
            }
            FilaCasilla(
                "Mi teléfono" + if (modalidad == ModalidadPrestador.DOMICILIO) " y dirección" else "",
                "Solo los ve cuando la solicitud esté aceptada", compartirContacto, { compartirContacto = it }, "compartir_contacto",
            )
            if (compartirContacto) {
                CampoTexto(telefono, { telefono = it }, "Teléfono o WhatsApp", teclado = KeyboardType.Phone)
                if (modalidad == ModalidadPrestador.DOMICILIO) CampoTexto(direccion, { direccion = it.take(200) }, "Dirección")
            }
        }
        CampoTexto(texto, { texto = it.take(500) }, "Mensaje adicional (opcional)", lineas = 2)
        BotonPrincipal(
            "Enviar solicitud",
            habilitado = mascotaId != null && categoria != null && modalidad != null && hora.isNotEmpty(),
            cargando = enviando,
            onClick = {
                viewModel.enviar(
                    mascotaId!!, categoria!!, servicio, modalidad!!, fecha.atTime(hora.first()), fechaFin?.atTime(hora.first()),
                    descripcion, texto,
                    if (compartirSalud) SaludCompartida(alergias, condiciones, comportamiento, indicaciones) else null,
                    if (compartirContacto) ContactoCompartido(telefono, direccion.takeIf { modalidad == ModalidadPrestador.DOMICILIO }) else null,
                    onEnviada,
                )
            },
            modifier = Modifier.testTag("enviar_cotizacion"),
        )
        Text("Las tarifas son orientativas y el pago se coordina directamente con el prestador, fuera de PetCare.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
    }
}

@Composable
private fun FilaCasilla(titulo: String, subtitulo: String, marcada: Boolean, onCambio: (Boolean) -> Unit, etiqueta: String) {
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
