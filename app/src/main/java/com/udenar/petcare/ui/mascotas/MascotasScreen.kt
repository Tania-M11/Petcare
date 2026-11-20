package com.udenar.petcare.ui.mascotas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.udenar.petcare.R
import com.udenar.petcare.ui.componentes.BotonCircular
import com.udenar.petcare.ui.componentes.FilaOpcion
import com.udenar.petcare.ui.componentes.TarjetaIlustrada
import com.udenar.petcare.ui.theme.Pc

/** Pestaña Mascotas: lista (vacía por ahora) y menú "+" con las tres formas de sumar una mascota. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MascotasScreen(avisar: (String) -> Unit, modifier: Modifier = Modifier) {
    var menuAbierto by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tus compañeros", style = MaterialTheme.typography.bodyLarge, color = Pc.TintaSuave)
                Text("Mascotas", style = MaterialTheme.typography.headlineLarge)
            }
            BotonCircular(
                icono = Icons.Rounded.Add,
                descripcion = "Agregar",
                onClick = { menuAbierto = true },
                fondo = Pc.Naranja,
                tinte = Pc.Blanco,
            )
        }

        TarjetaIlustrada(
            titulo = "Aún no hay mascotas",
            texto = "Agrega la tuya o pide el código para cuidar la de alguien.",
            ilustracion = R.drawable.chopper_amigos,
            degradado = listOf(Pc.Lavanda, Pc.LavandaOscura),
            alto = 210.dp,
            onClick = { menuAbierto = true },
        )
    }

    if (menuAbierto) {
        ModalBottomSheet(onDismissRequest = { menuAbierto = false }, containerColor = Pc.Crema) {
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .navigationBarsPadding(),
            ) {
                Text(
                    "¿Qué quieres hacer?",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                )
                val opciones = listOf(
                    Triple(Icons.Rounded.Pets, "Agregar mascota", "Crea su ficha, foto y cuidados"),
                    Triple(Icons.Rounded.Key, "Cuidar con código", "Escribe el código que te compartió el dueño"),
                    Triple(Icons.Rounded.QrCodeScanner, "Escanear QR", "Lee el carné de una mascota"),
                )
                val colores = listOf(Pc.Durazno to Pc.Naranja, Pc.LavandaClara to Pc.Lavanda, Pc.CieloClaro to Pc.Cielo)
                opciones.forEachIndexed { i, (icono, titulo, sub) ->
                    FilaOpcion(
                        icono = icono,
                        titulo = titulo,
                        subtitulo = sub,
                        fondoIcono = colores[i].first,
                        colorIcono = colores[i].second,
                        onClick = {
                            menuAbierto = false
                            avisar("\"$titulo\" llega en las próximas partes")
                        },
                    )
                }
            }
        }
    }
}
