package com.udenar.petcare.ui.profesional

import com.udenar.petcare.domain.modelo.Icono
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import com.udenar.petcare.R
import com.udenar.petcare.domain.modelo.ModoUso
import com.udenar.petcare.domain.profesionales.Categoria
import com.udenar.petcare.domain.profesionales.PerfilPrestador
import com.udenar.petcare.domain.profesionales.Precios
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.RepositorioModo
import com.udenar.petcare.domain.profesionales.RepositorioPrestadores
import com.udenar.petcare.domain.profesionales.RepositorioVeterinarios
import com.udenar.petcare.ui.componentes.Chip
import com.udenar.petcare.ui.componentes.EstadoVacio
import com.udenar.petcare.ui.componentes.FilaOpcion
import com.udenar.petcare.ui.componentes.Seccion
import com.udenar.petcare.ui.componentes.TarjetaIlustrada
import com.udenar.petcare.ui.navegacion.BarraInferior
import com.udenar.petcare.ui.navegacion.Pestana
import com.udenar.petcare.ui.theme.Pc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable data object RutaSolicitudesPres
@Serializable data object RutaAgendaPres
@Serializable data object RutaClientesPres
@Serializable data object RutaPerfilPres
@Serializable data object RutaEditarPres

/** Pestañas del modo prestador: el mismo esqueleto que el modo veterinario. */
enum class PestanaPres(override val ruta: Any, override val etiqueta: String, override val icono: ImageVector, override val etiquetaPrueba: String) : Pestana {
    SOLICITUDES(RutaSolicitudesPres, "Solicitudes", Icons.Rounded.Inbox, "pestana_solicitudes_pres"),
    AGENDA(RutaAgendaPres, "Agenda", Icons.Rounded.CalendarMonth, "pestana_agenda_pres"),
    CLIENTES(RutaClientesPres, "Clientes", Icons.Rounded.Groups, "pestana_clientes"),
    PERFIL(RutaPerfilPres, "Perfil", Icons.Rounded.Badge, "pestana_perfil_pres"),
}

@HiltViewModel
class ModoPrestadorViewModel @Inject constructor(
    private val prestadores: RepositorioPrestadores,
    veterinarios: RepositorioVeterinarios,
    private val modo: RepositorioModo,
) : ViewModel() {
    val perfil: StateFlow<PerfilPrestador?> = prestadores.miPerfil
    val perfilVet = veterinarios.miPerfil
    val categorias = MutableStateFlow<List<Categoria>>(emptyList())

    init {
        viewModelScope.launch {
            runCatching { prestadores.cargarMiPerfil() }
            runCatching { prestadores.categorias() }.onSuccess { categorias.value = it }
        }
    }

    fun volverAPersonal() = modo.cambiar(ModoUso.PERSONAL)
    fun modoVeterinario() = modo.cambiar(ModoUso.VETERINARIO)

    private fun guardar(avisar: (String) -> Unit, f: (PerfilPrestador) -> PerfilPrestador, despues: () -> Unit = {}) = viewModelScope.launch {
        val p = perfil.value ?: return@launch
        try {
            prestadores.guardar(f(p))
            despues()
        } catch (e: ProfesionalException) {
            avisar(e.message ?: "No se pudo guardar")
        }
    }

    fun recibiendo(v: Boolean, avisar: (String) -> Unit) =
        guardar(avisar, { it.copy(recibiendo = v) }) { avisar(if (v) "Recibes solicitudes de nuevo" else "Pausaste las solicitudes") }

    fun desactivar(avisar: (String) -> Unit) = guardar(avisar, { it.copy(activo = false) }) { modo.cambiar(ModoUso.PERSONAL) }
}

/** Estructura del modo prestador (RF-30, RF-35): solicitudes, agenda, clientes y perfil. */
@Composable
fun RaizPrestador(viewModel: ModoPrestadorViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val actual = PestanaPres.entries.firstOrNull { entrada?.destination?.hasRoute(it.ruta::class) == true }
    val aperturaVm: com.udenar.petcare.ui.raiz.AperturaViewModel = hiltViewModel()
    val apertura by aperturaVm.pendiente.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(apertura) {
        aperturaVm.tomar(ModoUso.PRESTADOR)?.let { a ->
            nav.irA(if (a.destino == com.udenar.petcare.data.avisos.DestinoAviso.PERFIL) PestanaPres.PERFIL else PestanaPres.SOLICITUDES)
        }
    }
    val avisos = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()
    val avisar: (String) -> Unit = { m ->
        alcance.launch {
            avisos.currentSnackbarData?.dismiss()
            avisos.showSnackbar(m)
        }
    }
    Scaffold(
        containerColor = Pc.Crema,
        snackbarHost = { SnackbarHost(avisos) { datos -> com.udenar.petcare.ui.componentes.AvisoMichi(datos.visuals.message) } },
        topBar = {
            if (actual != null) {
                Row(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Chip("Modo prestador", icono = Icono.TIJERAS, fondo = Pc.SolClaro, color = Pc.Tinta, modifier = Modifier.testTag("chip_modo_pres"))
                }
            }
        },
        bottomBar = { if (actual != null) BarraInferior(PestanaPres.entries, actual, onElegir = { nav.irA(it) }) },
    ) { relleno ->
        NavHost(nav, startDestination = RutaPerfilPres, modifier = Modifier.padding(relleno).consumeWindowInsets(relleno)) {
            composable<RutaSolicitudesPres> { SolicitudesPresScreen(onAbrir = { nav.navigate(RutaSolicitudPres(it)) }) }
            composable<RutaAgendaPres> { AgendaPresScreen(onAbrir = { nav.navigate(RutaSolicitudPres(it)) }) }
            composable<RutaClientesPres> { ClientesPresScreen(onAbrir = { nav.navigate(RutaSolicitudPres(it)) }) }
            composable<RutaSolicitudPres> { DetalleSolicitudPresScreen(onVolver = { nav.popBackStack() }, avisar = avisar) }
            composable<RutaPerfilPres> { PerfilPrestadorScreen(viewModel, onEditar = { nav.navigate(RutaEditarPres) }, avisar = avisar) }
            composable<RutaEditarPres> {
                FormPrestadorScreen(onVolver = { nav.popBackStack() }, onGuardado = {
                    nav.popBackStack()
                    avisar("Perfil actualizado")
                }, avisar = avisar)
            }
        }
    }
}

private fun NavHostController.irA(p: PestanaPres) = navigate(p.ruta) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun PerfilPrestadorScreen(viewModel: ModoPrestadorViewModel, onEditar: () -> Unit, avisar: (String) -> Unit) {
    val perfil by viewModel.perfil.collectAsStateWithLifecycle()
    val vet by viewModel.perfilVet.collectAsStateWithLifecycle()
    val categorias by viewModel.categorias.collectAsStateWithLifecycle()
    var confirmarOcultar by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Perfil", style = MaterialTheme.typography.headlineLarge)
        val p = perfil
        if (p == null) {
            Text("Cargando tu perfil…", color = Pc.TintaSuave)
        } else {
            val principal = categorias.firstOrNull { it.id == p.categoriaPrincipal }
            TarjetaIlustrada(
                titulo = p.nombre,
                texto = listOfNotNull(principal?.nombre, p.ciudad).joinToString(" · "),
                chip = if (p.recibiendo) "Recibiendo solicitudes" else "En pausa",
                ilustracion = R.drawable.gato_amigos,
                degradado = listOf(Pc.SolClaro, Pc.Sol),
                colorTexto = Pc.Tinta,
            )
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Pc.Blanco).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Recibiendo solicitudes", style = MaterialTheme.typography.titleSmall)
                    Text(if (p.recibiendo) "Los dueños pueden pedirte cotizaciones" else "Apareces en el directorio, pero en pausa", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                }
                Switch(p.recibiendo, { viewModel.recibiendo(it, avisar) }, colors = SwitchDefaults.colors(checkedTrackColor = Pc.Naranja), modifier = Modifier.testTag("interruptor_recibiendo"))
            }
            Seccion("Mis servicios") {
                if (p.servicios.isEmpty()) com.udenar.petcare.ui.componentes.VacioMichi(com.udenar.petcare.domain.recomendaciones.PoseMichi.DOCUMENTOS, "Aún no agregas servicios con precio.")
                p.servicios.forEach { s ->
                    Row {
                        com.udenar.petcare.ui.componentes.Simbolo(categorias.firstOrNull { it.id == s.categoriaId }?.icono3d ?: com.udenar.petcare.domain.modelo.Icono.HUELLAS, tam = 22.dp, modifier = Modifier.padding(end = 8.dp))
                        Text(s.nombre, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(Precios.texto(s), style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                    }
                }
            }
            if (p.galeria.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    p.galeria.forEach { url -> AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.size(88.dp).clip(MaterialTheme.shapes.medium)) }
                }
            }
        }
        Column(Modifier.clip(MaterialTheme.shapes.large).background(Pc.Blanco).padding(6.dp)) {
            FilaOpcion(Icons.Rounded.Edit, "Editar perfil", "Servicios, precios, horario, zona y galería", Pc.SolClaro, Pc.Naranja, icono3d = com.udenar.petcare.domain.modelo.Icono.LAPIZ, onClick = onEditar, modifier = Modifier.testTag("editar_perfil_pres"))
            if (vet?.activo == true) {
                FilaOpcion(Icons.Rounded.MedicalServices, "Cambiar a modo veterinario", "Solicitudes, agenda y pacientes", Pc.CieloClaro, Pc.CieloOscuro, icono3d = com.udenar.petcare.domain.modelo.Icono.VETERINARIO, onClick = viewModel::modoVeterinario)
            }
            FilaOpcion(Icons.Rounded.SwapHoriz, "Volver a modo personal", "Tus mascotas y cuidados", Pc.Durazno, Pc.Naranja, icono3d = com.udenar.petcare.domain.modelo.Icono.CASA, onClick = viewModel::volverAPersonal, modifier = Modifier.testTag("volver_personal_pres"))
            FilaOpcion(Icons.Rounded.VisibilityOff, "Ocultar mi perfil", "Dejas de aparecer en el directorio", Pc.LavandaClara, Pc.Lavanda, icono3d = com.udenar.petcare.domain.modelo.Icono.CANDADO, onClick = { confirmarOcultar = true })
        }
    }
    if (confirmarOcultar) {
        AlertDialog(
            onDismissRequest = { confirmarOcultar = false },
            containerColor = Pc.Crema,
            title = { Text("¿Ocultar tu perfil?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("No aparecerás en el directorio y volverás al modo personal. Puedes reactivarlo desde Perfil → Trabaja con PetCare.") },
            confirmButton = { TextButton(onClick = { confirmarOcultar = false; viewModel.desactivar(avisar) }) { Text("Ocultar") } },
            dismissButton = { TextButton(onClick = { confirmarOcultar = false }) { Text("Cancelar") } },
        )
    }
}
