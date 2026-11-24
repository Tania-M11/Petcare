package com.udenar.petcare.ui.componentes

import com.udenar.petcare.domain.modelo.Icono
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.udenar.petcare.domain.recomendaciones.PoseChopper
import com.udenar.petcare.ui.theme.Kalnia
import com.udenar.petcare.ui.theme.Pc

/**
 * Formulario como recorrido (Fase 6.1): una pregunta por pantalla, barra de progreso por
 * tramos, transición lateral y botones fijos abajo. [contenido] dibuja el paso [paso].
 */
@Composable
fun AsistentePasos(
    paso: Int,
    total: Int,
    onAtras: () -> Unit,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
    onSaltar: (() -> Unit)? = null,
    abajo: @Composable ColumnScope.() -> Unit = {},
    contenido: @Composable ColumnScope.(Int) -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Pc.Crema, Pc.Durazno.copy(alpha = 0.35f))))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonCircular(
                if (paso == 0) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
                if (paso == 0) "Cerrar" else "Atrás",
                onClick = if (paso == 0) onCerrar else onAtras,
                modifier = Modifier.testTag("asistente_atras"),
            )
            // Progreso por tramos: se llena con resorte al avanzar
            Row(Modifier.weight(1f).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(total) { i ->
                    val lleno by animateFloatAsState(if (i <= paso) 1f else 0f, spring(stiffness = Spring.StiffnessLow), label = "tramo")
                    Box(Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(50)).background(Pc.Blanco)) {
                        Box(Modifier.fillMaxWidth(lleno).height(7.dp).clip(RoundedCornerShape(50)).background(Pc.Naranja))
                    }
                }
            }
            if (onSaltar != null) {
                TextButton(onClick = onSaltar, modifier = Modifier.testTag("asistente_saltar")) { Text("Saltar", color = Pc.TintaSuave) }
            } else {
                Box(Modifier.size(48.dp))
            }
        }
        AnimatedContent(
            paso,
            transitionSpec = {
                val adelante = targetState > initialState
                (slideInHorizontally(tween(320)) { if (adelante) it / 3 else -it / 3 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(220)) { if (adelante) -it / 3 else it / 3 } + fadeOut(tween(200)))
            },
            modifier = Modifier.weight(1f),
            label = "paso",
        ) { p ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) { contenido(p) }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { abajo() }
    }
}

/** Encabezado de cada paso: Chopper pregunta en grande; la ayuda va debajo. */
@Composable
fun PreguntaChopper(pregunta: String, ayuda: String? = null, pose: PoseChopper = PoseChopper.ORGANIZADOR) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(pose.dibujo()), contentDescription = null, modifier = Modifier.size(72.dp))
        Column(Modifier.padding(start = 10.dp)) {
            Text(pregunta, fontFamily = Kalnia, fontSize = 26.sp, lineHeight = 30.sp, color = Pc.Tinta, modifier = Modifier.testTag("pregunta"))
            ayuda?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

/** Opción grande e ilustrada; rebota al elegirla. [dibujo] o [emoji]. */
@Composable
fun TarjetaOpcionGrande(
    titulo: String,
    seleccionada: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    dibujo: Int? = null,
    icono: Icono? = null,
    color: Color = Pc.Durazno,
) {
    val escala by animateFloatAsState(if (seleccionada) 1.04f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "opcion")
    val grosor by animateDpAsState(if (seleccionada) 3.dp else 0.dp, label = "borde")
    Column(
        modifier
            .scale(escala)
            .clip(RoundedCornerShape(26.dp))
            .background(if (seleccionada) color else Pc.Blanco)
            .border(BorderStroke(grosor, Pc.Naranja), RoundedCornerShape(26.dp))
            .selectable(selected = seleccionada, role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            dibujo != null -> Image(painterResource(dibujo), contentDescription = null, modifier = Modifier.size(64.dp))
            icono != null -> BaldosaIcono(icono, fondo = if (seleccionada) Pc.Blanco.copy(alpha = 0.7f) else color.copy(alpha = 0.45f), tam = 60.dp)
        }
        Text(titulo, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, color = Pc.Tinta)
        subtitulo?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave, textAlign = TextAlign.Center) }
    }
}

/** Selector de número con botones grandes − / + (peso, cantidades). */
@Composable
fun Stepper(valor: Double, onCambio: (Double) -> Unit, paso: Double, unidad: String, minimo: Double = 0.0, maximo: Double = 200.0, decimales: Int = 1) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        BotonCircular(Icons.Rounded.Remove, "Menos", onClick = { onCambio((valor - paso).coerceAtLeast(minimo)) }, modifier = Modifier.testTag("stepper_menos"))
        Text(
            "%.${decimales}f %s".format(java.util.Locale.forLanguageTag("es-CO"), valor, unidad),
            fontFamily = Kalnia, fontSize = 34.sp, color = Pc.Tinta, modifier = Modifier.padding(horizontal = 20.dp).testTag("stepper_valor"),
        )
        BotonCircular(Icons.Rounded.Add, "Más", onClick = { onCambio((valor + paso).coerceAtMost(maximo)) }, modifier = Modifier.testTag("stepper_mas"))
    }
}

/** Fila del resumen final: etiqueta, valor y toque para volver a ese paso. */
@Composable
fun FilaResumen(etiqueta: String, valor: String, onEditar: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).selectable(selected = false, onClick = onEditar).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(etiqueta, style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave, modifier = Modifier.weight(0.4f))
        Text(valor, style = MaterialTheme.typography.bodyLarge, color = Pc.Tinta, modifier = Modifier.weight(0.6f))
        Text("Cambiar", style = MaterialTheme.typography.labelMedium, color = Pc.Naranja)
    }
}
