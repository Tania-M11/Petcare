package com.udenar.petcare.ui.componentes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.udenar.petcare.domain.modelo.Icono
import com.udenar.petcare.ui.theme.Pc
import java.io.File

/**
 * Foto opcional: tomarla con la cámara o elegirla de la galería, con vista previa y quitar.
 * [foto] es la Uri elegida (texto) o null.
 */
@Composable
fun ElegirFoto(foto: String?, onFoto: (String?) -> Unit, modifier: Modifier = Modifier, prefijoTag: String = "foto") {
    val contexto = LocalContext.current
    var pendiente by remember { mutableStateOf<Uri?>(null) }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) pendiente?.let { onFoto(it.toString()) } }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { onFoto(it.toString()) } }
    if (foto != null) {
        Box(modifier.fillMaxWidth()) {
            AsyncImage(foto, contentDescription = "Foto", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.large))
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(34.dp).clip(CircleShape).background(Pc.Blanco).clickable { onFoto(null) }.testTag("${prefijoTag}_quitar"),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, contentDescription = "Quitar foto", tint = Pc.Tinta) }
        }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TarjetaOpcionGrande("Cámara", false, onClick = {
                val carpeta = File(contexto.cacheDir, "camara").apply { mkdirs() }
                val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", File(carpeta, "foto_${System.currentTimeMillis()}.jpg"))
                pendiente = uri
                camara.launch(uri)
            }, icono = Icono.CAMARA, subtitulo = "Tomar foto", modifier = Modifier.weight(1f).testTag("${prefijoTag}_camara"))
            TarjetaOpcionGrande("Galería", false, onClick = {
                galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, icono = Icono.GALERIA, subtitulo = "Elegir una", modifier = Modifier.weight(1f).testTag("${prefijoTag}_galeria"))
        }
    }
}
