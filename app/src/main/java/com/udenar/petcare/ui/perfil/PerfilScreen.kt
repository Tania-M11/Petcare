package com.udenar.petcare.ui.perfil

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.udenar.petcare.R
import com.udenar.petcare.ui.componentes.Chip
import com.udenar.petcare.ui.componentes.FilaOpcion
import com.udenar.petcare.ui.componentes.TarjetaIlustrada
import com.udenar.petcare.ui.theme.Pc

/** Pestaña Perfil: cuenta, puerta al modo profesional, ajustes y créditos. */
@Composable
fun PerfilScreen(
    avisar: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PerfilViewModel = hiltViewModel(),
) {
    var acercaDe by remember { mutableStateOf(false) }
    val conexion by viewModel.conexion.collectAsStateWithLifecycle()
    val usuario by viewModel.usuario.collectAsStateWithLifecycle()
    var confirmarSalida by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Perfil", style = MaterialTheme.typography.headlineLarge)

        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(Pc.Blanco)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Pc.Durazno),
                contentAlignment = Alignment.Center,
            ) {
                val inicial = usuario?.nombre?.trim()?.firstOrNull()?.uppercase()
                if (inicial != null) {
                    Text(inicial, style = MaterialTheme.typography.headlineMedium, color = Pc.Naranja)
                } else {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = Pc.Naranja, modifier = Modifier.size(30.dp))
                }
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(usuario?.nombre.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text(usuario?.correo.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            }
        }

        TarjetaIlustrada(
            titulo = "Trabaja con PetCare",
            texto = "¿Eres veterinario o cuidas mascotas? Activa tu perfil profesional.",
            ilustracion = R.drawable.chopper_organizador,
            degradado = listOf(Pc.Durazno, Pc.NaranjaClaro),
            colorTexto = Pc.Tinta,
            onClick = { avisar("Los perfiles profesionales llegan en la fase 4") },
        )

        Column(
            Modifier
                .clip(MaterialTheme.shapes.large)
                .background(Pc.Blanco)
                .padding(6.dp),
        ) {
            FilaOpcion(
                icono = Icons.Rounded.NotificationsActive,
                titulo = "Notificaciones",
                subtitulo = "Elige de qué mascotas recibes avisos",
                fondoIcono = Pc.MentaClara,
                colorIcono = Pc.Menta,
                onClick = { avisar("Las notificaciones llegan en la parte 2.4") },
            )
            FilaOpcion(
                icono = Icons.Rounded.Info,
                titulo = "Acerca de PetCare",
                subtitulo = "Versión 0.4 · créditos",
                fondoIcono = Pc.LavandaClara,
                colorIcono = Pc.Lavanda,
                onClick = {
                    acercaDe = true
                    viewModel.comprobarServidor()
                },
            )
            FilaOpcion(
                icono = Icons.AutoMirrored.Rounded.Logout,
                titulo = "Cerrar sesión",
                subtitulo = null,
                fondoIcono = Pc.Durazno,
                colorIcono = Pc.Naranja,
                onClick = { confirmarSalida = true },
            )
        }
    }

    if (confirmarSalida) {
        AlertDialog(
            onDismissRequest = { confirmarSalida = false },
            containerColor = Pc.Crema,
            title = { Text("¿Cerrar sesión?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Tus datos quedan guardados en tu cuenta. Puedes volver a entrar cuando quieras.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarSalida = false
                    viewModel.cerrarSesion()
                }) { Text("Cerrar sesión") }
            },
            dismissButton = { TextButton(onClick = { confirmarSalida = false }) { Text("Cancelar") } },
        )
    }

    if (acercaDe) {
        AlertDialog(
            onDismissRequest = { acercaDe = false },
            containerColor = Pc.Crema,
            confirmButton = { TextButton(onClick = { acercaDe = false }) { Text("Cerrar") } },
            title = {
                Image(painterResource(R.drawable.logo_petcare), contentDescription = "PetCare", modifier = Modifier.height(30.dp))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Proyecto académico — Universidad de Nariño.", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Tony Tony Chopper y One Piece © Eiichiro Oda / Shueisha / Toei Animation. " +
                            "Se usa como ilustración con fines académicos y sin ánimo de lucro; " +
                            "PetCare no está afiliado a sus titulares.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Pc.TintaSuave,
                    )
                    Text("Tipografías Kalnia y Quicksand (SIL Open Font License).", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                    Chip(
                        when (conexion) {
                            Conexion.COMPROBANDO -> "Servidor: comprobando…"
                            Conexion.CONECTADO -> "Servidor conectado ✓"
                            Conexion.SIN_CONEXION -> "Servidor sin conexión"
                        },
                        fondo = when (conexion) {
                            Conexion.CONECTADO -> Pc.MentaClara
                            Conexion.SIN_CONEXION -> Pc.Durazno
                            Conexion.COMPROBANDO -> Pc.Blanco
                        },
                        modifier = Modifier.testTag("estado_servidor"),
                    )
                }
            },
        )
    }
}
