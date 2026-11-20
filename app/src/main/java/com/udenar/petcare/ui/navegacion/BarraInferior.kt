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

/** Barra inferior flotante y redondeada; la pestaña activa se abre en píldora con su nombre. */
@Composable
fun BarraInferior(actual: PestanaPersonal?, onElegir: (PestanaPersonal) -> Unit, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(32.dp)
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .fillMaxWidth()
            .shadow(18.dp, forma, ambientColor = Pc.Naranja.suave(0.3f), spotColor = Pc.Naranja.suave(0.3f))
            .clip(forma)
            .background(Pc.Blanco)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PestanaPersonal.entries.forEach { pestana ->
            val activa = pestana == actual
            val fondo by animateColorAsState(if (activa) Pc.Durazno else Pc.Blanco, label = "fondo")
            val tinte by animateColorAsState(if (activa) Pc.Naranja else Pc.TintaSuave, label = "tinte")
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(fondo)
                    .selectable(selected = activa, role = Role.Tab, onClick = { onElegir(pestana) })
                    .testTag(pestana.etiquetaPrueba)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(pestana.icono, contentDescription = pestana.etiqueta, tint = tinte, modifier = Modifier.size(24.dp))
                AnimatedVisibility(activa, enter = expandHorizontally(), exit = shrinkHorizontally()) {
                    Text(
                        pestana.etiqueta,
                        style = MaterialTheme.typography.labelLarge,
                        color = Pc.Tinta,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
