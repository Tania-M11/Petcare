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
    ruta: androidx.lifecycle.SavedStateHandle,
    private val prestadores: RepositorioPrestadores,
    private val modo: RepositorioModo,
    private val cuenta: RepositorioCuenta,
    private val archivos: ArchivosFotos,
    private val reloj: Clock,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoFormPrestador())
    val estado = _estado.asStateFlow()
    val hoy: LocalDate get() = LocalDate.now(reloj)

    /** Categoría elegida desde Perfil ("Baño y peluquería", "Cuidador y paseador"). */
    private val categoriaInicial: String? = runCatching { ruta.get<String>("categoria") }.getOrNull()

    /** Revisa solo los datos de un paso; si falta algo lo marca y no deja avanzar. */
    fun validarPaso(claves: Set<String>): Boolean {
        val errores = ValidadorPrestador.errores(_estado.value.borrador.comoPerfil("")).filterKeys { it in claves }
        _estado.update { it.copy(errores = errores, mensaje = errores.values.firstOrNull()) }
        return errores.isEmpty()
    }

    init {
        viewModelScope.launch {
            try {
                val categorias = prestadores.categorias()
                val perfil = prestadores.cargarMiPerfil()
                _estado.update {
                    it.copy(
                        categorias = categorias,
                        borrador = (perfil?.let(BorradorPrestador::desde) ?: BorradorPrestador(nombre = cuenta.usuario.value?.nombre.orEmpty())).let { b ->
                            if (categoriaInicial != null && categoriaInicial !in b.categorias && categorias.any { c -> c.id == categoriaInicial }) b.copy(categorias = b.categorias + categoriaInicial) else b
                        },
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

private enum class PasoPres(val claves: Set<String> = emptySet()) {
    TU(setOf("nombre")), CATEGORIAS(setOf("categorias")), SERVICIOS(setOf("servicios")), A_QUIEN(setOf("especies", "modalidades")),
    CONTACTO(setOf("telefono", "whatsapp", "correo")), DONDE(setOf("ciudad", "zona", "ubicacion")), HORARIO,
    SOBRE_TI(setOf("descripcion", "galeria")), RESUMEN,
}

/** Michi en la situación de cada categoría (las nuevas usan su ícono). */
private fun dibujoCategoria(id: String): Int? = when (id) {
    "PASEADOR" -> com.udenar.petcare.R.drawable.gato_celebra
    "BANO_PELUQUERIA" -> com.udenar.petcare.R.drawable.gato_higiene
    "CUIDADO_TEMPORAL" -> com.udenar.petcare.R.drawable.gato_dormido
    else -> null
}

/**
 * Perfil de cuidador, paseador o peluquería (RF-31, RF-32) por pasos (Fase 6): una pregunta por
 * pantalla con Michi, la tarjeta del directorio armándose y un resumen final. Al editar se abre
 * en el resumen y cada dato se cambia en su paso.
 */
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
    if (estado.cargando) {
        Box(Modifier.fillMaxSize().background(Pc.Crema), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = Pc.Naranja)
        }
        return
    }
    val pasos = PasoPres.entries
    var paso by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(if (estado.existente) PasoPres.RESUMEN.ordinal else 0) }
    var desdeResumen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(estado.existente) }
    val actual = pasos[paso]
    fun siguiente() {
        if (!viewModel.validarPaso(actual.claves)) return
        paso = if (desdeResumen) PasoPres.RESUMEN.ordinal else paso + 1
    }
    fun ir(p: PasoPres) {
        desdeResumen = true
        paso = p.ordinal
    }

    com.udenar.petcare.ui.componentes.AsistentePasos(
        paso = paso, total = pasos.size,
        onAtras = { if (desdeResumen && actual != PasoPres.RESUMEN) paso = PasoPres.RESUMEN.ordinal else if (paso == 0) onVolver() else paso-- },
        onCerrar = onVolver,
        onSaltar = if (actual == PasoPres.HORARIO || (actual == PasoPres.SERVICIOS && b.servicios.isEmpty())) ({ siguiente() }) else null,
        abajo = {
            if (actual == PasoPres.RESUMEN) {
                BotonPrincipal(
                    if (estado.existente) "Guardar cambios" else "Activar mi perfil",
                    onClick = { viewModel.guardar(onGuardado) }, cargando = estado.guardando, habilitado = !estado.subiendoFotos,
                    modifier = Modifier.testTag("pres_guardar"),
                )
            } else {
                BotonPrincipal(if (desdeResumen) "Listo" else "Continuar", onClick = ::siguiente, habilitado = !estado.subiendoFotos, modifier = Modifier.testTag("continuar"))
            }
        },
    ) { n ->
        when (pasos[n]) {
            PasoPres.TU -> {
                com.udenar.petcare.ui.componentes.PreguntaMichi(
                    if (estado.existente) "Tu perfil" else "Crea tu perfil de servicios",
                    "Cuéntanos quién eres para que los dueños confíen en ti", com.udenar.petcare.domain.recomendaciones.PoseMichi.SALUDO,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TipoPrestador.entries.forEach { t ->
                        com.udenar.petcare.ui.componentes.TarjetaOpcionGrande(
                            if (t == TipoPrestador.PERSONA) "Soy una persona" else "Tengo un negocio", b.tipo == t,
                            onClick = { viewModel.cambiar { it.copy(tipo = t) } },
                            icono = if (t == TipoPrestador.PERSONA) com.udenar.petcare.domain.modelo.Icono.CORAZON else com.udenar.petcare.domain.modelo.Icono.TIENDA,
                            color = Pc.SolClaro, modifier = Modifier.weight(1f),
                        )
                    }
                }
                com.udenar.petcare.ui.componentes.EditorFotoPerfil(
                    b.foto, b.nombre, estado.subiendoFoto, onElegir = viewModel::elegirFotoPerfil, onQuitar = { viewModel.cambiar { it.copy(foto = null) } },
                    fondo = Pc.SolClaro, color = Pc.Tinta,
                )
                CampoTexto(b.nombre, { v -> viewModel.cambiar { it.copy(nombre = v.take(100)) } }, if (b.tipo == TipoPrestador.NEGOCIO) "Nombre del negocio" else "Tu nombre", error = e["nombre"], modifier = Modifier.testTag("pres_nombre"))
                VistaPreviaPerfil { com.udenar.petcare.ui.servicios.TarjetaPrestador(b.comoPerfil(""), estado.categorias, onClick = {}) }
            }
            PasoPres.CATEGORIAS -> {
                com.udenar.petcare.ui.componentes.PreguntaMichi("¿A qué te dedicas?", "Puedes elegir varias; la primera es la principal", com.udenar.petcare.domain.recomendaciones.PoseMichi.HIGIENE)
                estado.categorias.chunked(2).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        fila.forEach { c ->
                            val i = b.categorias.indexOf(c.id)
                            com.udenar.petcare.ui.componentes.TarjetaOpcionGrande(
                                c.nombre, i >= 0,
                                onClick = { viewModel.cambiar { it.copy(categorias = if (c.id in it.categorias) it.categorias - c.id else it.categorias + c.id) } },
                                subtitulo = if (i == 0) "Principal" else null,
                                dibujo = dibujoCategoria(c.id), icono = c.icono3d, color = Pc.SolClaro,
                                modifier = Modifier.weight(1f).testTag("pres_categoria_${c.id}"),
                            )
                        }
                        if (fila.size == 1) Box(Modifier.weight(1f))
                    }
                }
                e["categorias"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            PasoPres.SERVICIOS -> {
                com.udenar.petcare.ui.componentes.PreguntaMichi("Tus servicios y precios", "Precio fijo, \"desde\" o a cotizar; el pago lo acuerdas tú", com.udenar.petcare.domain.recomendaciones.PoseMichi.DOCUMENTOS)
                if (b.servicios.isEmpty()) {
                    com.udenar.petcare.ui.componentes.VacioMichi(com.udenar.petcare.domain.recomendaciones.PoseMichi.DOCUMENTOS, "Aún no agregas servicios. Puedes hacerlo luego, pero con precios te escriben más.", fondo = Pc.Blanco)
                }
                b.servicios.forEach { sv ->
                    val cat = estado.categorias.firstOrNull { it.id == sv.categoriaId }
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Pc.Blanco).clickable { servicioEditado = sv }.padding(14.dp).testTag("pres_servicio_${sv.nombre}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.udenar.petcare.ui.componentes.BaldosaIcono(cat?.icono3d ?: com.udenar.petcare.domain.modelo.Icono.HUELLAS, tam = 40.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(sv.nombre, style = MaterialTheme.typography.titleSmall)
                            Text(Precios.texto(sv), style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                        }
                        Text("Editar", style = MaterialTheme.typography.labelMedium, color = Pc.Naranja)
                    }
                }
                e["servicios"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                BotonSecundario("Agregar servicio", icono = com.udenar.petcare.domain.modelo.Icono.MAS, onClick = {
                    if (b.categorias.isEmpty()) avisar("Primero elige a qué te dedicas")
                    else servicioEditado = ServicioPrestador(UUID.randomUUID().toString(), b.categorias.first(), "")
                }, modifier = Modifier.testTag("pres_agregar_servicio"))
            }
            PasoPres.A_QUIEN -> {
                com.udenar.petcare.ui.componentes.PreguntaMichi("¿A quiénes atiendes?", "Elige las especies y cómo trabajas", com.udenar.petcare.domain.recomendaciones.PoseMichi.AMIGOS)
                ElegirEspecies(b.especies, { esp -> viewModel.cambiar { it.copy(especies = if (esp in it.especies) it.especies - esp else it.especies + esp) } }, "pres")
                e["especies"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModalidadPrestador.entries.forEach { m ->
                        com.udenar.petcare.ui.componentes.TarjetaOpcionGrande(
                            m.etiqueta, m in b.modalidades,
                            onClick = { viewModel.cambiar { it.copy(modalidades = if (m in it.modalidades) it.modalidades - m else it.modalidades + m) } },
                            icono = if (m == ModalidadPrestador.DOMICILIO) com.udenar.petcare.domain.modelo.Icono.CASA else com.udenar.petcare.domain.modelo.Icono.TIENDA,
                            color = Pc.SolClaro, modifier = Modifier.weight(1f),
                        )
                    }
                }
                e["modalidades"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            PasoPres.CONTACTO -> {
                com.udenar.petcare.ui.componentes.PreguntaMichi("¿Cómo te contactan?", "Los dueños te llaman o te escriben desde tu perfil", com.udenar.petcare.domain.recomendaciones.PoseMichi.ALARMA)
                CampoTexto(b.telefono, { v -> viewModel.cambiar { it.copy(telefono = v) } }, "Teléfono", teclado = KeyboardType.Phone, error = e["telefono"], modifier = Modifier.testTag("pres_telefono"))
                if (b.telefono.isNotBlank()) {
                    ChipOpcion("Mi WhatsApp es el mismo número", b.whatsapp == b.telefono, onClick = { viewModel.cambiar { it.copy(whatsapp = if (it.whatsapp == it.telefono) "" else it.telefono) } }, icono = com.udenar.petcare.domain.modelo.Icono.MENSAJE)
                }
                CampoTexto(b.whatsapp, { v -> viewModel.cambiar { it.copy(whatsapp = v) } }, "WhatsApp (opcional)", teclado = KeyboardType.Phone, error = e["whatsapp"])
                CampoTexto(b.correo, { v -> viewModel.cambiar { it.copy(correo = v) } }, "Correo (opcional)", teclado = KeyboardType.Email, error = e["correo"])
            }
            PasoPres.DONDE -> {
                com.udenar.petcare.ui.componentes.PreguntaMichi("¿Dónde trabajas?", "Tu zona o tu local; así te encuentran cerca", com.udenar.petcare.domain.recomendaciones.PoseMichi.LEYENDO)
                CampoTexto(b.ciudad, { v -> viewModel.cambiar { it.copy(ciudad = v) } }, "Ciudad", error = e["ciudad"], modifier = Modifier.testTag("pres_ciudad"))
                CampoTexto(b.zona, { v -> viewModel.cambiar { it.copy(zona = v.take(120)) } }, "Zona (barrios, sector…)", error = e["zona"], modifier = Modifier.testTag("pres_zona"))
                if (ModalidadPrestador.ESTABLECIMIENTO in b.modalidades) {
                    CampoTexto(b.direccion, { v -> viewModel.cambiar { it.copy(direccion = v.take(150)) } }, "Dirección del local")
                }
                Text("¿Hasta dónde te desplazas?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("", "2", "5", "10", "20").forEach { km ->
                        ChipOpcion(if (km.isEmpty()) "Sin límite" else "$km km", b.radioKm == km, onClick = { viewModel.cambiar { it.copy(radioKm = km) } })
                    }
                }
                TarjetaUbicacion(b.ubicacion != null, "ubicacion" in e && b.ubicacion == null, "Ubicado en el mapa ✓", onClick = { mapa = true }, tag = "pres_ubicar")
            }
            PasoPres.HORARIO -> {
                com.udenar.petcare.ui.componentes.PreguntaMichi("¿Cuándo atiendes?", "Elige los días y el horario; puedes ajustar cada día", com.udenar.petcare.domain.recomendaciones.PoseMichi.AGENDA)
                TarjetaInterruptor(com.udenar.petcare.domain.modelo.Icono.CAMPANA, "Recibiendo solicitudes", "Apágalo cuando no puedas atender", b.recibiendo, { v -> viewModel.cambiar { it.copy(recibiendo = v) } })
                EditorHorarioAmigable(b.horario) { h -> viewModel.cambiar { it.copy(horario = h) } }
                Text("Días que no atiendes (vacaciones, festivos)", style = MaterialTheme.typography.titleSmall)
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
            PasoPres.SOBRE_TI -> {
                com.udenar.petcare.ui.componentes.PreguntaMichi("Presenta tu trabajo", "Una descripción y fotos dan confianza", com.udenar.petcare.domain.recomendaciones.PoseMichi.LEYENDO)
                CampoTexto(b.descripcion, { v -> viewModel.cambiar { it.copy(descripcion = v.take(1000)) } }, "Qué ofreces y cómo trabajas", error = e["descripcion"], lineas = 4, modifier = Modifier.testTag("pres_descripcion"))
                Text("Fotos de tus trabajos (hasta ${ValidadorPrestador.MAX_GALERIA}, públicas)", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    b.galeria.forEach { url ->
                        Box {
                            AsyncImage(url, contentDescription = "Foto de trabajo", contentScale = ContentScale.Crop, modifier = Modifier.size(96.dp).clip(MaterialTheme.shapes.medium))
                            Icon(
                                Icons.Rounded.Close, contentDescription = "Quitar foto", tint = Pc.SobreAcento,
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(MaterialTheme.shapes.small).background(Pc.Tinta.copy(alpha = 0.5f)).clickable { viewModel.quitarFoto(url) },
                            )
                        }
                    }
                    if (b.galeria.size < ValidadorPrestador.MAX_GALERIA) {
                        Column(
                            Modifier.size(96.dp).clip(MaterialTheme.shapes.medium).background(Pc.Blanco)
                                .clickable { if (!estado.subiendoFotos) fotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            com.udenar.petcare.ui.componentes.Simbolo(com.udenar.petcare.domain.modelo.Icono.GALERIA, tam = 30.dp)
                            Text(if (estado.subiendoFotos) "Subiendo…" else "Agregar", style = MaterialTheme.typography.labelMedium, color = Pc.TintaSuave)
                        }
                    }
                }
            }
            PasoPres.RESUMEN -> {
                com.udenar.petcare.ui.componentes.PreguntaMichi(
                    if (estado.existente) "Tu perfil" else "Revisa tu perfil antes de publicarlo",
                    "Toca un dato para cambiarlo", com.udenar.petcare.domain.recomendaciones.PoseMichi.CELEBRA,
                )
                VistaPreviaPerfil { com.udenar.petcare.ui.servicios.TarjetaPrestador(b.comoPerfil(""), estado.categorias, onClick = {}) }
                Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Pc.Blanco).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    com.udenar.petcare.ui.componentes.FilaResumen("Tú", "${b.nombre.ifBlank { "Sin nombre" }} · ${b.tipo.etiqueta}", onEditar = { ir(PasoPres.TU) })
                    com.udenar.petcare.ui.componentes.FilaResumen("Te dedicas a", b.categorias.mapNotNull { id -> estado.categorias.firstOrNull { it.id == id }?.nombre }.joinToString().ifEmpty { "Sin elegir" }, onEditar = { ir(PasoPres.CATEGORIAS) })
                    com.udenar.petcare.ui.componentes.FilaResumen("Servicios", if (b.servicios.isEmpty()) "Sin precios aún" else "${b.servicios.size} con precio", onEditar = { ir(PasoPres.SERVICIOS) })
                    com.udenar.petcare.ui.componentes.FilaResumen("Atiendes", (b.especies.map { it.etiqueta } + b.modalidades.map { it.etiqueta.lowercase() }).joinToString().ifEmpty { "Sin elegir" }, onEditar = { ir(PasoPres.A_QUIEN) })
                    com.udenar.petcare.ui.componentes.FilaResumen("Contacto", b.telefono.ifBlank { "Sin teléfono" }, onEditar = { ir(PasoPres.CONTACTO) })
                    com.udenar.petcare.ui.componentes.FilaResumen("Dónde", listOf(b.zona, b.ciudad).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { "Sin zona" } + if (b.ubicacion != null) " ✓" else "", onEditar = { ir(PasoPres.DONDE) })
                    com.udenar.petcare.ui.componentes.FilaResumen("Horario", (if (b.recibiendo) "" else "En pausa · ") + resumenHorario(b.horario), onEditar = { ir(PasoPres.HORARIO) })
                    com.udenar.petcare.ui.componentes.FilaResumen("Sobre ti", b.descripcion.take(60).ifBlank { "Sin descripción" } + if (b.galeria.isNotEmpty()) " · ${b.galeria.size} fotos" else "", onEditar = { ir(PasoPres.SOBRE_TI) })
                }
                Text("Los precios son orientativos: el pago se acuerda directamente contigo.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
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
            titulo = "¿Dónde te encuentran?",
            ayuda = "Pon el pin en tu establecimiento o en el centro de la zona donde trabajas.",
        )
    }
    servicioEditado?.let { sv ->
        DialogoServicio(
            inicial = sv,
            categorias = estado.categorias.filter { it.id in b.categorias },
            especies = b.especies,
            onGuardar = { nuevo ->
                viewModel.cambiar { br -> br.copy(servicios = br.servicios.filter { it.id != nuevo.id } + nuevo) }
                servicioEditado = null
            },
            onEliminar = {
                viewModel.cambiar { br -> br.copy(servicios = br.servicios.filter { it.id != sv.id }) }
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
