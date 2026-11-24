package com.udenar.petcare.ui.componentes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.udenar.petcare.domain.modelo.Icono
import com.udenar.petcare.ui.theme.Kalnia
import com.udenar.petcare.ui.theme.Pc

/** Foto de perfil del veterinario o prestador; sin foto, la inicial sobre su color. */
@Composable
fun AvatarProfesional(foto: String?, nombre: String, modifier: Modifier = Modifier, tam: Dp = 48.dp, fondo: Color = Pc.CieloClaro, color: Color = Pc.CieloOscuro) {
    Box(modifier.size(tam).clip(CircleShape).background(fondo), contentAlignment = Alignment.Center) {
        if (foto != null) {
            AsyncImage(foto, contentDescription = "Foto de $nombre", contentScale = ContentScale.Crop, modifier = Modifier.size(tam))
        } else {
            Text(nombre.trim().take(1).uppercase().ifEmpty { "?" }, fontFamily = Kalnia, fontSize = (tam.value * 0.42f).sp, color = color)
        }
    }
}

/** Elegir, cambiar o quitar la foto de perfil (Fase 6.7). */
@Composable
fun EditorFotoPerfil(foto: String?, nombre: String, subiendo: Boolean, onElegir: (Uri) -> Unit, onQuitar: () -> Unit, fondo: Color = Pc.CieloClaro, color: Color = Pc.CieloOscuro) {
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onElegir) }
    fun abrir() = galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(contentAlignment = Alignment.Center) {
            AvatarProfesional(
                foto, nombre, tam = 88.dp, fondo = fondo, color = color,
                modifier = Modifier.border(3.dp, Pc.Blanco, CircleShape).clickable(enabled = !subiendo) { abrir() }.testTag("foto_perfil"),
            )
            if (subiendo) CircularProgressIndicator(color = Pc.Naranja, modifier = Modifier.size(40.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (foto == null) "Agrega una foto tuya" else "Así te reconocerán", style = MaterialTheme.typography.titleSmall)
            Text("Una foto clara de tu cara genera confianza.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            Row {
                ChipOpcion(if (foto == null) "Elegir foto" else "Cambiar", false, onClick = { if (!subiendo) abrir() }, icono = Icono.CAMARA, modifier = Modifier.testTag("elegir_foto_perfil"))
                if (foto != null) TextButton(onClick = onQuitar) { Text("Quitar", color = Pc.TintaSuave) }
            }
        }
    }
}

/** Hasta [maximo] fotos opcionales para una solicitud, con miniaturas que se pueden quitar. */
@Composable
fun SelectorFotos(fotos: List<Uri>, onCambio: (List<Uri>) -> Unit, maximo: Int = 3) {
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maximo)) { uris ->
        if (uris.isNotEmpty()) onCambio((fotos + uris).distinct().take(maximo))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        fotos.forEach { uri ->
            Box {
                AsyncImage(uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)))
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).clip(CircleShape).background(Pc.Blanco).clickable { onCambio(fotos - uri) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, contentDescription = "Quitar foto", tint = Pc.Tinta, modifier = Modifier.size(16.dp)) }
            }
        }
        if (fotos.size < maximo) {
            Column(
                Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Pc.Blanco)
                    .border(1.5.dp, Pc.Linea, RoundedCornerShape(20.dp))
                    .clickable { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    .testTag("agregar_foto_solicitud"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icono3D(Icono.CAMARA, tam = 34.dp)
                Text("${fotos.size}/$maximo", style = MaterialTheme.typography.labelMedium, color = Pc.TintaSuave)
            }
        }
    }
}
