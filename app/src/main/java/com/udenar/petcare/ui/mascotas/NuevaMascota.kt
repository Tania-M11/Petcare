package com.udenar.petcare.ui.mascotas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.petcare.domain.mascotas.CampoMascota
import com.udenar.petcare.domain.mascotas.FichaMascota
import com.udenar.petcare.domain.mascotas.UnidadPeso
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.Sexo
import com.udenar.petcare.domain.modelo.Tamano
import com.udenar.petcare.domain.recomendaciones.PoseChopper
import com.udenar.petcare.ui.componentes.AsistentePasos
import com.udenar.petcare.ui.componentes.AvatarMascota
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.BotonSecundario
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.ChipOpcion
import com.udenar.petcare.ui.componentes.FilaResumen
import com.udenar.petcare.ui.componentes.PreguntaChopper
import com.udenar.petcare.ui.componentes.SelectorFecha
import com.udenar.petcare.ui.componentes.Stepper
import com.udenar.petcare.ui.componentes.TarjetaOpcionGrande
import com.udenar.petcare.ui.componentes.colores
import com.udenar.petcare.ui.componentes.ilustracion
import com.udenar.petcare.ui.theme.Pc

private enum class PasoMascota { ESPECIE, NOMBRE, FOTO, EDAD, DETALLES, SALUD, RESUMEN }

/**
 * Nueva mascota como un recorrido corto (Fase 6.2): una pregunta por pantalla, ilustraciones,
 * foto guiada con recorte automático y un resumen editable antes de guardar.
 */
@Composable
fun NuevaMascotaScreen(onVolver: () -> Unit, onGuardada: (String) -> Unit, viewModel: FormMascotaViewModel = hiltViewModel()) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    var paso by rememberSaveable { mutableIntStateOf(0) }
    val pasos = PasoMascota.entries
    val actual = pasos[paso]
    LaunchedEffect(f.guardada) { if (f.guardada) onGuardada(f.id) }
    fun siguiente() { if (paso < pasos.lastIndex) paso++ }
    val nombre = f.nombre.trim().ifEmpty { "tu mascota" }

    // Cámara y galería (el selector del sistema y la cámara no piden permisos)
    var archivoCamara by remember { mutableStateOf<android.net.Uri?>(null) }
    val colores = (f.especie ?: Especie.OTRO).colores().map { it.toArgb() }.toIntArray()
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) viewModel.elegirFotoGuiada(uri, colores) }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) archivoCamara?.let { viewModel.elegirFotoGuiada(it, colores) } }

    AsistentePasos(
        paso = paso,
        total = pasos.size,
        onAtras = { paso-- },
        onCerrar = onVolver,
        onSaltar = when (actual) {
            PasoMascota.FOTO, PasoMascota.EDAD, PasoMascota.DETALLES, PasoMascota.SALUD -> ({ siguiente() })
            else -> null
        },
        abajo = {
            when (actual) {
                PasoMascota.ESPECIE -> Unit // avanza al tocar una especie
                PasoMascota.RESUMEN -> BotonPrincipal("Agregar a $nombre 🐾", onClick = viewModel::guardar, cargando = f.guardando, modifier = Modifier.testTag("guardar_mascota"))
                PasoMascota.NOMBRE -> BotonPrincipal(
                    "Continuar", habilitado = f.nombre.isNotBlank() && (f.especie != Especie.OTRO || f.especieOtro.isNotBlank()),
                    onClick = { siguiente() }, modifier = Modifier.testTag("continuar"),
                )
                else -> BotonPrincipal("Continuar", habilitado = !f.procesandoFoto, onClick = { siguiente() }, modifier = Modifier.testTag("continuar"))
            }
            f.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        },
    ) { p ->
        when (pasos[p]) {
            PasoMascota.ESPECIE -> {
                PreguntaChopper("¿Quién llega a la familia?", "Elige su especie", PoseChopper.SALUDO)
                Especie.entries.chunked(3).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        fila.forEach { e ->
                            TarjetaOpcionGrande(
                                e.etiqueta, f.especie == e,
                                onClick = {
                                    viewModel.editar(CampoMascota.ESPECIE) { it.copy(especie = e) }
                                    siguiente()
                                },
                                dibujo = e.ilustracion(), color = e.colores().first(),
                                modifier = Modifier.weight(1f).testTag("especie_${e.name}"),
                            )
                        }
                        repeat(3 - fila.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
            PasoMascota.NOMBRE -> {
                PreguntaChopper("¿Cómo se llama?", "Así lo verás en tus recordatorios", PoseChopper.SALUDO)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AvatarMascota(f.especie ?: Especie.OTRO, f.foto, tam = 120.dp) }
                CampoTexto(
                    f.nombre, { v -> viewModel.editar(CampoMascota.NOMBRE) { it.copy(nombre = v.take(40)) } }, "Nombre",
                    error = f.errores[CampoMascota.NOMBRE], accion = ImeAction.Done,
                    onAccion = { if (f.nombre.isNotBlank()) siguiente() }, modifier = Modifier.testTag("nombre_mascota"),
                )
                if (f.especie == Especie.OTRO) {
                    CampoTexto(f.especieOtro, { v -> viewModel.editar(CampoMascota.ESPECIE_OTRO) { it.copy(especieOtro = v) } }, "¿Qué especie es?", error = f.errores[CampoMascota.ESPECIE_OTRO])
                }
            }
            PasoMascota.FOTO -> PasoFoto(
                nombre = nombre, foto = f.foto, especie = f.especie ?: Especie.OTRO, procesando = f.procesandoFoto,
                completa = f.fotoCompleta, pegatina = f.fotoPegatina, sinRecorte = f.sinRecorte,
                onCamara = {
                    val (_, uri) = viewModel.archivoCamara()
                    archivoCamara = uri
                    camara.launch(uri)
                },
                onGaleria = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onUsar = viewModel::usarFoto,
            )
            PasoMascota.EDAD -> PasoEdad(nombre, f, viewModel)
            PasoMascota.DETALLES -> {
                PreguntaChopper("Cuéntame de $nombre", "Sexo, tamaño y peso; todo se puede cambiar después", PoseChopper.ORGANIZADOR)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(Sexo.MACHO to "♂️", Sexo.HEMBRA to "♀️", Sexo.DESCONOCIDO to "❔").forEach { (s, e) ->
                        TarjetaOpcionGrande(s.etiqueta, f.sexo == s, onClick = { viewModel.editar { it.copy(sexo = s) } }, emoji = e, modifier = Modifier.weight(1f))
                    }
                }
                Text("Tamaño", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tamano.entries.forEachIndexed { i, t ->
                        TarjetaOpcionGrande(
                            t.etiqueta, f.tamano == t, onClick = { viewModel.editar { it.copy(tamano = if (it.tamano == t) null else t) } },
                            emoji = listOf("🐁", "🐕", "🐕‍🦺", "🐻").getOrElse(i) { "🐾" }, modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text("Peso (si lo sabes)", style = MaterialTheme.typography.titleSmall)
                val kg = f.pesoTexto.replace(',', '.').toDoubleOrNull() ?: 0.0
                Stepper(
                    kg, { v -> viewModel.editar(CampoMascota.PESO) { it.copy(pesoTexto = "%.1f".format(java.util.Locale.US, v), unidad = UnidadPeso.KG) } },
                    paso = if (kg < 5) 0.1 else 0.5, unidad = "kg",
                )
                f.errores[CampoMascota.PESO]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            PasoMascota.SALUD -> PasoSalud(nombre, f, viewModel)
            PasoMascota.RESUMEN -> {
                val rebote by animateFloatAsState(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy), label = "foto")
                PreguntaChopper("¡Todo listo!", "Revisa y toca lo que quieras cambiar", PoseChopper.AMIGOS)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AvatarMascota(f.especie ?: Especie.OTRO, f.foto, tam = 140.dp, modifier = Modifier.scale(rebote))
                }
                Text(nombre, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Pc.Blanco).padding(horizontal = 12.dp)) {
                    FilaResumen("Especie", f.especie?.let { if (it == Especie.OTRO) f.especieOtro.ifBlank { it.etiqueta } else it.etiqueta } ?: "—", onEditar = { paso = PasoMascota.ESPECIE.ordinal })
                    FilaResumen("Foto", if (f.foto != null) "Lista ✓" else "Sin foto", onEditar = { paso = PasoMascota.FOTO.ordinal })
                    FilaResumen("Edad", f.nacimiento?.let { FichaMascota.edad(it, f.aproximada, viewModel.hoy) } ?: "Sin dato", onEditar = { paso = PasoMascota.EDAD.ordinal })
                    FilaResumen(
                        "Detalles",
                        listOfNotNull(f.sexo.takeIf { it != Sexo.DESCONOCIDO }?.etiqueta, f.tamano?.etiqueta, f.pesoTexto.takeIf { (it.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0 }?.let { "$it kg" }).joinToString(" · ").ifEmpty { "Sin datos" },
                        onEditar = { paso = PasoMascota.DETALLES.ordinal },
                    )
                    FilaResumen("Salud", listOfNotNull(f.alergias.ifBlank { null }?.let { "Alergias" }, f.medicamentos.ifBlank { null }?.let { "Medicamentos" }).joinToString(" · ").ifEmpty { "Sin datos" }, onEditar = { paso = PasoMascota.SALUD.ordinal })
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PasoFoto(
    nombre: String, foto: String?, especie: Especie, procesando: Boolean, completa: String?, pegatina: String?, sinRecorte: Boolean,
    onCamara: () -> Unit, onGaleria: () -> Unit, onUsar: (String) -> Unit,
) {
    PreguntaChopper(
        if (foto == null) "¡Muéstrame a $nombre!" else if (procesando) "Un momento…" else "¡Qué buena foto!",
        when {
            foto == null -> "Una foto de frente y con buena luz queda mejor"
            procesando -> "Estoy recortando a $nombre del fondo ✂️"
            pegatina != null -> "La convertí en pegatina. Puedes usar la foto completa si prefieres"
            sinRecorte -> "No pude recortarla del fondo, pero la foto completa también se ve muy bien"
            else -> null
        },
        if (foto == null) PoseChopper.SALUDO else PoseChopper.AMIGOS,
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(210.dp).clip(CircleShape).border(4.dp, Pc.Blanco, CircleShape).clickable(onClick = onGaleria).testTag("elegir_foto"),
            contentAlignment = Alignment.Center,
        ) {
            AvatarMascota(especie, foto, tam = 210.dp)
            if (procesando) CircularProgressIndicator(color = Pc.Naranja, strokeWidth = 4.dp, modifier = Modifier.size(64.dp))
        }
    }
    AnimatedVisibility(completa != null && pegatina != null && !procesando, enter = scaleIn()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TarjetaOpcionGrande("Pegatina", foto == pegatina, onClick = { pegatina?.let(onUsar) }, emoji = "✂️", modifier = Modifier.weight(1f).testTag("usar_pegatina"))
            TarjetaOpcionGrande("Foto completa", foto == completa, onClick = { completa?.let(onUsar) }, emoji = "🖼️", modifier = Modifier.weight(1f).testTag("usar_completa"))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BotonSecundario("📷 Tomar foto", onClick = onCamara, modifier = Modifier.weight(1f).testTag("tomar_foto"))
        BotonSecundario("🖼️ Galería", onClick = onGaleria, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PasoEdad(nombre: String, f: FormMascota, viewModel: FormMascotaViewModel) {
    var modo by remember { mutableStateOf(if (f.nacimiento != null && !f.aproximada) "fecha" else "aprox") }
    var anos by remember { mutableStateOf(2f) }
    PreguntaChopper("¿Cuántos años tiene $nombre?", "Si no sabes la fecha exacta, una edad aproximada sirve", PoseChopper.ORGANIZADOR)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipOpcion("Edad aproximada", modo == "aprox", onClick = { modo = "aprox" })
        ChipOpcion("Sé su fecha", modo == "fecha", onClick = { modo = "fecha" }, modifier = Modifier.testTag("se_su_fecha"))
    }
    if (modo == "aprox") {
        val total = anos.toInt()
        val texto = when {
            anos < 1f -> "${(anos * 12).toInt().coerceAtLeast(1)} meses"
            total == 1 -> "1 año"
            else -> "$total años"
        }
        Text(texto, style = MaterialTheme.typography.headlineLarge, color = Pc.Naranja, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("edad_texto"))
        Slider(
            anos, { v ->
                anos = v
                if (v < 1f) viewModel.edadAproximada(0, (v * 12).toInt().coerceAtLeast(1)) else viewModel.edadAproximada(v.toInt(), 0)
            },
            valueRange = 0f..20f, colors = SliderDefaults.colors(thumbColor = Pc.Naranja, activeTrackColor = Pc.Naranja),
            modifier = Modifier.testTag("slider_edad"),
        )
        Row(Modifier.fillMaxWidth()) {
            Text("Cachorro", style = MaterialTheme.typography.labelSmall, color = Pc.TintaSuave, modifier = Modifier.weight(1f))
            Text("20 años", style = MaterialTheme.typography.labelSmall, color = Pc.TintaSuave)
        }
    } else {
        SelectorFecha("Fecha de nacimiento", f.nacimiento, { d -> viewModel.editar(CampoMascota.NACIMIENTO) { it.copy(nacimiento = d, aproximada = false) } }, hasta = viewModel.hoy)
    }
    f.nacimiento?.let { Text("Edad: ${FichaMascota.edad(it, f.aproximada, viewModel.hoy)}", style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave) }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PasoSalud(nombre: String, f: FormMascota, viewModel: FormMascotaViewModel) {
    var alergia by remember { mutableStateOf<Boolean?>(if (f.alergias.isNotBlank()) true else null) }
    var medicina by remember { mutableStateOf<Boolean?>(if (f.medicamentos.isNotBlank()) true else null) }
    PreguntaChopper("Lo importante de su salud", "Así quienes cuiden a $nombre lo sabrán. PetCare solo lo guarda.", PoseChopper.DOCTOR)
    Text("¿Tiene alguna alergia?", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TarjetaOpcionGrande("Sí", alergia == true, onClick = { alergia = true }, emoji = "🌼", modifier = Modifier.weight(1f).testTag("alergia_si"))
        TarjetaOpcionGrande("No", alergia == false, onClick = { alergia = false; viewModel.editar { it.copy(alergias = "") } }, emoji = "👍", modifier = Modifier.weight(1f))
    }
    AnimatedVisibility(alergia == true) {
        CampoTexto(f.alergias, { v -> viewModel.editar { it.copy(alergias = v) } }, "¿A qué?", modifier = Modifier.testTag("alergias"))
    }
    Text("¿Toma algún medicamento ahora?", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TarjetaOpcionGrande("Sí", medicina == true, onClick = { medicina = true }, emoji = "💊", modifier = Modifier.weight(1f))
        TarjetaOpcionGrande("No", medicina == false, onClick = { medicina = false; viewModel.editar { it.copy(medicamentos = "") } }, emoji = "👍", modifier = Modifier.weight(1f))
    }
    AnimatedVisibility(medicina == true) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CampoTexto(f.medicamentos, { v -> viewModel.editar { it.copy(medicamentos = v) } }, "¿Cuál? (tal como lo indicó su veterinario)")
            Text("Después podrás crear su recordatorio con las horas.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
        }
    }
}
