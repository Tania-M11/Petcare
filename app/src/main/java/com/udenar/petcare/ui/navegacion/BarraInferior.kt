package com.udenar.petcare.ui.navegacion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.udenar.petcare.ui.componentes.suave
import com.udenar.petcare.ui.theme.Pc

/**
 * Barra inferior flotante: píldora oscura (contrasta con las tarjetas claras) y la pestaña
 * activa se abre en una píldora naranja con su nombre.
 */
@Composable
fun <T : Pestana> BarraInferior(pestanas: List<T>, actual: T?, onElegir: (T) -> Unit, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(32.dp)
    // Con más de 4 pestañas solo van los íconos (la activa en su píldora naranja) para que quepan
    val compacta = pestanas.size > 4
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = if (compacta) 14.dp else 20.dp, vertical = 12.dp)
            .fillMaxWidth()
            .shadow(18.dp, forma, ambientColor = Pc.Tinta.suave(0.4f), spotColor = Pc.Tinta.suave(0.4f))
            .clip(forma)
            .background(Pc.Tinta)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pestanas.forEach { pestana ->
            val activa = pestana == actual
            val fondo by animateColorAsState(if (activa) Pc.Naranja else Pc.Tinta, label = "fondo")
            val tinte by animateColorAsState(if (activa) Pc.SobreAcento else Pc.Crema.suave(0.6f), label = "tinte")
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(fondo)
                    .selectable(selected = activa, role = Role.Tab, onClick = { onElegir(pestana) })
                    .testTag(pestana.etiquetaPrueba)
                    .padding(horizontal = if (compacta) 12.dp else 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(pestana.icono, contentDescription = pestana.etiqueta, tint = tinte, modifier = Modifier.size(24.dp))
                AnimatedVisibility(activa && !compacta, enter = expandHorizontally(), exit = shrinkHorizontally()) {
                    Text(
                        pestana.etiqueta,
                        style = MaterialTheme.typography.labelLarge,
                        color = Pc.SobreAcento,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
