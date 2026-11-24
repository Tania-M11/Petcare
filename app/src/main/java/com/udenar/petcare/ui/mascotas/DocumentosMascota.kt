package com.udenar.petcare.ui.mascotas

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
@Composable
fun TarjetaDocumentos(
    documentos: List<Documento>,
    visitas: List<Visita>,
    hoy: LocalDate,
    onAgregar: (origen: String, nombre: String, tipo: TipoDocumento, fecha: LocalDate, visitaId: String?) -> Unit,
    onAbrir: (Documento) -> Unit,
    onEliminar: (Documento) -> Unit,
) {
    var origen by remember { mutableStateOf<String?>(null) }
    var eliminar by remember { mutableStateOf<Documento?>(null) }
    val imagen = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { origen = it.toString() } }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { origen = it.toString() } }

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
            BotonAgregar("Imagen", "agregar_imagen") { imagen.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            Box(Modifier.size(8.dp))
            BotonAgregar("PDF", "agregar_pdf") { pdf.launch(arrayOf("application/pdf")) }
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

    origen?.let { o ->
        var nombre by remember { mutableStateOf("") }
        var tipo by remember { mutableStateOf(TipoDocumento.CARNE) }
        var fecha by remember { mutableStateOf(hoy) }
        var visita by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { origen = null },
            containerColor = Pc.Crema,
            title = { Text("Nuevo documento", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CampoTexto(nombre, { nombre = it }, "Nombre (p. ej. Carné de vacunas)")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TipoDocumento.entries.forEach { t -> ChipOpcion(t.etiqueta, tipo == t, onClick = { tipo = t }) }
                    }
                    SelectorFecha("Fecha del documento", fecha, { it?.let { f -> fecha = f } }, hasta = hoy)
                    if (visitas.isNotEmpty()) {
                        Text("Asociar a una visita (opcional)", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            visitas.take(6).forEach { v -> ChipOpcion("${v.motivo} · ${v.fecha.format(FECHA)}", visita == v.id, onClick = { visita = if (visita == v.id) null else v.id }) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onAgregar(o, nombre, tipo, fecha, visita)
                    origen = null
                }, modifier = Modifier.testTag("guardar_documento")) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { origen = null }) { Text("Cancelar") } },
        )
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
