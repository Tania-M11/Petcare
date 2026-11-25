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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Tune
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
import com.udenar.petcare.ui.profesional.icono
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
    val buscandoUbicacion: Boolean = false,
    val resultados: List<PerfilVeterinario> = emptyList(),
    /** Veterinarias de la ciudad que aún no usan PetCare (para llamar o llegar). */
    val lugares: List<com.udenar.petcare.domain.profesionales.LugarVeterinario> = emptyList(),
    /** Coordenadas de los lugares (de la base o buscadas por dirección en el teléfono). */
    val coordsLugares: Map<String, Coordenada> = emptyMap(),
    val sugerencias: List<SugerenciaVet> = emptyList(),
    /** Especies de mis mascotas (para marcar quién las atiende). */
    val misEspecies: Set<Especie> = emptySet(),
    val cargando: Boolean = true,
    val error: String? = null,
)

/**
 * Sugerencia según mis mascotas (organización, no salud): qué buscar y con qué filtros.
 * Nunca recomienda tratamientos; solo ayuda a encontrar a quien atienda lo que ya está registrado.
 */
data class SugerenciaVet(val id: String, val titulo: String, val detalle: String, val icono: Icono, val especie: Especie?, val servicio: ServicioVet?)

@HiltViewModel
class DirectorioVetViewModel @Inject constructor(
    private val veterinarios: RepositorioVeterinarios,
    mascotasRepo: com.udenar.petcare.domain.mascotas.RepositorioMascotas,
    cuidados: com.udenar.petcare.domain.cuidados.RepositorioCuidados,
    private val geocodificador: com.udenar.petcare.data.profesionales.GeocodificadorLugares,
    private val reloj: java.time.Clock,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoDirectorio())
    val estado = _estado.asStateFlow()
    private var busqueda: Job? = null

    init {
        buscar()
        // Sugerencias con mis mascotas y lo que tienen por vencer
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(mascotasRepo.observar(), cuidados.observarEventos(), cuidados.observarRegistros()) { m, e, r -> Triple(m, e, r) }
                .collect { (mascotas, eventos, registros) ->
                    val propias = mascotas.filter { it.esPropietario }.ifEmpty { mascotas }
                    _estado.update { it.copy(sugerencias = sugerir(propias, eventos, registros), misEspecies = propias.map { m -> m.especie }.toSet()) }
                }
        }
    }

    private fun sugerir(
        mascotas: List<com.udenar.petcare.domain.modelo.Mascota>,
        eventos: List<com.udenar.petcare.domain.cuidados.EventoCuidado>,
        registros: List<com.udenar.petcare.domain.cuidados.RegistroCuidado>,
    ): List<SugerenciaVet> {
        val ahora = java.time.LocalDateTime.now(reloj)
        val porId = mascotas.associateBy { it.id }
        val fecha = java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-CO"))
        val pendientes = eventos.filter { it.mascotaId in porId && !it.oculto }.mapNotNull { e ->
            val r = com.udenar.petcare.domain.cuidados.resumir(e, registros, ahora, reloj.zone)
            val m = porId.getValue(e.mascotaId)
            val vence = r.vencimiento == com.udenar.petcare.domain.cuidados.Vencimiento.VENCIDO
            val proxima = r.vencimiento == com.udenar.petcare.domain.cuidados.Vencimiento.PROXIMO ||
                (e.ancla == com.udenar.petcare.domain.cuidados.Ancla.ULTIMA_VEZ && r.proxima?.let { !it.isAfter(ahora.toLocalDate().plusDays(14)) } == true)
            if (!vence && !proxima) return@mapNotNull null
            val cuando = r.proxima?.let { (if (vence) "venció el " else "toca el ") + it.format(fecha) }.orEmpty()
            when (e.tipo) {
                com.udenar.petcare.domain.cuidados.TipoCuidado.VACUNA ->
                    SugerenciaVet("vac_${e.id}", "Vacuna de ${m.nombre}", "${e.titulo} · $cuando", Icono.VACUNA, m.especie, ServicioVet.VACUNACION)
                com.udenar.petcare.domain.cuidados.TipoCuidado.DESPARASITACION_INTERNA, com.udenar.petcare.domain.cuidados.TipoCuidado.DESPARASITACION_EXTERNA ->
                    SugerenciaVet("des_${e.id}", "Desparasitación de ${m.nombre}", "${e.titulo} · $cuando", Icono.ESCUDO, m.especie, ServicioVet.DESPARASITACION)
                else -> null
            }
        }
        val porMascota = mascotas.distinctBy { it.especie }.map { m ->
            val mismas = mascotas.filter { it.especie == m.especie }.joinToString(" y ") { it.nombre }
            SugerenciaVet("esp_${m.especie}", "Para $mismas", "Quienes atienden ${m.especie.etiqueta.lowercase()}", Icono.HUELLAS, m.especie, null)
        }
        return (pendientes + porMascota).take(6)
    }

    fun filtrar(f: (FiltroVeterinarios) -> FiltroVeterinarios) {
        _estado.update { it.copy(filtro = f(it.filtro)) }
        buscar()
    }

    fun aplicar(s: SugerenciaVet) = filtrar { it.copy(especie = s.especie, servicio = s.servicio) }

    fun limpiarFiltros() = filtrar { FiltroVeterinarios(ciudad = it.ciudad) }

    fun buscandoUbicacion(si: Boolean) = _estado.update { it.copy(buscandoUbicacion = si) }

    fun usarUbicacion(c: Coordenada?) {
        _estado.update { it.copy(ubicacion = c, buscandoUbicacion = false) }
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
                // Ubicarlos en el mapa (con la dirección) sin frenar la lista
                val coords = runCatching { geocodificador.coordenadas(l) }.getOrDefault(emptyMap())
                _estado.update { it.copy(coordsLugares = coords) }
            } catch (e: ProfesionalException) {
                _estado.update { it.copy(cargando = false, error = e.message) }
            }
        }
    }
}

/** Cuántos filtros (sin contar la ciudad) están activos. */
private fun FiltroVeterinarios.activos() = listOf(especie != null, servicio != null, domicilio, soloVerificados).count { it }

/**
 * Directorio de veterinarios (RF-24): búsqueda por ciudad, "Cerca de mí", filtros ordenados en
 * un panel, sugerencias para mis mascotas y resultados en lista o mapa (con mi ubicación).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DirectorioVeterinariosScreen(onVolver: () -> Unit, onAbrir: (String) -> Unit, avisar: (String) -> Unit, viewModel: DirectorioVetViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val f = estado.filtro
    val contexto = LocalContext.current
    var verMapa by remember { mutableStateOf(false) }
    var verFiltros by remember { mutableStateOf(false) }
    var ciudad by remember { mutableStateOf(f.ciudad) }
    var lugarElegido by remember { mutableStateOf<com.udenar.petcare.domain.profesionales.LugarVeterinario?>(null) }
    fun leerUbicacion() {
        if (!com.udenar.petcare.ui.mapas.ubicacionEncendida(contexto)) {
            avisar("Enciende la ubicación del teléfono para buscar cerca de ti")
            runCatching { contexto.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            return
        }
        viewModel.buscandoUbicacion(true)
        ubicacionActual(contexto) { c ->
            if (c != null) viewModel.usarUbicacion(c) else { viewModel.buscandoUbicacion(false); avisar("No pudimos obtener tu ubicación; intenta de nuevo al aire libre") }
        }
    }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) leerUbicacion() else avisar("Sin permiso de ubicación se ordena por ciudad y nombre")
    }
    fun cercaDeMi() {
        if (estado.ubicacion != null) { viewModel.usarUbicacion(null); return }
        val concedido = androidx.core.content.ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (concedido) leerUbicacion() else permiso.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    // Lugares ordenados por cercanía cuando se conoce la ubicación
    val lugares = estado.lugares.let { l ->
        val yo = estado.ubicacion ?: return@let l
        l.sortedBy { estado.coordsLugares[it.id]?.let { c -> com.udenar.petcare.domain.paseos.Geo.distanciaM(yo, c) } ?: Double.MAX_VALUE }
    }
    fun distancia(id: String): String? {
        val yo = estado.ubicacion ?: return null
        val c = estado.coordsLugares[id] ?: return null
        return com.udenar.petcare.domain.paseos.textoDistancia(com.udenar.petcare.domain.paseos.Geo.distanciaM(yo, c).toInt())
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
        // Encabezado: volver, título y lista/mapa como selector segmentado
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotonCircular(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onVolver)
            Text("Veterinarios", style = MaterialTheme.typography.headlineSmall, maxLines = 1, modifier = Modifier.padding(start = 14.dp).weight(1f))
            Row(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(Pc.Blanco).padding(4.dp)) {
                listOf(false to "Lista", true to "Mapa").forEach { (mapa, t) ->
                    Text(
                        t, style = MaterialTheme.typography.labelLarge, color = if (verMapa == mapa) Pc.SobreAcento else Pc.TintaSuave,
                        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(if (verMapa == mapa) Pc.Tinta else Pc.Blanco)
                            .clickable { verMapa = mapa }.padding(horizontal = 14.dp, vertical = 8.dp).testTag(if (mapa) "alternar_mapa" else "ver_lista"),
                    )
                }
            }
        }
        // Búsqueda por ciudad + botón de filtros con contador
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampoTexto(
                ciudad, { ciudad = it }, "Ciudad (p. ej. Ipiales)", icono = Icons.Rounded.Search, accion = ImeAction.Search,
                onAccion = { viewModel.filtrar { it.copy(ciudad = ciudad) } },
                modifier = Modifier.weight(1f).testTag("filtro_ciudad"),
            )
            Box {
                BotonCircular(androidx.compose.material.icons.Icons.Rounded.Tune, "Filtros", onClick = { verFiltros = true }, modifier = Modifier.testTag("abrir_filtros"), fondo = if (f.activos() > 0) Pc.Tinta else Pc.Blanco, tinte = if (f.activos() > 0) Pc.SobreAcento else Pc.Tinta)
                if (f.activos() > 0) {
                    Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(Pc.Naranja), contentAlignment = Alignment.Center) {
                        Text("${f.activos()}", style = MaterialTheme.typography.labelSmall, color = Pc.SobreAcento)
                    }
                }
            }
        }
        // Cerca de mí y filtros activos (cada uno se quita con un toque)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipOpcion(
                when { estado.buscandoUbicacion -> "Buscando…"; estado.ubicacion != null -> "Cerca de mí ✓"; else -> "Cerca de mí" },
                estado.ubicacion != null, icono = Icono.PIN, onClick = ::cercaDeMi, modifier = Modifier.testTag("cerca_de_mi"),
            )
            f.especie?.let { e -> ChipOpcion("${e.etiqueta} ✕", true, onClick = { viewModel.filtrar { it.copy(especie = null) } }) }
            f.servicio?.let { sv -> ChipOpcion("${sv.etiqueta} ✕", true, onClick = { viewModel.filtrar { it.copy(servicio = null) } }) }
            if (f.domicilio) ChipOpcion("A domicilio ✕", true, onClick = { viewModel.filtrar { it.copy(domicilio = false) } })
            if (f.soloVerificados) ChipOpcion("Verificados ✕", true, onClick = { viewModel.filtrar { it.copy(soloVerificados = false) } })
        }

        when {
            estado.error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(estado.error.orEmpty(), color = Pc.TintaSuave)
                BotonSecundario("Reintentar", onClick = viewModel::buscar)
            }
            estado.cargando && estado.resultados.isEmpty() && estado.lugares.isEmpty() -> Text("Buscando…", color = Pc.TintaSuave)
            verMapa -> Box(Modifier.weight(1f).fillMaxWidth()) {
                val puntos = estado.resultados.map {
                    PuntoMapa(it.id, Coordenada(it.lat, it.lon), it.verificacion.verificado, if (it.domicilio) Icono.CASA else Icono.VETERINARIO)
                } + lugares.mapNotNull { l -> estado.coordsLugares[l.id]?.let { c -> PuntoMapa("lugar_${l.id}", c, false, Icono.HOSPITAL, color = 0xFFF08A4B.toInt()) } }
                MapaMarcadores(
                    puntos,
                    onElegir = { id -> if (id.startsWith("lugar_")) lugarElegido = estado.lugares.firstOrNull { "lugar_${it.id}" == id } else onAbrir(id) },
                    miUbicacion = estado.ubicacion,
                    modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).testTag("mapa_veterinarios"),
                )
                Column(Modifier.align(Alignment.BottomCenter).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lugarElegido?.let { l -> TarjetaLugar(l, distancia(l.id)) }
                    Text(
                        "Azul: PetCare verificado · Lavanda: PetCare · Naranja: aún sin PetCare" + if (estado.ubicacion != null) " · Punto azul: tú" else "",
                        style = MaterialTheme.typography.labelSmall, color = Pc.Tinta,
                        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(Pc.Blanco.copy(alpha = 0.9f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                if (puntos.isEmpty()) {
                    Text("Ubicando las veterinarias…", style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.Center).clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(Pc.Blanco).padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Sugerencias para mis mascotas (solo si no hay filtros elegidos)
                if (estado.sugerencias.isNotEmpty() && f.activos() == 0) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Para tus mascotas", style = MaterialTheme.typography.titleMedium)
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("sugerencias_vet")) {
                                items(estado.sugerencias, key = { it.id }) { s ->
                                    Row(
                                        Modifier.width(230.dp).clip(MaterialTheme.shapes.large).background(Pc.Blanco).clickable { viewModel.aplicar(s) }.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        com.udenar.petcare.ui.componentes.BaldosaIcono(s.icono, tam = 40.dp)
                                        Column(Modifier.padding(start = 10.dp)) {
                                            Text(s.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                            Text(s.detalle, style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave, maxLines = 2)
                                            Text("Buscar", style = MaterialTheme.typography.labelMedium, color = Pc.Naranja)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Text(
                        when (estado.resultados.size) { 0 -> "Sin veterinarios con perfil en PetCare"; 1 -> "1 veterinario en PetCare"; else -> "${estado.resultados.size} veterinarios en PetCare" },
                        style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave,
                    )
                }
                if (estado.resultados.isEmpty()) {
                    item {
                        com.udenar.petcare.ui.componentes.VacioMichi(
                            com.udenar.petcare.domain.recomendaciones.PoseMichi.DOCTOR,
                            if (estado.lugares.isEmpty()) "No hay veterinarios con estos filtros. Prueba otra ciudad (por ejemplo, Ipiales) o quita algún filtro."
                            else "Aún no hay veterinarios con perfil en PetCare aquí. Mientras tanto, puedes llamar a estas veterinarias.",
                            fondo = Pc.Blanco,
                        )
                    }
                }
                items(estado.resultados, key = { it.id }) { v ->
                    Column {
                        // Atiende a todas mis especies: se marca para que lo vea primero
                        if (estado.misEspecies.isNotEmpty() && v.especies.containsAll(estado.misEspecies)) {
                            Chip("Atiende a tus mascotas", fondo = Pc.MentaClara, icono = Icono.CHECK, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        TarjetaVeterinario(v, onClick = { onAbrir(v.id) })
                    }
                }
                if (lugares.isNotEmpty()) {
                    item {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(if (estado.ubicacion != null) "Otras veterinarias, de la más cercana" else "Otras veterinarias cerca", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Aún no usan PetCare: no reciben citas por la app. Datos de directorios públicos; confirma por teléfono.",
                                style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave,
                            )
                        }
                    }
                    items(lugares, key = { "lugar_" + it.id }) { l -> TarjetaLugar(l, distancia(l.id)) }
                }
            }
        }
    }

    if (verFiltros) {
        var borrador by remember { mutableStateOf(f) }
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { verFiltros = false }, containerColor = Pc.Crema) {
            Column(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Filtros", style = MaterialTheme.typography.headlineSmall)
                Text("¿Para quién?", style = MaterialTheme.typography.titleSmall)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Especie.entries.filter { it != Especie.OTRO }.forEach { e ->
                        com.udenar.petcare.ui.componentes.ChipEspecie(e, borrador.especie == e, onClick = { borrador = borrador.copy(especie = if (borrador.especie == e) null else e) }, modifier = Modifier.testTag("filtro_especie_${e.name}"))
                    }
                }
                Text("¿Qué necesitas?", style = MaterialTheme.typography.titleSmall)
                ServicioVet.entries.chunked(3).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fila.forEach { sv ->
                            com.udenar.petcare.ui.componentes.TarjetaOpcionGrande(
                                sv.etiqueta, borrador.servicio == sv, onClick = { borrador = borrador.copy(servicio = if (borrador.servicio == sv) null else sv) },
                                icono = sv.icono(), color = Pc.CieloClaro, modifier = Modifier.weight(1f).testTag("filtro_servicio_${sv.name}"),
                            )
                        }
                        repeat(3 - fila.size) { Box(Modifier.weight(1f)) }
                    }
                }
                Text("Atención", style = MaterialTheme.typography.titleSmall)
                com.udenar.petcare.ui.profesional.TarjetaInterruptor(Icono.CASA, "A domicilio", "Que vaya a tu casa", borrador.domicilio, { borrador = borrador.copy(domicilio = it) })
                com.udenar.petcare.ui.profesional.TarjetaInterruptor(Icono.ESCUDO, "Solo verificados", "Con certificado revisado por PetCare", borrador.soloVerificados, { borrador = borrador.copy(soloVerificados = it) }, modifier = Modifier.testTag("filtro_verificados"))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonSecundario("Limpiar", onClick = { borrador = FiltroVeterinarios(ciudad = f.ciudad) }, modifier = Modifier.weight(1f))
                    BotonPrincipal("Ver resultados", onClick = { viewModel.filtrar { borrador.copy(ciudad = it.ciudad) }; verFiltros = false }, modifier = Modifier.weight(1f).testTag("aplicar_filtros"))
                }
            }
        }
    }
}

/** Veterinaria sin cuenta en PetCare: nombre, dirección, horario y botones para llamar o llegar. */
@Composable
private fun TarjetaLugar(l: com.udenar.petcare.domain.profesionales.LugarVeterinario, distancia: String? = null) {
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
                Text("${l.direccion}, ${l.ciudad}" + (distancia?.let { " · a $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
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
