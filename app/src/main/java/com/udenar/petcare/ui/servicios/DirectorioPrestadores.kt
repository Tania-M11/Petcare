package com.udenar.petcare.ui.servicios

import com.udenar.petcare.ui.componentes.Icono3D
import com.udenar.petcare.domain.modelo.Icono
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import coil3.compose.AsyncImage
import com.udenar.petcare.R
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.paseos.Coordenada
import com.udenar.petcare.domain.profesionales.Categoria
import com.udenar.petcare.domain.profesionales.Contacto
import com.udenar.petcare.domain.profesionales.FiltroPrestadores
import com.udenar.petcare.domain.profesionales.ModalidadPrestador
import com.udenar.petcare.domain.profesionales.PerfilPrestador
import com.udenar.petcare.domain.profesionales.Precios
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.RepositorioPrestadores
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
import com.udenar.petcare.ui.navegacion.RutaDetallePrestador
import com.udenar.petcare.ui.theme.Pc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class EstadoDirectorioPres(
    val filtro: FiltroPrestadores = FiltroPrestadores(),
    val ubicacion: Coordenada? = null,
    val categorias: List<Categoria> = emptyList(),
    val resultados: List<PerfilPrestador> = emptyList(),
    val cargando: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class DirectorioPresViewModel @Inject constructor(private val prestadores: RepositorioPrestadores) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoDirectorioPres())
    val estado = _estado.asStateFlow()
    private var busqueda: Job? = null

    init {
        viewModelScope.launch { runCatching { prestadores.categorias() }.onSuccess { c -> _estado.update { it.copy(categorias = c) } } }
        buscar()
    }

    fun filtrar(f: (FiltroPrestadores) -> FiltroPrestadores) {
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
                val r = prestadores.buscar(s.filtro, s.ubicacion?.lat, s.ubicacion?.lon)
                _estado.update { it.copy(resultados = r, cargando = false) }
            } catch (e: ProfesionalException) {
                _estado.update { it.copy(cargando = false, error = e.message) }
            }
        }
    }
}

/** Directorio de prestadores (RF-33): independiente del de veterinarios, mismos componentes. */
@Composable
fun DirectorioPrestadoresScreen(onVolver: () -> Unit, onAbrir: (String) -> Unit, avisar: (String) -> Unit, viewModel: DirectorioPresViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val f = estado.filtro
    val contexto = LocalContext.current
    var verMapa by remember { mutableStateOf(false) }
    var ciudad by remember { mutableStateOf(f.ciudad) }
    var zona by remember { mutableStateOf(f.zona) }
    var elegido by remember { mutableStateOf<PerfilPrestador?>(null) }
    // El permiso de ubicación se pide solo al tocar "Cerca de mí", nunca al abrir
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) ubicacionActual(contexto) { c -> if (c != null) viewModel.usarUbicacion(c) else avisar("No pudimos obtener tu ubicación") }
        else avisar("Sin permiso de ubicación se ordena por ciudad y nombre")
    }

    Column(
        Modifier.fillMaxSize().background(Pc.Crema).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotonCircular(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onVolver)
            Text("Paseo, baño y cuidado", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 16.dp).weight(1f))
            ChipOpcion(if (verMapa) "Lista" else "Mapa", false, onClick = { verMapa = !verMapa; elegido = null }, modifier = Modifier.testTag("alternar_mapa_pres"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoTexto(ciudad, { ciudad = it }, "Ciudad", icono = Icons.Rounded.Search, accion = ImeAction.Search,
                onAccion = { viewModel.filtrar { it.copy(ciudad = ciudad, zona = zona) } }, modifier = Modifier.weight(1f).testTag("filtro_ciudad_pres"))
            CampoTexto(zona, { zona = it }, "Zona o barrio", accion = ImeAction.Search,
                onAccion = { viewModel.filtrar { it.copy(ciudad = ciudad, zona = zona) } }, modifier = Modifier.weight(1f).testTag("filtro_zona_pres"))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            estado.categorias.forEach { c ->
                ChipOpcion("${c.icono} ${c.nombre}", f.categoria == c.id, onClick = { viewModel.filtrar { it.copy(categoria = if (it.categoria == c.id) null else c.id) } }, modifier = Modifier.testTag("filtro_cat_${c.id}"))
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipOpcion("Cerca de mí", estado.ubicacion != null, icono = Icono.PIN, onClick = {
                if (estado.ubicacion != null) viewModel.usarUbicacion(null)
                else permiso.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            })
            ModalidadPrestador.entries.forEach { m ->
                ChipOpcion(m.etiqueta, f.modalidad == m, onClick = { viewModel.filtrar { it.copy(modalidad = if (it.modalidad == m) null else m) } })
            }
            Especie.entries.filter { it != Especie.OTRO }.forEach { e ->
                ChipOpcion(e.etiqueta, f.especie == e, onClick = { viewModel.filtrar { it.copy(especie = if (it.especie == e) null else e) } })
            }
        }

        when {
            estado.error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(estado.error.orEmpty(), color = Pc.TintaSuave)
                BotonSecundario("Reintentar", onClick = viewModel::buscar)
            }
            estado.cargando && estado.resultados.isEmpty() -> Text("Buscando…", color = Pc.TintaSuave)
            estado.resultados.isEmpty() -> EstadoVacio(R.drawable.chopper_amigos, "Nadie con esos filtros", "Prueba con otra ciudad, zona o categoría.", fondoBlob = listOf(Pc.SolClaro, Pc.Durazno))
            verMapa -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MapaMarcadores(
                    estado.resultados.map { PuntoMapa(it.id, Coordenada(it.lat, it.lon), it.recibiendo) },
                    onElegir = { id -> elegido = estado.resultados.firstOrNull { it.id == id } },
                    modifier = Modifier.fillMaxWidth().weight(1f).clip(MaterialTheme.shapes.large).testTag("mapa_prestadores"),
                    colorDestacado = 0xFFF08A4B.toInt(),
                    colorNormal = 0xFF8C8394.toInt(),
                )
                // Tarjeta resumida del marcador tocado (RF-33)
                elegido?.let { p -> TarjetaPrestador(p, estado.categorias, onClick = { onAbrir(p.id) }, boton = true) }
                Text("Naranja: recibiendo solicitudes · Gris: en pausa. $ATRIBUCION_MAPA", style = MaterialTheme.typography.labelSmall, color = Pc.TintaSuave)
            }
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(estado.resultados, key = { it.id }) { p -> TarjetaPrestador(p, estado.categorias, onClick = { onAbrir(p.id) }) }
            }
        }
    }
}

@Composable
private fun TarjetaPrestador(p: PerfilPrestador, categorias: List<Categoria>, onClick: () -> Unit, boton: Boolean = false) {
    val principal = categorias.firstOrNull { it.id == p.categoriaPrincipal }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Pc.Blanco).clickable(onClick = onClick).padding(14.dp).testTag("pres_${p.nombre}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val foto = p.galeria.firstOrNull()
            if (foto != null) {
                AsyncImage(foto, null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(CircleShape))
            } else {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Pc.SolClaro), contentAlignment = Alignment.Center) { Icono3D(principal?.icono3d ?: Icono.HUELLAS, tam = 32.dp) }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.nombre, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(principal?.let { "${it.icono} ${it.nombre}" }, p.zona?.takeIf { it.isNotBlank() } ?: p.ciudad, p.distanciaKm?.let(Contacto::textoDistancia)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave,
                )
                Text(
                    listOfNotNull(p.precioDesde, p.promedio?.let { "★ " + String.format(Locale.forLanguageTag("es-CO"), "%.1f", it) }).joinToString(" · ").ifEmpty { "Precios a cotizar" },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!p.recibiendo) Chip("En pausa", fondo = Pc.Crema, color = Pc.TintaSuave)
        }
        if (boton) BotonPrincipal("Ver perfil", onClick = onClick, modifier = Modifier.testTag("ver_perfil_pres"))
    }
}

@HiltViewModel
class DetallePrestadorViewModel @Inject constructor(estado: SavedStateHandle, private val prestadores: RepositorioPrestadores) : ViewModel() {
    private val id = estado.toRoute<RutaDetallePrestador>().id
    val perfil = MutableStateFlow<PerfilPrestador?>(null)
    val categorias = MutableStateFlow<List<Categoria>>(emptyList())
    val error = MutableStateFlow<String?>(null)

    init { cargar() }

    fun cargar() = viewModelScope.launch {
        error.value = null
        try {
            categorias.value = prestadores.categorias()
            perfil.value = prestadores.obtener(id) ?: run { error.value = "Este perfil ya no está disponible."; null }
        } catch (e: ProfesionalException) {
            error.value = e.message
        }
    }
}

private val LOCALE = Locale.forLanguageTag("es-CO")
private val FECHA_CORTA = DateTimeFormatter.ofPattern("d MMM", LOCALE)

/** Perfil completo del prestador (RF-33). */
@Composable
fun DetallePrestadorScreen(onVolver: () -> Unit, onSolicitar: () -> Unit, avisar: (String) -> Unit, viewModel: DetallePrestadorViewModel = hiltViewModel()) {
    val perfil by viewModel.perfil.collectAsStateWithLifecycle()
    val categorias by viewModel.categorias.collectAsStateWithLifecycle()
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
        Modifier.fillMaxSize().background(Pc.Crema).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BotonCircular(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onVolver)
        error?.let {
            Text(it, color = Pc.TintaSuave)
            BotonSecundario("Reintentar", onClick = { viewModel.cargar() })
        }
        val p = perfil ?: return@Column
        val principal = categorias.firstOrNull { it.id == p.categoriaPrincipal }
        TarjetaIlustrada(
            titulo = p.nombre,
            texto = listOfNotNull(principal?.let { "${it.icono} ${it.nombre}" }, p.ciudad).joinToString(" · "),
            chip = if (p.recibiendo) p.tipo.etiqueta else "En pausa",
            ilustracion = R.drawable.chopper_amigos,
            degradado = listOf(Pc.SolClaro, Pc.Sol),
            colorTexto = Pc.Tinta,
            modifier = Modifier.testTag("cabecera_pres"),
        )
        if (p.galeria.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p.galeria.forEach { url -> AsyncImage(url, "Trabajo de ${p.nombre}", contentScale = ContentScale.Crop, modifier = Modifier.size(120.dp).clip(MaterialTheme.shapes.medium)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonSecundario("Llamar", onClick = { abrir(Contacto.enlaceLlamar(p.telefono), Intent.ACTION_DIAL) }, modifier = Modifier.weight(1f).testTag("llamar_pres"))
            p.whatsapp?.let { w -> BotonSecundario("WhatsApp", onClick = { abrir(Contacto.enlaceWhatsapp(w, "Hola, te encontré en PetCare.")) }, modifier = Modifier.weight(1f)) }
        }
        BotonSecundario("Cómo llegar", onClick = { abrir(Contacto.enlaceComoLlegar(p.lat, p.lon, p.nombre)) })
        BotonPrincipal(
            "Solicitar cotización",
            onClick = { if (p.recibiendo) onSolicitar() else avisar("${p.nombre} no está recibiendo solicitudes por ahora") },
            modifier = Modifier.testTag("solicitar_cotizacion"),
        )

        Text(p.descripcion, style = MaterialTheme.typography.bodyMedium)
        Seccion("Servicios") {
            p.categorias.forEach { cid ->
                val c = categorias.firstOrNull { it.id == cid }
                val deEsta = p.servicios.filter { it.categoriaId == cid }
                Text("${c?.icono.orEmpty()} ${c?.nombre ?: cid}", style = MaterialTheme.typography.titleSmall)
                if (deEsta.isEmpty()) Text("A cotizar", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                deEsta.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(s.nombre, style = MaterialTheme.typography.bodyMedium)
                            s.descripcion?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave) }
                        }
                        Text(Precios.texto(s), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("Precios orientativos: el pago se acuerda directamente, fuera de PetCare.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
        }
        Seccion("Atiende") {
            Text(p.especies.joinToString(", ") { it.etiqueta }, style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { p.modalidades.forEach { Chip(it.etiqueta, fondo = Pc.SolClaro) } }
            if (p.cobertura.isNotBlank()) Text("Cobertura: ${p.cobertura}", style = MaterialTheme.typography.bodyMedium)
        }
        if (p.horario.isNotEmpty() || p.noDisponible.isNotEmpty()) {
            Seccion("Horario") {
                DayOfWeek.entries.forEach { d ->
                    Row {
                        Text(d.getDisplayName(TextStyle.FULL, LOCALE).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(p.horario[d] ?: "No atiende", style = MaterialTheme.typography.bodyMedium, color = if (p.horario[d] == null) Pc.TintaSuave else Pc.Tinta)
                    }
                }
                if (p.noDisponible.isNotEmpty()) Text("No disponible: " + p.noDisponible.joinToString(", ") { it.format(FECHA_CORTA) }, style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            }
        }
        SeccionResenas(p.id, com.udenar.petcare.domain.profesionales.TipoResena.PRESTADOR, p.promedio, p.ratingCantidad, avisar)
    }
}
