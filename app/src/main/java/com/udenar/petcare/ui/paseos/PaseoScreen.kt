package com.udenar.petcare.ui.paseos

import com.udenar.petcare.ui.componentes.Icono3D
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.udenar.petcare.data.paseos.FaseSesion
import com.udenar.petcare.data.paseos.SesionPaseo
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.domain.paseos.RepositorioPaseos
import com.udenar.petcare.domain.paseos.TipoPaseo
import com.udenar.petcare.domain.paseos.textoDistancia
import com.udenar.petcare.domain.paseos.textoDuracion
import com.udenar.petcare.ui.componentes.BotonCircular
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.BotonSecundario
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.ChipOpcion
import com.udenar.petcare.ui.componentes.EditorHoras
import com.udenar.petcare.ui.componentes.Seccion
import com.udenar.petcare.ui.componentes.SelectorFecha
import com.udenar.petcare.ui.navegacion.RutaPaseo
import com.udenar.petcare.ui.theme.Kalnia
import com.udenar.petcare.ui.theme.Pc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class PaseoViewModel @Inject constructor(
    estado: SavedStateHandle,
    mascotasRepo: RepositorioMascotas,
    private val sesion: SesionPaseo,
    private val paseos: RepositorioPaseos,
    private val reloj: Clock,
) : ViewModel() {
    val preseleccion: String? = estado.toRoute<RutaPaseo>().mascotaId
    val mascotas: StateFlow<List<Mascota>> = mascotasRepo.observar().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sesionEstado = sesion.estado
    val hoy: LocalDate get() = LocalDate.now(reloj)
    fun ahora(): Instant = Instant.now(reloj)

    fun iniciar(ids: List<String>) = sesion.iniciar(ids)
    fun pausar() = sesion.pausar()
    fun reanudar() = sesion.reanudar()
    fun cancelar() = sesion.cancelar()

    fun finalizar(tipo: TipoPaseo, onListo: () -> Unit) = viewModelScope.launch {
        sesion.finalizar()?.let { paseos.guardarGps(it, tipo) }
        onListo()
    }

    fun guardarManual(ids: List<String>, tipo: TipoPaseo, fecha: LocalDate, hora: LocalTime, minutos: Int, km: Double?, notas: String, onListo: () -> Unit) =
        viewModelScope.launch {
            paseos.guardarManual(ids, tipo, fecha.atTime(hora).atZone(reloj.zone).toInstant(), minutos * 60, km?.let { (it * 1000).toInt() }, notas)
            onListo()
        }
}

/** Paseo con GPS o registro manual (RF-12). Lo pueden hacer el dueño y los cuidadores. */
@Composable
fun PaseoScreen(onVolver: () -> Unit, avisar: (String) -> Unit, viewModel: PaseoViewModel = hiltViewModel()) {
    val mascotas by viewModel.mascotas.collectAsStateWithLifecycle()
    val s by viewModel.sesionEstado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    var elegidas by remember { mutableStateOf(setOfNotNull(viewModel.preseleccion)) }
    var tipo by remember { mutableStateOf(TipoPaseo.PASEO) }
    var manual by remember { mutableStateOf(false) }
    var confirmarFin by remember { mutableStateOf(false) }
    LaunchedEffect(mascotas) { if (elegidas.isEmpty() && mascotas.size == 1) elegidas = setOf(mascotas.first().id) }

    // El reloj del paseo avanza cada segundo en pantalla
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.fase) {
        while (s.fase == FaseSesion.ACTIVO) {
            ahora = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val permisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            viewModel.iniciar(elegidas.toList())
            com.udenar.petcare.data.paseos.ServicioPaseo.iniciar(contexto)
        } else {
            avisar("Sin permiso de ubicación no se puede trazar la ruta. Puedes registrar el paseo manualmente.")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Pc.Crema)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotonCircular(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onVolver)
            Text(if (s.fase == FaseSesion.INACTIVO) "Nuevo paseo" else "Paseo en curso", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp))
        }

        if (s.fase == FaseSesion.INACTIVO) {
            Seccion("¿Con quién?") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    mascotas.forEach { m ->
                        ChipOpcion(m.nombre, m.id in elegidas, onClick = { elegidas = if (m.id in elegidas) elegidas - m.id else elegidas + m.id }, modifier = Modifier.testTag("paseo_${m.nombre}"))
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TipoPaseo.entries.forEach { t -> ChipOpcion(t.etiqueta, tipo == t, onClick = { tipo = t }, icono = t.icono) }
                }
            }
            BotonPrincipal("Iniciar con GPS", habilitado = elegidas.isNotEmpty(), onClick = {
                val pedir = buildList {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permisos.launch(pedir.toTypedArray())
            })
            BotonSecundario("Registrar paseo sin GPS", onClick = { manual = true }, modifier = Modifier.testTag("paseo_manual"))
            if (elegidas.isEmpty()) Text("Elige al menos una mascota.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
        } else {
            MapaPaseo(
                ruta = s.ruta, posicion = s.posicion, seguir = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(MaterialTheme.shapes.large),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Estadistica(textoDistancia(s.distanciaM.toInt()), "distancia")
                Estadistica(textoDuracion(s.duracionS(Instant.ofEpochMilli(ahora)).toInt()), "tiempo")
            }
            if (s.ruta.isEmpty()) Text("Buscando señal de GPS…", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            if (s.fase == FaseSesion.ACTIVO) BotonSecundario("Pausar", onClick = viewModel::pausar)
            else BotonSecundario("Reanudar", onClick = viewModel::reanudar)
            BotonPrincipal("Finalizar", onClick = { confirmarFin = true })
            Text("© OpenFreeMap © OpenMapTiles · Datos de © colaboradores de OpenStreetMap", style = MaterialTheme.typography.labelSmall, color = Pc.TintaSuave)
        }
    }

    if (confirmarFin) {
        AlertDialog(
            onDismissRequest = { confirmarFin = false },
            containerColor = Pc.Crema,
            title = { Text("¿Terminar el paseo?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Se guardará en el historial de cada mascota con su ruta.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarFin = false
                    com.udenar.petcare.data.paseos.ServicioPaseo.detener(contexto)
                    viewModel.finalizar(tipo) {
                        avisar("Paseo guardado")
                        onVolver()
                    }
                }) { Text("Guardar paseo") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmarFin = false
                    com.udenar.petcare.data.paseos.ServicioPaseo.detener(contexto)
                    viewModel.cancelar()
                    onVolver()
                }) { Text("Descartar", color = MaterialTheme.colorScheme.error) }
            },
        )
    }

    if (manual) {
        var fecha by remember { mutableStateOf(viewModel.hoy) }
        var hora by remember { mutableStateOf(listOf(LocalTime.now().withSecond(0).withNano(0))) }
        var minutos by remember { mutableStateOf("") }
        var km by remember { mutableStateOf("") }
        var notas by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { manual = false },
            containerColor = Pc.Crema,
            title = { Text("Registrar ${tipo.etiqueta.lowercase()}", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(Modifier.imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectorFecha("Fecha", fecha, { it?.let { f -> fecha = f } }, hasta = viewModel.hoy)
                    EditorHoras(hora, { h -> hora = h.takeLast(1) })
                    CampoTexto(minutos, { minutos = it.filter(Char::isDigit).take(3); error = null }, "Duración (minutos)", teclado = KeyboardType.Number, error = error)
                    CampoTexto(km, { km = it }, "Distancia en km (opcional)", teclado = KeyboardType.Decimal)
                    CampoTexto(notas, { notas = it }, "Notas (opcional)")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val m = minutos.toIntOrNull()
                    if (m == null || m <= 0 || hora.isEmpty()) {
                        error = "Escribe cuántos minutos duró"
                    } else {
                        viewModel.guardarManual(elegidas.toList(), tipo, fecha, hora.first(), m, km.replace(',', '.').toDoubleOrNull(), notas) {
                            avisar("Paseo registrado")
                            onVolver()
                        }
                        manual = false
                    }
                }, modifier = Modifier.testTag("guardar_paseo")) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { manual = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Estadistica(valor: String, etiqueta: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, fontFamily = Kalnia, fontSize = 30.sp, color = Pc.Tinta)
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = Pc.TintaSuave)
    }
}

/** Detalle de un paseo con su recorrido en el mapa. */
@Composable
fun DetallePaseoScreen(paseo: com.udenar.petcare.domain.paseos.Paseo?, onVolver: () -> Unit, onEliminar: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Pc.Crema)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotonCircular(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onVolver)
            Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                paseo?.let { Icono3D(it.tipo.icono, tam = 32.dp) }
                Text(paseo?.tipo?.etiqueta ?: "Paseo", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 8.dp))
            }
        }
        val p = paseo ?: return@Column
        if (p.ruta.isNotEmpty()) {
            MapaPaseo(
                ruta = p.ruta, posicion = null, seguir = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .clip(MaterialTheme.shapes.large)
                    .testTag("mapa_paseo"),
            )
        } else {
            Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Pc.Blanco).padding(20.dp)) {
                Text("Registrado sin GPS: no hay recorrido para mostrar.", style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            p.distanciaM?.let { Estadistica(textoDistancia(it), "distancia") }
            Estadistica(textoDuracion(p.duracionS), "tiempo")
        }
        val zona = java.time.ZoneId.systemDefault()
        Text(
            listOfNotNull(
                p.inicio.atZone(zona).toLocalDate().toString(),
                p.registradoPor?.nombre?.let { "por ${it.substringBefore(' ')}" },
                p.notas,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave,
        )
        if (p.ruta.isNotEmpty()) Text("© OpenFreeMap © OpenMapTiles · Datos de © colaboradores de OpenStreetMap", style = MaterialTheme.typography.labelSmall, color = Pc.TintaSuave)
        TextButton(onClick = onEliminar, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Eliminar paseo", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@HiltViewModel
class DetallePaseoViewModel @Inject constructor(estado: SavedStateHandle, private val paseos: RepositorioPaseos) : ViewModel() {
    private val id = estado.toRoute<com.udenar.petcare.ui.navegacion.RutaDetallePaseo>().id
    val paseo = kotlinx.coroutines.flow.MutableStateFlow<com.udenar.petcare.domain.paseos.Paseo?>(null)

    init {
        viewModelScope.launch { paseo.value = paseos.obtener(id) }
    }

    fun eliminar(onListo: () -> Unit) = viewModelScope.launch {
        paseos.eliminar(id)
        onListo()
    }
}
