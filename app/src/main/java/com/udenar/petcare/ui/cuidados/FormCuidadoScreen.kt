package com.udenar.petcare.ui.cuidados

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.petcare.domain.cuidados.ModoFrecuencia
import com.udenar.petcare.domain.cuidados.TIPOS_HIGIENE
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.ui.componentes.BotonCircular
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.ChipOpcion
import com.udenar.petcare.ui.componentes.EditorHoras
import com.udenar.petcare.ui.componentes.Seccion
import com.udenar.petcare.ui.componentes.SelectorDias
import com.udenar.petcare.ui.componentes.SelectorFecha
import com.udenar.petcare.ui.componentes.SelectorFrecuencia
import com.udenar.petcare.ui.theme.Pc

/** Crear o editar un cuidado: comidas, medicamentos, vacunas, desparasitación, higiene y actividades (RF-05 a RF-08, RF-11, RF-13). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormCuidadoScreen(onListo: () -> Unit, viewModel: FormCuidadoViewModel = hiltViewModel()) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    var confirmarEliminar by remember { mutableStateOf(false) }
    LaunchedEffect(f.listo) { if (f.listo) onListo() }

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
        com.udenar.petcare.ui.componentes.EncabezadoMichi(
            if (f.esNuevo) "Nuevo cuidado" else "Editar cuidado",
            f.clase.titulo,
            when (f.clase.name) {
                "MEDICAMENTO", "TRATAMIENTO", "VACUNA", "DESPARASITACION" -> com.udenar.petcare.domain.recomendaciones.PoseMichi.SALUD
                "HIGIENE" -> com.udenar.petcare.domain.recomendaciones.PoseMichi.HIGIENE
                "ACTIVIDAD" -> com.udenar.petcare.domain.recomendaciones.PoseMichi.CELEBRA
                else -> com.udenar.petcare.domain.recomendaciones.PoseMichi.ALARMA
            },
            onListo,
        )

        when (f.clase) {
            ClaseCuidado.COMIDA -> {
                Seccion("Comida") {
                    CampoTexto(f.titulo, { v -> viewModel.editar(CampoCuidado.TITULO) { it.copy(titulo = v) } }, "Nombre (p. ej. Desayuno)", error = f.errores[CampoCuidado.TITULO])
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        viewModel.sugerencias.forEach { s -> ChipOpcion(s, f.titulo == s, onClick = { viewModel.editar(CampoCuidado.TITULO) { it.copy(titulo = s) } }) }
                    }
                    Text("¿A qué hora?", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
                    EditorHoras(f.horas, { h -> viewModel.editar(CampoCuidado.HORAS) { it.copy(horas = h) } }, error = f.errores[CampoCuidado.HORAS])
                    Text("¿Qué días?", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
                    SelectorDias(f.dias, { d -> viewModel.editar(CampoCuidado.DIAS) { it.copy(dias = d) } })
                    f.errores[CampoCuidado.DIAS]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    CampoTexto(f.detalle["texto"].orEmpty(), { viewModel.detalle("texto", it) }, "Nota para quien la dé (opcional)", lineas = 2)
                    Text("PetCare solo te recuerda la hora: no calcula porciones ni sugiere alimentos.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                }
            }

            ClaseCuidado.MEDICAMENTO -> {
                Seccion("Medicamento") {
                    CampoTexto(f.titulo, { v -> viewModel.editar(CampoCuidado.TITULO) { it.copy(titulo = v) } }, "Nombre del medicamento", error = f.errores[CampoCuidado.TITULO])
                    CampoTexto(f.detalle["dosis"].orEmpty(), { viewModel.detalle("dosis", it) }, "Dosis indicada por el veterinario")
                    Text("¿A qué horas?", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
                    EditorHoras(f.horas, { h -> viewModel.editar(CampoCuidado.HORAS) { it.copy(horas = h) } }, error = f.errores[CampoCuidado.HORAS])
                    Text("¿Cada cuánto?", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
                    SelectorFrecuencia(
                        f.modo, f.n, f.dias,
                        onModo = { m -> viewModel.editar(CampoCuidado.N) { it.copy(modo = m) } },
                        onN = { v -> viewModel.editar(CampoCuidado.N) { it.copy(n = v) } },
                        onDias = { d -> viewModel.editar(CampoCuidado.DIAS) { it.copy(dias = d) } },
                        modos = listOf(ModoFrecuencia.DIARIO, ModoFrecuencia.DIAS_SEMANA, ModoFrecuencia.CADA_N_DIAS, ModoFrecuencia.CADA_N_SEMANAS),
                        errorN = f.errores[CampoCuidado.N],
                    )
                }
                Seccion("Tratamiento") {
                    SelectorFecha("Empieza", f.fecha, { d -> viewModel.editar(CampoCuidado.FECHA) { it.copy(fecha = d) } })
                    SelectorFecha(
                        "Termina (opcional)", f.fechaFin, { d -> viewModel.editar(CampoCuidado.FECHA_FIN) { it.copy(fechaFin = d) } },
                        desde = f.fecha, textoVacio = "Sin fecha de fin", permitirBorrar = true, error = f.errores[CampoCuidado.FECHA_FIN],
                    )
                    CampoTexto(f.detalle["indicaciones"].orEmpty(), { viewModel.detalle("indicaciones", it) }, "Indicaciones (opcional)", lineas = 2)
                    CampoTexto(f.detalle["veterinario"].orEmpty(), { viewModel.detalle("veterinario", it) }, "Veterinario que lo indicó (opcional)")
                    Text("Escribe la dosis tal como te la indicaron: PetCare no la valida ni la sugiere.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                }
            }

            ClaseCuidado.VACUNA -> {
                Seccion("Vacuna") {
                    CampoTexto(f.titulo, { v -> viewModel.editar(CampoCuidado.TITULO) { it.copy(titulo = v) } }, "Nombre de la vacuna", error = f.errores[CampoCuidado.TITULO])
                    SelectorFecha("Se aplicó el (opcional)", f.fechaAnterior, { d -> viewModel.editar { it.copy(fechaAnterior = d) } }, hasta = viewModel.hoy, permitirBorrar = true)
                    SelectorFecha("Próxima dosis", f.fecha, { d -> viewModel.editar(CampoCuidado.FECHA) { it.copy(fecha = d) } }, textoVacio = "Elige la fecha", error = f.errores[CampoCuidado.FECHA])
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Avisarme", style = MaterialTheme.typography.bodyLarge)
                        CampoTexto(
                            f.avisoDias, { v -> viewModel.editar { it.copy(avisoDias = v) } }, "Días", teclado = KeyboardType.Number,
                            modifier = Modifier
                                .padding(horizontal = 10.dp)
                                .weight(1f),
                        )
                        Text("días antes", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Seccion("Más datos (opcional)") {
                    CampoTexto(f.detalle["veterinario"].orEmpty(), { viewModel.detalle("veterinario", it) }, "Veterinario")
                    CampoTexto(f.detalle["clinica"].orEmpty(), { viewModel.detalle("clinica", it) }, "Clínica")
                    CampoTexto(f.detalle["lote"].orEmpty(), { viewModel.detalle("lote", it) }, "Lote")
                    CampoTexto(f.detalle["observaciones"].orEmpty(), { viewModel.detalle("observaciones", it) }, "Observaciones", lineas = 2)
                }
            }

            ClaseCuidado.DESPARASITACION -> {
                Seccion("Desparasitación") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(TipoCuidado.DESPARASITACION_INTERNA to "Interna", TipoCuidado.DESPARASITACION_EXTERNA to "Externa").forEach { (t, txt) ->
                            ChipOpcion(txt, f.tipo == t, onClick = { viewModel.editar { it.copy(tipo = t) } })
                        }
                    }
                    CampoTexto(f.titulo, { v -> viewModel.editar(CampoCuidado.TITULO) { it.copy(titulo = v) } }, "Producto", error = f.errores[CampoCuidado.TITULO])
                    SelectorFecha("Última aplicación (opcional)", f.fechaAnterior, { d -> viewModel.editar { it.copy(fechaAnterior = d) } }, hasta = viewModel.hoy, permitirBorrar = true)
                    Text("¿Cada cuánto? (lo que te indicó tu veterinario)", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
                    SelectorFrecuencia(
                        f.modo, f.n, f.dias,
                        onModo = { m -> viewModel.editar(CampoCuidado.N) { it.copy(modo = m) } },
                        onN = { v -> viewModel.editar(CampoCuidado.N) { it.copy(n = v) } },
                        onDias = {},
                        modos = ModoFrecuencia.INTERVALOS,
                        errorN = f.errores[CampoCuidado.N],
                    )
                    CampoTexto(f.detalle["notas"].orEmpty(), { viewModel.detalle("notas", it) }, "Notas (opcional)", lineas = 2)
                }
            }

            ClaseCuidado.HIGIENE -> {
                Seccion("Higiene") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TIPOS_HIGIENE.forEach { t ->
                            ChipOpcion(
                                t.etiqueta, f.tipo == t,
                                onClick = { viewModel.editar(CampoCuidado.TITULO) { it.copy(tipo = t, titulo = t.etiqueta) } },
                                modifier = Modifier.testTag("higiene_${t.name}"),
                            )
                        }
                    }
                    Text("¿Cada cuánto?", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
                    SelectorFrecuencia(
                        f.modo, f.n, f.dias,
                        onModo = { m -> viewModel.editar(CampoCuidado.N) { it.copy(modo = m) } },
                        onN = { v -> viewModel.editar(CampoCuidado.N) { it.copy(n = v) } },
                        onDias = {},
                        modos = ModoFrecuencia.INTERVALOS,
                        errorN = f.errores[CampoCuidado.N],
                    )
                    SelectorFecha("¿Cuándo toca la próxima vez?", f.fecha, { d -> viewModel.editar(CampoCuidado.FECHA) { it.copy(fecha = d) } })
                    Text("Al marcarlo como hecho, la próxima fecha se calcula desde ese día.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                    CampoTexto(f.detalle["notas"].orEmpty(), { viewModel.detalle("notas", it) }, "Notas (opcional)", lineas = 2)
                }
            }

            ClaseCuidado.PERSONALIZADO -> {
                Seccion("Actividad") {
                    CampoTexto(f.titulo, { v -> viewModel.editar(CampoCuidado.TITULO) { it.copy(titulo = v) } }, "Nombre (p. ej. Sesión de juego)", error = f.errores[CampoCuidado.TITULO])
                    Text("¿Cada cuánto?", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
                    SelectorFrecuencia(
                        f.modo, f.n, f.dias,
                        onModo = { m -> viewModel.editar(CampoCuidado.N) { it.copy(modo = m) } },
                        onN = { v -> viewModel.editar(CampoCuidado.N) { it.copy(n = v) } },
                        onDias = { d -> viewModel.editar(CampoCuidado.DIAS) { it.copy(dias = d) } },
                        errorN = f.errores[CampoCuidado.N],
                    )
                    f.errores[CampoCuidado.DIAS]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Text("Horas (opcional)", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
                    EditorHoras(f.horas, { h -> viewModel.editar(CampoCuidado.HORAS) { it.copy(horas = h) } })
                    SelectorFecha(if (f.modo == ModoFrecuencia.UNA_VEZ) "Fecha" else "Desde", f.fecha, { d -> viewModel.editar(CampoCuidado.FECHA) { it.copy(fecha = d) } })
                    if (f.modo != ModoFrecuencia.UNA_VEZ) {
                        SelectorFecha("Hasta (opcional)", f.fechaFin, { d -> viewModel.editar(CampoCuidado.FECHA_FIN) { it.copy(fechaFin = d) } }, desde = f.fecha, textoVacio = "Sin fecha de fin", permitirBorrar = true, error = f.errores[CampoCuidado.FECHA_FIN])
                    }
                    CampoTexto(f.detalle["duracionMin"].orEmpty(), { viewModel.detalle("duracionMin", it.filter(Char::isDigit).take(4)) }, "Duración en minutos (opcional)", teclado = KeyboardType.Number)
                    CampoTexto(f.detalle["observaciones"].orEmpty(), { viewModel.detalle("observaciones", it) }, "Observaciones (opcional)", lineas = 2)
                }
            }
        }

        f.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        BotonPrincipal(if (f.esNuevo) "Guardar cuidado" else "Guardar cambios", onClick = viewModel::guardar, cargando = f.guardando, habilitado = !f.cargando)
        if (!f.esNuevo && viewModel.esDelVeterinario) {
            Text("Lo registró el veterinario. Puedes ocultarlo: deja de sonar y de verse en la agenda, pero sigue en el historial.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            com.udenar.petcare.ui.componentes.BotonSecundario(
                if (viewModel.estaOculto) "Mostrar de nuevo" else "Ocultar",
                onClick = { viewModel.cambiarOculto(!viewModel.estaOculto) },
                modifier = Modifier.testTag("ocultar_cuidado"),
            )
        }
        if (!f.esNuevo) {
            TextButton(onClick = { confirmarEliminar = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Eliminar cuidado", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (confirmarEliminar) {
        AlertDialog(
            onDismissRequest = { confirmarEliminar = false },
            containerColor = Pc.Crema,
            title = { Text("¿Eliminar \"${f.titulo}\"?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Dejará de sonar y desaparecerá de la agenda en todos tus dispositivos.") },
            confirmButton = { TextButton(onClick = { confirmarEliminar = false; viewModel.eliminar() }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmarEliminar = false }) { Text("Cancelar") } },
        )
    }
}
