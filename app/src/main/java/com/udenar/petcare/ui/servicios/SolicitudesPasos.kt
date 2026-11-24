package com.udenar.petcare.ui.servicios

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.petcare.domain.cuidados.DescripcionCuidados
import com.udenar.petcare.domain.modelo.Icono
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.domain.profesionales.ContactoCompartido
import com.udenar.petcare.domain.profesionales.Modalidad
import com.udenar.petcare.domain.profesionales.ModalidadPrestador
import com.udenar.petcare.domain.profesionales.Precios
import com.udenar.petcare.domain.profesionales.SaludCompartida
import com.udenar.petcare.domain.profesionales.ServicioVet
import com.udenar.petcare.domain.recomendaciones.PoseChopper
import com.udenar.petcare.ui.componentes.AsistentePasos
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.ChipOpcion
import com.udenar.petcare.ui.componentes.EditorHoras
import com.udenar.petcare.ui.componentes.FilaResumen
import com.udenar.petcare.ui.componentes.PreguntaChopper
import com.udenar.petcare.ui.componentes.SelectorFecha
import com.udenar.petcare.ui.componentes.SelectorFotos
import com.udenar.petcare.ui.componentes.TarjetaOpcionGrande
import com.udenar.petcare.ui.componentes.colores
import com.udenar.petcare.ui.componentes.ilustracion
import com.udenar.petcare.ui.theme.Pc
import java.time.LocalDate
import java.time.LocalTime

private val ServicioVet.icono: Icono
    get() = when (this) {
        ServicioVet.CONSULTA -> Icono.VETERINARIO
        ServicioVet.VACUNACION -> Icono.VACUNA
        ServicioVet.DESPARASITACION -> Icono.ESCUDO
        ServicioVet.CIRUGIA -> Icono.HOSPITAL
        ServicioVet.OTROS -> Icono.ESTRELLA
    }

private fun hora(h: LocalTime) = h.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.forLanguageTag("es-CO")))

private fun fechaCorta(d: LocalDate, hoy: LocalDate) = when (d) {
    hoy -> "Hoy"
    hoy.plusDays(1) -> "Mañana"
    else -> DescripcionCuidados.fecha(d)
}

/** Para quién: tarjetas grandes con la ilustración de cada mascota; avanza al tocar. */
@Composable
private fun ColumnScope.PasoMascota(mascotas: List<Mascota>, elegida: String?, prefijo: String, especiesQueAtiende: Set<com.udenar.petcare.domain.modelo.Especie>, nombreProfesional: String, onElegir: (String) -> Unit) {
    PreguntaChopper("¿Para quién es?", null, PoseChopper.SALUDO)
    mascotas.chunked(2).forEach { fila ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            fila.forEach { m ->
                TarjetaOpcionGrande(
                    m.nombre, elegida == m.id, onClick = { onElegir(m.id) },
                    dibujo = m.especie.ilustracion(), color = m.especie.colores().first(),
                    modifier = Modifier.weight(1f).testTag("${prefijo}_${m.nombre}"),
                )
            }
            if (fila.size == 1) Box(Modifier.weight(1f))
        }
    }
    mascotas.firstOrNull { it.id == elegida }?.takeIf { it.especie !in especiesQueAtiende }?.let {
        // Se advierte sin bloquear (RF-25)
        Text(
            "$nombreProfesional no indica que atienda ${it.especie.etiqueta.lowercase()}. Puedes enviar la solicitud de todas formas.",
            style = MaterialTheme.typography.bodySmall, color = Pc.Naranja, modifier = Modifier.testTag("aviso_especie"),
        )
    }
}

/** Cuándo: hoy/mañana/otra fecha y horas frecuentes con un toque. */
@Composable
private fun ColumnScope.PasoCuando(
    pregunta: String, fecha: LocalDate, onFecha: (LocalDate) -> Unit, horaElegida: LocalTime, onHora: (LocalTime) -> Unit, hoy: LocalDate,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    PreguntaChopper(pregunta, "El profesional confirma o te propone otra hora", PoseChopper.ORGANIZADOR)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(hoy.plusDays(1) to "Mañana", hoy.plusDays(2) to "Pasado mañana").forEach { (d, t) ->
            TarjetaOpcionGrande(t, fecha == d, onClick = { onFecha(d) }, icono = Icono.CALENDARIO, subtitulo = DescripcionCuidados.fecha(d), modifier = Modifier.weight(1f).testTag("fecha_${t}"))
        }
    }
    SelectorFecha("Otra fecha", fecha, { it?.let(onFecha) }, desde = hoy)
    Text("¿A qué hora?", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(8, 9, 10, 11, 14, 15, 16, 17).map { LocalTime.of(it, 0) }.forEach { h ->
            ChipOpcion(hora(h), horaElegida == h, onClick = { onHora(h) }, modifier = Modifier.testTag("hora_${h.hour}"))
        }
    }
    EditorHoras(listOf(horaElegida), { h -> h.lastOrNull()?.let(onHora) })
    extra()
}

/** Detalles opcionales: mensaje y hasta 3 fotos (RF-34). */
@Composable
private fun ColumnScope.PasoDetalles(pregunta: String, ayuda: String, texto: String, onTexto: (String) -> Unit, etiquetaTexto: String, fotos: List<Uri>, onFotos: (List<Uri>) -> Unit, tagTexto: String? = null) {
    PreguntaChopper(pregunta, ayuda, PoseChopper.DOCTOR)
    CampoTexto(texto, onTexto, etiquetaTexto, lineas = 3, modifier = tagTexto?.let { Modifier.testTag(it) } ?: Modifier)
    Text("Fotos (opcional, hasta 3)", style = MaterialTheme.typography.titleSmall)
    Text("Por ejemplo, la herida, la piel o el lugar del servicio. Solo las ve a quien le envías la solicitud.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
    SelectorFotos(fotos, onFotos)
}

private enum class PasoCita { MASCOTA, SERVICIO, CUANDO, DETALLES, RESUMEN }

/** Pedir una cita al veterinario (RF-25) como recorrido corto, con fotos opcionales (Fase 6.7). */
@Composable
fun SolicitarVetScreen(onVolver: () -> Unit, onEnviada: () -> Unit, avisar: (String) -> Unit, viewModel: SolicitarVetViewModel = hiltViewModel()) {
    val vet by viewModel.vet.collectAsStateWithLifecycle()
    val mascotas by viewModel.mascotas.collectAsStateWithLifecycle()
    val mensaje by viewModel.mensaje.collectAsStateWithLifecycle()
    val enviando by viewModel.enviando.collectAsStateWithLifecycle()
    LaunchedEffect(mensaje) {
        mensaje?.let {
            avisar(it)
            viewModel.mensaje.value = null
        }
    }
    var paso by rememberSaveable { mutableIntStateOf(0) }
    var mascotaId by rememberSaveable { mutableStateOf<String?>(null) }
    var servicio by rememberSaveable { mutableStateOf<ServicioVet?>(null) }
    var modalidad by rememberSaveable { mutableStateOf(Modalidad.CONSULTORIO) }
    var fecha by rememberSaveable { mutableStateOf(viewModel.hoy.plusDays(1)) }
    var horaCita by rememberSaveable { mutableStateOf(LocalTime.of(9, 0)) }
    var texto by rememberSaveable { mutableStateOf("") }
    var fotos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    // Con una sola mascota no hace falta preguntar
    LaunchedEffect(mascotas) {
        if (mascotaId == null && mascotas.size == 1) {
            mascotaId = mascotas.first().id
            if (paso == 0) paso = 1
        }
    }
    val v = vet
    if (v == null || mascotas.isEmpty()) {
        AsistentePasos(paso = 0, total = 1, onAtras = onVolver, onCerrar = onVolver) {
            PreguntaChopper("Pedir una cita", if (v != null) "Primero agrega una mascota propia: solo el dueño puede pedir servicios." else "Cargando…", PoseChopper.DOCTOR)
        }
        return
    }
    val pasos = PasoCita.entries
    val actual = pasos[paso]
    val elegida = mascotas.firstOrNull { it.id == mascotaId }
    AsistentePasos(
        paso = paso, total = pasos.size,
        onAtras = { if (paso == 0 || (paso == 1 && mascotas.size == 1)) onVolver() else paso-- },
        onCerrar = onVolver,
        onSaltar = if (actual == PasoCita.DETALLES) ({ paso++ }) else null,
        abajo = {
            when (actual) {
                PasoCita.MASCOTA -> Unit
                PasoCita.SERVICIO -> BotonPrincipal("Continuar", habilitado = servicio != null, onClick = { paso++ }, modifier = Modifier.testTag("continuar"))
                PasoCita.RESUMEN -> {
                    BotonPrincipal(
                        "Enviar solicitud", cargando = enviando,
                        habilitado = mascotaId != null && servicio != null,
                        onClick = { viewModel.enviar(mascotaId!!, servicio!!, modalidad, fecha.atTime(horaCita), texto, fotos, onEnviada) },
                        modifier = Modifier.testTag("enviar_solicitud"),
                    )
                    Text("PetCare no cobra ni procesa pagos: el valor se acuerda directamente con el veterinario.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                }
                else -> BotonPrincipal("Continuar", onClick = { paso++ }, modifier = Modifier.testTag("continuar"))
            }
        },
    ) { p ->
        when (pasos[p]) {
            PasoCita.MASCOTA -> PasoMascota(mascotas, mascotaId, "pedir", v.especies, v.nombre) { id -> mascotaId = id; paso++ }
            PasoCita.SERVICIO -> {
                PreguntaChopper("¿Qué necesita ${elegida?.nombre ?: "tu mascota"}?", "Con ${v.nombre}", PoseChopper.DOCTOR)
                (v.servicios.takeIf { it.isNotEmpty() } ?: ServicioVet.entries.toSet()).sortedBy { it.ordinal }.chunked(2).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        fila.forEach { s ->
                            TarjetaOpcionGrande(s.etiqueta, servicio == s, onClick = { servicio = s }, icono = s.icono, color = Pc.CieloClaro, modifier = Modifier.weight(1f).testTag("servicio_${s.name}"))
                        }
                        if (fila.size == 1) Box(Modifier.weight(1f))
                    }
                }
                if (v.domicilio) {
                    Text("¿Dónde?", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Modalidad.entries.forEach { m ->
                            TarjetaOpcionGrande(m.etiqueta, modalidad == m, onClick = { modalidad = m }, icono = if (m == Modalidad.DOMICILIO) Icono.CASA else Icono.HOSPITAL, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            PasoCita.CUANDO -> PasoCuando("¿Cuándo te gustaría?", fecha, { fecha = it }, horaCita, { horaCita = it }, viewModel.hoy)
            PasoCita.DETALLES -> PasoDetalles(
                "¿Algo que deba saber?", "Cuéntale qué notas; es opcional", texto, { texto = it.take(500) }, "Mensaje para el veterinario", fotos, { fotos = it },
            )
            PasoCita.RESUMEN -> {
                PreguntaChopper("¿Todo bien?", "Toca un dato para cambiarlo", PoseChopper.ORGANIZADOR)
                FilaResumen("Para", elegida?.nombre.orEmpty(), onEditar = { paso = PasoCita.MASCOTA.ordinal })
                FilaResumen("Servicio", listOfNotNull(servicio?.etiqueta, modalidad.etiqueta.takeIf { v.domicilio }).joinToString(" · "), onEditar = { paso = PasoCita.SERVICIO.ordinal })
                FilaResumen("Cuándo", "${fechaCorta(fecha, viewModel.hoy)} · ${hora(horaCita)}", onEditar = { paso = PasoCita.CUANDO.ordinal })
                FilaResumen("Mensaje", texto.ifBlank { "Sin mensaje" }, onEditar = { paso = PasoCita.DETALLES.ordinal })
                FilaResumen("Fotos", if (fotos.isEmpty()) "Sin fotos" else "${fotos.size} adjunta${if (fotos.size == 1) "" else "s"}", onEditar = { paso = PasoCita.DETALLES.ordinal })
            }
        }
    }
}

private enum class PasoCotizacion { MASCOTA, QUE, DESCRIPCION, CUANDO, COMPARTIR, RESUMEN }

/** Pedir cotización o contratar (RF-34) por pasos, con lo que el dueño decide compartir (RF-36). */
@Composable
fun SolicitarPrestadorScreen(onVolver: () -> Unit, onEnviada: () -> Unit, avisar: (String) -> Unit, viewModel: SolicitarPrestadorViewModel = hiltViewModel()) {
    val p by viewModel.prestador.collectAsStateWithLifecycle()
    val categorias by viewModel.categorias.collectAsStateWithLifecycle()
    val mascotas by viewModel.mascotas.collectAsStateWithLifecycle()
    val mensaje by viewModel.mensaje.collectAsStateWithLifecycle()
    val enviando by viewModel.enviando.collectAsStateWithLifecycle()
    LaunchedEffect(mensaje) {
        mensaje?.let {
            avisar(it)
            viewModel.mensaje.value = null
        }
    }
    var paso by rememberSaveable { mutableIntStateOf(0) }
    var mascotaId by rememberSaveable { mutableStateOf<String?>(null) }
    var categoria by rememberSaveable { mutableStateOf<String?>(null) }
    var servicio by rememberSaveable { mutableStateOf<String?>(null) }
    var modalidad by rememberSaveable { mutableStateOf<ModalidadPrestador?>(null) }
    var fecha by rememberSaveable { mutableStateOf(viewModel.hoy.plusDays(1)) }
    var horaInicio by rememberSaveable { mutableStateOf(LocalTime.of(9, 0)) }
    var fechaFin by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var descripcion by rememberSaveable { mutableStateOf("") }
    var texto by rememberSaveable { mutableStateOf("") }
    var fotos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var compartirSalud by rememberSaveable { mutableStateOf(false) }
    var alergias by rememberSaveable { mutableStateOf("") }
    var condiciones by rememberSaveable { mutableStateOf("") }
    var comportamiento by rememberSaveable { mutableStateOf("") }
    var indicaciones by rememberSaveable { mutableStateOf("") }
    var compartirContacto by rememberSaveable { mutableStateOf(false) }
    var telefono by rememberSaveable { mutableStateOf("") }
    var direccion by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(mascotas) {
        if (mascotaId == null && mascotas.size == 1) {
            mascotaId = mascotas.first().id
            if (paso == 0) paso = 1
        }
    }
    LaunchedEffect(p) {
        p?.let {
            if (categoria == null) categoria = it.categoriaPrincipal
            if (modalidad == null) modalidad = it.modalidades.minByOrNull { m -> m.ordinal }
        }
    }
    // Al elegir otra mascota se precargan sus datos de salud (se pueden editar antes de compartir)
    LaunchedEffect(mascotaId, mascotas) {
        mascotas.firstOrNull { it.id == mascotaId }?.let { m ->
            alergias = m.alergias.orEmpty()
            condiciones = m.condiciones.orEmpty()
        }
    }
    val pr = p
    if (pr == null || mascotas.isEmpty()) {
        AsistentePasos(paso = 0, total = 1, onAtras = onVolver, onCerrar = onVolver) {
            PreguntaChopper("Pedir cotización", if (pr != null) "Primero agrega una mascota propia: solo el dueño puede pedir servicios." else "Cargando…", PoseChopper.AMIGOS)
        }
        return
    }
    val pasos = PasoCotizacion.entries
    val actual = pasos[paso]
    val elegida = mascotas.firstOrNull { it.id == mascotaId }
    val temporal = categoria == "CUIDADO_TEMPORAL"
    AsistentePasos(
        paso = paso, total = pasos.size,
        onAtras = { if (paso == 0 || (paso == 1 && mascotas.size == 1)) onVolver() else paso-- },
        onCerrar = onVolver,
        abajo = {
            when (actual) {
                PasoCotizacion.MASCOTA -> Unit
                PasoCotizacion.QUE -> BotonPrincipal("Continuar", habilitado = categoria != null && modalidad != null, onClick = { paso++ }, modifier = Modifier.testTag("continuar"))
                PasoCotizacion.DESCRIPCION -> BotonPrincipal("Continuar", habilitado = descripcion.trim().length >= 3, onClick = { paso++ }, modifier = Modifier.testTag("continuar"))
                PasoCotizacion.RESUMEN -> {
                    BotonPrincipal(
                        "Enviar solicitud", cargando = enviando,
                        habilitado = mascotaId != null && categoria != null && modalidad != null,
                        onClick = {
                            viewModel.enviar(
                                mascotaId!!, categoria!!, servicio, modalidad!!, fecha.atTime(horaInicio), fechaFin?.takeIf { temporal }?.atTime(horaInicio),
                                descripcion, texto,
                                if (compartirSalud) SaludCompartida(alergias, condiciones, comportamiento, indicaciones) else null,
                                if (compartirContacto) ContactoCompartido(telefono, direccion.takeIf { modalidad == ModalidadPrestador.DOMICILIO }) else null,
                                fotos, onEnviada,
                            )
                        },
                        modifier = Modifier.testTag("enviar_cotizacion"),
                    )
                    Text("Las tarifas son orientativas y el pago se coordina directamente con el prestador, fuera de PetCare.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
                }
                else -> BotonPrincipal("Continuar", onClick = { paso++ }, modifier = Modifier.testTag("continuar"))
            }
        },
    ) { n ->
        when (pasos[n]) {
            PasoCotizacion.MASCOTA -> PasoMascota(mascotas, mascotaId, "cotizar", pr.especies, pr.nombre) { id -> mascotaId = id; paso++ }
            PasoCotizacion.QUE -> {
                PreguntaChopper("¿Qué necesita ${elegida?.nombre ?: "tu mascota"}?", "A ${pr.nombre}", PoseChopper.AMIGOS)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pr.categorias.forEach { cid ->
                        val c = categorias.firstOrNull { it.id == cid }
                        TarjetaOpcionGrande(
                            c?.nombre ?: cid, categoria == cid, onClick = { categoria = cid; servicio = null },
                            icono = c?.icono3d ?: Icono.HUELLAS, color = Pc.SolClaro, modifier = Modifier.weight(1f).testTag("categoria_$cid"),
                        )
                    }
                }
                val servicios = pr.servicios.filter { it.categoriaId == categoria }
                if (servicios.isNotEmpty()) {
                    Text("Servicio (opcional)", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        servicios.forEach { s ->
                            ChipOpcion("${s.nombre} · ${Precios.texto(s)}", servicio == s.id, onClick = { servicio = if (servicio == s.id) null else s.id }, modifier = Modifier.testTag("cotizar_servicio_${s.nombre}"))
                        }
                    }
                }
                if (pr.modalidades.size > 1) {
                    Text("¿Dónde?", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pr.modalidades.sortedBy { it.ordinal }.forEach { m ->
                            TarjetaOpcionGrande(m.etiqueta, modalidad == m, onClick = { modalidad = m }, icono = if (m == ModalidadPrestador.DOMICILIO) Icono.CASA else Icono.TIENDA, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            PasoCotizacion.DESCRIPCION -> PasoDetalles(
                "Cuéntale qué necesitas", "Mientras más claro, mejor la cotización", descripcion, { descripcion = it.take(1000) }, "Describe lo que necesitas *",
                fotos, { fotos = it }, tagTexto = "cotizar_descripcion",
            )
            PasoCotizacion.CUANDO -> PasoCuando(if (temporal) "¿Desde cuándo?" else "¿Cuándo?", fecha, { fecha = it }, horaInicio, { horaInicio = it }, viewModel.hoy) {
                if (temporal) SelectorFecha("Hasta", fechaFin, { fechaFin = it }, desde = fecha, permitirBorrar = true)
                if (fecha in pr.noDisponible) Text("${pr.nombre} marcó ese día como no disponible; quizá te proponga otra fecha.", style = MaterialTheme.typography.bodySmall, color = Pc.Naranja)
            }
            PasoCotizacion.COMPARTIR -> {
                PreguntaChopper("¿Qué compartes?", "Siempre ve nombre, especie, raza, sexo, edad, tamaño y peso aproximados. Nunca la ficha completa.", PoseChopper.ORGANIZADOR)
                FilaCasilla("Datos de salud y comportamiento", "Los ve mientras la solicitud esté abierta", compartirSalud, { compartirSalud = it }, "compartir_salud")
                if (compartirSalud) {
                    CampoTexto(alergias, { alergias = it.take(500) }, "Alergias")
                    CampoTexto(condiciones, { condiciones = it.take(500) }, "Condiciones relevantes")
                    CampoTexto(comportamiento, { comportamiento = it.take(500) }, "Comportamiento (p. ej. tira de la correa, teme a otros perros)")
                    CampoTexto(indicaciones, { indicaciones = it.take(500) }, "Indicaciones especiales para el servicio")
                }
                FilaCasilla(
                    "Mi teléfono" + if (modalidad == ModalidadPrestador.DOMICILIO) " y dirección" else "",
                    "Solo los ve cuando la solicitud esté aceptada", compartirContacto, { compartirContacto = it }, "compartir_contacto",
                )
                if (compartirContacto) {
                    CampoTexto(telefono, { telefono = it }, "Teléfono o WhatsApp", teclado = KeyboardType.Phone)
                    if (modalidad == ModalidadPrestador.DOMICILIO) CampoTexto(direccion, { direccion = it.take(200) }, "Dirección")
                }
                CampoTexto(texto, { texto = it.take(500) }, "Mensaje adicional (opcional)", lineas = 2)
            }
            PasoCotizacion.RESUMEN -> {
                PreguntaChopper("¿Todo bien?", "Toca un dato para cambiarlo", PoseChopper.ORGANIZADOR)
                FilaResumen("Para", elegida?.nombre.orEmpty(), onEditar = { paso = PasoCotizacion.MASCOTA.ordinal })
                val nombreCategoria = categorias.firstOrNull { it.id == categoria }?.nombre ?: categoria.orEmpty()
                val nombreServicio = pr.servicios.firstOrNull { it.id == servicio }?.nombre
                FilaResumen("Qué", listOfNotNull(nombreServicio ?: nombreCategoria, modalidad?.etiqueta).joinToString(" · "), onEditar = { paso = PasoCotizacion.QUE.ordinal })
                FilaResumen("Detalle", descripcion.trim().take(80) + if (fotos.isNotEmpty()) " · ${fotos.size} foto${if (fotos.size == 1) "" else "s"}" else "", onEditar = { paso = PasoCotizacion.DESCRIPCION.ordinal })
                FilaResumen(
                    "Cuándo",
                    "${fechaCorta(fecha, viewModel.hoy)} · ${hora(horaInicio)}" + (fechaFin?.takeIf { temporal }?.let { " hasta ${DescripcionCuidados.fecha(it)}" } ?: ""),
                    onEditar = { paso = PasoCotizacion.CUANDO.ordinal },
                )
                FilaResumen(
                    "Compartes",
                    listOfNotNull("Salud".takeIf { compartirSalud }, "Contacto".takeIf { compartirContacto }).joinToString(" y ").ifEmpty { "Solo lo básico" },
                    onEditar = { paso = PasoCotizacion.COMPARTIR.ordinal },
                )
            }
        }
    }
}
