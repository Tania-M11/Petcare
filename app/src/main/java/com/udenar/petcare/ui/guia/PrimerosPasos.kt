package com.udenar.petcare.ui.guia

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.udenar.petcare.R
import com.udenar.petcare.domain.guia.EstadoPaso
import com.udenar.petcare.domain.guia.PasoGuia
import com.udenar.petcare.domain.guia.ProgresoGuia
import com.udenar.petcare.ui.componentes.BlobDecorativo
import com.udenar.petcare.ui.componentes.Chip
import com.udenar.petcare.ui.componentes.grano
import com.udenar.petcare.ui.theme.Pc

/** Tarjeta "Primeros pasos con Chopper" (plan, sección 4). */
@Composable
fun TarjetaPrimerosPasos(
    progreso: ProgresoGuia,
    onPaso: (PasoGuia) -> Unit,
    onOmitir: (PasoGuia) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Pc.Blanco)
            .testTag("primeros_pasos")
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                BlobDecorativo(listOf(Pc.Durazno, Pc.LavandaClara), semilla = 5, modifier = Modifier.fillMaxSize())
                Image(painterResource(R.drawable.chopper_organizador), contentDescription = null, modifier = Modifier.height(66.dp))
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Chip("Primeros pasos", fondo = Pc.Durazno, color = Pc.Naranja)
                Text("Tu camino en PetCare", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 6.dp))
            }
            Text(
                "${progreso.resueltos} de ${progreso.total}",
                style = MaterialTheme.typography.labelLarge,
                color = Pc.Naranja,
                modifier = Modifier.testTag("progreso_guia"),
            )
        }

        // Barra de progreso con el degradado de la marca
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(Pc.Crema),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(if (progreso.total == 0) 0f else progreso.resueltos / progreso.total.toFloat())
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Pc.Lavanda, Pc.Naranja)))
                    .grano(0.4f),
            )
        }

        progreso.pasos.forEach { (paso, estado) -> FilaPaso(paso, estado, onPaso, onOmitir) }
    }
}

@Composable
private fun FilaPaso(paso: PasoGuia, estado: EstadoPaso, onPaso: (PasoGuia) -> Unit, onOmitir: (PasoGuia) -> Unit) {
    val pendiente = estado == EstadoPaso.PENDIENTE
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (pendiente) Modifier.clickable { onPaso(paso) } else Modifier)
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .testTag("paso_${paso.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    when (estado) {
                        EstadoPaso.HECHO -> Pc.Menta
                        EstadoPaso.OMITIDO -> Pc.Linea
                        EstadoPaso.PENDIENTE -> Pc.Blanco
                    },
                )
                .border(2.dp, if (pendiente) Pc.NaranjaClaro else Pc.Blanco.copy(alpha = 0f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when (estado) {
                EstadoPaso.HECHO -> Icon(Icons.Rounded.Check, contentDescription = "Hecho", tint = Pc.Blanco, modifier = Modifier.size(18.dp))
                EstadoPaso.OMITIDO -> Icon(Icons.Rounded.Remove, contentDescription = "Omitido", tint = Pc.TintaSuave, modifier = Modifier.size(18.dp))
                EstadoPaso.PENDIENTE -> Unit
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                paso.titulo,
                style = MaterialTheme.typography.titleSmall,
                color = if (pendiente) Pc.Tinta else Pc.TintaSuave,
            )
            if (pendiente) Text(paso.descripcion, style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
        }
        if (pendiente) {
            IconButton(onClick = { onOmitir(paso) }, modifier = Modifier.testTag("omitir_${paso.id}")) {
                Icon(Icons.Rounded.Close, contentDescription = "Omitir ${paso.titulo}", tint = Pc.TintaSuave, modifier = Modifier.size(18.dp))
            }
        }
    }
}
