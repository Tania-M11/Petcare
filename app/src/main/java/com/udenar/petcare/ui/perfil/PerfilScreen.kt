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
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Explore
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.udenar.petcare.BuildConfig
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.udenar.petcare.R
import com.udenar.petcare.ui.componentes.Chip
import com.udenar.petcare.ui.componentes.FilaOpcion
import com.udenar.petcare.ui.componentes.TarjetaIlustrada
import com.udenar.petcare.domain.sync.EstadoSync
import com.udenar.petcare.ui.theme.Pc
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pestaña Perfil: cuenta, puerta al modo profesional, ajustes y créditos. */
@Composable
fun PerfilScreen(
    avisar: (String) -> Unit,
    irAIntenciones: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PerfilViewModel = hiltViewModel(),
) {
    var acercaDe by remember { mutableStateOf(false) }
    val conexion by viewModel.conexion.collectAsStateWithLifecycle()
    val usuario by viewModel.usuario.collectAsStateWithLifecycle()
    val estadoSync by viewModel.estadoSync.collectAsStateWithLifecycle()
    val horaExacta by viewModel.horaExacta.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    var notificacionesActivas by remember { mutableStateOf(NotificationManagerCompat.from(contexto).areNotificationsEnabled()) }
    LifecycleResumeEffect(Unit) {
        notificacionesActivas = NotificationManagerCompat.from(contexto).areNotificationsEnabled()
        viewModel.revisarHoraExacta()
        onPauseOrDispose { }
    }
    val pedirNotificaciones = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        notificacionesActivas = ok
        if (!ok) avisar("Actívalas en los ajustes del teléfono para recibir recordatorios")
    }
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
                icono = Icons.Rounded.Explore,
                titulo = "¿Qué te trae a PetCare?",
                subtitulo = usuario?.intenciones?.takeIf { it.isNotEmpty() }?.joinToString(" · ") { it.titulo }
                    ?: "Personaliza tus primeros pasos",
                fondoIcono = Pc.SolClaro,
                colorIcono = Pc.Naranja,
                onClick = irAIntenciones,
            )
            FilaOpcion(
                icono = Icons.Rounded.NotificationsActive,
                titulo = "Recordatorios",
                subtitulo = when {
                    !notificacionesActivas -> "Notificaciones desactivadas · toca para activarlas"
                    !horaExacta -> "Pueden llegar unos minutos tarde · toca para permitir la hora exacta"
                    else -> "Activados, a la hora exacta"
                },
                fondoIcono = if (notificacionesActivas && horaExacta) Pc.MentaClara else Pc.Durazno,
                colorIcono = if (notificacionesActivas && horaExacta) Pc.Menta else Pc.Naranja,
                onClick = {
                    when {
                        !notificacionesActivas && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                            pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
                        !notificacionesActivas -> contexto.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, contexto.packageName),
                        )
                        !horaExacta && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> contexto.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${contexto.packageName}".toUri()),
                        )
                        else -> avisar("Tus recordatorios están listos")
                    }
                },
                modifier = Modifier.testTag("fila_recordatorios"),
            )
            if (BuildConfig.DEBUG) {
                FilaOpcion(
                    icono = Icons.Rounded.Alarm,
                    titulo = "Probar recordatorio",
                    subtitulo = "Solo en desarrollo: suena en 1 minuto, aunque cierres la app",
                    fondoIcono = Pc.SolClaro,
                    colorIcono = Pc.Naranja,
                    onClick = {
                        viewModel.probarRecordatorio()
                        avisar("Cierra la app: en 1 minuto llega la notificación")
                    },
                )
            }
            FilaOpcion(
                icono = Icons.Rounded.CloudSync,
                titulo = "Sincronización",
                subtitulo = describirSync(estadoSync),
                fondoIcono = Pc.CieloClaro,
                colorIcono = Pc.CieloOscuro,
                onClick = {
                    viewModel.sincronizarAhora()
                    avisar("Sincronizando cuando haya conexión…")
                },
                modifier = Modifier.testTag("fila_sync"),
            )
            FilaOpcion(
                icono = Icons.Rounded.Info,
                titulo = "Acerca de PetCare",
                subtitulo = "Versión 0.7 · créditos",
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

private val HORA = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.forLanguageTag("es-CO"))

/** "Al día · 19 nov, 9:05 p. m." / "2 cambios por subir" / "Aún no sincroniza". */
internal fun describirSync(e: EstadoSync?): String = when {
    e == null -> "Revisando…"
    e.porSubir == 1 -> "1 cambio por subir"
    e.porSubir > 1 -> "${e.porSubir} cambios por subir"
    e.ultima == null -> "Aún no sincroniza en este teléfono"
    else -> "Al día · " + HORA.format(e.ultima.atZone(ZoneId.systemDefault()))
}
