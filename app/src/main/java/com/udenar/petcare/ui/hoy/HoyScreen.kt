package com.udenar.petcare.ui.hoy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.petcare.R
import com.udenar.petcare.domain.cuidados.EstadoRegistro
import com.udenar.petcare.domain.guia.DestinoPaso
import com.udenar.petcare.domain.guia.Guia
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.ui.componentes.AvatarMascota
import com.udenar.petcare.ui.componentes.BotonCircular
import com.udenar.petcare.ui.componentes.EstadoVacio
import com.udenar.petcare.ui.componentes.TarjetaIlustrada
import com.udenar.petcare.ui.componentes.TituloSeccion
import com.udenar.petcare.ui.cuidados.ClaseCuidado
import com.udenar.petcare.ui.guia.TarjetaPrimerosPasos
import com.udenar.petcare.ui.theme.Pc
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FORMATO_DIA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-CO"))

/** Pestaña Hoy (RF-15): saludo, semana o mes, guía, alertas y pendientes con un toque. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoyScreen(
    irAMascotas: () -> Unit,
    onAbrirMascota: (String) -> Unit,
    onNuevoCuidado: (mascotaId: String, clase: String) -> Unit,
    onPasear: (mascotaId: String?) -> Unit,
    onPerfilVeterinario: () -> Unit,
    avisar: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HoyViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val nombre by viewModel.nombre.collectAsStateWithLifecycle()
    val guia by viewModel.guia.collectAsStateWithLifecycle()
    val mascotas by viewModel.mascotas.collectAsStateWithLifecycle()
    val agenda by viewModel.agenda.collectAsStateWithLifecycle()
    val alertas by viewModel.alertas.collectAsStateWithLifecycle()
    val filtro by viewModel.filtro.collectAsStateWithLifecycle()
    val mes by viewModel.mes.collectAsStateWithLifecycle()
    val resumenMes by viewModel.resumenMes.collectAsStateWithLifecycle()
    val solicitudes by viewModel.solicitudes.collectAsStateWithLifecycle()
    var verMes by remember { mutableStateOf(false) }
    var nuevo by remember { mutableStateOf(false) }

    // Permiso de notificaciones (Android 13+): se revisa al volver a la app
    val contexto = LocalContext.current
    fun notificacionesActivas() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    LifecycleResumeEffect(Unit) {
        viewModel.notificaciones(notificacionesActivas())
        viewModel.cargarSolicitudes()
        onPauseOrDispose { }
    }
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        viewModel.notificaciones(ok)
        if (!ok) avisar("Puedes activarlas luego en los ajustes del teléfono")
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Image(painterResource(R.drawable.logo_petcare), contentDescription = "PetCare", modifier = Modifier.height(26.dp))
                    Text("Hola,", style = MaterialTheme.typography.bodyLarge, color = Pc.TintaSuave, modifier = Modifier.padding(top = 14.dp))
                    Text(nombre.ifBlank { "¡Hola!" }, style = MaterialTheme.typography.headlineLarge)
                }
                BotonCircular(Icons.Rounded.CalendarMonth, "Ver mes", onClick = { verMes = true }, modifier = Modifier.testTag("ver_mes"))
            }

            Column {
                TiraSemana(semana = estado.semana, hoy = estado.hoy, seleccionado = estado.seleccionado, onElegir = viewModel::elegirDia)
                if (estado.seleccionado != estado.hoy) {
                    TextButton(onClick = { viewModel.elegirDia(estado.hoy) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Volver a hoy", color = Pc.Naranja, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (mascotas.isNotEmpty()) FiltroMascotas(mascotas, filtro, onFiltro = viewModel::filtrar)

            val progreso = guia
            when {
                progreso != null && progreso.completa -> TarjetaIlustrada(
                    titulo = "¡Todo listo!",
                    texto = "Ya conoces lo básico. Toca para cerrar esta guía.",
                    chip = "Primeros pasos",
                    ilustracion = R.drawable.chopper_amigos,
                    degradado = listOf(Pc.Menta, Pc.Cielo),
                    onClick = { viewModel.omitir(Guia.CERRADA, avisar) },
                )
                progreso != null -> TarjetaPrimerosPasos(
                    progreso = progreso,
                    onPaso = { paso ->
                        when (paso.destino) {
                            DestinoPaso.NOTIFICACIONES -> pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
                            DestinoPaso.MASCOTAS -> {
                                irAMascotas()
                                paso.disponibleEn?.let { avisar("\"${paso.titulo}\" llega en $it") }
                            }
                            else -> when {
                                paso.disponibleEn != null -> avisar("\"${paso.titulo}\" llega en ${paso.disponibleEn}")
                                paso.id == Guia.CERTIFICADO && viewModel.tienePerfilVet -> viewModel.abrirModoVeterinario()
                                else -> onPerfilVeterinario()
                            }
                        }
                    },
                    onOmitir = { paso -> viewModel.omitir(paso.id, avisar) },
                )
                mascotas.isEmpty() -> TarjetaIlustrada(
                    titulo = "Agrega a tu primera mascota",
                    texto = "Chopper te ayuda a no olvidar ningún cuidado.",
                    chip = "Empieza aquí",
                    ilustracion = R.drawable.chopper_saludo,
                    degradado = listOf(Pc.NaranjaClaro, Pc.Naranja),
                    onClick = irAMascotas,
                )
            }

            if (solicitudes.isNotEmpty()) {
                TarjetaSolicitudes(
                    solicitudes = solicitudes,
                    nombreMascota = { id -> mascotas.firstOrNull { it.id == id }?.nombre ?: "tu mascota" },
                    onResolver = { s, aprobar -> viewModel.resolver(s, aprobar, avisar) },
                )
            }

            if (alertas.isNotEmpty() && estado.seleccionado == estado.hoy) TarjetaAlertas(alertas, onAbrir = onAbrirMascota)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val lista = agenda
                val pendientes = lista?.count { it.toma.pendiente } ?: 0
                TituloSeccion(
                    if (estado.seleccionado == estado.hoy) "Hoy" else estado.seleccionado.format(FORMATO_DIA).replaceFirstChar { it.uppercase() },
                    accion = {
                        if (!lista.isNullOrEmpty()) {
                            Text(
                                if (pendientes == 0) "Todo hecho 🎉" else "$pendientes pendiente${if (pendientes == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (pendientes == 0) Pc.CieloOscuro else Pc.Naranja,
                            )
                        }
                    },
                )
                when {
                    lista == null -> CircularProgressIndicator(color = Pc.Naranja, modifier = Modifier.align(Alignment.CenterHorizontally))
                    lista.isEmpty() -> EstadoVacio(
                        ilustracion = R.drawable.chopper_comida,
                        titulo = if (mascotas.isEmpty()) "Nada pendiente" else "Sin cuidados este día",
                        texto = if (mascotas.isEmpty()) "Cuando agregues a tu mascota y sus cuidados, aparecerán aquí."
                        else "Agrega comidas, medicinas o baños desde el botón + o desde la ficha de tu mascota.",
                        fondoBlob = listOf(Pc.SolClaro, Pc.Durazno),
                    )
                    else -> lista.forEach { item ->
                        TarjetaToma(
                            item = item,
                            hoy = estado.hoy,
                            sePuedeMarcar = viewModel.sePuedeMarcar(item.toma),
                            onHecho = { viewModel.marcar(item.toma, item.toma.evento.tipo.estadoHecho) },
                            onOmitir = { viewModel.marcar(item.toma, EstadoRegistro.OMITIDA) },
                            onDeshacer = { viewModel.deshacer(item.toma) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(72.dp))
        }

        if (mascotas.isNotEmpty()) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Pc.Naranja)
                    .clickable { nuevo = true }
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                    .testTag("nuevo_cuidado"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = Pc.Blanco)
                Text("Registrar", style = MaterialTheme.typography.labelLarge, color = Pc.Blanco, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }

    if (verMes) {
        ModalBottomSheet(onDismissRequest = { verMes = false }, containerColor = Pc.Crema) {
            CalendarioMes(
                mes = mes, hoy = estado.hoy, seleccionado = estado.seleccionado, resumen = resumenMes,
                onMes = viewModel::cambiarMes,
                onDia = { d ->
                    viewModel.elegirDia(d)
                    verMes = false
                },
            )
        }
    }

    if (nuevo) {
        val propias = mascotas.filter { it.esPropietario }
        var mascotaElegida by remember { mutableStateOf(if (propias.size == 1) propias.first().id else filtro?.takeIf { f -> propias.any { it.id == f } }) }
        ModalBottomSheet(onDismissRequest = { nuevo = false }, containerColor = Pc.Crema) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
                val elegida = mascotaElegida
                var queHacer by remember { mutableStateOf(if (propias.isEmpty()) "paseo" else null) }
                if (queHacer == null) {
                    Text("¿Qué quieres registrar?", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
                    listOf("paseo" to "🦮  Un paseo (con GPS o sin él)", "cuidado" to "➕  Un cuidado nuevo (comida, medicina, baño…)").forEach { (clave, texto) ->
                        Text(
                            texto,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    if (clave == "paseo") {
                                        nuevo = false
                                        onPasear(filtro)
                                    } else queHacer = clave
                                }
                                .padding(14.dp)
                                .testTag("registrar_$clave"),
                        )
                    }
                } else if (queHacer == "paseo") {
                    nuevo = false
                    onPasear(filtro)
                } else if (elegida == null) {
                    Text("¿Para quién?", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
                    propias.forEach { m ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { mascotaElegida = m.id }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AvatarMascota(m.especie, m.fotoLocal, tam = 40.dp)
                            Text(m.nombre, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 14.dp))
                        }
                    }
                } else {
                    Text(
                        "¿Qué cuidado para ${mascotas.firstOrNull { it.id == elegida }?.nombre.orEmpty()}?",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                    )
                    ClaseCuidado.entries.forEach { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    nuevo = false
                                    onNuevoCuidado(elegida, c.name)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(c.emoji, fontSize = 22.sp)
                            Text(c.titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 14.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Fila de avatares: toca uno para ver solo sus cuidados; otra vez para ver todos. */
@Composable
private fun FiltroMascotas(mascotas: List<Mascota>, filtro: String?, onFiltro: (String?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        mascotas.forEach { m ->
            val activa = filtro == m.id
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onFiltro(m.id) }
                    .padding(4.dp)
                    .testTag("filtro_${m.nombre}"),
            ) {
                AvatarMascota(
                    m.especie, m.fotoLocal, tam = 58.dp,
                    modifier = if (activa) Modifier.border(3.dp, Pc.Naranja, CircleShape) else if (filtro != null) Modifier.size(58.dp) else Modifier,
                )
                Text(
                    m.nombre,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (filtro == null || activa) Pc.Tinta else Pc.TintaSuave,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
