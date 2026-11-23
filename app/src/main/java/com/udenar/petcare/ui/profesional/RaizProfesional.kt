package com.udenar.petcare.ui.profesional

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.udenar.petcare.R
import com.udenar.petcare.domain.modelo.ModoUso
import com.udenar.petcare.domain.profesionales.PerfilVeterinario
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.RepositorioModo
import com.udenar.petcare.domain.profesionales.RepositorioVeterinarios
import com.udenar.petcare.domain.profesionales.ValidadorVeterinario
import com.udenar.petcare.domain.profesionales.Verificacion
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.Chip
import com.udenar.petcare.ui.componentes.EstadoVacio
import com.udenar.petcare.ui.componentes.FilaOpcion
import com.udenar.petcare.ui.componentes.Seccion
import com.udenar.petcare.ui.componentes.TarjetaIlustrada
import com.udenar.petcare.ui.navegacion.BarraInferior
import com.udenar.petcare.ui.navegacion.Pestana
import com.udenar.petcare.ui.theme.Pc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable data object RutaSolicitudesVet
@Serializable data object RutaAgendaVet
@Serializable data object RutaPacientesVet
@Serializable data object RutaPerfilVet
@Serializable data object RutaEditarVet

/** Pestañas del modo veterinario (plan, sección 3: mismo esqueleto para los profesionales). */
enum class PestanaVet(override val ruta: Any, override val etiqueta: String, override val icono: ImageVector, override val etiquetaPrueba: String) : Pestana {
    SOLICITUDES(RutaSolicitudesVet, "Solicitudes", Icons.Rounded.Inbox, "pestana_solicitudes"),
    AGENDA(RutaAgendaVet, "Agenda", Icons.Rounded.CalendarMonth, "pestana_agenda"),
    PACIENTES(RutaPacientesVet, "Pacientes", Icons.Rounded.Pets, "pestana_pacientes"),
    PERFIL(RutaPerfilVet, "Perfil", Icons.Rounded.Badge, "pestana_perfil_vet"),
}

@HiltViewModel
class ModoVetViewModel @Inject constructor(
    private val veterinarios: RepositorioVeterinarios,
    private val modo: RepositorioModo,
    prestadores: com.udenar.petcare.domain.profesionales.RepositorioPrestadores,
) : ViewModel() {
    val perfil: StateFlow<PerfilVeterinario?> = veterinarios.miPerfil
    val perfilPres = prestadores.miPerfil
    fun modoPrestador() = modo.cambiar(ModoUso.PRESTADOR)
    private val _subiendo = MutableStateFlow(false)
    val subiendo = _subiendo.asStateFlow()

    init {
        viewModelScope.launch { runCatching { veterinarios.cargarMiPerfil() } }
    }

    fun volverAPersonal() = modo.cambiar(ModoUso.PERSONAL)

    fun subirCertificado(leer: suspend () -> Pair<ByteArray, String>?, avisar: (String) -> Unit) = viewModelScope.launch {
        _subiendo.value = true
        try {
            val (bytes, tipo) = leer() ?: throw ProfesionalException("No pudimos leer el archivo.")
            veterinarios.subirCertificado(bytes, tipo)
            avisar("Certificado enviado. Lo revisaremos pronto.")
        } catch (e: ProfesionalException) {
            avisar(e.message ?: "No se pudo subir el certificado")
        } finally {
            _subiendo.value = false
        }
    }

    /** Ocultar el perfil del directorio: deja de ser veterinario activo y vuelve al modo personal. */
    fun desactivar(avisar: (String) -> Unit) = viewModelScope.launch {
        val p = perfil.value ?: return@launch
        try {
            veterinarios.guardar(p.copy(activo = false))
            modo.cambiar(ModoUso.PERSONAL)
        } catch (e: ProfesionalException) {
            avisar(e.message ?: "No se pudo desactivar")
        }
    }
}

/** Estructura del modo veterinario: 4 pestañas con la misma barra flotante del modo personal. */
@Composable
fun RaizProfesional(viewModel: ModoVetViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val actual = PestanaVet.entries.firstOrNull { entrada?.destination?.hasRoute(it.ruta::class) == true }
    val aperturaVm: com.udenar.petcare.ui.raiz.AperturaViewModel = hiltViewModel()
    val apertura by aperturaVm.pendiente.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(apertura) {
        aperturaVm.tomar(ModoUso.VETERINARIO)?.let { a ->
            nav.irA(if (a.destino == com.udenar.petcare.data.avisos.DestinoAviso.PERFIL) PestanaVet.PERFIL else PestanaVet.SOLICITUDES)
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
        snackbarHost = { SnackbarHost(avisos) },
        topBar = {
            if (actual != null) {
                Row(
                    Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Chip("🩺 Modo veterinario", fondo = Pc.CieloClaro, color = Pc.CieloOscuro, modifier = Modifier.testTag("chip_modo_vet"))
                }
            }
        },
        bottomBar = { if (actual != null) BarraInferior(PestanaVet.entries, actual, onElegir = { nav.irA(it) }) },
    ) { relleno ->
        NavHost(
            nav,
            startDestination = RutaPerfilVet,
            modifier = Modifier
                .padding(relleno)
                .consumeWindowInsets(relleno),
        ) {
            composable<RutaSolicitudesVet> { SolicitudesVetScreen(avisar, onFicha = { nav.navigate(RutaFichaPaciente(it)) }) }
            composable<RutaAgendaVet> { AgendaVetScreen(avisar, onFicha = { nav.navigate(RutaFichaPaciente(it)) }) }
            composable<RutaPacientesVet> { PacientesVetScreen(onFicha = { nav.navigate(RutaFichaPaciente(it)) }) }
            composable<RutaFichaPaciente> {
                val perfil by viewModel.perfil.collectAsStateWithLifecycle()
                FichaPacienteScreen(miId = perfil?.id, onVolver = { nav.popBackStack() }, avisar = avisar)
            }
            composable<RutaPerfilVet> {
                PerfilVetScreen(viewModel, onEditar = { nav.navigate(RutaEditarVet) }, avisar = avisar)
            }
            composable<RutaEditarVet> {
                FormVeterinarioScreen(onVolver = { nav.popBackStack() }, onGuardado = {
                    nav.popBackStack()
                    avisar("Perfil actualizado")
                }, avisar = avisar)
            }
        }
    }
}

private fun NavHostController.irA(p: PestanaVet) = navigate(p.ruta) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** Perfil público, verificación (RF-23) y vuelta al modo personal. */
@Composable
private fun PerfilVetScreen(viewModel: ModoVetViewModel, onEditar: () -> Unit, avisar: (String) -> Unit) {
    val perfil by viewModel.perfil.collectAsStateWithLifecycle()
    val subiendo by viewModel.subiendo.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    var confirmarOcultar by remember { mutableStateOf(false) }
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.subirCertificado({
            withContext(Dispatchers.IO) {
                val r = contexto.contentResolver
                val tamano = r.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
                if (tamano != null && tamano > ValidadorVeterinario.MAX_CERTIFICADO) throw ProfesionalException("El archivo pesa más de 5 MB.")
                val tipo = r.getType(uri) ?: return@withContext null
                r.openInputStream(uri)?.use { it.readBytes() }?.let { it to tipo }
            }
        }, avisar)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Perfil", style = MaterialTheme.typography.headlineLarge)
        val p = perfil
        if (p == null) {
            Text("Cargando tu perfil…", color = Pc.TintaSuave)
        } else {
            TarjetaIlustrada(
                titulo = p.nombre,
                texto = listOfNotNull(p.clinica, p.ciudad).joinToString(" · "),
                chip = p.verificacion.insignia,
                ilustracion = R.drawable.chopper_veterinario,
                degradado = listOf(Pc.Cielo, Pc.CieloOscuro),
            )
            Seccion("Verificación") {
                Chip(
                    when (p.verificacion) {
                        Verificacion.SIN_CERTIFICADO -> "Sin certificado"
                        Verificacion.EN_REVISION -> "En revisión"
                        Verificacion.VERIFICADO -> "Verificado ✓"
                        Verificacion.RECHAZADO -> "Rechazado"
                    },
                    fondo = if (p.verificacion.verificado) Pc.MentaClara else Pc.CieloClaro,
                    modifier = Modifier.testTag("estado_verificacion"),
                )
                Text(p.verificacion.mensaje, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("mensaje_verificacion"))
                if (!p.verificacion.verificado) {
                    Text("Imagen (JPG o PNG) o PDF de tu tarjeta profesional, hasta 5 MB. Solo tú y el equipo verificador pueden verlo.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                    BotonPrincipal(
                        if (p.verificacion == Verificacion.SIN_CERTIFICADO) "Subir certificado" else "Subir otro certificado",
                        onClick = { elegir.launch(arrayOf("image/jpeg", "image/png", "application/pdf")) },
                        cargando = subiendo,
                        modifier = Modifier.testTag("subir_certificado"),
                    )
                }
            }
        }
        Column(
            Modifier
                .clip(MaterialTheme.shapes.large)
                .background(Pc.Blanco)
                .padding(6.dp),
        ) {
            FilaOpcion(Icons.Rounded.Edit, "Editar perfil", "Especies, servicios, horario y ubicación", Pc.CieloClaro, Pc.CieloOscuro, onClick = onEditar, modifier = Modifier.testTag("editar_perfil_vet"))
            val pres by viewModel.perfilPres.collectAsStateWithLifecycle()
            if (pres?.activo == true) {
                FilaOpcion(Icons.Rounded.SwapHoriz, "Cambiar a modo prestador", "Solicitudes, agenda y clientes", Pc.SolClaro, Pc.Naranja, onClick = viewModel::modoPrestador)
            }
            FilaOpcion(Icons.Rounded.SwapHoriz, "Volver a modo personal", "Tus mascotas y cuidados", Pc.Durazno, Pc.Naranja, onClick = viewModel::volverAPersonal, modifier = Modifier.testTag("volver_personal"))
            FilaOpcion(Icons.Rounded.VisibilityOff, "Ocultar mi perfil", "Dejas de aparecer en el directorio", Pc.LavandaClara, Pc.Lavanda, onClick = { confirmarOcultar = true })
        }
    }

    if (confirmarOcultar) {
        AlertDialog(
            onDismissRequest = { confirmarOcultar = false },
            containerColor = Pc.Crema,
            title = { Text("¿Ocultar tu perfil?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("No aparecerás en el directorio y volverás al modo personal. Puedes reactivarlo desde Perfil → Trabaja con PetCare.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarOcultar = false
                    viewModel.desactivar(avisar)
                }) { Text("Ocultar") }
            },
            dismissButton = { TextButton(onClick = { confirmarOcultar = false }) { Text("Cancelar") } },
        )
    }
}
