package com.udenar.petcare.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.udenar.petcare.domain.mascotas.emoji
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.ui.theme.Pc
import java.io.File

/** Colores pastel por especie para el avatar sin foto. */
fun Especie.colores() = when (this) {
    Especie.PERRO -> listOf(Pc.Durazno, Pc.NaranjaClaro)
    Especie.GATO -> listOf(Pc.LavandaClara, Pc.Lavanda)
    Especie.AVE, Especie.PEZ -> listOf(Pc.CieloClaro, Pc.Cielo)
    Especie.CONEJO, Especie.ROEDOR -> listOf(Pc.SolClaro, Pc.Sol)
    Especie.REPTIL, Especie.EQUINO -> listOf(Pc.MentaClara, Pc.Menta)
    Especie.OTRO -> listOf(Pc.Durazno, Pc.LavandaClara)
}

/** Foto de la mascota (archivo local) o, si no hay, el emoji de su especie sobre un degradado. */
@Composable
fun AvatarMascota(
    especie: Especie,
    fotoLocal: String?,
    modifier: Modifier = Modifier,
    tam: Dp = 64.dp,
    forma: Shape = CircleShape,
) {
    Box(
        modifier
            .size(tam)
            .clip(forma)
            .background(Brush.linearGradient(especie.colores())),
        contentAlignment = Alignment.Center,
    ) {
        if (fotoLocal != null) {
            AsyncImage(
                model = File(fotoLocal),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(tam),
            )
        } else {
            Text(especie.emoji, fontSize = (tam.value * 0.46f).sp)
        }
    }
}
