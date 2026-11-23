package com.udenar.petcare.ui.componentes

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import com.udenar.petcare.R
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

/** Ilustración de cada especie (carpeta icons/, convertidas a WebP de 256 px). */
@DrawableRes
fun Especie.ilustracion(): Int = when (this) {
    Especie.PERRO -> R.drawable.especie_perro
    Especie.GATO -> R.drawable.especie_gato
    Especie.AVE -> R.drawable.especie_ave
    Especie.CONEJO -> R.drawable.especie_conejo
    Especie.ROEDOR -> R.drawable.especie_roedor
    Especie.REPTIL -> R.drawable.especie_reptil
    Especie.PEZ -> R.drawable.especie_pez
    Especie.EQUINO -> R.drawable.especie_equino
    Especie.OTRO -> R.drawable.especie_otro
}

/** Opción de especie con su ilustración (mismo estilo que ChipOpcion). */
@Composable
fun ChipEspecie(especie: Especie, seleccionado: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (seleccionado) Pc.Durazno else Pc.Blanco)
            .border(1.5.dp, if (seleccionado) Pc.Naranja else Pc.Linea, RoundedCornerShape(50))
            .selectable(selected = seleccionado, role = Role.RadioButton, onClick = onClick)
            .padding(start = 8.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(especie.ilustracion()), contentDescription = null, modifier = Modifier.size(28.dp))
        Text(
            especie.etiqueta,
            style = MaterialTheme.typography.labelLarge,
            color = if (seleccionado) Pc.Tinta else Pc.TintaSuave,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** Foto de la mascota (archivo local) o, si no hay, la ilustración de su especie sobre un degradado. */
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
            Image(painterResource(especie.ilustracion()), contentDescription = null, modifier = Modifier.size(tam * 0.72f))
        }
    }
}
