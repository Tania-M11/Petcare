package com.udenar.petcare.ui.profesional

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.udenar.petcare.domain.modelo.Icono
import com.udenar.petcare.ui.componentes.Simbolo
import com.udenar.petcare.ui.theme.Pc

/** "Así te verán": la tarjeta del directorio con lo que se va escribiendo (Fase 6.7). */
@Composable
fun VistaPreviaPerfil(contenido: @Composable () -> Unit) {
    var abierta by rememberSaveable { mutableStateOf(true) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Pc.LavandaClara)
            .border(1.dp, Pc.Lavanda.copy(alpha = 0.35f), MaterialTheme.shapes.large)
            .testTag("vista_previa"),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { abierta = !abierta }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Simbolo(Icono.OJO, tam = 20.dp)
            Text("Así te verán los dueños", style = MaterialTheme.typography.labelLarge, color = Pc.LavandaOscura, modifier = Modifier.weight(1f).padding(start = 8.dp))
            Text(if (abierta) "Ocultar" else "Ver", style = MaterialTheme.typography.labelMedium, color = Pc.TintaSuave)
        }
        AnimatedVisibility(abierta) {
            Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) { contenido() }
        }
    }
}
