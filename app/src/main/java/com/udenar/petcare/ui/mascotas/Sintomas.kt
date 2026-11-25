package com.udenar.petcare.ui.mascotas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.udenar.petcare.domain.documentos.RepositorioDocumentos
import com.udenar.petcare.domain.documentos.TipoDocumento
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Icono
import com.udenar.petcare.domain.recomendaciones.PoseMichi
import com.udenar.petcare.domain.salud.RepositorioSintomas
import com.udenar.petcare.domain.salud.Sintoma
import com.udenar.petcare.domain.salud.TipoSintoma
import com.udenar.petcare.ui.componentes.AsistentePasos
import com.udenar.petcare.ui.componentes.BaldosaIcono
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.ChipOpcion
import com.udenar.petcare.ui.componentes.ElegirFoto
import com.udenar.petcare.ui.componentes.FilaResumen
import com.udenar.petcare.ui.componentes.PreguntaMichi
import com.udenar.petcare.ui.componentes.TarjetaOpcionGrande
import com.udenar.petcare.ui.componentes.VacioMichi
import com.udenar.petcare.ui.navegacion.RutaNuevoSintoma
import com.udenar.petcare.ui.theme.Pc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

private val ES = Locale.forLanguageTag("es-CO")
private val FECHA_HORA = DateTimeFormatter.ofPattern("d MMM · h:mm a", ES)

data class FormSintoma(
    val tipo: TipoSintoma? = null,
    val fechaHora: LocalDateTime,
    val descripcion: String = "",
    val foto: String? = null,
    val guardando: Boolean = false,
    val listo: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class NuevoSintomaViewModel @Inject constructor(
    ruta: SavedStateHandle,
    mascotas: RepositorioMascotas,
    private val sintomas: RepositorioSintomas,
    private val documentos: RepositorioDocumentos,
    private val reloj: Clock,
) : ViewModel() {
    val mascotaId = ruta.toRoute<RutaNuevoSintoma>().mascotaId
    val ahora: LocalDateTime get() = LocalDateTime.now(reloj).withSecond(0).withNano(0)
    private val _f = MutableStateFlow(FormSintoma(fechaHora = LocalDateTime.now(reloj).withSecond(0).withNano(0)))
    val f = _f.asStateFlow()
    val nombre = MutableStateFlow("tu mascota")

    init {
        viewModelScope.launch { mascotas.obtener(mascotaId)?.let { nombre.value = it.nombre } }
    }

    fun editar(c: (FormSintoma) -> FormSintoma) = _f.update(c)

    fun guardar() {
        val s = _f.value
        val tipo = s.tipo ?: return
        if (s.guardando) return
        _f.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            // La foto queda como documento de la mascota (si quien anota puede subir documentos)
            val documentoId = s.foto?.let { uri ->
                runCatching {
                    documentos.agregar(mascotaId, uri, "${tipo.etiqueta} · ${s.fechaHora.format(FECHA_HORA)}", TipoDocumento.FOTO, s.fechaHora.toLocalDate(), null)
                }.getOrNull()
            }
            sintomas.guardar(Sintoma(UUID.randomUUID().toString(), mascotaId, tipo, s.fechaHora, s.descripcion, documentoId))
            _f.update { it.copy(guardando = false, listo = true) }
        }
    }
}

private enum class PasoSintoma { QUE, CUANDO, DETALLE, RESUMEN }

/**
 * Anotar un síntoma o cambio de comportamiento por pasos. La app solo guarda lo que la persona
 * observa; nunca interpreta ni diagnostica, y recuerda consultar al veterinario.
 */
@Composable
fun NuevoSintomaScreen(onCerrar: () -> Unit, onGuardado: () -> Unit, viewModel: NuevoSintomaViewModel = hiltViewModel()) {
    val f by viewModel.f.collectAsStateWithLifecycle()
    val nombre by viewModel.nombre.collectAsStateWithLifecycle()
    LaunchedEffect(f.listo) { if (f.listo) onGuardado() }
    var paso by rememberSaveable { mutableIntStateOf(0) }
    val pasos = PasoSintoma.entries
    val actual = pasos[paso]
    AsistentePasos(
        paso = paso, total = pasos.size,
        onAtras = { if (paso == 0) onCerrar() else paso-- },
        onCerrar = onCerrar,
        onSaltar = if (actual == PasoSintoma.DETALLE) ({ paso++ }) else null,
        abajo = {
            if (actual == PasoSintoma.RESUMEN) {
                BotonPrincipal("Guardar", onClick = viewModel::guardar, cargando = f.guardando, modifier = Modifier.testTag("guardar_sintoma"))
            } else {
                BotonPrincipal("Continuar", habilitado = actual != PasoSintoma.QUE || f.tipo != null, onClick = { paso++ }, modifier = Modifier.testTag("continuar"))
            }
        },
    ) { n ->
        when (pasos[n]) {
            PasoSintoma.QUE -> {
                PreguntaMichi("¿Qué notaste en $nombre?", "Anótalo para mostrárselo a su veterinario", PoseMichi.SALUD)
                TipoSintoma.entries.chunked(2).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        fila.forEach { t ->
                            TarjetaOpcionGrande(
                                t.etiqueta, f.tipo == t, onClick = { viewModel.editar { it.copy(tipo = t) } },
                                icono = t.icono, color = Pc.CieloClaro, modifier = Modifier.weight(1f).testTag("sintoma_${t.name}"),
                            )
                        }
                        if (fila.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
            PasoSintoma.CUANDO -> {
                PreguntaMichi("¿Cuándo pasó?", null, PoseMichi.ALARMA)
                val ahora = viewModel.ahora
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Ahora" to ahora, "Hoy temprano" to ahora.toLocalDate().atTime(8, 0), "Ayer" to ahora.minusDays(1).withHour(18).withMinute(0)).forEach { (t, d) ->
                        TarjetaOpcionGrande(t, f.fechaHora == d, onClick = { viewModel.editar { it.copy(fechaHora = d) } }, icono = Icono.RELOJ, modifier = Modifier.weight(1f))
                    }
                }
                com.udenar.petcare.ui.componentes.SelectorFecha(
                    "Fecha", f.fechaHora.toLocalDate(),
                    { d -> d?.let { nueva -> viewModel.editar { it.copy(fechaHora = nueva.atTime(it.fechaHora.toLocalTime())) } } },
                    hasta = ahora.toLocalDate(),
                )
                com.udenar.petcare.ui.componentes.EditorHoras(
                    listOf(f.fechaHora.toLocalTime()),
                    { h -> h.lastOrNull()?.let { hora -> viewModel.editar { it.copy(fechaHora = it.fechaHora.toLocalDate().atTime(hora)) } } },
                    horaSugerida = LocalTime.now(),
                )
            }
            PasoSintoma.DETALLE -> {
                PreguntaMichi("Cuéntalo con tus palabras", "Cuánto duró, cuántas veces, qué cambió… y una foto si ayuda (opcional)", PoseMichi.LEYENDO)
                CampoTexto(f.descripcion, { v -> viewModel.editar { it.copy(descripcion = v.take(1000)) } }, "Descripción", lineas = 4, modifier = Modifier.testTag("descripcion_sintoma"))
                ElegirFoto(f.foto, { u -> viewModel.editar { it.copy(foto = u) } }, prefijoTag = "sintoma_foto")
            }
            PasoSintoma.RESUMEN -> {
                PreguntaMichi("¿Todo bien?", "Toca un dato para cambiarlo", PoseMichi.LEYENDO)
                FilaResumen("Qué", f.tipo?.etiqueta ?: "—", onEditar = { paso = PasoSintoma.QUE.ordinal })
                FilaResumen("Cuándo", f.fechaHora.format(FECHA_HORA), onEditar = { paso = PasoSintoma.CUANDO.ordinal })
                FilaResumen("Detalle", listOfNotNull(f.descripcion.take(60).ifBlank { null }, "Con foto".takeIf { f.foto != null }).joinToString(" · ").ifEmpty { "Sin detalle" }, onEditar = { paso = PasoSintoma.DETALLE.ordinal })
                Text(
                    "PetCare solo guarda lo que observas; no interpreta ni diagnostica. Si te preocupa, consulta a su veterinario.",
                    style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave,
                )
            }
        }
    }
}

/** Tarjeta de la ficha (Salud): los últimos síntomas anotados y el botón para anotar uno. */
@Composable
fun TarjetaSintomas(sintomas: List<Sintoma>, onNuevo: () -> Unit, onEliminar: (String) -> Unit, onVeterinarios: () -> Unit) {
    var verTodos by remember { mutableStateOf(false) }
    var borrar by remember { mutableStateOf<Sintoma?>(null) }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Pc.Blanco).padding(16.dp).testTag("tarjeta_sintomas"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BaldosaIcono(Icono.TERMOMETRO, tam = 38.dp, modifier = Modifier.padding(end = 12.dp))
            Text("Síntomas y comportamiento", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            ChipOpcion("Anotar", false, onClick = onNuevo, icono = Icono.MAS, modifier = Modifier.testTag("anotar_sintoma"))
        }
        if (sintomas.isEmpty()) {
            VacioMichi(PoseMichi.SALUD, "Si notas algo distinto (no come, vomita, tose, se rasca, está raro), anótalo aquí con fecha y foto.")
        } else {
            (if (verTodos) sintomas else sintomas.take(4)).forEach { s ->
                Row(Modifier.fillMaxWidth().clickable { borrar = s }.padding(vertical = 4.dp).testTag("sintoma_${s.tipo.name}"), verticalAlignment = Alignment.Top) {
                    BaldosaIcono(s.tipo.icono, tam = 34.dp)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(s.tipo.etiqueta, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(s.fechaHora.format(FECHA_HORA), s.registradoPor?.nombre?.substringBefore(' '), "con foto".takeIf { s.documentoId != null }).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium, color = Pc.TintaSuave,
                        )
                        s.descripcion?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if (sintomas.size > 4) TextButton(onClick = { verTodos = !verTodos }) { Text(if (verTodos) "Ver menos" else "Ver los ${sintomas.size}", color = Pc.Naranja) }
            // Nunca diagnostica: si se repite, la única sugerencia es hablar con el veterinario
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Pc.CieloClaro).clickable(onClick = onVeterinarios).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BaldosaIcono(Icono.VETERINARIO, fondo = Pc.Blanco, tam = 32.dp)
                Text("Si te preocupa, muéstraselo a su veterinario. Buscar veterinarios", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
    borrar?.let { s ->
        AlertDialog(
            onDismissRequest = { borrar = null }, containerColor = Pc.Crema,
            title = { Text(s.tipo.etiqueta) },
            text = { Text(listOfNotNull(s.fechaHora.format(FECHA_HORA), s.descripcion).joinToString("\n")) },
            confirmButton = { TextButton(onClick = { borrar = null }) { Text("Cerrar") } },
            dismissButton = { TextButton(onClick = { onEliminar(s.id); borrar = null }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
        )
    }
}
