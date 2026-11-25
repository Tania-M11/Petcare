package com.udenar.petcare.ui.profesional

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.udenar.petcare.R
import com.udenar.petcare.domain.profesionales.Contacto
import com.udenar.petcare.domain.profesionales.EstadoSolicitudVet
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.RepositorioAtencionVet
import com.udenar.petcare.domain.profesionales.SolicitudVet
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.BotonSecundario
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.EditorHoras
import com.udenar.petcare.ui.componentes.EstadoVacio
import com.udenar.petcare.ui.componentes.SelectorFecha
import com.udenar.petcare.ui.componentes.TituloSeccion
import com.udenar.petcare.ui.servicios.FilaSolicitud
import com.udenar.petcare.ui.servicios.textoCita
import com.udenar.petcare.ui.theme.Pc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Bandeja del veterinario: la comparten las pestañas Solicitudes y Agenda. */
@HiltViewModel
class BandejaVetViewModel @Inject constructor(
    private val atencion: RepositorioAtencionVet,
    tiempoReal: com.udenar.petcare.data.sync.TiempoReal,
    private val reloj: Clock,
) : ViewModel() {
    val solicitudes = MutableStateFlow<List<SolicitudVet>>(emptyList())
    val error = MutableStateFlow<String?>(null)
    val cargando = MutableStateFlow(true)
    val hoy: LocalDate get() = LocalDate.now(reloj)
    fun ahora(): LocalDateTime = LocalDateTime.now(reloj)

    init {
        cargar()
        viewModelScope.launch { tiempoReal.cambiosVet.collect { cargar() } }
    }

    fun cargar() = viewModelScope.launch {
        try {
            solicitudes.value = atencion.bandeja()
            error.value = null
        } catch (e: ProfesionalException) {
            error.value = e.message
        } finally {
            cargando.value = false
        }
    }

    private fun accion(avisar: (String) -> Unit, ok: String, bloque: suspend () -> Unit) = viewModelScope.launch {
        try {
            bloque()
            avisar(ok)
        } catch (e: ProfesionalException) {
            avisar(e.message ?: "No se pudo completar")
        }
        cargar()
    }

    fun aceptar(s: SolicitudVet, fecha: LocalDateTime, avisar: (String) -> Unit) =
        accion(avisar, "Cita confirmada. ${s.mascotaNombre} ya es tu paciente.") { atencion.resolver(s.id, true, fecha.takeIf { it != s.fechaPreferida }) }

    fun rechazar(s: SolicitudVet, motivo: String, avisar: (String) -> Unit) = accion(avisar, "Solicitud rechazada") { atencion.resolver(s.id, false, motivo = motivo) }

    fun completar(s: SolicitudVet, avisar: (String) -> Unit) = accion(avisar, "Cita completada") { atencion.completar(s.id) }
}

/** Pestaña Solicitudes: Nuevas · Aceptadas · Anteriores. */
@Composable
fun SolicitudesVetScreen(avisar: (String) -> Unit, onFicha: (String) -> Unit, viewModel: BandejaVetViewModel = hiltViewModel()) {
    val todas by viewModel.solicitudes.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val cargando by viewModel.cargando.collectAsStateWithLifecycle()
    var abierta by remember { mutableStateOf<SolicitudVet?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Solicitudes", style = MaterialTheme.typography.headlineLarge)
        error?.let {
            Text(it, color = Pc.TintaSuave)
            BotonSecundario("Reintentar", onClick = { viewModel.cargar() })
        }
        if (!cargando && error == null && todas.isEmpty()) {
            EstadoVacio(R.drawable.gato_doctor, "Aún no tienes solicitudes", "Cuando un dueño te pida una cita, aparecerá aquí para que la aceptes o la rechaces.", fondoBlob = listOf(Pc.CieloClaro, Pc.LavandaClara))
        }
        listOf(
            "Nuevas" to todas.filter { it.estado == EstadoSolicitudVet.PENDIENTE },
            "Aceptadas" to todas.filter { it.estado == EstadoSolicitudVet.ACEPTADA }.sortedBy { it.fecha },
            "Anteriores" to todas.filter { !it.estado.activa }.take(20),
        ).filter { it.second.isNotEmpty() }.forEach { (titulo, lista) ->
            TituloSeccion("$titulo (${lista.size})")
            lista.forEach { s -> FilaSolicitud(s, "${s.mascotaNombre} (${s.mascotaEspecie.etiqueta}) · ${s.propietarioNombre.orEmpty()}", onClick = { abierta = s }) }
        }
    }
    abierta?.let { s -> DialogoSolicitudVet(s, viewModel, onCerrar = { abierta = null }, onFicha = onFicha, avisar = avisar) }
}

private val DIA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-CO"))

/** Pestaña Agenda: citas aceptadas desde hoy, agrupadas por día. */
@Composable
fun AgendaVetScreen(avisar: (String) -> Unit, onFicha: (String) -> Unit, viewModel: BandejaVetViewModel = hiltViewModel()) {
    val todas by viewModel.solicitudes.collectAsStateWithLifecycle()
    var abierta by remember { mutableStateOf<SolicitudVet?>(null) }
    val citas = todas.filter { it.estado == EstadoSolicitudVet.ACEPTADA && !it.fecha.toLocalDate().isBefore(viewModel.hoy) }.sortedBy { it.fecha }
    val atrasadas = todas.filter { it.estado == EstadoSolicitudVet.ACEPTADA && it.fecha.toLocalDate().isBefore(viewModel.hoy) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Agenda", style = MaterialTheme.typography.headlineLarge)
        if (citas.isEmpty() && atrasadas.isEmpty()) {
            EstadoVacio(R.drawable.gato_doctor, "Tu agenda está libre", "Las citas que aceptes se ordenarán aquí por día y hora.", fondoBlob = listOf(Pc.CieloClaro, Pc.LavandaClara))
        }
        if (atrasadas.isNotEmpty()) {
            TituloSeccion("Por cerrar")
            Text("Citas pasadas: márcalas como completadas.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            atrasadas.forEach { s -> FilaSolicitud(s, s.mascotaNombre, onClick = { abierta = s }) }
        }
        citas.groupBy { it.fecha.toLocalDate() }.forEach { (dia, lista) ->
            TituloSeccion(if (dia == viewModel.hoy) "Hoy" else dia.format(DIA).replaceFirstChar { it.uppercase() })
            lista.forEach { s -> FilaSolicitud(s, "${s.mascotaNombre} · ${s.modalidad.etiqueta.lowercase()}", onClick = { abierta = s }, modifier = Modifier.testTag("cita_${s.mascotaNombre}")) }
        }
    }
    abierta?.let { s -> DialogoSolicitudVet(s, viewModel, onCerrar = { abierta = null }, onFicha = onFicha, avisar = avisar) }
}

/** Detalle con las acciones según el estado (RF-25). */
@Composable
private fun DialogoSolicitudVet(s: SolicitudVet, viewModel: BandejaVetViewModel, onCerrar: () -> Unit, onFicha: (String) -> Unit, avisar: (String) -> Unit) {
    val contexto = LocalContext.current
    var paso by remember { mutableStateOf("ver") }
    var fecha by remember { mutableStateOf(s.fechaPreferida.toLocalDate()) }
    var hora by remember { mutableStateOf(listOf(s.fechaPreferida.toLocalTime())) }
    var motivo by remember { mutableStateOf("") }
    fun abrir(enlace: String, accion: String) = try {
        contexto.startActivity(Intent(accion, enlace.toUri()))
    } catch (_: ActivityNotFoundException) {
        avisar("No hay una aplicación para abrir esto")
    }
    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = Pc.Crema,
        title = { Text("${s.servicio.etiqueta} · ${s.mascotaNombre}", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (paso) {
                    "aceptar" -> {
                        Text("Confirma la hora o propón otra:")
                        SelectorFecha("Fecha", fecha, { it?.let { f -> fecha = f } }, desde = viewModel.hoy)
                        EditorHoras(hora, { h -> hora = h.takeLast(1) })
                    }
                    "rechazar" -> CampoTexto(motivo, { motivo = it.take(300) }, "Motivo (opcional)", lineas = 2)
                    else -> {
                        Text("${s.mascotaEspecie.etiqueta} · dueño: ${s.propietarioNombre.orEmpty()}")
                        Text((if (s.estado == EstadoSolicitudVet.PENDIENTE) "Pide: " else "Cita: ") + s.fecha.textoCita(), style = MaterialTheme.typography.titleSmall)
                        Text(s.modalidad.etiqueta, color = Pc.TintaSuave)
                        s.mensaje?.let { Text("“$it”", style = MaterialTheme.typography.bodyMedium) }
                        com.udenar.petcare.ui.componentes.FotosSolicitud(s.fotos)
                        s.motivoRechazo?.let { Text("Motivo: $it", color = Pc.TintaSuave) }
                        Text("Estado: ${s.estado.etiqueta}", color = Pc.TintaSuave)
                        s.propietarioTelefono?.let { tel ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BotonSecundario("Llamar", onClick = { abrir(Contacto.enlaceLlamar(tel), Intent.ACTION_DIAL) }, modifier = Modifier.weight(1f))
                                BotonSecundario("WhatsApp", onClick = { abrir(Contacto.enlaceWhatsapp(tel), Intent.ACTION_VIEW) }, modifier = Modifier.weight(1f))
                            }
                        }
                        when (s.estado) {
                            EstadoSolicitudVet.PENDIENTE -> {
                                BotonPrincipal("Aceptar", onClick = { paso = "aceptar" }, modifier = Modifier.testTag("aceptar_solicitud"))
                                BotonSecundario("Rechazar", onClick = { paso = "rechazar" }, modifier = Modifier.testTag("rechazar_solicitud"))
                            }
                            EstadoSolicitudVet.ACEPTADA -> {
                                BotonPrincipal("Ver ficha de ${s.mascotaNombre}", onClick = { onCerrar(); onFicha(s.mascotaId) }, modifier = Modifier.testTag("ver_ficha"))
                                BotonSecundario("Marcar como completada", onClick = { onCerrar(); viewModel.completar(s, avisar) }, modifier = Modifier.testTag("completar_solicitud"))
                            }
                            EstadoSolicitudVet.COMPLETADA -> BotonSecundario("Ver ficha de ${s.mascotaNombre}", onClick = { onCerrar(); onFicha(s.mascotaId) })
                            else -> Unit
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (paso) {
                "aceptar" -> TextButton(onClick = {
                    val elegida = fecha.atTime(hora.firstOrNull() ?: s.fechaPreferida.toLocalTime())
                    if (elegida.isBefore(viewModel.ahora())) {
                        avisar("Elige una hora que no haya pasado")
                    } else {
                        onCerrar()
                        viewModel.aceptar(s, elegida, avisar)
                    }
                }, modifier = Modifier.testTag("confirmar_aceptar")) { Text("Confirmar cita") }
                "rechazar" -> TextButton(onClick = { onCerrar(); viewModel.rechazar(s, motivo, avisar) }, modifier = Modifier.testTag("confirmar_rechazo")) { Text("Rechazar") }
                else -> TextButton(onClick = onCerrar) { Text("Cerrar") }
            }
        },
        dismissButton = if (paso != "ver") ({ TextButton(onClick = { paso = "ver" }) { Text("Atrás") } }) else null,
    )
}
