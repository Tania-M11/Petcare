package com.udenar.petcare.ui.profesional

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    val foto: String? = null,
) {
    fun comoPerfil(id: String) = PerfilVeterinario(
        id = id, nombre = nombre, especies = especies,
        especialidades = especialidades.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        servicios = servicios, clinica = clinica, telefono = telefono, whatsapp = whatsapp, correo = correo,
        descripcion = descripcion, domicilio = domicilio, horario = horario.filterValues { it.isNotBlank() },
        tarifa = tarifa, ciudad = ciudad, direccion = direccion,
        lat = ubicacion?.lat ?: 0.0, lon = ubicacion?.lon ?: 0.0, activo = true, foto = foto,
    )

    companion object {
        fun desde(p: PerfilVeterinario) = BorradorVet(
            p.nombre, p.especies, p.servicios, p.especialidades.joinToString(", "), p.clinica.orEmpty(), p.telefono,
            p.whatsapp.orEmpty(), p.correo.orEmpty(), p.descripcion.orEmpty(), p.domicilio, p.horario, p.tarifa.orEmpty(),
            p.ciudad, p.direccion, Coordenada(p.lat, p.lon), p.activo, p.foto,
        )
    }
}

data class EstadoFormVet(
    val borrador: BorradorVet = BorradorVet(),
    val cargando: Boolean = true,
    val guardando: Boolean = false,
    val subiendoFoto: Boolean = false,
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
    private val archivos: com.udenar.petcare.data.fotos.ArchivosFotos,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoFormVet())

    /** Comprime (512 px) y sube la foto; queda en el perfil al guardar. */
    fun elegirFoto(uri: android.net.Uri) {
        _estado.update { it.copy(subiendoFoto = true) }
        viewModelScope.launch {
            try {
                val archivo = archivos.comprimir(uri, "perfil")
                val url = try { veterinarios.subirFoto(archivo.readBytes()) } finally { archivo.delete() }
                cambiar { it.copy(foto = url) }
            } catch (e: ProfesionalException) {
                _estado.update { it.copy(mensaje = e.message) }
            } catch (_: Exception) {
                _estado.update { it.copy(mensaje = "No pudimos leer la foto") }
            } finally {
                _estado.update { it.copy(subiendoFoto = false) }
            }
        }
    }
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

    /** Revisa solo los datos de un paso; si falta algo lo marca y no deja avanzar. */
    fun validarPaso(claves: Set<String>): Boolean {
        val errores = ValidadorVeterinario.errores(_estado.value.borrador.comoPerfil("")).filterKeys { it in claves }
        _estado.update { it.copy(errores = errores, mensaje = errores.values.firstOrNull()) }
        return errores.isEmpty()
    }

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

private enum class PasoVet(val claves: Set<String> = emptySet()) {
    TU(setOf("nombre")), ESPECIES(setOf("especies")), SERVICIOS, CONTACTO(setOf("telefono", "whatsapp", "correo")),
    HORARIO, DONDE(setOf("ciudad", "direccion", "ubicacion")), SOBRE_TI, RESUMEN,
}

/**
 * Perfil de veterinario (RF-22) por pasos (Fase 6): una pregunta por pantalla con Michi, la
 * tarjeta del directorio que se va armando y un resumen final. Al editar se abre en el resumen
 * y cada dato se cambia en su paso.
 */
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
    if (estado.cargando) {
        Box(Modifier.fillMaxSize().background(Pc.Crema), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = Pc.Naranja)
        }
        return
    }
    val pasos = PasoVet.entries
    // Editar = abrir en el resumen; activar = empezar por el primer paso
    var paso by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(if (estado.existente) PasoVet.RESUMEN.ordinal else 0) }
    var desdeResumen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(estado.existente) }
    val actual = pasos[paso]
    fun siguiente() {
        if (!viewModel.validarPaso(actual.claves)) return
        paso = if (desdeResumen) PasoVet.RESUMEN.ordinal else paso + 1
    }
    fun ir(p: PasoVet) {
        desdeResumen = true
        paso = p.ordinal
    }

    com.udenar.petcare.ui.componentes.AsistentePasos(
        paso = paso, total = pasos.size,
        onAtras = { if (desdeResumen && actual != PasoVet.RESUMEN) paso = PasoVet.RESUMEN.ordinal else if (paso == 0) onVolver() else paso-- },
        onCerrar = onVolver,
        onSaltar = if (actual == PasoVet.HORARIO || actual == PasoVet.SOBRE_TI) ({ siguiente() }) else null,
        abajo = {
            if (actual == PasoVet.RESUMEN) {
                BotonPrincipal(
                    if (estado.existente) "Guardar cambios" else "Activar perfil de veterinario",
                    onClick = { viewModel.guardar(onGuardado) }, cargando = estado.guardando,
                    modifier = Modifier.testTag("vet_guardar"),
                )
            } else {
                BotonPrincipal(if (desdeResumen) "Listo" else "Continuar", onClick = ::siguiente, modifier = Modifier.testTag("continuar"))
            }
        },
    ) { n ->
        when (pasos[n]) {
            PasoVet.TU -> {
                com.udenar.petcare.ui.componentes.PreguntaMichi(
                    if (estado.existente) "Tu perfil" else "¡Hola, colega!",
                    "Así te presentarás a los dueños de mascotas", com.udenar.petcare.domain.recomendaciones.PoseMichi.DOCTOR,
                )
                com.udenar.petcare.ui.componentes.EditorFotoPerfil(b.foto, b.nombre, estado.subiendoFoto, onElegir = viewModel::elegirFoto, onQuitar = { viewModel.cambiar { it.copy(foto = null) } })
                CampoTexto(b.nombre, { v -> viewModel.cambiar { it.copy(nombre = v) } }, "Nombre completo", error = e["nombre"], modifier = Modifier.testTag("vet_nombre"))
                CampoTexto(b.clinica, { v -> viewModel.cambiar { it.copy(clinica = v) } }, "Clínica o consultorio (opcional)")
                VistaPreviaPerfil { com.udenar.petcare.ui.servicios.TarjetaVeterinario(b.comoPerfil(""), onClick = {}) }
            }
            PasoVet.ESPECIES -> {
                com.udenar.petcare.ui.componentes.PreguntaMichi("¿A quiénes atiendes?", "Elige todas las que quieras", com.udenar.petcare.domain.recomendaciones.PoseMichi.AMIGOS)
                ElegirEspecies(b.especies, { esp -> viewModel.cambiar { it.copy(especies = if (esp in it.especies) it.especies - esp else it.especies + esp) } }, "vet")
                e["especies"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            PasoVet.SERVICIOS -> {
                com.udenar.petcare.ui.componentes.PreguntaMichi("¿Qué ofreces?", "Toca los servicios de tu consulta", com.udenar.petcare.domain.recomendaciones.PoseMichi.SALUD)
                ServicioVet.entries.chunked(2).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        fila.forEach { sv ->
                            com.udenar.petcare.ui.componentes.TarjetaOpcionGrande(
                                sv.etiqueta, sv in b.servicios,
                                onClick = { viewModel.cambiar { it.copy(servicios = if (sv in it.servicios) it.servicios - sv else it.servicios + sv) } },
                                icono = sv.icono(), color = Pc.CieloClaro, modifier = Modifier.weight(1f).testTag("vet_servicio_${sv.name}"),
                            )
                        }
                        if (fila.size == 1) Box(Modifier.weight(1f))
                    }
                }
                TarjetaInterruptor(com.udenar.petcare.domain.modelo.Icono.CASA, "Atiendo a domicilio", "Aparecerás en búsquedas de visitas a casa", b.domicilio, { v -> viewModel.cambiar { it.copy(domicilio = v) } })
                CampoTexto(b.tarifa, { v -> viewModel.cambiar { it.copy(tarifa = v.take(100)) } }, "Tarifa de consulta (referencial, opcional)")
                CampoTexto(b.especialidades, { v -> viewModel.cambiar { it.copy(especialidades = v) } }, "Especialidades (opcional, separadas por coma)")
            }
            PasoVet.CONTACTO -> {
                com.udenar.petcare.ui.componentes.PreguntaMichi("¿Cómo te contactan?", "Los dueños te llaman o te escriben desde tu perfil", com.udenar.petcare.domain.recomendaciones.PoseMichi.ALARMA)
                CampoTexto(b.telefono, { v -> viewModel.cambiar { it.copy(telefono = v) } }, "Teléfono", teclado = KeyboardType.Phone, error = e["telefono"], modifier = Modifier.testTag("vet_telefono"))
                if (b.telefono.isNotBlank()) {
                    ChipOpcion("Mi WhatsApp es el mismo número", b.whatsapp == b.telefono, onClick = { viewModel.cambiar { it.copy(whatsapp = if (it.whatsapp == it.telefono) "" else it.telefono) } }, icono = com.udenar.petcare.domain.modelo.Icono.MENSAJE)
                }
                CampoTexto(b.whatsapp, { v -> viewModel.cambiar { it.copy(whatsapp = v) } }, "WhatsApp (opcional)", teclado = KeyboardType.Phone, error = e["whatsapp"])
                CampoTexto(b.correo, { v -> viewModel.cambiar { it.copy(correo = v) } }, "Correo (opcional)", teclado = KeyboardType.Email, error = e["correo"])
            }
            PasoVet.HORARIO -> {
                com.udenar.petcare.ui.componentes.PreguntaMichi("¿Cuándo atiendes?", "Dos toques y listo; puedes ajustar cada día", com.udenar.petcare.domain.recomendaciones.PoseMichi.AGENDA)
                EditorHorarioAmigable(b.horario) { h -> viewModel.cambiar { it.copy(horario = h) } }
            }
            PasoVet.DONDE -> {
                com.udenar.petcare.ui.componentes.PreguntaMichi("¿Dónde atiendes?", "Te mostramos en el mapa del directorio", com.udenar.petcare.domain.recomendaciones.PoseMichi.LEYENDO)
                CampoTexto(b.ciudad, { v -> viewModel.cambiar { it.copy(ciudad = v) } }, "Ciudad", error = e["ciudad"], modifier = Modifier.testTag("vet_ciudad"))
                CampoTexto(b.direccion, { v -> viewModel.cambiar { it.copy(direccion = v) } }, "Dirección", error = e["direccion"], modifier = Modifier.testTag("vet_direccion"))
                TarjetaUbicacion(b.ubicacion != null, "ubicacion" in e && b.ubicacion == null, "Consultorio ubicado en el mapa ✓", onClick = { mapa = true }, tag = "vet_ubicar")
            }
            PasoVet.SOBRE_TI -> {
                com.udenar.petcare.ui.componentes.PreguntaMichi("Cuéntales de ti", "Tu experiencia, cómo trabajas, lo que te hace especial (opcional)", com.udenar.petcare.domain.recomendaciones.PoseMichi.LEYENDO)
                CampoTexto(b.descripcion, { v -> viewModel.cambiar { it.copy(descripcion = v.take(1000)) } }, "Sobre mí", lineas = 5)
                Text("${b.descripcion.length}/1000", style = MaterialTheme.typography.labelSmall, color = Pc.TintaSuave)
            }
            PasoVet.RESUMEN -> {
                com.udenar.petcare.ui.componentes.PreguntaMichi(
                    if (estado.existente) "Tu perfil" else "¡Listo para recibir pacientes!",
                    "Toca un dato para cambiarlo", com.udenar.petcare.domain.recomendaciones.PoseMichi.CELEBRA,
                )
                VistaPreviaPerfil { com.udenar.petcare.ui.servicios.TarjetaVeterinario(b.comoPerfil(""), onClick = {}) }
                Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Pc.Blanco).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    com.udenar.petcare.ui.componentes.FilaResumen("Tú", listOfNotNull(b.nombre.ifBlank { null }, b.clinica.ifBlank { null }).joinToString(" · ").ifEmpty { "Sin nombre" }, onEditar = { ir(PasoVet.TU) })
                    com.udenar.petcare.ui.componentes.FilaResumen("Atiendes", b.especies.joinToString { it.etiqueta }.ifEmpty { "Sin especies" }, onEditar = { ir(PasoVet.ESPECIES) })
                    com.udenar.petcare.ui.componentes.FilaResumen("Servicios", (b.servicios.map { it.etiqueta } + listOfNotNull("A domicilio".takeIf { b.domicilio })).joinToString().ifEmpty { "Sin servicios" }, onEditar = { ir(PasoVet.SERVICIOS) })
                    com.udenar.petcare.ui.componentes.FilaResumen("Contacto", b.telefono.ifBlank { "Sin teléfono" }, onEditar = { ir(PasoVet.CONTACTO) })
                    com.udenar.petcare.ui.componentes.FilaResumen("Horario", resumenHorario(b.horario), onEditar = { ir(PasoVet.HORARIO) })
                    com.udenar.petcare.ui.componentes.FilaResumen("Dónde", listOf(b.direccion, b.ciudad).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { "Sin dirección" } + if (b.ubicacion != null) " ✓" else "", onEditar = { ir(PasoVet.DONDE) })
                    com.udenar.petcare.ui.componentes.FilaResumen("Sobre ti", b.descripcion.take(60).ifBlank { "Sin descripción" }, onEditar = { ir(PasoVet.SOBRE_TI) })
                }
                if (!estado.existente) {
                    Text(
                        "Después podrás subir tu certificado para obtener la insignia Verificado.",
                        style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave,
                    )
                }
            }
        }
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

/** Ícono de cada servicio veterinario. */
fun ServicioVet.icono(): com.udenar.petcare.domain.modelo.Icono = when (this) {
    ServicioVet.CONSULTA -> com.udenar.petcare.domain.modelo.Icono.ESTETOSCOPIO_VET
    ServicioVet.VACUNACION -> com.udenar.petcare.domain.modelo.Icono.VACUNA
    ServicioVet.DESPARASITACION -> com.udenar.petcare.domain.modelo.Icono.ESCUDO
    ServicioVet.CIRUGIA -> com.udenar.petcare.domain.modelo.Icono.HOSPITAL
    ServicioVet.OTROS -> com.udenar.petcare.domain.modelo.Icono.MAS
}

/** Tarjeta grande para ubicar el lugar en el mapa; se pinta de verde cuando ya está. */
@Composable
fun TarjetaUbicacion(ubicado: Boolean, conError: Boolean, textoListo: String, onClick: () -> Unit, tag: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(if (ubicado) Pc.MentaClara else Pc.Blanco)
            .border(1.5.dp, if (conError) MaterialTheme.colorScheme.error else if (ubicado) Pc.Menta else Pc.Linea, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.udenar.petcare.ui.componentes.BaldosaIcono(if (ubicado) com.udenar.petcare.domain.modelo.Icono.CHECK else com.udenar.petcare.domain.modelo.Icono.MAPA, tam = 46.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(if (ubicado) textoListo else "Ubicar en el mapa", style = MaterialTheme.typography.titleSmall)
            Text(if (ubicado) "Toca para cambiarlo" else "Mueve el mapa hasta el lugar exacto", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
        }
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
