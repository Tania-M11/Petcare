package com.udenar.petcare.ui.componentes

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.udenar.petcare.domain.profesionales.FotosSolicitudes
import com.udenar.petcare.ui.theme.Pc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class FotosSolicitudViewModel @Inject constructor(private val fotos: FotosSolicitudes) : ViewModel() {
    private val cache = mutableMapOf<String, ImageBitmap?>()

    suspend fun cargar(ruta: String): ImageBitmap? = cache.getOrPut(ruta) {
        runCatching {
            val bytes = fotos.descargar(ruta)
            withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
        }.getOrNull()
    }
}

/** Miniaturas de las fotos que el dueño adjuntó a la solicitud (RF-34); se amplían al tocarlas. */
@Composable
fun FotosSolicitud(rutas: List<String>, viewModel: FotosSolicitudViewModel = hiltViewModel()) {
    if (rutas.isEmpty()) return
    var ampliada by remember { mutableStateOf<ImageBitmap?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Fotos del dueño", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
        Row(Modifier.horizontalScroll(rememberScrollState()).testTag("fotos_solicitud"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rutas.forEach { ruta ->
                var imagen by remember(ruta) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(ruta) { imagen = viewModel.cargar(ruta) }
                Box(
                    Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)).background(Pc.Crema).clickable(enabled = imagen != null) { ampliada = imagen },
                    contentAlignment = Alignment.Center,
                ) {
                    imagen?.let { Image(it, contentDescription = "Foto adjunta", contentScale = ContentScale.Crop, modifier = Modifier.size(84.dp)) }
                        ?: Simbolo(com.udenar.petcare.domain.modelo.Icono.CAMARA, tam = 28.dp)
                }
            }
        }
    }
    ampliada?.let { img ->
        Dialog(onDismissRequest = { ampliada = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).clickable { ampliada = null }.padding(16.dp), contentAlignment = Alignment.Center) {
                Image(img, contentDescription = "Foto adjunta", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
