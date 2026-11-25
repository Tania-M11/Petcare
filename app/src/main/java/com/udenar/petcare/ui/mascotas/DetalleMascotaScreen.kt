package com.udenar.petcare.ui.mascotas

import com.udenar.petcare.ui.componentes.BaldosaIcono
import com.udenar.petcare.ui.componentes.Simbolo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.petcare.domain.mascotas.FichaMascota
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.ui.componentes.AvatarMascota
import com.udenar.petcare.ui.cuidados.ClaseCuidado
import com.udenar.petcare.ui.cuidados.SeccionCuidados
import com.udenar.petcare.ui.componentes.BotonCircular
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.Chip
import com.udenar.petcare.ui.componentes.Seccion
import com.udenar.petcare.ui.componentes.colores
import com.udenar.petcare.ui.componentes.grano
import com.udenar.petcare.ui.theme.Kalnia
import com.udenar.petcare.ui.theme.Pc
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FECHA = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO"))

/** Ficha de la mascota (vista del propietario): Resumen · Cuidados · Salud · Historial. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DetalleMascotaScreen(
    onVolver: () -> Unit,
    onEditar: (String) -> Unit,
    onCuidado: (clase: String, id: String?) -> Unit,
    onVisita: (id: String?) -> Unit,
    onCarne: (String) -> Unit,
    onPasear: (String) -> Unit,
    onPaseo: (String) -> Unit,
    avisar: (String) -> Unit,
    onVeterinarios: () -> Unit = {},
    onSintoma: (String) -> Unit = {},
    viewModel: DetalleMascotaViewModel = hiltViewModel(),
) {
    val sintomas by viewModel.sintomas.collectAsStateWithLifecycle()
    val m by viewModel.mascota.collectAsStateWithLifecycle()
    val codigo by viewModel.codigo.collectAsStateWithLifecycle()
    val cuidados by viewModel.cuidados.collectAsStateWithLifecycle()
    val pesos by viewModel.pesos.collectAsStateWithLifecycle()
    val visitas by viewModel.visitas.collectAsStateWithLifecycle()
    val estadoCuidado by viewModel.estadoCuidado.collectAsStateWithLifecycle()
    val historial by viewModel.historial.collectAsStateWithLifecycle()
    val miembros by viewModel.miembros.collectAsStateWithLifecycle()
    val vets by viewModel.vets.collectAsStateWithLifecycle()
    val recordatoriosActivos by viewModel.recordatoriosActivos.collectAsStateWithLifecycle()
    val documentos by viewModel.documentos.collectAsStateWithLifecycle()
    val paseos by viewModel.paseos.collectAsStateWithLifecycle()
    val progreso by viewModel.progreso.collectAsStateWithLifecycle()
    val recomendacionesMascota by viewModel.recomendaciones.collectAsStateWithLifecycle()
    var seccion by rememberSaveable { mutableIntStateOf(0) }
    var elegirClase by remember { mutableStateOf(false) }
    var confirmarEliminar by remember { mutableStateOf(false) }
    var confirmarSalir by remember { mutableStateOf(false) }
    val contexto = LocalContext.current

    val mascota = m
    if (mascota == null) {
        Box(Modifier.fillMaxSize().background(Pc.Crema), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Pc.Naranja)
        }
        return
    }
    LaunchedEffect(mascota.porSubir, mascota.esPropietario) {
        if (!mascota.porSubir && mascota.esPropietario) {
            viewModel.cargarCodigo()
            viewModel.cargarMiembros()
            viewModel.cargarVets()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Pc.Crema)
            .verticalScroll(rememberScrollState()),
    ) {
        // Encabezado con degradado de la especie, grano y la foto
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                .background(Brush.verticalGradient(mascota.especie.colores()))
                .grano(0.35f)
                .statusBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth()) {
                BotonCircular(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onVolver)
                Box(Modifier.weight(1f))
                if (mascota.esPropietario) BotonCircular(Icons.Rounded.Edit, "Editar", onClick = { onEditar(mascota.id) })
            }
            AvatarMascota(mascota.especie, mascota.fotoLocal, tam = 132.dp, forma = RoundedCornerShape(40.dp))
            Text(mascota.nombre, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 12.dp))
            Text(describir(mascota, viewModel.hoy), style = MaterialTheme.typography.bodyLarge, color = Pc.Tinta.copy(alpha = 0.75f))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Chip(mascota.sexo.let { if (it.name == "DESCONOCIDO") "Sexo sin definir" else it.etiqueta })
                mascota.tamano?.let { Chip(it.etiqueta) }
                when (mascota.esterilizado) {
                    true -> Chip("Esterilizado/a")
                    false -> Chip("Sin esterilizar")
                    null -> Unit
                }
                if (mascota.porSubir) Chip("Por subir", fondo = Pc.Blanco.copy(alpha = 0.6f), icono = com.udenar.petcare.domain.modelo.Icono.NUBE)
            }
        }

        Column(
            Modifier
                .padding(20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChipsSeccion(seccion, onElegir = { seccion = it })

            when (seccion) {
                1 -> {
                    TarjetaPaseos(paseos, onPasear = { onPasear(mascota.id) }, onAbrir = { onPaseo(it.id) })
                    SeccionCuidados(
                    cuidados,
                    // Solo el dueño crea o cambia cuidados; quien cuida los ve y registra desde Hoy
                    onAgregar = if (mascota.esPropietario) ({ elegirClase = true }) else null,
                    onAbrir = { e -> if (mascota.esPropietario) onCuidado(ClaseCuidado.de(e.tipo).name, e.id) },
                )
                }
                2 -> {
                    SaludBasica(mascota)
                    // Lo puede anotar el dueño o quien la cuida
                    TarjetaSintomas(sintomas, onNuevo = { onSintoma(mascota.id) }, onEliminar = viewModel::eliminarSintoma, onVeterinarios = onVeterinarios)
                    TarjetaPeso(pesos, viewModel.hoy, onRegistrar = viewModel::registrarPeso, onEliminar = { viewModel.eliminarPeso(it) }, editable = mascota.esPropietario)
                    // Las visitas veterinarias no se comparten con los cuidadores (sección 5)
                    if (mascota.esPropietario) {
                        TarjetaVisitas(visitas, onNueva = { onVisita(null) }, onAbrir = { onVisita(it.id) })
                        // Documentos: tampoco se comparten con los cuidadores (sección 5)
                        TarjetaDocumentos(
                            documentos, visitas, viewModel.hoy,
                            onAgregar = { o, n, t, f, v -> viewModel.agregarDocumento(o, n, t, f, v, avisar) },
                            onAbrir = { d -> viewModel.abrirDocumento(d, abrir = { ruta -> abrirDocumento(contexto, ruta, d.mime) }, avisar = avisar) },
                            onEliminar = viewModel::eliminarDocumento,
                        )
                    }
                }
                3 -> TarjetaHistorial(historial)
                else -> {
                    // Lo que conviene atender ahora con esta mascota
                    if (recomendacionesMascota.isNotEmpty()) {
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(recomendacionesMascota.size) { i ->
                                val r = recomendacionesMascota[i]
                                com.udenar.petcare.ui.componentes.TarjetaRecomendacion(
                                    r,
                                    onAccion = {
                                        when (r.accion) {
                                            com.udenar.petcare.domain.recomendaciones.AccionSugerida.NUEVO_CUIDADO -> elegirClase = true
                                            com.udenar.petcare.domain.recomendaciones.AccionSugerida.COMPLETAR_FICHA -> onEditar(mascota.id)
                                            com.udenar.petcare.domain.recomendaciones.AccionSugerida.PASEAR -> onPasear(mascota.id)
                                            com.udenar.petcare.domain.recomendaciones.AccionSugerida.REGISTRAR_PESO -> seccion = 2
                                            com.udenar.petcare.domain.recomendaciones.AccionSugerida.VER_CUIDADOS -> seccion = 1
                                            else -> onVeterinarios()
                                        }
                                    },
                                    onDescartar = { viewModel.descartar(r.id) },
                                )
                            }
                        }
                    }
                    progreso?.takeIf { it.hayDatos }?.let { com.udenar.petcare.ui.componentes.TarjetaProgreso(mascota.nombre, it) }
                        ?: com.udenar.petcare.ui.componentes.VacioMichi(com.udenar.petcare.domain.recomendaciones.PoseMichi.CELEBRA, "Cuando marques sus cuidados y paseos, aquí verás su racha y sus gráficas de la semana.", fondo = Pc.Blanco)
                    TarjetaEstadoCuidado(estadoCuidado)
                    com.udenar.petcare.ui.componentes.BotonSecundario("Carné y QR", onClick = { onCarne(mascota.id) }, modifier = Modifier.testTag("ver_carne"))
                    // Reporte PDF para llevar o enviar al veterinario (solo el dueño ve visitas y documentos)
                    if (mascota.esPropietario) {
                        com.udenar.petcare.ui.componentes.BotonSecundario(
                            "Reporte PDF para el veterinario", icono = com.udenar.petcare.domain.modelo.Icono.DOCUMENTO,
                            onClick = {
                                viewModel.generarReporte(contexto, onListo = { archivo ->
                                    val uri = androidx.core.content.FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", archivo)
                                    val enviar = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Reporte de ${mascota.nombre}")
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching { contexto.startActivity(android.content.Intent.createChooser(enviar, "Compartir reporte")) }
                                }, avisar = avisar)
                            },
                            modifier = Modifier.testTag("reporte_pdf"),
                        )
                    }
                    mascota.veterinario?.takeIf { !it.vacio }?.let { v -> TarjetaVeterinario(v, contexto) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(Pc.Blanco)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Recordatorios en este teléfono", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (recordatoriosActivos) "Te avisaremos de los cuidados de ${mascota.nombre}" else "No sonarán aquí (los demás miembros deciden por su cuenta)",
                                style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave,
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = recordatoriosActivos,
                            onCheckedChange = viewModel::cambiarRecordatorios,
                            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pc.Naranja),
                            modifier = Modifier.testTag("interruptor_recordatorios"),
                        )
                    }
                    if (mascota.esPropietario) {
                        FichaYCodigo(
                            mascota, codigo, contexto, avisar, viewModel, onEditar, onEliminar = { confirmarEliminar = true },
                            onRegenerar = { viewModel.regenerarCodigo(avisar) }, onDesactivar = { viewModel.desactivarCodigo(avisar) },
                        )
                        TarjetaPersonas(miembros, onQuitar = { viewModel.quitar(it, avisar) })
                        TarjetaVetsVinculados(vets, onDesvincular = { viewModel.desvincularVet(it, avisar) })
                    } else {
                        FichaSoloLectura(mascota)
                        TextButton(onClick = { confirmarSalir = true }, modifier = Modifier.fillMaxWidth().testTag("dejar_de_cuidar")) {
                            Text("Dejar de cuidar a ${mascota.nombre}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }

    if (elegirClase) {
        ModalBottomSheet(onDismissRequest = { elegirClase = false }, containerColor = Pc.Crema) {
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .navigationBarsPadding(),
            ) {
                Text("¿Qué cuidado agregas?", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
                ClaseCuidado.entries.forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                elegirClase = false
                                onCuidado(c.name, null)
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .testTag("clase_${c.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BaldosaIcono(c.icono, fondo = Pc.Durazno, tam = 40.dp)
                        Text(c.titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 14.dp))
                    }
                }
            }
        }
    }

    if (confirmarSalir) {
        AlertDialog(
            onDismissRequest = { confirmarSalir = false },
            containerColor = Pc.Crema,
            title = { Text("¿Dejar de cuidar a ${mascota.nombre}?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Dejarás de verla y de recibir sus recordatorios. Para volver necesitarás un código y la aprobación del dueño.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarSalir = false
                    viewModel.salir(onVolver, avisar)
                }) { Text("Dejar de cuidar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmarSalir = false }) { Text("Cancelar") } },
        )
    }

    if (confirmarEliminar) {
        AlertDialog(
            onDismissRequest = { confirmarEliminar = false },
            containerColor = Pc.Crema,
            title = { Text("¿Eliminar a ${mascota.nombre}?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Se borrará su ficha en todos tus dispositivos. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarEliminar = false
                    viewModel.eliminar(onVolver)
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmarEliminar = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
    Column {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = Pc.TintaSuave)
        Text(valor, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Código para que otra persona pida cuidar a la mascota (RF-20; la solicitud llega en la parte 3.1). */
@Composable
private fun TarjetaCodigo(
    mascota: Mascota,
    estado: EstadoCodigo,
    contexto: Context,
    avisar: (String) -> Unit,
    onReintentar: () -> Unit,
    onRegenerar: () -> Unit,
    onDesactivar: () -> Unit,
) {
    Seccion("Código para cuidadores") {
        when (estado) {
            EstadoCodigo.Cargando -> CircularProgressIndicator(color = Pc.Naranja, modifier = Modifier.align(Alignment.CenterHorizontally))
            EstadoCodigo.SinSincronizar -> Text(
                "Se genera cuando ${mascota.nombre} llegue al servidor. Conéctate a internet.",
                style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave,
            )
            EstadoCodigo.SinConexion -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Conéctate a internet para verlo.", style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave, modifier = Modifier.weight(1f))
                TextButton(onClick = onReintentar) { Text("Reintentar") }
            }
            is EstadoCodigo.Listo -> {
                val c = estado.codigo.codigo
                Text(
                    c.chunked(4).joinToString(" "),
                    fontFamily = Kalnia,
                    fontSize = 34.sp,
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center,
                    color = Pc.Tinta,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(Pc.LavandaClara)
                        .padding(vertical = 14.dp)
                        .testTag("codigo_mascota"),
                )
                Text(
                    "Compártelo solo con quien quieras que cuide a ${mascota.nombre}. Siempre tendrás que aprobar cada solicitud.",
                    style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    BotonCircular(Icons.Rounded.ContentCopy, "Copiar código", onClick = {
                        val portapapeles = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        portapapeles.setPrimaryClip(ClipData.newPlainText("Código PetCare", c))
                        avisar("Código copiado")
                    }, fondo = Pc.Durazno, tinte = Pc.Naranja)
                    BotonCircular(Icons.Rounded.Share, "Compartir código", onClick = {
                        val texto = "Te comparto el código de ${mascota.nombre} en PetCare: $c. " +
                            "Escríbelo en \"Cuidar con código\" y yo apruebo tu solicitud."
                        contexto.startActivity(
                            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto), "Compartir código"),
                        )
                    }, fondo = Pc.Durazno, tinte = Pc.Naranja)
                }
                if (!estado.codigo.activo) {
                    Text("Este código está desactivado: nadie puede usarlo para pedir cuidar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onRegenerar) { Text(if (estado.codigo.activo) "Generar otro" else "Activar con código nuevo", style = MaterialTheme.typography.labelLarge) }
                    if (estado.codigo.activo) TextButton(onClick = onDesactivar) { Text("Desactivar", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave) }
                }
                Text("Cambiar o desactivar el código no quita a quienes ya cuidan a ${mascota.nombre}.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            }
        }
    }
}

@Composable
private fun FichaYCodigo(
    mascota: com.udenar.petcare.domain.modelo.Mascota,
    codigo: EstadoCodigo,
    contexto: android.content.Context,
    avisar: (String) -> Unit,
    viewModel: DetalleMascotaViewModel,
    onEditar: (String) -> Unit,
    onEliminar: () -> Unit,
    onRegenerar: () -> Unit,
    onDesactivar: () -> Unit,
) {
    Seccion("Ficha") {
        Dato("Especie", mascota.especieOtro ?: mascota.especie.etiqueta)
        mascota.raza?.let { Dato("Raza", it) }
        mascota.fechaNacimiento?.let {
            Dato("Nacimiento", it.format(FECHA) + if (mascota.nacimientoAproximado) " (aprox.)" else "")
        }
        mascota.pesoGramos?.let { Dato("Peso", FichaMascota.peso(it)) }
        mascota.color?.let { Dato("Color", it) }
        mascota.microchip?.let { Dato("Microchip", it) }
        mascota.notas?.let { Dato("Notas", it) }
    }
    TarjetaCodigo(mascota, codigo, contexto, avisar, onReintentar = viewModel::cargarCodigo, onRegenerar = onRegenerar, onDesactivar = onDesactivar)
    BotonPrincipal("Editar ficha", onClick = { onEditar(mascota.id) })
    TextButton(onClick = onEliminar, modifier = Modifier.fillMaxWidth()) {
        Text("Eliminar mascota", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SaludBasica(mascota: com.udenar.petcare.domain.modelo.Mascota) {
    val salud = listOfNotNull(
        mascota.alergias?.let { "Alergias" to it },
        mascota.condiciones?.let { "Condiciones" to it },
        mascota.medicamentosActuales?.let { "Medicamentos actuales" to it },
        mascota.alimentacionEspecial?.let { "Alimentación especial" to it },
        mascota.observaciones?.let { "Observaciones" to it },
    )
    Seccion("Salud básica") {
        if (salud.isEmpty()) {
            com.udenar.petcare.ui.componentes.VacioMichi(com.udenar.petcare.domain.recomendaciones.PoseMichi.DOCUMENTOS, "Sin datos aún. Puedes agregarlos al editar la ficha.")
        } else {
            salud.forEach { (t, v) -> Dato(t, v) }
        }
        Text("Esto no constituye un diagnóstico médico.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
    }
}

@Composable
private fun FichaSoloLectura(mascota: Mascota) {
    Seccion("Ficha") {
        Dato("Especie", mascota.especieOtro ?: mascota.especie.etiqueta)
        mascota.raza?.let { Dato("Raza", it) }
        mascota.pesoGramos?.let { Dato("Peso", FichaMascota.peso(it)) }
        mascota.notas?.let { Dato("Notas", it) }
        Text("La cuidas: puedes registrar comidas, dosis y paseos, pero solo el dueño edita su ficha.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
    }
}

/** Personas que cuidan a la mascota (solo la ve el dueño). */
@Composable
private fun TarjetaPersonas(miembros: List<com.udenar.petcare.domain.compartir.Miembro>?, onQuitar: (com.udenar.petcare.domain.compartir.Miembro) -> Unit) {
    var quitar by remember { mutableStateOf<com.udenar.petcare.domain.compartir.Miembro?>(null) }
    Seccion("Personas") {
        when {
            miembros == null -> Text("Conéctate a internet para ver quién la cuida.", style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
            miembros.none { it.rol == com.udenar.petcare.domain.compartir.RolMascota.CUIDADOR } ->
                Text("Solo tú. Comparte su código para que alguien más te ayude.", style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
        }
        miembros.orEmpty().forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("miembro_${m.nombre}")) {
                Column(Modifier.weight(1f)) {
                    Text(m.nombre, style = MaterialTheme.typography.titleSmall)
                    Text(if (m.rol == com.udenar.petcare.domain.compartir.RolMascota.PROPIETARIO) "Dueño/a" else "Cuidador/a", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                }
                if (m.rol == com.udenar.petcare.domain.compartir.RolMascota.CUIDADOR) {
                    TextButton(onClick = { quitar = m }) { Text("Quitar", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    quitar?.let { m ->
        AlertDialog(
            onDismissRequest = { quitar = null },
            containerColor = Pc.Crema,
            title = { Text("¿Quitar a ${m.nombre}?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Dejará de ver la mascota y de recibir sus recordatorios.") },
            confirmButton = { TextButton(onClick = { onQuitar(m); quitar = null }) { Text("Quitar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { quitar = null }) { Text("Cancelar") } },
        )
    }
}

/** Veterinarios vinculados por una cita aceptada (RF-26): ven la ficha clínica hasta que se desvinculen. */
@Composable
private fun TarjetaVetsVinculados(vets: List<com.udenar.petcare.domain.profesionales.VetVinculado>?, onDesvincular: (com.udenar.petcare.domain.profesionales.VetVinculado) -> Unit) {
    if (vets.isNullOrEmpty()) return
    var quitar by remember { mutableStateOf<com.udenar.petcare.domain.profesionales.VetVinculado?>(null) }
    Seccion("Veterinarios con acceso") {
        vets.forEach { v ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("vet_vinculado_${v.nombre}")) {
                Column(Modifier.weight(1f)) {
                    Text(v.nombre + if (v.verificado) " ✓" else "", style = MaterialTheme.typography.titleSmall)
                    Text(listOfNotNull(v.clinica, "ve la ficha clínica").joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                }
                TextButton(onClick = { quitar = v }) { Text("Desvincular", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    quitar?.let { v ->
        AlertDialog(
            onDismissRequest = { quitar = null },
            containerColor = Pc.Crema,
            title = { Text("¿Desvincular a ${v.nombre}?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Dejará de ver la ficha clínica. Lo que registró se queda en el historial.") },
            confirmButton = { TextButton(onClick = { onDesvincular(v); quitar = null }) { Text("Desvincular", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { quitar = null }) { Text("Cancelar") } },
        )
    }
}

/** Veterinario de la mascota con botón Llamar (RF-04); lo ven propietario y cuidadores. */
@Composable
private fun TarjetaVeterinario(v: com.udenar.petcare.domain.modelo.VeterinarioManual, contexto: Context) {
    Seccion("Veterinario de ${"la mascota"}") {
        Text(v.nombre.ifBlank { "Veterinario" }, style = MaterialTheme.typography.titleSmall)
        listOf(v.clinica, v.direccion, v.horario, v.notas).filter { it.isNotBlank() }.forEach {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
        }
        if (v.telefono.isNotBlank()) {
            BotonPrincipal("Llamar ${v.telefono}", onClick = {
                // ACTION_DIAL abre el marcador sin llamar ni pedir permisos
                contexto.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:${v.telefono.filter { it.isDigit() || it == '+' }}")))
            })
        }
    }
}

/** Paseos recientes (RF-12): los registran el dueño y los cuidadores. */
@Composable
private fun TarjetaPaseos(paseos: List<com.udenar.petcare.domain.paseos.Paseo>, onPasear: () -> Unit, onAbrir: (com.udenar.petcare.domain.paseos.Paseo) -> Unit) {
    Seccion("Paseos") {
        if (paseos.isEmpty()) Text("Registra sus paseos con GPS o a mano.", style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
        paseos.take(5).forEach { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onAbrir(p) }
                    .padding(vertical = 6.dp)
                    .testTag("paseo_registrado"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Simbolo(p.tipo.icono, tam = 30.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        listOfNotNull(p.distanciaM?.let { com.udenar.petcare.domain.paseos.textoDistancia(it) }, com.udenar.petcare.domain.paseos.textoDuracion(p.duracionS)).joinToString(" · "),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        listOfNotNull(p.inicio.atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(FECHA), if (p.esManual) "sin GPS" else "con ruta", p.registradoPor?.nombre?.substringBefore(' ')).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave,
                    )
                }
            }
        }
        com.udenar.petcare.ui.componentes.BotonSecundario("Pasear ahora", onClick = onPasear, modifier = Modifier.testTag("pasear_ahora"))
    }
}
