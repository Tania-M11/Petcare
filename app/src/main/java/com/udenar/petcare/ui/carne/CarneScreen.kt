package com.udenar.petcare.ui.carne

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.udenar.petcare.data.local.ConfigQrDao
import com.udenar.petcare.data.local.ConfigQrEntidad
import com.udenar.petcare.data.qr.ImagenQr
import com.udenar.petcare.domain.cuidados.DescripcionCuidados
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.RepositorioCuidados
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.cuidados.resumir
import com.udenar.petcare.domain.mascotas.CampoQr
import com.udenar.petcare.domain.mascotas.ConfigQr
import com.udenar.petcare.domain.mascotas.QrMascota
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.domain.salud.RepositorioSalud
import com.udenar.petcare.domain.salud.Visita
import com.udenar.petcare.ui.componentes.AvatarMascota
import com.udenar.petcare.ui.componentes.BotonCircular
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.ChipOpcion
import com.udenar.petcare.ui.componentes.Seccion
import com.udenar.petcare.ui.mascotas.describir
import com.udenar.petcare.ui.navegacion.RutaCarne
import com.udenar.petcare.ui.theme.Pc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private val FECHA = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO"))

data class DatosCarne(
    val mascota: Mascota,
    val vacunas: List<Pair<EventoCuidado, LocalDate?>>,
    val medicamentos: List<EventoCuidado>,
    val visitas: List<Visita>,
    val config: ConfigQr,
)

/** Ficha resumen + QR (RF-18): cada rol ve lo que la sección 5 le permite. */
@HiltViewModel
class CarneViewModel @Inject constructor(
    estado: SavedStateHandle,
    mascotas: RepositorioMascotas,
    cuidados: RepositorioCuidados,
    salud: RepositorioSalud,
    private val configDao: ConfigQrDao,
    private val reloj: Clock,
) : ViewModel() {
    private val id = estado.toRoute<RutaCarne>().id
    val hoy: LocalDate get() = LocalDate.now(reloj)

    /** Código para el QR (solo si el dueño lo activa); se consulta una vez. */
    private val codigo = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    val datos: StateFlow<DatosCarne?> = combine(
        mascotas.observar().map { l -> l.firstOrNull { it.id == id } },
        combine(cuidados.observarEventos(), cuidados.observarRegistros()) { e, r -> e.filter { it.mascotaId == id } to r },
        salud.observarVisitas(id),
        configDao.observar(id),
    ) { m, (eventos, registros), visitas, cfg ->
        m ?: return@combine null
        val ahora = LocalDateTime.now(reloj)
        DatosCarne(
            mascota = m,
            vacunas = eventos.filter { it.tipo == TipoCuidado.VACUNA }.map { it to resumir(it, registros, ahora, reloj.zone).proxima },
            medicamentos = eventos.filter { it.tipo == TipoCuidado.MEDICAMENTO && (it.fechaFin == null || !it.fechaFin.isBefore(hoy)) },
            visitas = if (m.esPropietario) visitas.take(3) else emptyList(),
            config = cfg?.let { c ->
                ConfigQr(c.campos.split(',').mapNotNull { n -> CampoQr.entries.firstOrNull { it.name == n } }.toSet(), c.contacto)
            } ?: ConfigQr(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val repoMascotas = mascotas

    val codigoQr: StateFlow<String?> = codigo

    init {
        viewModelScope.launch { codigo.value = runCatching { repoMascotas.codigo(id)?.takeIf { it.activo }?.codigo }.getOrNull() }
    }

    fun guardarConfig(c: ConfigQr) = viewModelScope.launch {
        configDao.guardar(ConfigQrEntidad(id, c.campos.joinToString(",") { it.name }, c.contacto))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarneScreen(onVolver: () -> Unit, avisar: (String) -> Unit, viewModel: CarneViewModel = hiltViewModel()) {
    val d by viewModel.datos.collectAsStateWithLifecycle()
    val codigo by viewModel.codigoQr.collectAsStateWithLifecycle()
    var completo by remember { mutableStateOf(false) }
    val contexto = LocalContext.current
    val datos = d ?: return

    val m = datos.mascota
    Column(
        Modifier
            .fillMaxSize()
            .background(Pc.Crema)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotonCircular(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onVolver)
            Text("Carné de ${m.nombre}", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(Pc.Blanco)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarMascota(m.especie, m.fotoLocal, tam = 72.dp, forma = RoundedCornerShape(24.dp))
            Column(Modifier.padding(start = 14.dp)) {
                Text(m.nombre, style = MaterialTheme.typography.headlineSmall)
                Text(describir(m, viewModel.hoy), style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
            }
        }
        Seccion("Salud básica") {
            Dato("Alergias", m.alergias ?: "Ninguna registrada")
            m.condiciones?.let { Dato("Condiciones", it) }
            m.medicamentosActuales?.let { Dato("Medicamentos actuales", it) }
        }
        m.veterinario?.takeIf { !it.vacio }?.let { v ->
            Seccion("Veterinario") {
                Dato(v.nombre.ifBlank { "Veterinario" }, listOf(v.clinica, v.telefono).filter { it.isNotBlank() }.joinToString(" · "))
            }
        }
        Seccion("Vacunas") {
            if (datos.vacunas.isEmpty()) Text("Sin vacunas registradas.", style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
            datos.vacunas.forEach { (e, proxima) ->
                Dato(e.titulo, listOfNotNull(e.detalle["aplicadaEl"]?.let { "Aplicada: ${LocalDate.parse(it).format(FECHA)}" }, proxima?.let { "Próxima: ${it.format(FECHA)}" }).joinToString(" · "))
            }
        }
        Seccion("Medicamentos") {
            if (datos.medicamentos.isEmpty()) Text("Sin tratamientos activos.", style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
            datos.medicamentos.forEach { e -> Dato(e.titulo, listOfNotNull(e.detalle["dosis"], DescripcionCuidados.frecuencia(e.frecuencia)).joinToString(" · ")) }
        }
        if (datos.visitas.isNotEmpty()) {
            Seccion("Últimas visitas") {
                datos.visitas.forEach { v -> Dato(v.motivo, listOfNotNull(v.fecha.format(FECHA), v.veterinario).joinToString(" · ")) }
            }
        }

        if (m.esPropietario) {
            val texto = QrMascota.texto(m, datos.config, codigo)
            val imagen = remember(texto) { ImagenQr.generar(texto) }
            Seccion("Código QR") {
                Text(
                    "Lo lee cualquier cámara, sin internet. Elige qué datos mostrar; actualízalo si cambian.",
                    style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color.White)
                        .clickable { completo = true }
                        .testTag("imagen_qr"),
                ) { Image(imagen.asImageBitmap(), contentDescription = "Código QR de ${m.nombre}", modifier = Modifier.fillMaxSize()) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    BotonCircular(Icons.Rounded.Fullscreen, "Pantalla completa", onClick = { completo = true }, fondo = Pc.Durazno, tinte = Pc.Naranja)
                    BotonCircular(Icons.Rounded.Share, "Compartir QR", onClick = {
                        val uri = ImagenQr.paraCompartir(contexto, imagen, m.nombre)
                        contexto.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                "Compartir QR",
                            ),
                        )
                    }, fondo = Pc.Durazno, tinte = Pc.Naranja)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        BotonCircular(Icons.Rounded.Download, "Guardar en la galería", onClick = {
                            avisar(if (ImagenQr.guardarEnGaleria(contexto, imagen, m.nombre)) "QR guardado en Imágenes/PetCare" else "No se pudo guardar")
                        }, fondo = Pc.Durazno, tinte = Pc.Naranja)
                    }
                }
                Text("Qué incluye", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoQr.entries.forEach { c ->
                        val activo = c in datos.config.campos
                        ChipOpcion(
                            c.descripcion.substringBefore(" (").substringBefore(" para").let { if (c == CampoQr.CODIGO) "Código" else it },
                            activo,
                            onClick = { viewModel.guardarConfig(datos.config.copy(campos = if (activo) datos.config.campos - c else datos.config.campos + c)) },
                            modifier = Modifier.testTag("campo_qr_${c.name}"),
                        )
                    }
                }
                if (CampoQr.CODIGO in datos.config.campos) {
                    Text(
                        if (codigo == null) "El código no está disponible (sin internet o desactivado)." else "Con el código cualquiera puede pedir cuidarla; tú siempre apruebas.",
                        style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave,
                    )
                }
                if (CampoQr.CONTACTO in datos.config.campos) {
                    CampoTexto(datos.config.contacto, { viewModel.guardarConfig(datos.config.copy(contacto = it.take(80))) }, "Contacto (p. ej. tu nombre y teléfono)")
                }
            }
        }
    }

    if (completo && m.esPropietario) {
        val imagen = remember(datos, codigo) { ImagenQr.generar(QrMascota.texto(m, datos.config, codigo), 1000) }
        Dialog(onDismissRequest = { completo = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .clickable { completo = false },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(imagen.asImageBitmap(), contentDescription = "Código QR", modifier = Modifier.size(340.dp))
                    Text(m.nombre, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
                    Text("Toca para cerrar", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                }
            }
        }
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
    Column {
        Text(etiqueta, style = MaterialTheme.typography.titleSmall)
        if (valor.isNotBlank()) Text(valor, style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
    }
}
