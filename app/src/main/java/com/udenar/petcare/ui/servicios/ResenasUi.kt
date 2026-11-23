package com.udenar.petcare.ui.servicios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.udenar.petcare.domain.profesionales.MotivoReporte
import com.udenar.petcare.domain.profesionales.ProfesionalException
import com.udenar.petcare.domain.profesionales.RepositorioResenas
import com.udenar.petcare.domain.profesionales.Resena
import com.udenar.petcare.domain.profesionales.TipoReporte
import com.udenar.petcare.domain.profesionales.TipoResena
import com.udenar.petcare.domain.profesionales.ValidadorResena
import com.udenar.petcare.ui.componentes.BotonSecundario
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.ChipOpcion
import com.udenar.petcare.ui.componentes.Seccion
import com.udenar.petcare.ui.theme.Pc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private val LOCALE = Locale.forLanguageTag("es-CO")
private val FECHA = DateTimeFormatter.ofPattern("d MMM yyyy", LOCALE)

/** Fila de 5 estrellas; con [onCambio] se puede elegir. */
@Composable
fun Estrellas(valor: Int, onCambio: ((Int) -> Unit)? = null, tam: Dp = 22.dp) {
    Row {
        (1..5).forEach { n ->
            Icon(
                if (n <= valor) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                contentDescription = "$n estrellas",
                tint = if (n <= valor) Pc.Sol else Pc.TintaSuave,
                modifier = Modifier
                    .size(tam)
                    .then(if (onCambio != null) Modifier.selectable(selected = n == valor, role = Role.RadioButton, onClick = { onCambio(n) }).testTag("estrella_$n") else Modifier),
            )
        }
    }
}

/** Calificar un servicio completado (RF-28): estrellas y comentario opcional. */
@Composable
fun DialogoResena(titulo: String, inicial: Resena?, onGuardar: (Int, String) -> Unit, onEliminar: (() -> Unit)?, onCerrar: () -> Unit) {
    var estrellas by remember { mutableStateOf(inicial?.estrellas ?: 0) }
    var comentario by remember { mutableStateOf(inicial?.comentario.orEmpty()) }
    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = Pc.Crema,
        title = { Text(titulo, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Estrellas(estrellas, onCambio = { estrellas = it }, tam = 40.dp)
                CampoTexto(comentario, { comentario = it.take(ValidadorResena.MAX_COMENTARIO) }, "Cuéntales a otros dueños cómo te fue (opcional)", lineas = 3, modifier = Modifier.testTag("comentario_resena"))
                Text("${comentario.length}/${ValidadorResena.MAX_COMENTARIO} · Tu nombre se muestra con la reseña.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            }
        },
        confirmButton = {
            TextButton(onClick = { onGuardar(estrellas, comentario) }, enabled = estrellas in 1..5, modifier = Modifier.testTag("guardar_resena")) { Text("Publicar") }
        },
        dismissButton = {
            Row {
                onEliminar?.let { TextButton(onClick = it) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } }
                TextButton(onClick = onCerrar) { Text("Cancelar") }
            }
        },
    )
}

@Composable
fun DialogoReporte(titulo: String, onEnviar: (MotivoReporte, String) -> Unit, onCerrar: () -> Unit) {
    var motivo by remember { mutableStateOf<MotivoReporte?>(null) }
    var detalle by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = Pc.Crema,
        title = { Text(titulo, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MotivoReporte.entries.forEach { m -> ChipOpcion(m.etiqueta, motivo == m, onClick = { motivo = m }, modifier = Modifier.testTag("motivo_${m.name}")) }
                }
                CampoTexto(detalle, { detalle = it.take(500) }, "Detalles (opcional)", lineas = 2)
                Text("El equipo de PetCare revisa cada reporte. Una reseña con varios reportes se oculta mientras tanto.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            }
        },
        confirmButton = {
            TextButton(onClick = { motivo?.let { onEnviar(it, detalle) } }, enabled = motivo != null, modifier = Modifier.testTag("enviar_reporte")) { Text("Reportar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

@HiltViewModel
class ResenasViewModel @Inject constructor(private val resenas: RepositorioResenas) : ViewModel() {
    val lista = MutableStateFlow<List<Resena>>(emptyList())
    val hayMas = MutableStateFlow(false)
    private var pagina = 0

    fun cargar(profesionalId: String, tipo: TipoResena, desdeCero: Boolean = true) = viewModelScope.launch {
        if (desdeCero) pagina = 0
        runCatching { resenas.de(profesionalId, tipo, pagina, TAMANO) }.onSuccess { nuevas ->
            lista.value = if (desdeCero) nuevas else lista.value + nuevas
            hayMas.value = nuevas.size == TAMANO
            pagina++
        }
    }

    fun reportar(tipo: TipoReporte, refId: String, motivo: MotivoReporte, detalle: String, avisar: (String) -> Unit) = viewModelScope.launch {
        try {
            resenas.reportar(tipo, refId, motivo, detalle)
            avisar("Gracias. Revisaremos el reporte.")
        } catch (e: ProfesionalException) {
            avisar(e.message ?: "No se pudo enviar el reporte")
        }
    }

    companion object {
        const val TAMANO = 20
    }
}

/** Promedio, lista paginada y reportes (perfil del veterinario o del prestador). */
@Composable
fun SeccionResenas(profesionalId: String, tipo: TipoResena, promedio: Double?, cantidad: Int, avisar: (String) -> Unit) {
    val vm: ResenasViewModel = hiltViewModel(key = "resenas_$profesionalId")
    val lista by vm.lista.collectAsStateWithLifecycle()
    val hayMas by vm.hayMas.collectAsStateWithLifecycle()
    var reportar by remember { mutableStateOf<Resena?>(null) }
    var reportarPerfil by remember { mutableStateOf(false) }
    LaunchedEffect(profesionalId) { vm.cargar(profesionalId, tipo) }
    Seccion("Reseñas") {
        if (promedio == null || cantidad == 0) {
            Text("Aún sin reseñas", style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(String.format(LOCALE, "%.1f", promedio), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(end = 8.dp).testTag("promedio_resenas"))
                Column {
                    Estrellas(Math.round(promedio).toInt())
                    Text("$cantidad ${if (cantidad == 1) "reseña" else "reseñas"}", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                }
            }
        }
        lista.forEach { r ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("resena_${r.autorNombre.orEmpty()}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.autorNombre?.substringBefore(' ') ?: "Cliente", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Estrellas(r.estrellas, tam = 16.dp)
                }
                r.comentario?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.fecha.format(FECHA), style = MaterialTheme.typography.labelSmall, color = Pc.TintaSuave, modifier = Modifier.weight(1f))
                    if (!r.mia) TextButton(onClick = { reportar = r }) { Text("Reportar", style = MaterialTheme.typography.labelSmall, color = Pc.TintaSuave) }
                }
            }
        }
        if (hayMas) BotonSecundario("Ver más reseñas", onClick = { vm.cargar(profesionalId, tipo, desdeCero = false) })
        TextButton(onClick = { reportarPerfil = true }, modifier = Modifier.testTag("reportar_perfil")) {
            Text("Reportar este perfil", style = MaterialTheme.typography.labelMedium, color = Pc.TintaSuave)
        }
    }
    reportar?.let { r ->
        DialogoReporte("Reportar reseña", onEnviar = { m, d -> reportar = null; vm.reportar(TipoReporte.RESENA, r.id, m, d, avisar) }, onCerrar = { reportar = null })
    }
    if (reportarPerfil) {
        DialogoReporte(
            "Reportar perfil",
            onEnviar = { m, d ->
                reportarPerfil = false
                vm.reportar(if (tipo == TipoResena.VET) TipoReporte.PERFIL_VET else TipoReporte.PERFIL_PRESTADOR, profesionalId, m, d, avisar)
            },
            onCerrar = { reportarPerfil = false },
        )
    }
}
