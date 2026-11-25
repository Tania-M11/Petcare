package com.udenar.petcare.ui.mascotas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
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
import com.udenar.petcare.ui.componentes.AvatarMascota
import com.udenar.petcare.ui.componentes.BotonCircular
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.ChipOpcion
import com.udenar.petcare.ui.componentes.Seccion
import com.udenar.petcare.ui.theme.Pc
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO"))

/** Agregar o editar una mascota (RF-02, RF-03). */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FormMascotaScreen(
    onVolver: () -> Unit,
    onGuardada: (String) -> Unit,
    viewModel: FormMascotaViewModel = hiltViewModel(),
) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    var verCalendario by rememberSaveable { mutableStateOf(false) }
    var verSalud by rememberSaveable { mutableStateOf(false) }
    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.elegirFoto(uri)
    }
    LaunchedEffect(f.guardada) { if (f.guardada) onGuardada(f.id) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Pc.Crema)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotonCircular(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onVolver)
            Text(
                if (f.esNueva) "Nueva mascota" else "Editar ficha",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 16.dp),
            )
        }

        // Foto: el selector del sistema no pide permisos
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .clickable { elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                .testTag("elegir_foto"),
        ) {
            AvatarMascota(f.especie ?: Especie.OTRO, f.foto, tam = 124.dp)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Pc.Naranja),
                contentAlignment = Alignment.Center,
            ) {
                if (f.procesandoFoto) {
                    CircularProgressIndicator(color = Pc.SobreAcento, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = "Elegir foto", tint = Pc.SobreAcento, modifier = Modifier.size(22.dp))
                }
            }
        }

        Seccion("Lo básico") {
            CampoTexto(f.nombre, { v -> viewModel.editar(CampoMascota.NOMBRE) { it.copy(nombre = v) } }, "Nombre", error = f.errores[CampoMascota.NOMBRE])
            Text("Especie", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Especie.entries.forEach { e ->
                    com.udenar.petcare.ui.componentes.ChipEspecie(
                        e,
                        seleccionado = f.especie == e,
                        onClick = { viewModel.editar(CampoMascota.ESPECIE) { it.copy(especie = e) } },
                        modifier = Modifier.testTag("especie_${e.name}"),
                    )
                }
            }
            f.errores[CampoMascota.ESPECIE]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            AnimatedVisibility(f.especie == Especie.OTRO) {
                CampoTexto(
                    f.especieOtro, { v -> viewModel.editar(CampoMascota.ESPECIE_OTRO) { it.copy(especieOtro = v) } }, "¿Qué especie?",
                    error = f.errores[CampoMascota.ESPECIE_OTRO],
                )
            }
            Text("Sexo", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sexo.entries.forEach { s -> ChipOpcion(s.etiqueta, f.sexo == s, onClick = { viewModel.editar { it.copy(sexo = s) } }) }
            }
        }

        Seccion("Edad y tamaño") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(Pc.Crema)
                    .clickable { verCalendario = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = Pc.Naranja)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text("Fecha de nacimiento", style = MaterialTheme.typography.labelMedium, color = Pc.TintaSuave)
                    Text(f.nacimiento?.format(FECHA) ?: "Sin fecha (opcional)", style = MaterialTheme.typography.bodyLarge)
                }
                f.nacimiento?.let {
                    Text(FichaMascota.edad(it, f.aproximada, viewModel.hoy), style = MaterialTheme.typography.labelLarge, color = Pc.Naranja)
                }
            }
            f.errores[CampoMascota.NACIMIENTO]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (f.nacimiento != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Es una fecha aproximada", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = f.aproximada,
                        onCheckedChange = { v -> viewModel.editar { it.copy(aproximada = v) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Pc.Naranja),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                CampoTexto(
                    f.pesoTexto, { v -> viewModel.editar(CampoMascota.PESO) { it.copy(pesoTexto = v) } }, "Peso (opcional)",
                    error = f.errores[CampoMascota.PESO], teclado = KeyboardType.Decimal, modifier = Modifier.weight(1f),
                )
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChipOpcion("kg", f.unidad == UnidadPeso.KG, onClick = { viewModel.editar { it.copy(unidad = UnidadPeso.KG) } })
                    ChipOpcion("g", f.unidad == UnidadPeso.G, onClick = { viewModel.editar { it.copy(unidad = UnidadPeso.G) } })
                }
            }
            Text("Tamaño", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tamano.entries.forEach { t ->
                    ChipOpcion(t.etiqueta, f.tamano == t, onClick = { viewModel.editar { it.copy(tamano = if (it.tamano == t) null else t) } })
                }
            }
        }

        Seccion("Más detalles") {
            CampoTexto(f.raza, { v -> viewModel.editar { it.copy(raza = v) } }, "Raza (opcional)")
            CampoTexto(f.color, { v -> viewModel.editar { it.copy(color = v) } }, "Color (opcional)")
            Text("¿Está esterilizado/a?", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(true to "Sí", false to "No", null to "No sé").forEach { (valor, texto) ->
                    ChipOpcion(texto, f.esterilizado == valor, onClick = { viewModel.editar { it.copy(esterilizado = valor) } })
                }
            }
            CampoTexto(f.notas, { v -> viewModel.editar { it.copy(notas = v) } }, "Notas (opcional)", lineas = 3)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(Pc.Blanco)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { verSalud = !verSalud },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Salud básica", style = MaterialTheme.typography.titleMedium)
                    Text("Alergias, condiciones y medicamentos actuales", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                }
                Icon(if (verSalud) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = Pc.TintaSuave)
            }
            AnimatedVisibility(verSalud) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Escribe lo que te haya indicado tu veterinario. PetCare solo lo guarda; no diagnostica ni recomienda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Pc.TintaSuave,
                    )
                    CampoTexto(f.alergias, { v -> viewModel.editar { it.copy(alergias = v) } }, "Alergias", lineas = 2)
                    CampoTexto(f.condiciones, { v -> viewModel.editar { it.copy(condiciones = v) } }, "Condiciones conocidas", lineas = 2)
                    CampoTexto(f.medicamentos, { v -> viewModel.editar { it.copy(medicamentos = v) } }, "Medicamentos actuales", lineas = 2)
                    CampoTexto(f.observaciones, { v -> viewModel.editar { it.copy(observaciones = v) } }, "Observaciones importantes", lineas = 2)
                }
            }
        }

        Seccion("Veterinario de la mascota (opcional)") {
            CampoTexto(f.vet.nombre, { v -> viewModel.editar { it.copy(vet = it.vet.copy(nombre = v)) } }, "Nombre del veterinario")
            CampoTexto(f.vet.telefono, { v -> viewModel.editar { it.copy(vet = it.vet.copy(telefono = v)) } }, "Teléfono", teclado = KeyboardType.Phone)
            CampoTexto(f.vet.clinica, { v -> viewModel.editar { it.copy(vet = it.vet.copy(clinica = v)) } }, "Clínica")
            CampoTexto(f.vet.direccion, { v -> viewModel.editar { it.copy(vet = it.vet.copy(direccion = v)) } }, "Dirección")
            CampoTexto(f.vet.horario, { v -> viewModel.editar { it.copy(vet = it.vet.copy(horario = v)) } }, "Horario")
            CampoTexto(f.vet.notas, { v -> viewModel.editar { it.copy(vet = it.vet.copy(notas = v)) } }, "Observaciones", lineas = 2)
        }

        f.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        BotonPrincipal(
            texto = if (f.esNueva) "Agregar mascota" else "Guardar cambios",
            onClick = viewModel::guardar,
            cargando = f.guardando,
            habilitado = !f.cargando && !f.procesandoFoto,
        )
    }

    if (verCalendario) {
        val hoyUtc = viewModel.hoy.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = f.nacimiento?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= hoyUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { verCalendario = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { ms ->
                        val fecha = java.time.Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.editar(CampoMascota.NACIMIENTO) { it.copy(nacimiento = fecha) }
                    }
                    verCalendario = false
                }) { Text("Listo") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.editar(CampoMascota.NACIMIENTO) { it.copy(nacimiento = null, aproximada = false) }
                    verCalendario = false
                }) { Text("Sin fecha") }
            },
        ) { DatePicker(state = estado) }
    }
}

