package com.udenar.petcare.ui.profesional

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.udenar.petcare.domain.cuenta.RepositorioCuenta
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.ModoUso
import com.udenar.petcare.domain.paseos.Coordenada
import com.udenar.petcare.domain.profesionales.PerfilVeterinario
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.RepositorioModo
import com.udenar.petcare.domain.profesionales.RepositorioVeterinarios
import com.udenar.petcare.domain.profesionales.ServicioVet
import com.udenar.petcare.domain.profesionales.ValidadorVeterinario
import com.udenar.petcare.ui.componentes.BotonCircular
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.BotonSecundario
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.ChipOpcion
import com.udenar.petcare.ui.componentes.Seccion
import com.udenar.petcare.ui.mapas.ATRIBUCION_MAPA
import com.udenar.petcare.ui.mapas.SelectorUbicacion
import com.udenar.petcare.ui.mapas.ubicacionActual
import com.udenar.petcare.ui.theme.Pc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** Borrador editable del perfil; los textos se guardan tal cual se escriben. */
data class BorradorVet(
    val nombre: String = "",
    val especies: Set<Especie> = emptySet(),
    val servicios: Set<ServicioVet> = setOf(ServicioVet.CONSULTA),
    val especialidades: String = "",
    val clinica: String = "",
    val telefono: String = "",
    val whatsapp: String = "",
    val correo: String = "",
    val descripcion: String = "",
    val domicilio: Boolean = false,
    val horario: Map<DayOfWeek, String> = emptyMap(),
    val tarifa: String = "",
    val ciudad: String = "",
    val direccion: String = "",
    val ubicacion: Coordenada? = null,
    val activo: Boolean = true,
) {
    fun comoPerfil(id: String) = PerfilVeterinario(
        id = id, nombre = nombre, especies = especies,
        especialidades = especialidades.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        servicios = servicios, clinica = clinica, telefono = telefono, whatsapp = whatsapp, correo = correo,
        descripcion = descripcion, domicilio = domicilio, horario = horario.filterValues { it.isNotBlank() },
        tarifa = tarifa, ciudad = ciudad, direccion = direccion,
        lat = ubicacion?.lat ?: 0.0, lon = ubicacion?.lon ?: 0.0, activo = true,
    )

    companion object {
        fun desde(p: PerfilVeterinario) = BorradorVet(
            p.nombre, p.especies, p.servicios, p.especialidades.joinToString(", "), p.clinica.orEmpty(), p.telefono,
            p.whatsapp.orEmpty(), p.correo.orEmpty(), p.descripcion.orEmpty(), p.domicilio, p.horario, p.tarifa.orEmpty(),
            p.ciudad, p.direccion, Coordenada(p.lat, p.lon), p.activo,
        )
    }
}

data class EstadoFormVet(
    val borrador: BorradorVet = BorradorVet(),
    val cargando: Boolean = true,
    val guardando: Boolean = false,
    val errores: Map<String, String> = emptyMap(),
    val mensaje: String? = null,
    /** true si ya existía un perfil (edición); false = activación. */
    val existente: Boolean = false,
)

@HiltViewModel
class FormVeterinarioViewModel @Inject constructor(
    private val veterinarios: RepositorioVeterinarios,
    private val modo: RepositorioModo,
    private val cuenta: RepositorioCuenta,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoFormVet())
    val estado = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            val perfil = try {
                veterinarios.cargarMiPerfil()
            } catch (e: ProfesionalException) {
                veterinarios.miPerfil.value.also { if (it == null) _estado.update { s -> s.copy(mensaje = e.message) } }
            }
            _estado.update {
                it.copy(
                    borrador = perfil?.let(BorradorVet::desde) ?: BorradorVet(nombre = cuenta.usuario.value?.nombre.orEmpty()),
                    cargando = false,
                    existente = perfil?.activo == true,
                )
            }
        }
    }

    fun cambiar(f: (BorradorVet) -> BorradorVet) = _estado.update { s ->
        val nuevo = f(s.borrador)
        // Al corregir un campo, su error desaparece
        s.copy(borrador = nuevo, errores = if (s.errores.isEmpty()) s.errores else ValidadorVeterinario.errores(nuevo.comoPerfil("")).filterKeys { it in s.errores })
    }

    fun mensajeVisto() = _estado.update { it.copy(mensaje = null) }

    /** Guarda; si era una activación, cambia al modo veterinario. */
    fun guardar(onListo: (activado: Boolean) -> Unit) {
        val s = _estado.value
        val perfil = s.borrador.comoPerfil(cuenta.usuario.value?.id.orEmpty())
        val errores = ValidadorVeterinario.errores(perfil)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(errores = errores, mensaje = errores.values.first()) }
            return
        }
        _estado.update { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                veterinarios.guardar(perfil)
                _estado.update { it.copy(guardando = false) }
                if (!s.existente) modo.cambiar(ModoUso.VETERINARIO)
                onListo(!s.existente)
            } catch (e: ProfesionalException) {
                _estado.update { it.copy(guardando = false, mensaje = e.message) }
            }
        }
    }
}

private val LOCALE = Locale.forLanguageTag("es-CO")

/** Perfil de veterinario (RF-22): activación y edición. */
@Composable
fun FormVeterinarioScreen(onVolver: () -> Unit, onGuardado: (activado: Boolean) -> Unit, avisar: (String) -> Unit, viewModel: FormVeterinarioViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val b = estado.borrador
    val e = estado.errores
    var mapa by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(estado.mensaje) {
        estado.mensaje?.let {
            avisar(it)
            viewModel.mensajeVisto()
        }
    }

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
            Text(if (estado.existente) "Mi perfil profesional" else "Perfil de veterinario", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp))
        }
        if (!estado.existente) {
            Text(
                "Así te verán los dueños en el directorio. Después podrás subir tu certificado para obtener la insignia Verificado.",
                style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave,
            )
        }
        if (estado.cargando) {
            Text("Cargando…", color = Pc.TintaSuave)
            return@Column
        }

        Seccion("Tú") {
            CampoTexto(b.nombre, { v -> viewModel.cambiar { it.copy(nombre = v) } }, "Nombre completo *", error = e["nombre"], modifier = Modifier.testTag("vet_nombre"))
            CampoTexto(b.clinica, { v -> viewModel.cambiar { it.copy(clinica = v) } }, "Clínica o consultorio")
            CampoTexto(b.especialidades, { v -> viewModel.cambiar { it.copy(especialidades = v) } }, "Especialidades (separadas por coma)")
            CampoTexto(b.descripcion, { v -> viewModel.cambiar { it.copy(descripcion = v.take(1000)) } }, "Descripción", lineas = 3)
        }

        Seccion("Especies que atiendes *") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Especie.entries.forEach { esp ->
                    ChipOpcion(esp.etiqueta, esp in b.especies, onClick = {
                        viewModel.cambiar { it.copy(especies = if (esp in it.especies) it.especies - esp else it.especies + esp) }
                    }, modifier = Modifier.testTag("vet_especie_${esp.name}"))
                }
            }
            e["especies"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }

        Seccion("Servicios") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ServicioVet.entries.forEach { s ->
                    ChipOpcion(s.etiqueta, s in b.servicios, onClick = {
                        viewModel.cambiar { it.copy(servicios = if (s in it.servicios) it.servicios - s else it.servicios + s) }
                    })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Atiendo a domicilio", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(
                    b.domicilio, { v -> viewModel.cambiar { it.copy(domicilio = v) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = Pc.Naranja),
                )
            }
            CampoTexto(b.tarifa, { v -> viewModel.cambiar { it.copy(tarifa = v.take(100)) } }, "Tarifa de consulta (referencial, opcional)")
        }

        Seccion("Contacto") {
            CampoTexto(b.telefono, { v -> viewModel.cambiar { it.copy(telefono = v) } }, "Teléfono *", teclado = KeyboardType.Phone, error = e["telefono"], modifier = Modifier.testTag("vet_telefono"))
            CampoTexto(b.whatsapp, { v -> viewModel.cambiar { it.copy(whatsapp = v) } }, "WhatsApp", teclado = KeyboardType.Phone, error = e["whatsapp"])
            CampoTexto(b.correo, { v -> viewModel.cambiar { it.copy(correo = v) } }, "Correo", teclado = KeyboardType.Email, error = e["correo"])
        }

        Seccion("Horario") {
            Text("Escribe los rangos de cada día, p. ej. 8:00-12:00, 14:00-18:00. Déjalo vacío si no atiendes.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            DayOfWeek.entries.forEach { dia ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dia.getDisplayName(TextStyle.SHORT, LOCALE).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(52.dp))
                    CampoTexto(
                        b.horario[dia].orEmpty(),
                        { v -> viewModel.cambiar { it.copy(horario = it.horario + (dia to v.take(60))) } },
                        "Horario",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Seccion("Dónde atiendes") {
            CampoTexto(b.ciudad, { v -> viewModel.cambiar { it.copy(ciudad = v) } }, "Ciudad *", error = e["ciudad"], modifier = Modifier.testTag("vet_ciudad"))
            CampoTexto(b.direccion, { v -> viewModel.cambiar { it.copy(direccion = v) } }, "Dirección *", error = e["direccion"], modifier = Modifier.testTag("vet_direccion"))
            Text(
                if (b.ubicacion != null) "Consultorio ubicado en el mapa ✓" else "Aún no has ubicado tu consultorio en el mapa *",
                style = MaterialTheme.typography.bodyMedium,
                color = if (b.ubicacion == null && "ubicacion" in e) MaterialTheme.colorScheme.error else Pc.Tinta,
            )
            BotonSecundario(if (b.ubicacion != null) "Cambiar ubicación" else "Ubicar en el mapa", onClick = { mapa = true }, modifier = Modifier.testTag("vet_ubicar"))
        }

        BotonPrincipal(
            if (estado.existente) "Guardar cambios" else "Activar perfil de veterinario",
            onClick = { viewModel.guardar(onGuardado) },
            cargando = estado.guardando,
            modifier = Modifier.testTag("vet_guardar"),
        )
    }

    if (mapa) {
        DialogoUbicacion(
            inicial = b.ubicacion,
            onElegir = { c ->
                viewModel.cambiar { it.copy(ubicacion = c) }
                mapa = false
            },
            onCerrar = { mapa = false },
            avisar = avisar,
        )
    }
}

/** Pantalla completa para poner el pin sobre el consultorio o el lugar donde se atiende. */
@Composable
fun DialogoUbicacion(
    inicial: Coordenada?,
    onElegir: (Coordenada) -> Unit,
    onCerrar: () -> Unit,
    avisar: (String) -> Unit,
    titulo: String = "Ubica tu consultorio",
    ayuda: String = "Mueve el mapa hasta que el pin quede sobre la entrada.",
) {
    val contexto = LocalContext.current
    var centro by remember { mutableStateOf(inicial ?: com.udenar.petcare.ui.mapas.CENTRO_INICIAL) }
    var irA by remember { mutableStateOf<Coordenada?>(null) }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) ubicacionActual(contexto) { c -> if (c != null) irA = c else avisar("No pudimos obtener tu ubicación") }
        else avisar("Sin permiso de ubicación: mueve el mapa a mano")
    }
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Pc.Crema)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BotonCircular(Icons.Rounded.Close, "Cerrar", onClick = onCerrar)
                Text(titulo, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 12.dp))
            }
            Text(ayuda, style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(MaterialTheme.shapes.large),
            ) {
                SelectorUbicacion(inicial, irA, onCentro = { centro = it }, modifier = Modifier.fillMaxSize())
            }
            Text(ATRIBUCION_MAPA, style = MaterialTheme.typography.labelSmall, color = Pc.TintaSuave)
            BotonSecundario("Usar mi ubicación", onClick = {
                permiso.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            })
            BotonPrincipal("Usar este punto", onClick = { onElegir(centro) }, modifier = Modifier.testTag("vet_usar_punto"))
        }
    }
}
