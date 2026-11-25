package com.udenar.petcare.ui.componentes

import com.udenar.petcare.domain.modelo.Icono
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.udenar.petcare.ui.theme.Pc

/** Botón redondo blanco con ícono (barra superior, como en la referencia). */
@Composable
fun BotonCircular(
    icono: ImageVector,
    descripcion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fondo: Color = Pc.Blanco,
    tinte: Color = Pc.Tinta,
    tam: Dp = 46.dp,
) {
    Box(
        modifier = modifier
            .size(tam)
            .shadow(6.dp, CircleShape, ambientColor = Pc.Naranja.suave(0.25f), spotColor = Pc.Naranja.suave(0.25f))
            .clip(CircleShape)
            .background(fondo)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = descripcion, tint = tinte, modifier = Modifier.size(tam * 0.46f))
    }
}

/** Etiqueta en píldora (p. ej. "Primeros pasos"). */
@Composable
fun Chip(texto: String, modifier: Modifier = Modifier, fondo: Color = Pc.Blanco, color: Color = Pc.Tinta, icono: Icono? = null) {
    androidx.compose.foundation.layout.Row(
        modifier.clip(RoundedCornerShape(50)).background(fondo).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        icono?.let { Icono3D(it, tam = 16.dp) }
        Text(texto, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** Fondo orgánico con degradado y grano, detrás de las ilustraciones de Chopper. */
@Composable
fun BlobDecorativo(colores: List<Color>, semilla: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val forma = pathBlob(size.width, size.height, semilla)
        // El grano se recorta a la silueta del blob para que no se vea el rectángulo
        clipPath(forma) {
            drawRect(Brush.linearGradient(colores))
            drawRect(brushGrano(), alpha = 0.4f)
        }
    }
}

/**
 * Tarjeta protagonista con degradado, grano, blob e ilustración de Chopper
 * (inspirada en las tarjetas "Self care" de la referencia).
 */
@Composable
fun TarjetaIlustrada(
    titulo: String,
    @DrawableRes ilustracion: Int,
    degradado: List<Color>,
    modifier: Modifier = Modifier,
    chip: String? = null,
    texto: String? = null,
    colorTexto: Color = Pc.SobreAcento,
    alto: Dp = 190.dp,
    onClick: (() -> Unit)? = null,
) {
    val forma = MaterialTheme.shapes.large
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(alto)
            .shadow(14.dp, forma, ambientColor = degradado.last().suave(0.5f), spotColor = degradado.last().suave(0.5f))
            .clip(forma)
            .background(Brush.linearGradient(degradado))
            .grano()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        BlobDecorativo(
            colores = listOf(Pc.SobreAcento.suave(0.45f), Pc.SobreAcento.suave(0.05f)),
            semilla = titulo.length,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 4.dp, bottom = 2.dp)
                .size(alto * 0.95f),
        )
        Image(
            painter = painterResource(ilustracion),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp)
                .height(alto * 0.82f)
                .fillMaxWidth(0.46f),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            if (chip != null) Chip(chip, color = if (colorTexto == Pc.SobreAcento) degradado.last() else Pc.Tinta)
            Spacer(Modifier.weight(1f))
            Text(
                titulo,
                style = MaterialTheme.typography.headlineSmall,
                color = colorTexto,
                modifier = Modifier.fillMaxWidth(0.55f),
            )
            if (texto != null) {
                Text(
                    texto,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorTexto.suave(0.9f),
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .padding(top = 4.dp),
                )
            }
        }
        if (onClick != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Pc.Blanco.suave(0.35f))
                    .border(1.dp, Pc.SobreAcento.suave(0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = colorTexto, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Estado vacío con Chopper: ilustración, título y explicación breve (RNF-10). */
@Composable
fun EstadoVacio(
    @DrawableRes ilustracion: Int,
    titulo: String,
    texto: String,
    modifier: Modifier = Modifier,
    fondoBlob: List<Color> = listOf(Pc.Durazno, Pc.LavandaClara),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Pc.Blanco)
            .padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
            BlobDecorativo(fondoBlob, semilla = titulo.length, modifier = Modifier.fillMaxSize())
            Image(
                painterResource(ilustracion),
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .height(122.dp),
            )
        }
        Text(titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = Pc.TintaSuave,
            textAlign = TextAlign.Center,
        )
    }
}

/** Encabezado de sección: título en Quicksand con acción opcional a la derecha. */
@Composable
fun TituloSeccion(texto: String, modifier: Modifier = Modifier, accion: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        accion?.invoke()
    }
}

/** Fila de opción para listas de ajustes (ícono en cuadro pastel + texto). */
@Composable
fun FilaOpcion(
    icono: ImageVector,
    titulo: String,
    subtitulo: String?,
    fondoIcono: Color,
    colorIcono: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icono3d: com.udenar.petcare.domain.modelo.Icono? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icono3d != null) BaldosaIcono(icono3d, fondo = fondoIcono, tam = 42.dp) else Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(fondoIcono),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icono, contentDescription = null, tint = colorIcono, modifier = Modifier.size(22.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(titulo, style = MaterialTheme.typography.titleSmall)
            if (subtitulo != null) {
                Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            }
        }
    }
}
