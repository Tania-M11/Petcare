package com.udenar.petcare.ui.guia

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.petcare.R
import com.udenar.petcare.domain.guia.Intencion
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.grano
import com.udenar.petcare.ui.theme.Pc

private data class AspectoIntencion(val ilustracion: Int, val claro: Color, val fuerte: Color)

private fun Intencion.aspecto() = when (this) {
    Intencion.MASCOTAS -> AspectoIntencion(R.drawable.chopper_amigos, Pc.Durazno, Pc.NaranjaClaro)
    Intencion.CUIDAR -> AspectoIntencion(R.drawable.chopper_comida, Pc.SolClaro, Pc.Sol)
    Intencion.VETERINARIO -> AspectoIntencion(R.drawable.chopper_veterinario, Pc.CieloClaro, Pc.Cielo)
    Intencion.SERVICIOS -> AspectoIntencion(R.drawable.chopper_organizador, Pc.LavandaClara, Pc.Lavanda)
}

/**
 * "¿Qué te trae a PetCare?" (plan, sección 4). Se puede elegir varias o saltar.
 * [onListo] se llama cuando la respuesta quedó guardada.
 */
@Composable
fun IntencionesScreen(
    onListo: () -> Unit,
    onVolver: (() -> Unit)? = null,
    viewModel: IntencionesViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.listo) { if (estado.listo) onListo() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Pc.Crema)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Chopper quiere saber", style = MaterialTheme.typography.bodyLarge, color = Pc.TintaSuave)
                Text("¿Qué te trae a PetCare?", style = MaterialTheme.typography.headlineLarge)
            }
            Image(painterResource(R.drawable.chopper_saludo), contentDescription = null, modifier = Modifier.height(92.dp))
        }
        Text(
            "Elige todas las que quieras. Así te mostramos por dónde empezar; puedes cambiarlo después en tu Perfil.",
            style = MaterialTheme.typography.bodyMedium,
            color = Pc.TintaSuave,
        )

        Intencion.entries.chunked(2).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                fila.forEach { i ->
                    TarjetaIntencion(
                        intencion = i,
                        elegida = i in estado.elegidas,
                        onAlternar = { viewModel.alternar(i) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        estado.error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(4.dp))
        BotonPrincipal(
            texto = if (estado.elegidas.isEmpty()) "Elige al menos una" else "Continuar",
            onClick = viewModel::continuar,
            habilitado = estado.elegidas.isNotEmpty(),
            cargando = estado.guardando,
        )
        TextButton(
            onClick = onVolver ?: viewModel::ahoraNo,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(if (onVolver != null) "Cancelar" else "Ahora no", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
        }
    }
}

@Composable
private fun TarjetaIntencion(intencion: Intencion, elegida: Boolean, onAlternar: () -> Unit, modifier: Modifier = Modifier) {
    val a = intencion.aspecto()
    val borde by animateColorAsState(if (elegida) Pc.Naranja else Color.Transparent, label = "borde")
    val escala by animateFloatAsState(if (elegida) 1.06f else 1f, label = "escala")
    val forma = MaterialTheme.shapes.large
    Column(
        modifier
            .clip(forma)
            .background(Brush.verticalGradient(listOf(a.claro, a.fuerte)))
            .grano(0.35f)
            .border(3.dp, borde, forma)
            .toggleable(value = elegida, role = Role.Checkbox, onValueChange = { onAlternar() })
            .testTag("intencion_${intencion.name}")
            .padding(14.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Image(
                painterResource(a.ilustracion),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .height(112.dp)
                    .scale(escala),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (elegida) Pc.Naranja else Pc.Blanco.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                if (elegida) Icon(Icons.Rounded.Check, contentDescription = null, tint = Pc.Blanco, modifier = Modifier.size(18.dp))
            }
        }
        Text(intencion.titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
        Text(intencion.descripcion, style = MaterialTheme.typography.bodySmall, color = Pc.Tinta.copy(alpha = 0.7f))
    }
}
