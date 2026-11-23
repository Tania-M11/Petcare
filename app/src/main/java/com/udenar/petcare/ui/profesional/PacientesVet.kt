package com.udenar.petcare.ui.profesional

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.udenar.petcare.R
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.documentos.TipoDocumento
import com.udenar.petcare.domain.mascotas.FichaMascota
import com.udenar.petcare.domain.profesionales.DocumentoClinico
import com.udenar.petcare.domain.profesionales.FichaClinica
import com.udenar.petcare.domain.profesionales.NuevoCuidadoClinico
import com.udenar.petcare.domain.profesionales.Paciente
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.RepositorioAtencionVet
import com.udenar.petcare.domain.profesionales.TIPOS_CLINICOS
import com.udenar.petcare.domain.profesionales.ValidadorVeterinario
import com.udenar.petcare.domain.profesionales.esVeterinario
import com.udenar.petcare.ui.componentes.AvatarMascota
import com.udenar.petcare.ui.componentes.BotonCircular
import com.udenar.petcare.ui.componentes.BotonSecundario
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.ChipOpcion
import com.udenar.petcare.ui.componentes.EditorHoras
import com.udenar.petcare.ui.componentes.EstadoVacio
import com.udenar.petcare.ui.componentes.Seccion
import com.udenar.petcare.ui.componentes.SelectorFecha
import com.udenar.petcare.ui.theme.Pc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@Serializable data class RutaFichaPaciente(val id: String)

@HiltViewModel
class PacientesViewModel @Inject constructor(
    private val atencion: RepositorioAtencionVet,
    tiempoReal: com.udenar.petcare.data.sync.TiempoReal,
) : ViewModel() {
    val pacientes = MutableStateFlow<List<Paciente>>(emptyList())
    val error = MutableStateFlow<String?>(null)
    val cargando = MutableStateFlow(true)

    init {
        cargar()
        viewModelScope.launch { tiempoReal.cambiosVet.collect { cargar() } }
    }

    fun cargar() = viewModelScope.launch {
        try {
            pacientes.value = atencion.pacientes()
            error.value = null
        } catch (e: ProfesionalException) {
            error.value = e.message
        } finally {
            cargando.value = false
        }
    }
}

/** Pestaña Pacientes: mascotas vinculadas (RF-26). */
@Composable
fun PacientesVetScreen(onFicha: (String) -> Unit, viewModel: PacientesViewModel = hiltViewModel()) {
    val pacientes by viewModel.pacientes.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val cargando by viewModel.cargando.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pacientes", style = MaterialTheme.typography.headlineLarge)
        error?.let {
            Text(it, color = Pc.TintaSuave)
            BotonSecundario("Reintentar", onClick = { viewModel.cargar() })
        }
        if (!cargando && error == null && pacientes.isEmpty()) {
            EstadoVacio(R.drawable.chopper_doctor, "Sin pacientes vinculados", "Al aceptar una cita, la mascota queda vinculada y podrás ver y completar su ficha clínica.", fondoBlob = listOf(Pc.CieloClaro, Pc.LavandaClara))
        }
        pacientes.forEach { p ->
            val m = p.mascota
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(Pc.Blanco)
                    .clickable { onFicha(m.id) }
                    .padding(14.dp)
                    .testTag("paciente_${m.nombre}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarMascota(m.especie, m.fotoLocal, tam = 48.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(m.nombre, style = MaterialTheme.typography.titleMedium)
                    Text(listOfNotNull(m.especie.etiqueta, m.raza, m.fechaNacimiento?.let { FichaMascota.edad(it, m.nacimientoAproximado, LocalDate.now()) }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                }
            }
        }
    }
}

@HiltViewModel
class FichaPacienteViewModel @Inject constructor(
    estado: SavedStateHandle,
    private val atencion: RepositorioAtencionVet,
    private val reloj: Clock,
) : ViewModel() {
    val mascotaId = estado.toRoute<RutaFichaPaciente>().id
    val ficha = MutableStateFlow<FichaClinica?>(null)
    val error = MutableStateFlow<String?>(null)
    val ocupado = MutableStateFlow(false)
    val hoy: LocalDate get() = LocalDate.now(reloj)

    init { cargar() }

    fun cargar() = viewModelScope.launch {
        try {
            ficha.value = atencion.ficha(mascotaId)
            error.value = null
        } catch (e: ProfesionalException) {
            error.value = e.message
        }
    }

    private fun accion(avisar: (String) -> Unit, ok: String, despues: () -> Unit = {}, bloque: suspend () -> Unit) = viewModelScope.launch {
        ocupado.value = true
        try {
            bloque()
            avisar(ok)
            despues()
            cargar()
        } catch (e: ProfesionalException) {
            avisar(e.message ?: "No se pudo guardar")
        } finally {
            ocupado.value = false
        }
    }

    fun visita(fecha: LocalDate, motivo: String, tratamiento: String, medicamentos: String, notas: String, avisar: (String) -> Unit, listo: () -> Unit) =
        accion(avisar, "Visita registrada", listo) { atencion.registrarVisita(mascotaId, fecha, motivo, tratamiento, medicamentos, notas) }

    fun cuidado(c: NuevoCuidadoClinico, avisar: (String) -> Unit, listo: () -> Unit) =
        accion(avisar, "${c.tipo.etiqueta} registrada en la ficha", listo) { atencion.registrarCuidado(mascotaId, c) }

    fun peso(kg: Double, avisar: (String) -> Unit, listo: () -> Unit) =
        accion(avisar, "Peso registrado", listo) { atencion.registrarPeso(mascotaId, (kg * 1000).toInt()) }

    fun documento(leer: suspend () -> Triple<String, ByteArray, String>?, tipo: TipoDocumento, avisar: (String) -> Unit) = accion(avisar, "Documento subido") {
        val (nombre, bytes, mime) = leer() ?: throw ProfesionalException("No pudimos leer el archivo.")
        atencion.subirDocumento(mascotaId, nombre, tipo, bytes, mime)
    }

    fun abrir(d: DocumentoClinico, carpeta: File, alListo: (File) -> Unit, avisar: (String) -> Unit) = viewModelScope.launch {
        try {
            val ruta = d.ruta ?: throw ProfesionalException("El archivo aún no se sube.")
            val bytes = atencion.descargarDocumento(ruta)
            val archivo = withContext(Dispatchers.IO) { File(carpeta, "${d.id}.${if (d.mime == "application/pdf") "pdf" else "jpg"}").apply { writeBytes(bytes) } }
            alListo(archivo)
        } catch (e: ProfesionalException) {
            avisar(e.message ?: "No se pudo abrir")
        }
    }

    fun desvincular(veterinarioId: String, avisar: (String) -> Unit, listo: () -> Unit) = viewModelScope.launch {
        try {
            atencion.desvincular(mascotaId, veterinarioId)
            avisar("Ya no tienes acceso a esta ficha")
            listo()
        } catch (e: ProfesionalException) {
            avisar(e.message ?: "No se pudo desvincular")
        }
    }
}

private val FECHA = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO"))

/** Ficha clínica del paciente: lectura de todo y registro de lo propio (RF-26, RF-27). */
@Composable
fun FichaPacienteScreen(miId: String?, onVolver: () -> Unit, avisar: (String) -> Unit, viewModel: FichaPacienteViewModel = hiltViewModel()) {
    val ficha by viewModel.ficha.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    var dialogo by remember { mutableStateOf<String?>(null) }
    var tipoDoc by remember { mutableStateOf(TipoDocumento.INFORME) }
    val elegirDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.documento({
            withContext(Dispatchers.IO) {
                val r = contexto.contentResolver
                var nombre = "Documento"
                var tamano: Long? = null
                r.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        nombre = c.getString(0)?.substringBeforeLast('.') ?: nombre
                        tamano = c.getLong(1)
                    }
                }
                if ((tamano ?: 0) > ValidadorVeterinario.MAX_CERTIFICADO) throw ProfesionalException("El archivo pesa más de 5 MB.")
                val mime = r.getType(uri) ?: return@withContext null
                r.openInputStream(uri)?.use { it.readBytes() }?.let { Triple(nombre, it, mime) }
            }
        }, tipoDoc, avisar)
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
            Text(ficha?.mascota?.nombre ?: "Ficha clínica", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp))
        }
        error?.let {
            Text(it, color = Pc.TintaSuave)
            BotonSecundario("Reintentar", onClick = { viewModel.cargar() })
        }
        val f = ficha ?: return@Column
        val m = f.mascota
        Text(
            listOfNotNull(m.especie.etiqueta, m.raza, m.fechaNacimiento?.let { FichaMascota.edad(it, m.nacimientoAproximado, viewModel.hoy) }, m.pesoGramos?.let { FichaMascota.peso(it) }).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave,
        )
        Seccion("Salud básica") {
            Dato("Alergias", m.alergias)
            Dato("Condiciones", m.condiciones)
            Dato("Medicamentos actuales", m.medicamentosActuales)
            Dato("Observaciones", m.observaciones)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipOpcion("➕ Visita", false, onClick = { dialogo = "visita" }, modifier = Modifier.testTag("nueva_visita_vet"))
            ChipOpcion("💉 Vacuna o medicamento", false, onClick = { dialogo = "cuidado" }, modifier = Modifier.testTag("nuevo_cuidado_vet"))
            ChipOpcion("⚖️ Peso", false, onClick = { dialogo = "peso" })
            ChipOpcion("📄 Documento", false, onClick = { dialogo = "documento" })
        }
        Seccion("Vacunas, desparasitación y medicamentos") {
            if (f.cuidados.isEmpty()) Text("Sin registros.", color = Pc.TintaSuave)
            f.cuidados.forEach { e -> FilaClinica(e.tipo.etiqueta + " · " + e.titulo, describir(e), e.creadoPor?.nombre, e.creadoPor.esVeterinario) }
        }
        Seccion("Visitas") {
            if (f.visitas.isEmpty()) Text("Sin visitas registradas.", color = Pc.TintaSuave)
            f.visitas.forEach { v ->
                FilaClinica(
                    v.motivo, listOfNotNull(v.fecha.format(FECHA), v.tratamiento?.let { "Tratamiento: $it" }, v.medicamentos?.let { "Medicamentos: $it" }, v.notas).joinToString("\n"),
                    v.registradoPor?.nombre, v.registradoPor.esVeterinario,
                )
            }
        }
        Seccion("Peso") {
            if (f.pesos.isEmpty()) Text("Sin pesajes.", color = Pc.TintaSuave)
            f.pesos.take(6).forEach { p -> FilaClinica(FichaMascota.peso(p.gramos), p.fecha.format(FECHA), p.registradoPor?.nombre, p.registradoPor.esVeterinario) }
        }
        Seccion("Documentos") {
            if (f.documentos.isEmpty()) Text("Sin documentos.", color = Pc.TintaSuave)
            f.documentos.forEach { d ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            viewModel.abrir(d, contexto.cacheDir, { archivo ->
                                val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", archivo)
                                try {
                                    contexto.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, d.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                } catch (_: ActivityNotFoundException) {
                                    avisar("No hay una aplicación para abrir este archivo")
                                }
                            }, avisar)
                        }
                        .padding(vertical = 6.dp),
                ) {
                    Text("${d.tipo.emoji}  ", style = MaterialTheme.typography.titleMedium)
                    Column {
                        Text(d.nombre, style = MaterialTheme.typography.titleSmall)
                        Text(listOfNotNull(d.fecha.format(FECHA), d.subidoPor).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                    }
                }
            }
        }
        TextButton(onClick = { dialogo = "desvincular" }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Desvincularme de ${m.nombre}", color = MaterialTheme.colorScheme.error)
        }
    }

    when (dialogo) {
        "visita" -> DialogoVisita(viewModel, avisar) { dialogo = null }
        "cuidado" -> DialogoCuidado(viewModel, avisar) { dialogo = null }
        "peso" -> {
            var kg by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { dialogo = null }, containerColor = Pc.Crema,
                title = { Text("Registrar peso", style = MaterialTheme.typography.headlineSmall) },
                text = { CampoTexto(kg, { kg = it }, "Peso en kg", teclado = KeyboardType.Decimal) },
                confirmButton = {
                    TextButton(onClick = {
                        val v = kg.replace(',', '.').toDoubleOrNull()
                        if (v == null || v <= 0) avisar("Escribe un peso válido") else viewModel.peso(v, avisar) { dialogo = null }
                    }) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { dialogo = null }) { Text("Cancelar") } },
            )
        }
        "documento" -> AlertDialog(
            onDismissRequest = { dialogo = null }, containerColor = Pc.Crema,
            title = { Text("Subir documento", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Imagen o PDF, hasta 5 MB. El dueño lo verá en la ficha de su mascota.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TipoDocumento.entries.forEach { t -> ChipOpcion("${t.emoji} ${t.etiqueta}", tipoDoc == t, onClick = { tipoDoc = t }) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    dialogo = null
                    elegirDoc.launch(arrayOf("image/jpeg", "image/png", "application/pdf"))
                }) { Text("Elegir archivo") }
            },
            dismissButton = { TextButton(onClick = { dialogo = null }) { Text("Cancelar") } },
        )
        "desvincular" -> AlertDialog(
            onDismissRequest = { dialogo = null }, containerColor = Pc.Crema,
            title = { Text("¿Desvincularte?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Dejarás de ver la ficha de ${ficha?.mascota?.nombre.orEmpty()}. Lo que registraste se queda en su historial.") },
            confirmButton = {
                TextButton(onClick = {
                    dialogo = null
                    miId?.let { viewModel.desvincular(it, avisar, onVolver) }
                }) { Text("Desvincularme", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialogo = null }) { Text("Cancelar") } },
        )
    }
}

private fun describir(e: EventoCuidado): String = listOfNotNull(
    if (e.tipo == TipoCuidado.MEDICAMENTO) "Desde ${e.fechaInicio.format(FECHA)}" + (e.fechaFin?.let { " hasta ${it.format(FECHA)}" } ?: "") +
        e.frecuencia.horas.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = " · ") { it.toString() }.orEmpty()
    else "Fecha: ${e.fechaInicio.format(FECHA)}",
    e.detalle["dosis"]?.let { "Dosis: $it" },
    e.detalle["indicaciones"],
).joinToString("\n")

@Composable
private fun Dato(etiqueta: String, valor: String?) {
    Column {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = Pc.TintaSuave)
        Text(valor?.takeIf { it.isNotBlank() } ?: "—", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FilaClinica(titulo: String, detalle: String, autor: String?, porVeterinario: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleSmall)
        if (detalle.isNotBlank()) Text(detalle, style = MaterialTheme.typography.bodySmall)
        autor?.let { Text((if (porVeterinario) "🩺 " else "") + it, style = MaterialTheme.typography.labelSmall, color = Pc.TintaSuave) }
    }
}

@Composable
private fun DialogoVisita(viewModel: FichaPacienteViewModel, avisar: (String) -> Unit, cerrar: () -> Unit) {
    var fecha by remember { mutableStateOf(viewModel.hoy) }
    var motivo by remember { mutableStateOf("") }
    var tratamiento by remember { mutableStateOf("") }
    var medicamentos by remember { mutableStateOf("") }
    var notas by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = cerrar, containerColor = Pc.Crema,
        title = { Text("Registrar visita", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectorFecha("Fecha", fecha, { it?.let { f -> fecha = f } }, hasta = viewModel.hoy)
                CampoTexto(motivo, { motivo = it.take(200) }, "Motivo *", modifier = Modifier.testTag("visita_motivo"))
                CampoTexto(tratamiento, { tratamiento = it.take(1000) }, "Tratamiento indicado", lineas = 2)
                CampoTexto(medicamentos, { medicamentos = it.take(1000) }, "Medicamentos", lineas = 2)
                CampoTexto(notas, { notas = it.take(1000) }, "Notas", lineas = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (motivo.isBlank()) avisar("Escribe el motivo") else viewModel.visita(fecha, motivo, tratamiento, medicamentos, notas, avisar, cerrar)
            }, modifier = Modifier.testTag("guardar_visita_vet")) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogoCuidado(viewModel: FichaPacienteViewModel, avisar: (String) -> Unit, cerrar: () -> Unit) {
    var tipo by remember { mutableStateOf(TipoCuidado.VACUNA) }
    var titulo by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf(viewModel.hoy) }
    var horas by remember { mutableStateOf(listOf<LocalTime>()) }
    var fin by remember { mutableStateOf<LocalDate?>(null) }
    var dosis by remember { mutableStateOf("") }
    var indicaciones by remember { mutableStateOf("") }
    val medicamento = tipo == TipoCuidado.MEDICAMENTO
    AlertDialog(
        onDismissRequest = cerrar, containerColor = Pc.Crema,
        title = { Text("Registrar en la ficha", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TIPOS_CLINICOS.forEach { t -> ChipOpcion(t.etiqueta, tipo == t, onClick = { tipo = t }) }
                }
                CampoTexto(titulo, { titulo = it.take(80) }, if (medicamento) "Medicamento *" else "Producto o vacuna *", modifier = Modifier.testTag("cuidado_vet_titulo"))
                SelectorFecha(if (medicamento) "Desde" else "Próxima aplicación", fecha, { it?.let { f -> fecha = f } }, desde = if (medicamento) null else viewModel.hoy)
                if (medicamento) {
                    EditorHoras(horas, { horas = it })
                    SelectorFecha("Hasta (opcional)", fin, { fin = it }, desde = fecha, permitirBorrar = true)
                }
                CampoTexto(dosis, { dosis = it.take(200) }, "Dosis (tal como la indicas)")
                CampoTexto(indicaciones, { indicaciones = it.take(300) }, "Indicaciones", lineas = 2)
                Text("El dueño recibirá el recordatorio. La app no valida ni sugiere dosis.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    titulo.isBlank() -> avisar("Escribe el nombre")
                    medicamento && horas.isEmpty() -> avisar("Agrega al menos una hora")
                    else -> viewModel.cuidado(NuevoCuidadoClinico(tipo, titulo, fecha, horas, fin, dosis, indicaciones), avisar, cerrar)
                }
            }, modifier = Modifier.testTag("guardar_cuidado_vet")) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
}
