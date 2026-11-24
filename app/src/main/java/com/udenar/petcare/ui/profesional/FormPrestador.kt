package com.udenar.petcare.ui.profesional

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.udenar.petcare.data.fotos.ArchivosFotos
import com.udenar.petcare.domain.cuenta.RepositorioCuenta
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.ModoUso
import com.udenar.petcare.domain.paseos.Coordenada
import com.udenar.petcare.domain.profesionales.Categoria
import com.udenar.petcare.domain.profesionales.ModalidadPrestador
import com.udenar.petcare.domain.profesionales.ModoPrecio
import com.udenar.petcare.domain.profesionales.PerfilPrestador
import com.udenar.petcare.domain.profesionales.Precios
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.RepositorioModo
import com.udenar.petcare.domain.profesionales.RepositorioPrestadores
import com.udenar.petcare.domain.profesionales.ServicioPrestador
import com.udenar.petcare.domain.profesionales.TipoPrestador
import com.udenar.petcare.domain.profesionales.UnidadPrecio
import com.udenar.petcare.domain.profesionales.ValidadorPrestador
import com.udenar.petcare.ui.componentes.BotonCircular
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.BotonSecundario
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.Chip
import com.udenar.petcare.ui.componentes.ChipOpcion
import com.udenar.petcare.ui.componentes.Seccion
import com.udenar.petcare.ui.componentes.SelectorFecha
import com.udenar.petcare.ui.theme.Pc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class BorradorPrestador(
    val nombre: String = "",
    val tipo: TipoPrestador = TipoPrestador.PERSONA,
    val descripcion: String = "",
    val telefono: String = "",
    val whatsapp: String = "",
    val correo: String = "",
    val categorias: List<String> = emptyList(),
    val especies: Set<Especie> = emptySet(),
    val modalidades: Set<ModalidadPrestador> = setOf(ModalidadPrestador.DOMICILIO),
    val servicios: List<ServicioPrestador> = emptyList(),
    val horario: Map<DayOfWeek, String> = emptyMap(),
    val noDisponible: List<LocalDate> = emptyList(),
    val ciudad: String = "",
    val direccion: String = "",
    val zona: String = "",
    val radioKm: String = "",
    val ubicacion: Coordenada? = null,
    val recibiendo: Boolean = true,
    val galeria: List<String> = emptyList(),
    val foto: String? = null,
) {
    fun comoPerfil(id: String) = PerfilPrestador(
        id = id, nombre = nombre, tipo = tipo, descripcion = descripcion, telefono = telefono, whatsapp = whatsapp, correo = correo,
        ciudad = ciudad, direccion = direccion, zona = zona, radioKm = radioKm.trim().toIntOrNull(),
        lat = ubicacion?.lat ?: 0.0, lon = ubicacion?.lon ?: 0.0, modalidades = modalidades, especies = especies,
        categorias = categorias, servicios = servicios.filter { it.categoriaId in categorias }, horario = horario.filterValues { it.isNotBlank() },
        noDisponible = noDisponible, recibiendo = recibiendo, galeria = galeria, activo = true, foto = foto,
    )

    companion object {
        fun desde(p: PerfilPrestador) = BorradorPrestador(
            p.nombre, p.tipo, p.descripcion, p.telefono, p.whatsapp.orEmpty(), p.correo.orEmpty(), p.categorias, p.especies, p.modalidades,
            p.servicios, p.horario, p.noDisponible, p.ciudad, p.direccion.orEmpty(), p.zona.orEmpty(), p.radioKm?.toString().orEmpty(),
            Coordenada(p.lat, p.lon), p.recibiendo, p.galeria, p.foto,
        )
    }
}

data class EstadoFormPrestador(
    val borrador: BorradorPrestador = BorradorPrestador(),
    val categorias: List<Categoria> = emptyList(),
    val cargando: Boolean = true,
    val guardando: Boolean = false,
    val subiendoFotos: Boolean = false,
    val subiendoFoto: Boolean = false,
    val errores: Map<String, String> = emptyMap(),
    val mensaje: String? = null,
    val existente: Boolean = false,
)

@HiltViewModel
class FormPrestadorViewModel @Inject constructor(
    private val prestadores: RepositorioPrestadores,
    private val modo: RepositorioModo,
    private val cuenta: RepositorioCuenta,
    private val archivos: ArchivosFotos,
    private val reloj: Clock,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoFormPrestador())
    val estado = _estado.asStateFlow()
    val hoy: LocalDate get() = LocalDate.now(reloj)

    init {
        viewModelScope.launch {
            try {
                val categorias = prestadores.categorias()
                val perfil = prestadores.cargarMiPerfil()
                _estado.update {
                    it.copy(
                        categorias = categorias,
                        borrador = perfil?.let(BorradorPrestador::desde) ?: BorradorPrestador(nombre = cuenta.usuario.value?.nombre.orEmpty()),
                        cargando = false,
                        existente = perfil?.activo == true,
                    )
                }
            } catch (e: ProfesionalException) {
                _estado.update { it.copy(mensaje = e.message, cargando = false) }
            }
        }
    }

    fun cambiar(f: (BorradorPrestador) -> BorradorPrestador) = _estado.update { s ->
        val nuevo = f(s.borrador)
        s.copy(borrador = nuevo, errores = if (s.errores.isEmpty()) s.errores else ValidadorPrestador.errores(nuevo.comoPerfil("")).filterKeys { it in s.errores })
    }

    fun mensajeVisto() = _estado.update { it.copy(mensaje = null) }

    /** Comprime y sube cada foto; quedan en la galería al guardar el perfil. */
    fun agregarFotos(uris: List<Uri>) {
        val espacio = ValidadorPrestador.MAX_GALERIA - _estado.value.borrador.galeria.size
        if (espacio <= 0) {
            _estado.update { it.copy(mensaje = "Ya tienes ${ValidadorPrestador.MAX_GALERIA} fotos") }
            return
        }
        _estado.update { it.copy(subiendoFotos = true) }
        viewModelScope.launch {
            try {
                uris.take(espacio).forEach { uri ->
                    val archivo = archivos.comprimir(uri, "galeria")
                    val url = try {
                        prestadores.subirFoto(archivo.readBytes())
                    } finally {
                        archivo.delete()
                    }
                    cambiar { it.copy(galeria = it.galeria + url) }
                }
            } catch (e: ProfesionalException) {
                _estado.update { it.copy(mensaje = e.message) }
            } catch (_: Exception) {
                _estado.update { it.copy(mensaje = "No pudimos leer una de las fotos") }
            } finally {
                _estado.update { it.copy(subiendoFotos = false) }
            }
        }
    }

    /** Foto de perfil (Fase 6.7): se sube al elegirla y queda al guardar. */
    fun elegirFotoPerfil(uri: Uri) {
        _estado.update { it.copy(subiendoFoto = true) }
        viewModelScope.launch {
            try {
                val archivo = archivos.comprimir(uri, "perfil")
                val url = try { prestadores.subirFoto(archivo.readBytes()) } finally { archivo.delete() }
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

    fun quitarFoto(url: String) {
        cambiar { it.copy(galeria = it.galeria - url) }
        viewModelScope.launch { runCatching { prestadores.borrarFoto(url) } }
    }

    fun guardar(onListo: (activado: Boolean) -> Unit) {
        val s = _estado.value
        val perfil = s.borrador.comoPerfil(cuenta.usuario.value?.id.orEmpty())
        val errores = ValidadorPrestador.errores(perfil)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(errores = errores, mensaje = errores.values.first()) }
            return
        }
        _estado.update { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                prestadores.guardar(perfil)
                _estado.update { it.copy(guardando = false) }
                if (!s.existente) modo.cambiar(ModoUso.PRESTADOR)
                onListo(!s.existente)
            } catch (e: ProfesionalException) {
                _estado.update { it.copy(guardando = false, mensaje = e.message) }
            }
        }
    }
}

private val LOCALE = Locale.forLanguageTag("es-CO")
private val FECHA_CORTA = DateTimeFormatter.ofPattern("d MMM", LOCALE)

/** Perfil de prestador (RF-31, RF-32): activación y edición. */
@Composable
fun FormPrestadorScreen(onVolver: () -> Unit, onGuardado: (activado: Boolean) -> Unit, avisar: (String) -> Unit, viewModel: FormPrestadorViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val b = estado.borrador
    val e = estado.errores
    var mapa by remember { mutableStateOf(false) }
    var servicioEditado by remember { mutableStateOf<ServicioPrestador?>(null) }
    var fechaNoDisp by remember { mutableStateOf<LocalDate?>(null) }
    val fotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(ValidadorPrestador.MAX_GALERIA)) { uris ->
        if (uris.isNotEmpty()) viewModel.agregarFotos(uris)
    }
    LaunchedEffect(estado.mensaje) {
        estado.mensaje?.let {
            avisar(it)
            viewModel.mensajeVisto()
        }
    }

    Column(Modifier.fillMaxSize().background(Pc.Crema).statusBarsPadding().navigationBarsPadding().imePadding()) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotonCircular(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onVolver)
            Text(if (estado.existente) "Mi perfil de servicios" else "Ofrece tus servicios", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp))
        }
        if (!estado.cargando) VistaPreviaPerfil { com.udenar.petcare.ui.servicios.TarjetaPrestador(b.comoPerfil(""), estado.categorias, onClick = {}) }
    }
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (estado.cargando) {
            Text("Cargando…", color = Pc.TintaSuave)
            return@Column
        }
        if (!estado.existente) {
            Text("Así te verán los dueños en el directorio. Los precios son orientativos y el pago se acuerda directamente contigo.", style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
        }

        Seccion("Quién eres") {
            com.udenar.petcare.ui.componentes.EditorFotoPerfil(
                b.foto, b.nombre, estado.subiendoFoto, onElegir = viewModel::elegirFotoPerfil, onQuitar = { viewModel.cambiar { it.copy(foto = null) } },
                fondo = Pc.SolClaro, color = Pc.Tinta,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TipoPrestador.entries.forEach { t -> ChipOpcion(t.etiqueta, b.tipo == t, onClick = { viewModel.cambiar { it.copy(tipo = t) } }) }
            }
            CampoTexto(b.nombre, { v -> viewModel.cambiar { it.copy(nombre = v.take(100)) } }, if (b.tipo == TipoPrestador.NEGOCIO) "Nombre del negocio *" else "Tu nombre *", error = e["nombre"], modifier = Modifier.testTag("pres_nombre"))
            CampoTexto(b.descripcion, { v -> viewModel.cambiar { it.copy(descripcion = v.take(1000)) } }, "Descripción *", error = e["descripcion"], lineas = 3, modifier = Modifier.testTag("pres_descripcion"))
        }

        Seccion("Categorías *") {
            Text("La primera que elijas es la principal.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                estado.categorias.forEach { c ->
                    val i = b.categorias.indexOf(c.id)
                    ChipOpcion(
                        c.nombre + if (i == 0) " · principal" else "", i >= 0, icono = c.icono3d,
                        onClick = { viewModel.cambiar { it.copy(categorias = if (c.id in it.categorias) it.categorias - c.id else it.categorias + c.id) } },
                        modifier = Modifier.testTag("pres_categoria_${c.id}"),
                    )
                }
            }
            e["categorias"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }

        Seccion("Servicios y precios") {
            if (b.servicios.isEmpty()) Text("Agrega lo que ofreces: precio fijo, \"desde\" o a cotizar.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            b.servicios.forEach { s ->
                val cat = estado.categorias.firstOrNull { it.id == s.categoriaId }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { servicioEditado = s }
                        .padding(vertical = 6.dp)
                        .testTag("pres_servicio_${s.nombre}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(cat?.icono ?: "•", modifier = Modifier.padding(end = 10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.nombre, style = MaterialTheme.typography.titleSmall)
                        Text(Precios.texto(s), style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                    }
                }
            }
            e["servicios"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            BotonSecundario("Agregar servicio", onClick = {
                if (b.categorias.isEmpty()) avisar("Primero elige una categoría")
                else servicioEditado = ServicioPrestador(UUID.randomUUID().toString(), b.categorias.first(), "")
            }, modifier = Modifier.testTag("pres_agregar_servicio"))
        }

        Seccion("A quién y cómo atiendes *") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Especie.entries.forEach { esp ->
                    ChipOpcion(esp.etiqueta, esp in b.especies, onClick = {
                        viewModel.cambiar { it.copy(especies = if (esp in it.especies) it.especies - esp else it.especies + esp) }
                    }, modifier = Modifier.testTag("pres_especie_${esp.name}"))
                }
            }
            e["especies"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModalidadPrestador.entries.forEach { m ->
                    ChipOpcion(m.etiqueta, m in b.modalidades, onClick = {
                        viewModel.cambiar { it.copy(modalidades = if (m in it.modalidades) it.modalidades - m else it.modalidades + m) }
                    })
                }
            }
            e["modalidades"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }

        Seccion("Contacto") {
            CampoTexto(b.telefono, { v -> viewModel.cambiar { it.copy(telefono = v) } }, "Teléfono *", teclado = KeyboardType.Phone, error = e["telefono"], modifier = Modifier.testTag("pres_telefono"))
            CampoTexto(b.whatsapp, { v -> viewModel.cambiar { it.copy(whatsapp = v) } }, "WhatsApp", teclado = KeyboardType.Phone, error = e["whatsapp"])
            CampoTexto(b.correo, { v -> viewModel.cambiar { it.copy(correo = v) } }, "Correo", teclado = KeyboardType.Email, error = e["correo"])
        }

        Seccion("Dónde trabajas") {
            CampoTexto(b.ciudad, { v -> viewModel.cambiar { it.copy(ciudad = v) } }, "Ciudad *", error = e["ciudad"], modifier = Modifier.testTag("pres_ciudad"))
            CampoTexto(b.zona, { v -> viewModel.cambiar { it.copy(zona = v.take(120)) } }, "Zona de cobertura (barrios, sector…)", error = e["zona"], modifier = Modifier.testTag("pres_zona"))
            CampoTexto(b.radioKm, { v -> viewModel.cambiar { it.copy(radioKm = v.filter(Char::isDigit).take(3)) } }, "Radio en km (opcional)", teclado = KeyboardType.Number)
            CampoTexto(b.direccion, { v -> viewModel.cambiar { it.copy(direccion = v.take(150)) } }, "Dirección del establecimiento (opcional)")
            Text(
                if (b.ubicacion != null) "Ubicado en el mapa ✓" else "Aún no te has ubicado en el mapa *",
                style = MaterialTheme.typography.bodyMedium,
                color = if (b.ubicacion == null && "ubicacion" in e) MaterialTheme.colorScheme.error else Pc.Tinta,
            )
            BotonSecundario(if (b.ubicacion != null) "Cambiar ubicación" else "Ubicar en el mapa", onClick = { mapa = true }, modifier = Modifier.testTag("pres_ubicar"))
        }

        Seccion("Disponibilidad") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Recibiendo solicitudes", style = MaterialTheme.typography.titleSmall)
                    Text("Apágalo cuando no puedas atender", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                }
                Switch(b.recibiendo, { v -> viewModel.cambiar { it.copy(recibiendo = v) } }, colors = SwitchDefaults.colors(checkedTrackColor = Pc.Naranja))
            }
            Text("Horario por día, p. ej. 7:00-11:00, 15:00-18:00. Vacío = no atiendes.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            DayOfWeek.entries.forEach { dia ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dia.getDisplayName(TextStyle.SHORT, LOCALE).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(52.dp))
                    CampoTexto(b.horario[dia].orEmpty(), { v -> viewModel.cambiar { it.copy(horario = it.horario + (dia to v.take(60))) } }, "Horario", modifier = Modifier.weight(1f))
                }
            }
            Text("Fechas en que no atiendes", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                b.noDisponible.sorted().forEach { f ->
                    ChipOpcion("${f.format(FECHA_CORTA)} ✕", true, onClick = { viewModel.cambiar { it.copy(noDisponible = it.noDisponible - f) } })
                }
            }
            SelectorFecha("Agregar fecha", fechaNoDisp, { f ->
                if (f != null) viewModel.cambiar { it.copy(noDisponible = (it.noDisponible + f).distinct()) }
                fechaNoDisp = null
            }, desde = viewModel.hoy, textoVacio = "Elegir")
        }

        Seccion("Galería de trabajos") {
            Text("Hasta ${ValidadorPrestador.MAX_GALERIA} fotos. Son públicas en tu perfil.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                b.galeria.forEach { url ->
                    Box {
                        AsyncImage(url, contentDescription = "Foto de trabajo", contentScale = ContentScale.Crop, modifier = Modifier.size(96.dp).clip(MaterialTheme.shapes.medium))
                        Icon(
                            Icons.Rounded.Close, contentDescription = "Quitar foto", tint = Pc.Blanco,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(MaterialTheme.shapes.small).background(Pc.Tinta.copy(alpha = 0.5f)).clickable { viewModel.quitarFoto(url) },
                        )
                    }
                }
            }
            if (b.galeria.size < ValidadorPrestador.MAX_GALERIA) {
                BotonSecundario(if (estado.subiendoFotos) "Subiendo…" else "Agregar fotos", onClick = {
                    if (!estado.subiendoFotos) fotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
            }
        }

        BotonPrincipal(
            if (estado.existente) "Guardar cambios" else "Activar perfil de prestador",
            onClick = { viewModel.guardar(onGuardado) },
            cargando = estado.guardando,
            habilitado = !estado.subiendoFotos,
            modifier = Modifier.testTag("pres_guardar"),
        )
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
            titulo = "¿Dónde te encuentran?",
            ayuda = "Pon el pin en tu establecimiento o en el centro de la zona donde trabajas.",
        )
    }
    servicioEditado?.let { s ->
        DialogoServicio(
            inicial = s,
            categorias = estado.categorias.filter { it.id in b.categorias },
            especies = b.especies,
            onGuardar = { nuevo ->
                viewModel.cambiar { br -> br.copy(servicios = br.servicios.filter { it.id != nuevo.id } + nuevo) }
                servicioEditado = null
            },
            onEliminar = {
                viewModel.cambiar { br -> br.copy(servicios = br.servicios.filter { it.id != s.id }) }
                servicioEditado = null
            },
            onCerrar = { servicioEditado = null },
        )
    }
}

@Composable
private fun DialogoServicio(
    inicial: ServicioPrestador,
    categorias: List<Categoria>,
    especies: Set<Especie>,
    onGuardar: (ServicioPrestador) -> Unit,
    onEliminar: () -> Unit,
    onCerrar: () -> Unit,
) {
    var s by remember { mutableStateOf(inicial) }
    var monto by remember { mutableStateOf(inicial.montoCop?.toString().orEmpty()) }
    var errores by remember { mutableStateOf(emptyList<String>()) }
    AlertDialog(
        onDismissRequest = onCerrar, containerColor = Pc.Crema,
        title = { Text(if (inicial.nombre.isEmpty()) "Nuevo servicio" else "Editar servicio", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categorias.forEach { c -> ChipOpcion(c.nombre, s.categoriaId == c.id, onClick = { s = s.copy(categoriaId = c.id) }, icono = c.icono3d) }
                }
                CampoTexto(s.nombre, { s = s.copy(nombre = it.take(80)) }, "Nombre del servicio *", modifier = Modifier.testTag("servicio_nombre"))
                CampoTexto(s.descripcion.orEmpty(), { s = s.copy(descripcion = it.take(300)) }, "Descripción (opcional)", lineas = 2)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModoPrecio.entries.forEach { m -> ChipOpcion(m.etiqueta, s.modo == m, onClick = { s = s.copy(modo = m) }, modifier = Modifier.testTag("modo_${m.name}")) }
                }
                if (s.modo != ModoPrecio.COTIZACION) {
                    CampoTexto(monto, { monto = it.filter(Char::isDigit).take(9) }, "Precio en pesos *", teclado = KeyboardType.Number, modifier = Modifier.testTag("servicio_monto"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        UnidadPrecio.entries.forEach { u -> ChipOpcion(u.etiqueta, s.unidad == u, onClick = { s = s.copy(unidad = if (s.unidad == u) null else u) }) }
                    }
                }
                if (especies.size > 1) {
                    Text("Solo para (opcional)", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        especies.forEach { esp -> ChipOpcion(esp.etiqueta, esp in s.especies, onClick = { s = s.copy(especies = if (esp in s.especies) s.especies - esp else s.especies + esp) }) }
                    }
                }
                errores.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text("Precios orientativos: el pago se coordina fuera de PetCare.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val listo = s.copy(montoCop = monto.toIntOrNull()?.takeIf { s.modo != ModoPrecio.COTIZACION }, especies = s.especies.intersect(especies))
                errores = ValidadorPrestador.erroresServicio(listo, categorias.map { it.id })
                if (errores.isEmpty()) onGuardar(listo)
            }, modifier = Modifier.testTag("servicio_guardar")) { Text("Listo") }
        },
        dismissButton = {
            Row {
                if (inicial.nombre.isNotEmpty()) TextButton(onClick = onEliminar) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onCerrar) { Text("Cancelar") }
            }
        },
    )
}

/** Chip de categoría con su ícono (se reutiliza en el directorio y el perfil). */
@Composable
fun ChipCategoria(c: Categoria, principal: Boolean = false) = Chip(c.nombre, fondo = if (principal) Pc.SolClaro else Pc.Blanco, icono = c.icono3d)
