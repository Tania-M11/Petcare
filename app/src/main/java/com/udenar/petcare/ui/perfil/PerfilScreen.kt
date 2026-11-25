package com.udenar.petcare.ui.perfil

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.MedicalServices
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
    onPerfilVeterinario: () -> Unit,
    onPerfilPrestador: (categoria: String?) -> Unit,
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
    var eliminar by remember { mutableStateOf(false) }
    val eliminando by viewModel.eliminando.collectAsStateWithLifecycle()
    val perfilVet by viewModel.perfilVet.collectAsStateWithLifecycle()
    val perfilPres by viewModel.perfilPres.collectAsStateWithLifecycle()

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

        if (perfilPres?.activo == true) {
            TarjetaIlustrada(
                titulo = "Modo prestador",
                texto = "Tus solicitudes, agenda, clientes y servicios.",
                chip = "Cambiar de modo",
                ilustracion = R.drawable.gato_higiene,
                degradado = listOf(Pc.SolClaro, Pc.Sol),
                colorTexto = Pc.Tinta,
                onClick = viewModel::modoPrestador,
                modifier = Modifier.testTag("tarjeta_modo_pres"),
            )
        }
        if (perfilVet?.activo == true) {
            // RF-30: cambiar de modo sin cerrar sesión
            TarjetaIlustrada(
                titulo = "Modo veterinario",
                texto = "Tus solicitudes, agenda, pacientes y verificación.",
                chip = "Cambiar de modo",
                ilustracion = R.drawable.gato_doctor,
                degradado = listOf(Pc.Cielo, Pc.CieloOscuro),
                onClick = viewModel::modoVeterinario,
                modifier = Modifier.testTag("tarjeta_modo_vet"),
            )
        }
        // Perfiles para trabajar con mascotas: cada uno con su Michi y su estado
        PerfilesProfesionales(
            vetActivo = perfilVet?.activo == true,
            categoriasPres = if (perfilPres?.activo == true) perfilPres?.categorias.orEmpty() else emptyList(),
            onVeterinario = onPerfilVeterinario,
            onPrestador = onPerfilPrestador,
        )

        Column(
            Modifier
                .clip(MaterialTheme.shapes.large)
                .background(Pc.Blanco)
                .padding(6.dp),
        ) {
            FilaOpcion(
                icono = Icons.Rounded.Explore, icono3d = com.udenar.petcare.domain.modelo.Icono.BRILLOS,
                titulo = "¿Qué te trae a PetCare?",
                subtitulo = usuario?.intenciones?.takeIf { it.isNotEmpty() }?.joinToString(" · ") { it.titulo }
                    ?: "Personaliza tus primeros pasos",
                fondoIcono = Pc.SolClaro,
                colorIcono = Pc.Naranja,
                onClick = irAIntenciones,
            )
            FilaOpcion(
                icono = Icons.Rounded.NotificationsActive, icono3d = com.udenar.petcare.domain.modelo.Icono.CAMPANA,
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
                    icono = Icons.Rounded.Alarm, icono3d = com.udenar.petcare.domain.modelo.Icono.ALARMA,
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
            val resumenActivo by viewModel.resumenSemanal.collectAsStateWithLifecycle()
            FilaOpcion(
                icono = Icons.Rounded.Info, icono3d = com.udenar.petcare.domain.modelo.Icono.CALENDARIO,
                titulo = "Resumen semanal",
                subtitulo = if (resumenActivo) "Los domingos a las 7 p. m. · toca para apagarlo" else "Apagado · toca para activarlo",
                fondoIcono = if (resumenActivo) Pc.MentaClara else Pc.Crema,
                colorIcono = Pc.Menta,
                onClick = {
                    viewModel.cambiarResumen(!resumenActivo)
                    avisar(if (resumenActivo) "Listo, no te enviaré el resumen" else "Te enviaré el resumen los domingos")
                },
                modifier = Modifier.testTag("fila_resumen"),
            )
            // Apariencia (Fase 6.1): como el teléfono → claro → oscuro
            val apariencia = com.udenar.petcare.ui.theme.Apariencia.actual
            FilaOpcion(
                icono = Icons.Rounded.Info, icono3d = if (com.udenar.petcare.ui.theme.Pc.oscuro) com.udenar.petcare.domain.modelo.Icono.LUNA else com.udenar.petcare.domain.modelo.Icono.SOL,
                titulo = "Apariencia",
                subtitulo = apariencia.etiqueta + " · toca para cambiar",
                fondoIcono = Pc.LavandaClara,
                colorIcono = Pc.Lavanda,
                onClick = {
                    val siguiente = com.udenar.petcare.ui.theme.Apariencia.entries.let { it[(apariencia.ordinal + 1) % it.size] }
                    com.udenar.petcare.ui.theme.Apariencia.guardar(contexto, siguiente)
                },
                modifier = Modifier.testTag("fila_apariencia"),
            )
            FilaOpcion(
                icono = Icons.Rounded.CloudSync, icono3d = com.udenar.petcare.domain.modelo.Icono.NUBE,
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
                icono = Icons.Rounded.Info, icono3d = com.udenar.petcare.domain.modelo.Icono.INFO,
                titulo = "Acerca de PetCare",
                subtitulo = "Versión ${BuildConfig.VERSION_NAME} · créditos",
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
        TextButton(onClick = { eliminar = true }, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("eliminar_cuenta")) {
            Text("Eliminar mi cuenta", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
        }
    }

    if (eliminar) {
        var confirmacion by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!eliminando) eliminar = false },
            containerColor = Pc.Crema,
            title = { Text("¿Eliminar tu cuenta?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Se borran para siempre tus mascotas, sus cuidados, fotos y documentos, tus perfiles profesionales, " +
                            "solicitudes y reseñas. Quienes cuidan tus mascotas pierden el acceso. No se puede deshacer.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    com.udenar.petcare.ui.componentes.CampoTexto(
                        confirmacion, { confirmacion = it }, "Escribe ELIMINAR para confirmar",
                        modifier = Modifier.testTag("confirmar_eliminar"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.eliminarCuenta(avisar) },
                    enabled = confirmacion.trim().equals("ELIMINAR", ignoreCase = true) && !eliminando,
                    modifier = Modifier.testTag("boton_eliminar_cuenta"),
                ) { Text(if (eliminando) "Eliminando…" else "Eliminar cuenta", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { eliminar = false }, enabled = !eliminando) { Text("Cancelar") } },
        )
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
                    Text("Michi, la mascota de PetCare, es una ilustración creada para el proyecto.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                    Text("Tipografías Kalnia y Quicksand (SIL Open Font License).", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                    Text("Íconos: Material Symbols de Google (licencia Apache 2.0).", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
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

private data class PerfilPro(
    val tag: String,
    val titulo: String,
    val texto: String,
    val dibujo: Int,
    val fondo: androidx.compose.ui.graphics.Color,
    val activo: Boolean,
    val accion: String,
    val onClick: () -> Unit,
)

/**
 * "Trabaja con mascotas": veterinario, cuidador y paseador, y baño y peluquería. Cada tarjeta
 * dice si ya está activa; tocarla crea el perfil o lo edita para que otros dueños te encuentren.
 */
@Composable
private fun PerfilesProfesionales(vetActivo: Boolean, categoriasPres: List<String>, onVeterinario: () -> Unit, onPrestador: (String?) -> Unit) {
    val hayPres = categoriasPres.isNotEmpty()
    val cuidador = "PASEADOR" in categoriasPres || "CUIDADO_TEMPORAL" in categoriasPres
    val peluqueria = "BANO_PELUQUERIA" in categoriasPres
    val perfiles = listOf(
        PerfilPro("perfil_pro_vet", "Veterinario/a", "Recibe pacientes y citas", R.drawable.gato_doctor, Pc.CieloClaro, vetActivo, if (vetActivo) "Editar perfil" else "Crear perfil", onVeterinario),
        PerfilPro(
            "perfil_pro_cuidador", "Cuidador y paseador", "Paseos y cuidado en casa", R.drawable.gato_amigos, Pc.SolClaro, cuidador,
            when { cuidador -> "Editar perfil"; hayPres -> "Agregar a mi perfil"; else -> "Crear perfil" },
        ) { onPrestador(if (cuidador) null else "PASEADOR") },
        PerfilPro(
            "perfil_pro_peluqueria", "Baño y peluquería", "Baños, cortes y uñas", R.drawable.gato_higiene, Pc.MentaClara, peluqueria,
            when { peluqueria -> "Editar perfil"; hayPres -> "Agregar a mi perfil"; else -> "Crear perfil" },
        ) { onPrestador(if (peluqueria) null else "BANO_PELUQUERIA") },
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text("Trabaja con mascotas", style = MaterialTheme.typography.titleLarge)
            Text("Crea tu perfil y los dueños te encontrarán en Servicios para escribirte.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
        }
        // Mismo alto para las tres tarjetas (el botón queda siempre abajo)
        Row(Modifier.horizontalScroll(rememberScrollState()).height(androidx.compose.foundation.layout.IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            perfiles.forEach { p ->
                Column(
                    Modifier
                        .width(190.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(28.dp))
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(p.fondo, Pc.Blanco)))
                        .clickable(onClick = p.onClick)
                        .testTag(p.tag)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        Image(painterResource(p.dibujo), contentDescription = null, modifier = Modifier.align(Alignment.Center).size(110.dp))
                        if (p.activo) {
                            Chip("Activo", fondo = Pc.Menta, color = Pc.Tinta, icono = com.udenar.petcare.domain.modelo.Icono.CHECK, modifier = Modifier.align(Alignment.TopEnd))
                        }
                    }
                    Text(p.titulo, style = MaterialTheme.typography.titleMedium)
                    Text(p.texto, style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(if (p.activo) Pc.Blanco else Pc.Tinta).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(p.accion, style = MaterialTheme.typography.labelLarge, color = if (p.activo) Pc.Tinta else Pc.Crema)
                    }
                }
            }
        }
    }
}
