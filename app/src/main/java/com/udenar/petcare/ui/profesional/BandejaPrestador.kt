package com.udenar.petcare.ui.profesional

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.udenar.petcare.R
import com.udenar.petcare.domain.mascotas.FichaMascota
import com.udenar.petcare.domain.profesionales.Contacto
import com.udenar.petcare.domain.profesionales.DetalleSolicitudPres
import com.udenar.petcare.domain.profesionales.EstadoSolicitudPres
import com.udenar.petcare.domain.profesionales.Precios
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.Propuesta
import com.udenar.petcare.domain.profesionales.RepositorioCotizaciones
import com.udenar.petcare.domain.profesionales.SolicitudPrestador
import com.udenar.petcare.ui.componentes.BotonCircular
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.BotonSecundario
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.Chip
import com.udenar.petcare.ui.componentes.EditorHoras
import com.udenar.petcare.ui.componentes.EstadoVacio
import com.udenar.petcare.ui.componentes.Seccion
import com.udenar.petcare.ui.componentes.SelectorFecha
import com.udenar.petcare.ui.componentes.TituloSeccion
import com.udenar.petcare.ui.servicios.FilaSolicitudPres
import com.udenar.petcare.ui.servicios.colores
import com.udenar.petcare.ui.servicios.textoCita
import com.udenar.petcare.ui.theme.Pc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@Serializable data class RutaSolicitudPres(val id: String)

@HiltViewModel
class BandejaPresViewModel @Inject constructor(
    private val cotizaciones: RepositorioCotizaciones,
    tiempoReal: com.udenar.petcare.data.sync.TiempoReal,
    private val reloj: Clock,
) : ViewModel() {
    val solicitudes = MutableStateFlow<List<SolicitudPrestador>>(emptyList())
    val error = MutableStateFlow<String?>(null)
    val cargando = MutableStateFlow(true)
    val hoy: LocalDate get() = LocalDate.now(reloj)

    init {
        cargar()
        viewModelScope.launch { tiempoReal.cambiosVet.collect { cargar() } }
    }

    fun cargar() = viewModelScope.launch {
        try {
            solicitudes.value = cotizaciones.bandeja()
            error.value = null
        } catch (e: ProfesionalException) {
            error.value = e.message
        } finally {
            cargando.value = false
        }
    }
}

@Composable
private fun ListaPres(titulo: String, cuerpo: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(titulo, style = MaterialTheme.typography.headlineLarge)
        cuerpo()
    }
}

/** Pestaña Solicitudes del prestador (RF-35). */
@Composable
fun SolicitudesPresScreen(onAbrir: (String) -> Unit, viewModel: BandejaPresViewModel = hiltViewModel()) {
    val todas by viewModel.solicitudes.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val cargando by viewModel.cargando.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.cargar() }
    ListaPres("Solicitudes") {
        error?.let {
            Text(it, color = Pc.TintaSuave)
            BotonSecundario("Reintentar", onClick = { viewModel.cargar() })
        }
        if (!cargando && error == null && todas.isEmpty()) {
            EstadoVacio(R.drawable.chopper_amigos, "Aún no tienes solicitudes", "Cuando un dueño te pida una cotización, aparecerá aquí para que respondas con tu precio.", fondoBlob = listOf(Pc.SolClaro, Pc.Durazno))
        }
        listOf(
            "Nuevas" to todas.filter { it.estado == EstadoSolicitudPres.PENDIENTE },
            "Cotizadas (esperan al dueño)" to todas.filter { it.estado == EstadoSolicitudPres.COTIZADA },
            "Aceptadas" to todas.filter { it.estado == EstadoSolicitudPres.ACEPTADA }.sortedBy { it.inicio },
            "Anteriores" to todas.filter { !it.estado.abierta }.take(20),
        ).filter { it.second.isNotEmpty() }.forEach { (t, lista) ->
            TituloSeccion("$t (${lista.size})")
            lista.forEach { s -> FilaSolicitudPres(s, "${s.resumen.nombre} (${s.resumen.especie.etiqueta}) · ${s.propietarioNombre.orEmpty()}", onClick = { onAbrir(s.id) }) }
        }
    }
}

private val DIA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-CO"))

/** Agenda: servicios aceptados desde hoy, por día. */
@Composable
fun AgendaPresScreen(onAbrir: (String) -> Unit, viewModel: BandejaPresViewModel = hiltViewModel()) {
    val todas by viewModel.solicitudes.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.cargar() }
    val aceptadas = todas.filter { it.estado == EstadoSolicitudPres.ACEPTADA }.sortedBy { it.inicio }
    ListaPres("Agenda") {
        if (aceptadas.isEmpty()) EstadoVacio(R.drawable.chopper_amigos, "Tu agenda está libre", "Los servicios que acuerdes se ordenarán aquí por día y hora.", fondoBlob = listOf(Pc.SolClaro, Pc.Durazno))
        val (pasadas, proximas) = aceptadas.partition { it.inicio.toLocalDate().isBefore(viewModel.hoy) }
        if (pasadas.isNotEmpty()) {
            TituloSeccion("Por cerrar")
            pasadas.forEach { s -> FilaSolicitudPres(s, s.resumen.nombre, onClick = { onAbrir(s.id) }) }
        }
        proximas.groupBy { it.inicio.toLocalDate() }.forEach { (dia, lista) ->
            TituloSeccion(if (dia == viewModel.hoy) "Hoy" else dia.format(DIA).replaceFirstChar { it.uppercase() })
            lista.forEach { s -> FilaSolicitudPres(s, "${s.resumen.nombre} · ${s.modalidad.etiqueta.lowercase()}", onClick = { onAbrir(s.id) }, modifier = Modifier.testTag("agenda_pres_${s.resumen.nombre}")) }
        }
    }
}

/** Clientes: historial de servicios completados, por dueño. */
@Composable
fun ClientesPresScreen(onAbrir: (String) -> Unit, viewModel: BandejaPresViewModel = hiltViewModel()) {
    val todas by viewModel.solicitudes.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.cargar() }
    val completadas = todas.filter { it.estado == EstadoSolicitudPres.COMPLETADA }
    ListaPres("Clientes") {
        if (completadas.isEmpty()) EstadoVacio(R.drawable.chopper_amigos, "Aún no tienes clientes", "Aquí verás el historial de servicios que hayas completado.", fondoBlob = listOf(Pc.SolClaro, Pc.Durazno))
        completadas.groupBy { it.propietarioNombre ?: "Cliente" }.forEach { (cliente, lista) ->
            TituloSeccion("$cliente · ${lista.size} ${if (lista.size == 1) "servicio" else "servicios"}")
            lista.forEach { s -> FilaSolicitudPres(s, "${s.resumen.nombre} · ${s.servicioNombre}", onClick = { onAbrir(s.id) }) }
        }
    }
}

@HiltViewModel
class DetalleSolicitudPresViewModel @Inject constructor(
    estado: SavedStateHandle,
    private val cotizaciones: RepositorioCotizaciones,
    private val reloj: Clock,
) : ViewModel() {
    private val id = estado.toRoute<RutaSolicitudPres>().id
    val detalle = MutableStateFlow<DetalleSolicitudPres?>(null)
    val error = MutableStateFlow<String?>(null)
    val ocupado = MutableStateFlow(false)
    val hoy: LocalDate get() = LocalDate.now(reloj)
    fun ahora(): LocalDateTime = LocalDateTime.now(reloj)

    init { cargar() }

    fun cargar() = viewModelScope.launch {
        try {
            detalle.value = cotizaciones.detalle(id)
            error.value = null
        } catch (e: ProfesionalException) {
            error.value = e.message
        }
    }

    private fun accion(avisar: (String) -> Unit, ok: String, bloque: suspend () -> Unit) = viewModelScope.launch {
        ocupado.value = true
        try {
            bloque()
            avisar(ok)
        } catch (e: ProfesionalException) {
            avisar(e.message ?: "No se pudo completar")
        } finally {
            ocupado.value = false
            cargar()
        }
    }

    fun cotizar(p: Propuesta, avisar: (String) -> Unit) = accion(avisar, "Propuesta enviada: el dueño debe confirmarla") { cotizaciones.cotizar(id, p) }
    fun aceptar(avisar: (String) -> Unit) = accion(avisar, "Servicio aceptado y agendado") { cotizaciones.aceptar(id) }
    fun rechazar(motivo: String, avisar: (String) -> Unit) = accion(avisar, "Solicitud rechazada") { cotizaciones.rechazar(id, motivo) }
    fun cancelar(motivo: String, avisar: (String) -> Unit) = accion(avisar, "Servicio cancelado") { cotizaciones.cancelar(id, motivo) }
    fun completar(avisar: (String) -> Unit) = accion(avisar, "Servicio completado") { cotizaciones.completar(id) }
}

/** Detalle para el prestador: solo lo que el dueño compartió y el estado permite (RF-36). */
@Composable
fun DetalleSolicitudPresScreen(onVolver: () -> Unit, avisar: (String) -> Unit, viewModel: DetalleSolicitudPresViewModel = hiltViewModel()) {
    val d by viewModel.detalle.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val ocupado by viewModel.ocupado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    var dialogo by remember { mutableStateOf<String?>(null) }
    fun abrir(enlace: String, accion: String) = try {
        contexto.startActivity(Intent(accion, enlace.toUri()))
    } catch (_: ActivityNotFoundException) {
        avisar("No hay una aplicación para abrir esto")
    }
    Column(
        Modifier.fillMaxSize().background(Pc.Crema).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BotonCircular(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onVolver)
        error?.let {
            Text(it, color = Pc.TintaSuave)
            BotonSecundario("Reintentar", onClick = { viewModel.cargar() })
        }
        val det = d ?: return@Column
        val s = det.solicitud
        val r = s.resumen
        Text("${s.servicioNombre} · ${r.nombre}", style = MaterialTheme.typography.headlineMedium)
        Chip(s.estado.etiqueta, fondo = s.estado.colores().first, color = s.estado.colores().second, modifier = Modifier.testTag("estado_solicitud_pres"))
        Seccion("Mascota") {
            Text(listOfNotNull(r.especie.etiqueta, r.raza, r.edad, r.tamano?.lowercase(), r.pesoGramos?.let { "≈ " + FichaMascota.peso(it) }).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            Text("Dueño: ${s.propietarioNombre.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
        }
        Seccion("Lo que pide") {
            Text(s.descripcion, style = MaterialTheme.typography.bodyMedium)
            Text("${s.inicio.textoCita()}" + (s.fin?.let { " → ${it.textoCita()}" } ?: "") + " · ${s.modalidad.etiqueta.lowercase()}", style = MaterialTheme.typography.titleSmall)
            s.precioReferencia?.let { Text("Precio publicado: ${Precios.pesos(it)} (${s.precioModo.etiqueta.lowercase()})", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave) }
            s.mensaje?.let { Text("“$it”", style = MaterialTheme.typography.bodyMedium) }
            com.udenar.petcare.ui.componentes.FotosSolicitud(s.fotos)
        }
        det.salud?.let { sa ->
            Seccion("Salud y comportamiento (compartido)") {
                listOf("Alergias" to sa.alergias, "Condiciones" to sa.condiciones, "Comportamiento" to sa.comportamiento, "Indicaciones" to sa.indicaciones)
                    .filter { !it.second.isNullOrBlank() }
                    .forEach { (k, v) -> Text("$k: $v", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("salud_compartida")) }
            }
        }
        det.contacto?.let { c ->
            Seccion("Contacto del dueño") {
                c.direccion?.let { Text("Dirección: $it", style = MaterialTheme.typography.bodyMedium) }
                c.telefono?.let { tel ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotonSecundario("Llamar", onClick = { abrir(Contacto.enlaceLlamar(tel), Intent.ACTION_DIAL) }, modifier = Modifier.weight(1f).testTag("llamar_dueno"))
                        BotonSecundario("WhatsApp", onClick = { abrir(Contacto.enlaceWhatsapp(tel), Intent.ACTION_VIEW) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (det.contacto == null && s.estado != EstadoSolicitudPres.ACEPTADA && s.estado.abierta) {
            Text("Si el dueño compartió su contacto, lo verás cuando la solicitud quede aceptada.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
        }
        if (s.hayPropuesta) {
            Seccion("Tu propuesta") {
                s.cotizacionPrecio?.let { Text("Precio: ${Precios.pesos(it)}", style = MaterialTheme.typography.titleSmall) }
                s.propuestaInicio?.let { Text("Fecha propuesta: ${it.textoCita()}") }
                s.cotizacionObservaciones?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        s.motivo?.let { Text("Motivo: $it", color = Pc.TintaSuave) }

        when (s.estado) {
            EstadoSolicitudPres.PENDIENTE, EstadoSolicitudPres.COTIZADA -> {
                BotonPrincipal(if (s.estado == EstadoSolicitudPres.COTIZADA) "Cambiar propuesta" else "Enviar cotización", onClick = { dialogo = "cotizar" }, cargando = ocupado, modifier = Modifier.testTag("cotizar_solicitud"))
                if (s.aceptableDirecto) {
                    BotonSecundario("Aceptar tal como la pidió", onClick = { viewModel.aceptar(avisar) }, modifier = Modifier.testTag("aceptar_directo"))
                }
                BotonSecundario("Rechazar", onClick = { dialogo = "rechazar" }, modifier = Modifier.testTag("rechazar_pres"))
                if (s.estado == EstadoSolicitudPres.PENDIENTE) TextButton(onClick = { dialogo = "cancelar" }) { Text("Cancelar", color = MaterialTheme.colorScheme.error) }
            }
            EstadoSolicitudPres.ACEPTADA -> {
                val empezo = !s.inicio.isAfter(viewModel.ahora())
                BotonPrincipal("Marcar como completado", onClick = { viewModel.completar(avisar) }, habilitado = empezo, cargando = ocupado, modifier = Modifier.testTag("completar_pres"))
                if (!empezo) Text("Podrás completarlo cuando llegue la fecha del servicio.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                TextButton(onClick = { dialogo = "cancelar" }) { Text("Cancelar el servicio", color = MaterialTheme.colorScheme.error) }
            }
            else -> Unit
        }
        Text("Sin pagos en PetCare: el valor se acuerda directamente con el dueño.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
    }

    val det = d
    when (dialogo) {
        "cotizar" -> if (det != null) DialogoCotizar(det.solicitud, viewModel.hoy, onEnviar = { p -> dialogo = null; viewModel.cotizar(p, avisar) }, onCerrar = { dialogo = null })
        "rechazar", "cancelar" -> {
            var motivo by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { dialogo = null }, containerColor = Pc.Crema,
                title = { Text(if (dialogo == "rechazar") "¿Rechazar la solicitud?" else "¿Cancelar el servicio?", style = MaterialTheme.typography.headlineSmall) },
                text = { CampoTexto(motivo, { motivo = it.take(300) }, "Motivo (opcional)", lineas = 2) },
                confirmButton = {
                    TextButton(onClick = {
                        val accion = dialogo
                        dialogo = null
                        if (accion == "rechazar") viewModel.rechazar(motivo, avisar) else viewModel.cancelar(motivo, avisar)
                    }, modifier = Modifier.testTag("confirmar_motivo")) { Text(if (dialogo == "rechazar") "Rechazar" else "Confirmar", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { dialogo = null }) { Text("Volver") } },
            )
        }
    }
}

@Composable
private fun DialogoCotizar(s: SolicitudPrestador, hoy: LocalDate, onEnviar: (Propuesta) -> Unit, onCerrar: () -> Unit) {
    var precio by remember { mutableStateOf(s.cotizacionPrecio?.toString() ?: s.precioReferencia?.toString().orEmpty()) }
    var observaciones by remember { mutableStateOf(s.cotizacionObservaciones.orEmpty()) }
    var vigencia by remember { mutableStateOf(s.cotizacionVigencia) }
    var otraFecha by remember { mutableStateOf(s.propuestaInicio != null) }
    var fecha by remember { mutableStateOf((s.propuestaInicio ?: s.inicio).toLocalDate()) }
    var hora by remember { mutableStateOf(listOf((s.propuestaInicio ?: s.inicio).toLocalTime())) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onCerrar, containerColor = Pc.Crema,
        title = { Text("Tu propuesta", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoTexto(precio, { precio = it.filter(Char::isDigit).take(9); error = null }, "Precio en pesos", teclado = KeyboardType.Number, modifier = Modifier.testTag("cotizacion_precio"))
                CampoTexto(observaciones, { observaciones = it.take(500) }, "Observaciones (qué incluye, condiciones…)", lineas = 2)
                SelectorFecha("Válida hasta (opcional)", vigencia, { vigencia = it }, desde = hoy, permitirBorrar = true)
                Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.udenar.petcare.ui.componentes.ChipOpcion("Proponer otra fecha", otraFecha, onClick = { otraFecha = !otraFecha })
                }
                if (otraFecha) {
                    SelectorFecha("Fecha", fecha, { it?.let { f -> fecha = f } }, desde = hoy)
                    EditorHoras(hora, { h -> hora = h.takeLast(1) })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text("El dueño debe confirmar tu propuesta para que quede aceptada.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = precio.toIntOrNull()
                val nueva = if (otraFecha && hora.isNotEmpty()) fecha.atTime(hora.first()) else null
                if (p == null && nueva == null) {
                    error = "Escribe un precio o propone otra fecha"
                } else {
                    val dur = s.fin?.let { java.time.Duration.between(s.inicio, it) }
                    onEnviar(Propuesta(p, observaciones, vigencia, nueva, nueva?.let { n -> dur?.let { n.plus(it) } }))
                }
            }, modifier = Modifier.testTag("enviar_propuesta")) { Text("Enviar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}
