package com.udenar.petcare.ui.mascotas

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
import androidx.compose.runtime.remember
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

/** Ficha de la mascota (vista del propietario). Las secciones de salud y cuidados llegan en la fase 2. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DetalleMascotaScreen(
    onVolver: () -> Unit,
    onEditar: (String) -> Unit,
    onCuidado: (clase: String, id: String?) -> Unit,
    avisar: (String) -> Unit,
    viewModel: DetalleMascotaViewModel = hiltViewModel(),
) {
    val m by viewModel.mascota.collectAsStateWithLifecycle()
    val codigo by viewModel.codigo.collectAsStateWithLifecycle()
    val cuidados by viewModel.cuidados.collectAsStateWithLifecycle()
    var elegirClase by remember { mutableStateOf(false) }
    var confirmarEliminar by remember { mutableStateOf(false) }
    val contexto = LocalContext.current

    val mascota = m
    if (mascota == null) {
        Box(Modifier.fillMaxSize().background(Pc.Crema), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Pc.Naranja)
        }
        return
    }
    LaunchedEffect(mascota.porSubir) { if (!mascota.porSubir) viewModel.cargarCodigo() }

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
                BotonCircular(Icons.Rounded.Edit, "Editar", onClick = { onEditar(mascota.id) })
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
                if (mascota.porSubir) Chip("Por subir ☁", fondo = Pc.Blanco.copy(alpha = 0.6f))
            }
        }

        Column(
            Modifier
                .padding(20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SeccionCuidados(
                cuidados,
                onAgregar = { elegirClase = true },
                onAbrir = { e -> onCuidado(ClaseCuidado.de(e.tipo).name, e.id) },
            )

            TarjetaCodigo(mascota, codigo, contexto, avisar, onReintentar = viewModel::cargarCodigo)

            Seccion("Ficha") {
                Dato("Especie", mascota.especieOtro ?: mascota.especie.etiqueta)
                mascota.raza?.let { Dato("Raza", it) }
                mascota.fechaNacimiento?.let {
                    Dato("Nacimiento", it.format(FECHA) + if (mascota.nacimientoAproximado) " (aprox.)" else "")
                }
                mascota.pesoGramos?.let { Dato("Peso", FichaMascota.peso(it)) }
                mascota.color?.let { Dato("Color", it) }
                mascota.notas?.let { Dato("Notas", it) }
            }

            val salud = listOfNotNull(
                mascota.alergias?.let { "Alergias" to it },
                mascota.condiciones?.let { "Condiciones" to it },
                mascota.medicamentosActuales?.let { "Medicamentos actuales" to it },
                mascota.observaciones?.let { "Observaciones" to it },
            )
            Seccion("Salud básica") {
                if (salud.isEmpty()) {
                    Text("Sin datos. Puedes agregarlos al editar la ficha.", style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
                } else {
                    salud.forEach { (t, v) -> Dato(t, v) }
                }
                Text("Esto no constituye un diagnóstico médico.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            }

            BotonPrincipal("Editar ficha", onClick = { onEditar(mascota.id) })
            TextButton(onClick = { confirmarEliminar = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Eliminar mascota", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
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
                        Text(c.emoji, fontSize = 22.sp)
                        Text(c.titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 14.dp))
                    }
                }
            }
        }
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
                    Text("Este código está desactivado.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
