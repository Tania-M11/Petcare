package com.udenar.petcare.ui.hoy

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.udenar.petcare.ui.componentes.grano
import com.udenar.petcare.ui.theme.Kalnia
import com.udenar.petcare.ui.theme.Pc
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val ES = Locale.forLanguageTag("es-CO")

/**
 * Tira de la semana (reemplaza la pestaña Calendario): el día elegido crece y se pinta
 * de naranja, como el selector de días de la referencia visual.
 */
@Composable
fun TiraSemana(
    semana: List<LocalDate>,
    hoy: LocalDate,
    seleccionado: LocalDate,
    onElegir: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        // Barra degradada fina, eco del deslizador de la referencia
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(Pc.Lavanda, Pc.NaranjaClaro, Pc.Naranja)))
                .grano(0.5f),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            semana.forEach { dia ->
                val elegido = dia == seleccionado
                val color by animateColorAsState(if (elegido) Pc.Naranja else Pc.TintaSuave.copy(alpha = 0.55f), label = "color")
                val tam by animateDpAsState(if (elegido) 32.dp else 20.dp, label = "tam")
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onElegir(dia) }
                        .padding(vertical = 4.dp)
                        .testTag("dia_${dia.dayOfMonth}"),
                ) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (dia == hoy) Pc.Naranja else Pc.Linea),
                    )
                    Text(
                        dia.dayOfMonth.toString(),
                        fontFamily = Kalnia,
                        fontSize = tam.value.sp,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        if (elegido && dia == hoy) "Hoy" else dia.dayOfWeek.getDisplayName(TextStyle.SHORT, ES).trimEnd('.'),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                    )
                }
            }
        }
    }
}
