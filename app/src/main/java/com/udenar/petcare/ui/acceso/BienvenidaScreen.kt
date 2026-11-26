package com.udenar.petcare.ui.acceso

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.udenar.petcare.R
import com.udenar.petcare.domain.modelo.Icono
import com.udenar.petcare.ui.componentes.BaldosaIcono
import com.udenar.petcare.ui.componentes.BotonEmpezar
import com.udenar.petcare.ui.componentes.HuellasFlotantes
import com.udenar.petcare.ui.componentes.formaNube
import com.udenar.petcare.ui.componentes.grano
import com.udenar.petcare.ui.theme.Pc
import kotlinx.coroutines.delay

private data class Ventaja(val icono: Icono, val texto: String)

private val VENTAJAS = listOf(
    Ventaja(Icono.ALARMA, "Recordatorios incluso sin conexión"),
    Ventaja(Icono.PASEO, "Paseos con mapa y distancia"),
    Ventaja(Icono.EQUIPO, "Cuidado compartido con tu familia"),
    Ventaja(Icono.VETERINARIO, "Veterinarios y servicios cercanos"),
)

/**
 * Portada sin sesión: Michi dormido en su cojín bajo una nube de color con el logo, el título
 * grande, lo que hace la app (rota solo) y el botón para empezar.
 */
@Composable
fun BienvenidaScreen(onCrearCuenta: () -> Unit, onEntrar: () -> Unit) {
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_800)
            i = (i + 1) % VENTAJAS.size
        }
    }
    val respira = rememberInfiniteTransition(label = "respira")
    val escala by respira.animateFloat(1f, 1.03f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "escala")

    Column(Modifier.fillMaxSize().background(Pc.Crema).navigationBarsPadding()) {
        // Nube de color con el logo y Michi
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(formaNube(ondas = 4, alto = 0.06f))
                .background(Brush.verticalGradient(listOf(Pc.LavandaClara, Pc.Durazno)))
                .grano(0.3f),
        ) {
            HuellasFlotantes()
            Image(
                painterResource(R.drawable.logo_petcare),
                contentDescription = "PetCare",
                modifier = Modifier.statusBarsPadding().padding(start = 24.dp, top = 16.dp).height(34.dp),
            )
            Image(
                painterResource(R.drawable.gato_dormido),
                contentDescription = "Michi durmiendo en su cojín",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
                    .fillMaxWidth(0.86f)
                    .offset(y = (-6 * (escala - 1) * 100).dp)
                    .testTag("portada_michi"),
            )
        }

        Column(Modifier.padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Puntos(VENTAJAS.size, i)
            Text(
                buildAnnotatedString {
                    append("Cuidar juntos\nes ")
                    withStyle(SpanStyle(color = Pc.Naranja)) { append("más fácil") }
                },
                style = MaterialTheme.typography.displaySmall,
            )
            AnimatedContent(
                VENTAJAS[i],
                transitionSpec = { (fadeIn(tween(300)) + slideInHorizontally { it / 4 }) togetherWith fadeOut(tween(150)) },
                label = "ventaja",
            ) { v ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BaldosaIcono(v.icono, tam = 36.dp)
                    Text(v.texto, style = MaterialTheme.typography.titleSmall, color = Pc.TintaSuave, modifier = Modifier.padding(start = 12.dp))
                }
            }
            Spacer(Modifier.height(2.dp))
            BotonEmpezar("Crear mi cuenta", onClick = onCrearCuenta, modifier = Modifier.testTag("crear_cuenta"))
            TextButton(onClick = onEntrar, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("ya_tengo_cuenta")) {
                Text(
                    buildAnnotatedString {
                        append("¿Ya tienes cuenta? ")
                        withStyle(SpanStyle(color = Pc.Naranja)) { append("Entrar") }
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Pc.TintaSuave,
                )
            }
        }
    }
}

/** Indicador de páginas: el actual se alarga en una píldora naranja. */
@Composable
private fun Puntos(total: Int, actual: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { n ->
            val ancho by animateDpAsState(if (n == actual) 22.dp else 8.dp, label = "ancho")
            val color by animateColorAsState(if (n == actual) Pc.Naranja else Pc.Linea, label = "color")
            Box(Modifier.height(8.dp).width(ancho).clip(RoundedCornerShape(50)).background(color))
        }
    }
}
