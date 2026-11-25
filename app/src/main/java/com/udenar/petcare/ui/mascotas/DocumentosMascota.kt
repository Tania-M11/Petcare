package com.udenar.petcare.ui.mascotas

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import com.udenar.petcare.ui.componentes.Icono3D
import com.udenar.petcare.domain.modelo.Icono
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.udenar.petcare.domain.documentos.Documento
import com.udenar.petcare.domain.documentos.TipoDocumento
import com.udenar.petcare.domain.salud.Visita
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.ChipOpcion
import com.udenar.petcare.ui.componentes.SelectorFecha
import com.udenar.petcare.ui.theme.Pc
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FECHA = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO"))

/** Abre el documento con el visor del teléfono (imágenes y PDF) sin exponer rutas internas. */
fun abrirDocumento(contexto: Context, ruta: String, mime: String) {
    val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", File(ruta))
    contexto.startActivity(
        Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Abrir con"),
    )
}

/** Documentos de la mascota (RF-17): solo el propietario. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TarjetaDocumentos(
    documentos: List<Documento>,
    visitas: List<Visita>,
    hoy: LocalDate,
    onAgregar: (origen: String, nombre: String, tipo: TipoDocumento, fecha: LocalDate, visitaId: String?) -> Unit,
    onAbrir: (Documento) -> Unit,
    onEliminar: (Documento) -> Unit,
) {
    // Agregar en pasos (Fase 6.2): qué es → de dónde → detalles con vista previa
    var agregando by remember { mutableStateOf(false) }
    var tipo by remember { mutableStateOf<TipoDocumento?>(null) }
    var origen by remember { mutableStateOf<String?>(null) }
    var esPdf by remember { mutableStateOf(false) }
    var eliminar by remember { mutableStateOf<Documento?>(null) }
    val contexto = androidx.compose.ui.platform.LocalContext.current
    var fotoCamara by remember { mutableStateOf<android.net.Uri?>(null) }
    val imagen = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { origen = it.toString(); esPdf = false } }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { origen = it.toString(); esPdf = true } }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) fotoCamara?.let { origen = it.toString(); esPdf = false } }
    fun cerrar() { agregando = false; tipo = null; origen = null }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Pc.Blanco)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.udenar.petcare.ui.componentes.TituloConIcono("Documentos", Modifier.weight(1f))
            BotonAgregar("Agregar", "agregar_documento") { agregando = true }
        }
        if (documentos.isEmpty()) {
            Text("Carnés, fórmulas, exámenes e informes: guárdalos aquí para tenerlos siempre a mano.", style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
        }
        documentos.forEach { d ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onAbrir(d) }
                    .padding(vertical = 6.dp)
                    .testTag("documento_${d.nombre}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Pc.CieloClaro), contentAlignment = Alignment.Center) {
                    Icono3D(if (d.esPdf) Icono.DOCUMENTO else d.tipo.icono, tam = 28.dp)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(d.nombre, style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(d.tipo.etiqueta, d.fecha.format(FECHA), if (d.porSubir) "por subir" else null).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave,
                    )
                }
                IconButton(onClick = { eliminar = d }) { Icon(Icons.Rounded.Close, contentDescription = "Eliminar ${d.nombre}", tint = Pc.TintaSuave) }
            }
        }
    }

    if (agregando) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = ::cerrar, containerColor = Pc.Crema) {
            Column(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val t = tipo
                val o = origen
                when {
                    t == null -> {
                        Text("¿Qué documento es?", style = MaterialTheme.typography.headlineSmall)
                        TipoDocumento.entries.chunked(3).forEach { fila ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                fila.forEach { d ->
                                    com.udenar.petcare.ui.componentes.TarjetaOpcionGrande(d.etiqueta, false, onClick = { tipo = d }, icono = d.icono, color = Pc.CieloClaro, modifier = Modifier.weight(1f).testTag("tipo_doc_${d.name}"))
                                }
                                repeat(3 - fila.size) { Box(Modifier.weight(1f)) }
                            }
                        }
                    }
                    o == null -> {
                        Text("¿De dónde lo tomas?", style = MaterialTheme.typography.headlineSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            com.udenar.petcare.ui.componentes.TarjetaOpcionGrande("Cámara", false, onClick = {
                                val carpeta = File(contexto.cacheDir, "camara").apply { mkdirs() }
                                val archivo = File(carpeta, "doc_${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", archivo)
                                fotoCamara = uri
                                camara.launch(uri)
                            }, icono = Icono.CAMARA, subtitulo = "Toma la foto", modifier = Modifier.weight(1f).testTag("doc_camara"))
                            com.udenar.petcare.ui.componentes.TarjetaOpcionGrande("Galería", false, onClick = {
                                imagen.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }, icono = Icono.GALERIA, subtitulo = "Una imagen", modifier = Modifier.weight(1f).testTag("doc_galeria"))
                            com.udenar.petcare.ui.componentes.TarjetaOpcionGrande("PDF", false, onClick = {
                                pdf.launch(arrayOf("application/pdf"))
                            }, icono = Icono.DOCUMENTO, subtitulo = "Un archivo", modifier = Modifier.weight(1f).testTag("agregar_pdf"))
                        }
                        androidx.compose.material3.TextButton(onClick = { tipo = null }) { Text("Cambiar tipo", color = Pc.TintaSuave) }
                    }
                    else -> {
                        var nombre by remember(t) { mutableStateOf(t.etiqueta) }
                        var fecha by remember { mutableStateOf(hoy) }
                        var visita by remember { mutableStateOf<String?>(null) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).background(Pc.Blanco), contentAlignment = Alignment.Center) {
                                if (esPdf) Icono3D(Icono.DOCUMENTO, tam = 44.dp)
                                else coil3.compose.AsyncImage(o, contentDescription = "Vista previa", contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(72.dp))
                            }
                            Text("Casi listo", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 14.dp))
                        }
                        CampoTexto(nombre, { nombre = it.take(80) }, "Nombre", modifier = Modifier.testTag("nombre_documento"))
                        Text("¿De cuándo es?", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChipOpcion("Hoy", fecha == hoy, onClick = { fecha = hoy })
                            ChipOpcion("Ayer", fecha == hoy.minusDays(1), onClick = { fecha = hoy.minusDays(1) })
                        }
                        SelectorFecha("Otra fecha", fecha, { it?.let { f -> fecha = f } }, hasta = hoy)
                        if (visitas.isNotEmpty()) {
                            Text("¿Es de una visita? (opcional)", style = MaterialTheme.typography.titleSmall)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                visitas.take(6).forEach { v -> ChipOpcion("${v.motivo} · ${v.fecha.format(FECHA)}", visita == v.id, onClick = { visita = if (visita == v.id) null else v.id }, icono = Icono.HOSPITAL) }
                            }
                        }
                        com.udenar.petcare.ui.componentes.BotonPrincipal("Guardar documento", habilitado = nombre.isNotBlank(), onClick = {
                            onAgregar(o, nombre, t, fecha, visita)
                            cerrar()
                        }, modifier = Modifier.testTag("guardar_documento"))
                    }
                }
            }
        }
    }

    eliminar?.let { d ->
        AlertDialog(
            onDismissRequest = { eliminar = null },
            containerColor = Pc.Crema,
            title = { Text("¿Eliminar \"${d.nombre}\"?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Se borrará en todos tus dispositivos.") },
            confirmButton = { TextButton(onClick = { onEliminar(d); eliminar = null }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { eliminar = null }) { Text("Cancelar") } },
        )
    }
}
