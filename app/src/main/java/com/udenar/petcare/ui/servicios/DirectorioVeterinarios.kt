package com.udenar.petcare.ui.servicios

import com.udenar.petcare.ui.componentes.Simbolo
import com.udenar.petcare.domain.modelo.Icono
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.udenar.petcare.R
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.paseos.Coordenada
import com.udenar.petcare.domain.profesionales.Contacto
import com.udenar.petcare.domain.profesionales.FiltroVeterinarios
import com.udenar.petcare.domain.profesionales.PerfilVeterinario
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.RepositorioVeterinarios
import com.udenar.petcare.domain.profesionales.ServicioVet
import com.udenar.petcare.ui.componentes.BotonCircular
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.BotonSecundario
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.Chip
import com.udenar.petcare.ui.componentes.ChipOpcion
import com.udenar.petcare.ui.componentes.EstadoVacio
import com.udenar.petcare.ui.componentes.Seccion
import com.udenar.petcare.ui.componentes.TarjetaIlustrada
import com.udenar.petcare.ui.mapas.ATRIBUCION_MAPA
import com.udenar.petcare.ui.mapas.MapaMarcadores
import com.udenar.petcare.ui.mapas.PuntoMapa
import com.udenar.petcare.ui.mapas.ubicacionActual
import com.udenar.petcare.ui.navegacion.RutaDetalleVet
import com.udenar.petcare.ui.theme.Pc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class EstadoDirectorio(
    val filtro: FiltroVeterinarios = FiltroVeterinarios(),
    val ubicacion: Coordenada? = null,
    val resultados: List<PerfilVeterinario> = emptyList(),
    /** Veterinarias de la ciudad que aún no usan PetCare (para llamar o llegar). */
    val lugares: List<com.udenar.petcare.domain.profesionales.LugarVeterinario> = emptyList(),
    val cargando: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class DirectorioVetViewModel @Inject constructor(private val veterinarios: RepositorioVeterinarios) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoDirectorio())
    val estado = _estado.asStateFlow()
    private var busqueda: Job? = null

    init { buscar() }

    fun filtrar(f: (FiltroVeterinarios) -> FiltroVeterinarios) {
        _estado.update { it.copy(filtro = f(it.filtro)) }
        buscar()
    }

    fun usarUbicacion(c: Coordenada?) {
        _estado.update { it.copy(ubicacion = c) }
        buscar()
    }

    fun buscar() {
        busqueda?.cancel()
        busqueda = viewModelScope.launch {
            _estado.update { it.copy(cargando = true, error = null) }
            val s = _estado.value
            try {
                val r = veterinarios.buscar(s.filtro, s.ubicacion?.lat, s.ubicacion?.lon)
                // Los lugares sin cuenta solo se filtran por ciudad (no tienen especies ni servicios cargados)
                val l = runCatching { veterinarios.lugares(s.filtro.ciudad) }.getOrDefault(emptyList())
                _estado.update { it.copy(resultados = r, lugares = l, cargando = false) }
            } catch (e: ProfesionalException) {
                _estado.update { it.copy(cargando = false, error = e.message) }
            }
        }
    }
}

/** Directorio de veterinarios (RF-24): lista ↔ mapa, filtros y orden por distancia. */
@Composable
fun DirectorioVeterinariosScreen(onVolver: () -> Unit, onAbrir: (String) -> Unit, avisar: (String) -> Unit, viewModel: DirectorioVetViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val f = estado.filtro
    val contexto = LocalContext.current
    var verMapa by remember { mutableStateOf(false) }
    var ciudad by remember { mutableStateOf(f.ciudad) }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) ubicacionActual(contexto) { c -> if (c != null) viewModel.usarUbicacion(c) else avisar("No pudimos obtener tu ubicación") }
        else avisar("Sin permiso de ubicación se ordena por ciudad y nombre")
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Pc.Crema)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotonCircular(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onVolver)
            Text("Veterinarios", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp).weight(1f))
            ChipOpcion(if (verMapa) "Lista" else "Mapa", false, onClick = { verMapa = !verMapa }, modifier = Modifier.testTag("alternar_mapa"))
        }
        CampoTexto(
            ciudad, { ciudad = it }, "Ciudad", icono = Icons.Rounded.Search, accion = ImeAction.Search,
            onAccion = { viewModel.filtrar { it.copy(ciudad = ciudad) } },
            modifier = Modifier.testTag("filtro_ciudad"),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipOpcion("Cerca de mí", estado.ubicacion != null, icono = Icono.PIN, onClick = {
                if (estado.ubicacion != null) viewModel.usarUbicacion(null)
                else permiso.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            })
            ChipOpcion("Solo verificados", f.soloVerificados, onClick = { viewModel.filtrar { it.copy(soloVerificados = !it.soloVerificados) } }, modifier = Modifier.testTag("filtro_verificados"))
            ChipOpcion("A domicilio", f.domicilio, onClick = { viewModel.filtrar { it.copy(domicilio = !it.domicilio) } })
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Especie.entries.filter { it != Especie.OTRO }.forEach { e ->
                ChipOpcion(e.etiqueta, f.especie == e, onClick = { viewModel.filtrar { it.copy(especie = if (it.especie == e) null else e) } })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ServicioVet.entries.forEach { s ->
                ChipOpcion(s.etiqueta, f.servicio == s, onClick = { viewModel.filtrar { it.copy(servicio = if (it.servicio == s) null else s) } })
            }
        }

        when {
            estado.error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(estado.error.orEmpty(), color = Pc.TintaSuave)
                BotonSecundario("Reintentar", onClick = viewModel::buscar)
            }
            estado.cargando && estado.resultados.isEmpty() -> Text("Buscando…", color = Pc.TintaSuave)
            estado.resultados.isEmpty() && estado.lugares.isEmpty() -> EstadoVacio(
                R.drawable.gato_doctor, "Aún no hay nadie aquí",
                "No hay veterinarios con esos filtros. Prueba otra ciudad (por ejemplo, Ipiales) o quita algún filtro.", fondoBlob = listOf(Pc.CieloClaro, Pc.LavandaClara),
            )
            verMapa -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MapaMarcadores(
                    estado.resultados.map { PuntoMapa(it.id, Coordenada(it.lat, it.lon), it.verificacion.verificado, if (it.domicilio) com.udenar.petcare.domain.modelo.Icono.CASA else com.udenar.petcare.domain.modelo.Icono.VETERINARIO) },
                    onElegir = onAbrir,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(MaterialTheme.shapes.large)
                        .testTag("mapa_veterinarios"),
                )
                Text("Azul: verificado · Lavanda: no verificado. $ATRIBUCION_MAPA", style = MaterialTheme.typography.labelSmall, color = Pc.TintaSuave)
            }
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (estado.resultados.isEmpty()) {
                    item {
                        com.udenar.petcare.ui.componentes.VacioMichi(
                            com.udenar.petcare.domain.recomendaciones.PoseMichi.DOCTOR,
                            "Aún no hay veterinarios con perfil en PetCare aquí. Mientras tanto, puedes llamar a estas veterinarias.",
                            fondo = Pc.Blanco,
                        )
                    }
                }
                items(estado.resultados, key = { it.id }) { v -> TarjetaVeterinario(v, onClick = { onAbrir(v.id) }) }
                if (estado.lugares.isNotEmpty()) {
                    item {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text("Otras veterinarias cerca", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Aún no usan PetCare: no reciben citas por la app. Datos de directorios públicos; confirma por teléfono.",
                                style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave,
                            )
                        }
                    }
                    items(estado.lugares, key = { "lugar_" + it.id }) { l -> TarjetaLugar(l) }
                }
            }
        }
    }
}

/** Veterinaria sin cuenta en PetCare: nombre, dirección, horario y botones para llamar o llegar. */
@Composable
private fun TarjetaLugar(l: com.udenar.petcare.domain.profesionales.LugarVeterinario) {
    val contexto = LocalContext.current
    fun abrir(uri: String, accion: String = Intent.ACTION_VIEW) {
        runCatching { contexto.startActivity(Intent(accion, android.net.Uri.parse(uri))) }
    }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Pc.Blanco).padding(14.dp).testTag("lugar_${l.nombre}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.udenar.petcare.ui.componentes.BaldosaIcono(com.udenar.petcare.domain.modelo.Icono.HOSPITAL, tam = 42.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(l.nombre, style = MaterialTheme.typography.titleSmall)
                Text("${l.direccion}, ${l.ciudad}", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                l.horario?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            l.telefono?.let { tel ->
                ChipOpcion("Llamar", false, icono = com.udenar.petcare.domain.modelo.Icono.TELEFONO, onClick = { abrir(com.udenar.petcare.domain.profesionales.Contacto.enlaceLlamar(tel), Intent.ACTION_DIAL) })
            }
            ChipOpcion("Cómo llegar", false, icono = com.udenar.petcare.domain.modelo.Icono.MAPA, onClick = {
                abrir("geo:0,0?q=" + android.net.Uri.encode("${l.nombre}, ${l.direccion}, ${l.ciudad}, Nariño, Colombia"))
            })
        }
    }
}

@Composable
internal fun TarjetaVeterinario(v: PerfilVeterinario, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Pc.Blanco)
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag("vet_${v.nombre}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.udenar.petcare.ui.componentes.AvatarProfesional(v.foto, v.nombre, tam = 52.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(v.nombre.ifBlank { "Tu nombre" }, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(v.clinica, v.ciudad, v.distanciaKm?.let(Contacto::textoDistancia)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave,
            )
            Text(
                v.especies.joinToString(", ") { it.etiqueta } + if (v.domicilio) " · a domicilio" else "",
                style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave,
            )
        }
        Chip(v.verificacion.insignia, fondo = if (v.verificacion.verificado) Pc.MentaClara else Pc.Crema, color = if (v.verificacion.verificado) Pc.Tinta else Pc.TintaSuave)
    }
}

@HiltViewModel
class DetalleVetViewModel @Inject constructor(estado: SavedStateHandle, private val veterinarios: RepositorioVeterinarios) : ViewModel() {
    private val id = estado.toRoute<RutaDetalleVet>().id
    val perfil = MutableStateFlow<PerfilVeterinario?>(null)
    val error = MutableStateFlow<String?>(null)

    init { cargar() }

    fun cargar() = viewModelScope.launch {
        error.value = null
        try {
            perfil.value = veterinarios.obtener(id) ?: run { error.value = "Este perfil ya no está disponible."; null }
        } catch (e: ProfesionalException) {
            error.value = e.message
        }
    }
}

private val LOCALE = Locale.forLanguageTag("es-CO")

/** Detalle del veterinario: contacto directo, cómo llegar e insignia (RF-24). */
@Composable
fun DetalleVeterinarioScreen(onVolver: () -> Unit, onSolicitar: () -> Unit, avisar: (String) -> Unit, viewModel: DetalleVetViewModel = hiltViewModel()) {
    val perfil by viewModel.perfil.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    fun abrir(enlace: String, accion: String = Intent.ACTION_VIEW) {
        try {
            contexto.startActivity(Intent(accion, enlace.toUri()))
        } catch (_: ActivityNotFoundException) {
            avisar("No hay una aplicación para abrir esto")
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
        BotonCircular(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onVolver)
        error?.let {
            Text(it, color = Pc.TintaSuave)
            BotonSecundario("Reintentar", onClick = { viewModel.cargar() })
        }
        val v = perfil ?: return@Column
        v.foto?.let { f ->
            com.udenar.petcare.ui.componentes.AvatarProfesional(
                f, v.nombre, tam = 104.dp, fondo = Pc.CieloClaro,
                modifier = Modifier.align(Alignment.CenterHorizontally).border(4.dp, Pc.Blanco, androidx.compose.foundation.shape.CircleShape).testTag("foto_profesional"),
            )
        }
        TarjetaIlustrada(
            titulo = v.nombre,
            texto = listOfNotNull(v.clinica, v.ciudad).joinToString(" · "),
            chip = v.verificacion.insignia,
            ilustracion = R.drawable.gato_doctor,
            degradado = listOf(Pc.Cielo, Pc.CieloOscuro),
            modifier = Modifier.testTag("cabecera_vet"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonSecundario("Llamar", onClick = { abrir(Contacto.enlaceLlamar(v.telefono), Intent.ACTION_DIAL) }, modifier = Modifier.weight(1f).testTag("llamar_vet"))
            v.whatsapp?.let { w -> BotonSecundario("WhatsApp", onClick = { abrir(Contacto.enlaceWhatsapp(w, "Hola, te encontré en PetCare.")) }, modifier = Modifier.weight(1f)) }
        }
        BotonSecundario("Cómo llegar", onClick = { abrir(Contacto.enlaceComoLlegar(v.lat, v.lon, v.clinica ?: v.nombre)) })
        BotonPrincipal("Solicitar servicio", onClick = onSolicitar, modifier = Modifier.testTag("solicitar_servicio"))

        Seccion("Atiende") {
            Text(v.especies.joinToString(", ") { it.etiqueta }, style = MaterialTheme.typography.bodyMedium)
            if (v.servicios.isNotEmpty()) Text(v.servicios.joinToString(" · ") { it.etiqueta }, style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
            if (v.especialidades.isNotEmpty()) Text("Especialidades: " + v.especialidades.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Simbolo(if (v.domicilio) Icono.CASA else Icono.HOSPITAL, tam = 20.dp)
                Text(if (v.domicilio) "  Atiende a domicilio" else "  Atiende en el consultorio", style = MaterialTheme.typography.bodyMedium)
            }
            v.tarifa?.let { Text("Consulta: $it (referencial; el pago se acuerda directamente)", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave) }
        }
        if (v.horario.isNotEmpty()) {
            Seccion("Horario") {
                DayOfWeek.entries.forEach { d ->
                    Row {
                        Text(d.getDisplayName(TextStyle.FULL, LOCALE).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(v.horario[d] ?: "Cerrado", style = MaterialTheme.typography.bodyMedium, color = if (v.horario[d] == null) Pc.TintaSuave else Pc.Tinta)
                    }
                }
            }
        }
        Seccion("Dónde") {
            Text(v.direccion + ", " + v.ciudad, style = MaterialTheme.typography.bodyMedium)
        }
        v.descripcion?.let { Seccion("Sobre mí") { Text(it, style = MaterialTheme.typography.bodyMedium) } }
        SeccionResenas(v.id, com.udenar.petcare.domain.profesionales.TipoResena.VET, v.promedio, v.ratingCantidad, avisar)
    }
}
