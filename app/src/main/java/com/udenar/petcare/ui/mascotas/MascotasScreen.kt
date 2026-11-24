package com.udenar.petcare.ui.mascotas

import com.udenar.petcare.ui.componentes.colores
import com.udenar.petcare.ui.cuidados.icono
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.petcare.R
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.ui.componentes.AvatarMascota
import com.udenar.petcare.ui.componentes.BotonCircular
import com.udenar.petcare.ui.componentes.FilaOpcion
import com.udenar.petcare.ui.componentes.TarjetaIlustrada
import com.udenar.petcare.ui.theme.Pc

/** Pestaña Mascotas: lista de mis mascotas y menú "+" con las tres formas de sumar una. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MascotasScreen(
    avisar: (String) -> Unit,
    onAgregar: () -> Unit,
    onCuidar: () -> Unit,
    onQrLeido: (String) -> Unit,
    onAbrir: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MascotasViewModel = hiltViewModel(),
) {
    val mascotas by viewModel.mascotas.collectAsStateWithLifecycle()
    val dias by viewModel.hoy.collectAsStateWithLifecycle()
    var menuAbierto by remember { mutableStateOf(false) }
    val contexto = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
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
                    modifier = Modifier.testTag("boton_mas"),
                )
            }
        }
        val lista = mascotas
        when {
            lista == null -> item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Pc.Naranja)
                }
            }
            lista.isEmpty() -> item {
                TarjetaIlustrada(
                    titulo = "Aún no hay mascotas",
                    texto = "Agrega la tuya o pide el código para cuidar la de alguien.",
                    ilustracion = R.drawable.chopper_amigos,
                    degradado = listOf(Pc.Lavanda, Pc.LavandaOscura),
                    alto = 210.dp,
                    onClick = { menuAbierto = true },
                )
            }
            else -> {
                val (mias, cuidadas) = lista.partition { it.esPropietario }
                items(mias, key = { it.id }) { m ->
                    LaunchedEffect(m.id, m.fotoUrl) { viewModel.asegurarFoto(m) }
                    TarjetaMascota(m, viewModel.descripcion(m), dias[m.id], onClick = { onAbrir(m.id) })
                }
                if (cuidadas.isNotEmpty()) {
                    item {
                        Text("Cuidas a…", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(cuidadas, key = { it.id }) { m ->
                        LaunchedEffect(m.id, m.fotoUrl) { viewModel.asegurarFoto(m) }
                        TarjetaMascota(m, viewModel.descripcion(m), dias[m.id], onClick = { onAbrir(m.id) })
                    }
                }
            }
        }
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
                FilaOpcion(Icons.Rounded.Pets, "Agregar mascota", "Crea su ficha, foto y cuidados", Pc.Durazno, Pc.Naranja, icono3d = com.udenar.petcare.domain.modelo.Icono.HUELLAS, onClick = {
                    menuAbierto = false
                    onAgregar()
                })
                FilaOpcion(Icons.Rounded.Key, "Cuidar con código", "Escribe el código que te compartió el dueño", Pc.LavandaClara, Pc.Lavanda, icono3d = com.udenar.petcare.domain.modelo.Icono.EQUIPO, onClick = {
                    menuAbierto = false
                    onCuidar()
                })
                FilaOpcion(Icons.Rounded.QrCodeScanner, "Escanear QR", "Lee el carné de una mascota", Pc.CieloClaro, Pc.Cielo, icono3d = com.udenar.petcare.domain.modelo.Icono.CAMARA, onClick = {
                    menuAbierto = false
                    com.udenar.petcare.ui.compartir.escanearQr(contexto, onTexto = onQrLeido, onError = avisar)
                })
            }
        }
    }
}

@Composable
private fun TarjetaMascota(m: Mascota, descripcion: String, dia: DiaMascota?, onClick: () -> Unit) {
    // Tarjeta con el color de su especie, cómo va su día y lo próximo que le toca
    val color = m.especie.colores().first()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(color.copy(alpha = 0.45f), Pc.Blanco, Pc.Blanco)))
            .clickable(onClick = onClick)
            .testTag("mascota_${m.nombre}")
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarMascota(m.especie, m.fotoLocal, tam = 72.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(m.nombre, style = MaterialTheme.typography.headlineSmall)
            Text(descripcion, style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
            when {
                dia == null -> Unit
                dia.proximo != null -> com.udenar.petcare.ui.componentes.Chip(
                    dia.proximo, fondo = Pc.Crema, icono = dia.tipoProximo?.icono,
                    modifier = Modifier.padding(top = 4.dp),
                )
                else -> com.udenar.petcare.ui.componentes.Chip("Todo listo por hoy", fondo = Pc.MentaClara, icono = com.udenar.petcare.domain.modelo.Icono.CHECK, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (dia != null && dia.total > 0) com.udenar.petcare.ui.componentes.AnilloProgreso(dia.hechos, dia.total, tam = 56.dp)
        if (m.porSubir) {
            Icon(
                Icons.Rounded.CloudUpload,
                contentDescription = "Por subir",
                tint = Pc.TintaSuave.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
