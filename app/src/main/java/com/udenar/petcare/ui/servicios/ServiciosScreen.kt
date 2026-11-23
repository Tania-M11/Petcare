package com.udenar.petcare.ui.servicios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.udenar.petcare.R
import com.udenar.petcare.ui.componentes.TarjetaIlustrada
import com.udenar.petcare.ui.theme.Pc

/** Pestaña Servicios: puerta a los directorios de veterinarios y prestadores (fase 4). */
@Composable
fun ServiciosScreen(avisar: (String) -> Unit, onVeterinarios: () -> Unit, onPrestadores: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column {
            Text("Encuentra quién te ayude", style = MaterialTheme.typography.bodyLarge, color = Pc.TintaSuave)
            Text("Servicios", style = MaterialTheme.typography.headlineLarge)
        }

        MisSolicitudes(avisar = avisar)

        // Buscador en píldora (se activa con los directorios)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Pc.Blanco)
                .clickable(onClick = onVeterinarios)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = Pc.TintaSuave, modifier = Modifier.size(22.dp))
            Text(
                "Busca veterinarios por ciudad",
                style = MaterialTheme.typography.bodyMedium,
                color = Pc.TintaSuave,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        TarjetaIlustrada(
            titulo = "Veterinarios",
            texto = "Con insignia de verificación, cerca de ti.",
            chip = "Verificados ✓",
            ilustracion = R.drawable.chopper_veterinario,
            degradado = listOf(Pc.Cielo, Pc.CieloOscuro),
            onClick = onVeterinarios,
            modifier = Modifier.testTag("tarjeta_veterinarios"),
        )
        TarjetaIlustrada(
            titulo = "Paseo, baño y cuidado",
            texto = "Prestadores con precios y reseñas.",
            chip = "Cotiza gratis",
            ilustracion = R.drawable.chopper_doctor,
            degradado = listOf(Pc.SolClaro, Pc.Sol),
            colorTexto = Pc.Tinta,
            onClick = onPrestadores,
            modifier = Modifier.testTag("tarjeta_prestadores"),
        )
    }
}
