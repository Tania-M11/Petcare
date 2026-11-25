package com.udenar.petcare.ui.cuidados

import com.udenar.petcare.domain.modelo.Icono
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.petcare.domain.cuidados.DescripcionCuidados
import com.udenar.petcare.domain.cuidados.ModoFrecuencia
import com.udenar.petcare.domain.cuidados.TIPOS_HIGIENE
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.recomendaciones.PoseMichi
import com.udenar.petcare.ui.componentes.AsistentePasos
import com.udenar.petcare.ui.componentes.BotonPrincipal
import com.udenar.petcare.ui.componentes.CampoTexto
import com.udenar.petcare.ui.componentes.ChipOpcion
import com.udenar.petcare.ui.componentes.EditorHoras
import com.udenar.petcare.ui.componentes.PreguntaMichi
import com.udenar.petcare.ui.componentes.SelectorDias
import com.udenar.petcare.ui.componentes.SelectorFecha
import com.udenar.petcare.ui.componentes.Stepper
import com.udenar.petcare.ui.componentes.TarjetaOpcionGrande
import com.udenar.petcare.ui.theme.Kalnia
import com.udenar.petcare.ui.theme.Pc
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Un paso del recorrido: qué pregunta, si se puede seguir y si se puede saltar. */
private class Paso(val id: String, val valido: () -> Boolean = { true }, val saltable: Boolean = false, val contenido: @Composable ColumnScope.() -> Unit)

/**
 * Cuidado nuevo por pasos (Fase 6.2): las preguntas cambian según el tipo y al final se ve
 * en lenguaje natural ("Todos los días a las 8:00 a. m."). La app no sugiere dosis ni frecuencias:
 * los atajos de hora y fecha solo ahorran toques.
 */
@Composable
fun NuevoCuidadoScreen(onListo: () -> Unit, onGuardado: () -> Unit = onListo, viewModel: FormCuidadoViewModel = hiltViewModel()) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    var paso by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(f.listo) { if (f.listo) onGuardado() }
    val hoy = viewModel.hoy

    val pasos: List<Paso> = when (f.clase) {
        ClaseCuidado.COMIDA -> listOf(
            Paso("cual", { f.titulo.isNotBlank() }) {
                PreguntaMichi("¿Qué comida es?", "Solo te recuerdo la hora; las porciones las decides tú", PoseMichi.CELEBRA)
                OpcionesGrandes(
                    listOf("Desayuno" to Icono.AMANECER, "Almuerzo" to Icono.SOL, "Merienda" to Icono.GALLETA, "Cena" to Icono.LUNA), f.titulo,
                ) { nombre ->
                    viewModel.editar(CampoCuidado.TITULO) {
                        it.copy(titulo = nombre, horas = listOf(HORA_COMIDA[nombre] ?: LocalTime.of(8, 0)))
                    }
                    paso++
                }
                CampoTexto(f.titulo, { v -> viewModel.editar(CampoCuidado.TITULO) { it.copy(titulo = v.take(80)) } }, "U otro nombre", modifier = Modifier.testTag("titulo_cuidado"))
            },
            Paso("hora", { f.horas.isNotEmpty() }) {
                PreguntaMichi("¿A qué hora?", "Puedes agregar varias", PoseMichi.ALARMA)
                HorasRapidas(f.horas) { h -> viewModel.editar(CampoCuidado.HORAS) { it.copy(horas = h) } }
            },
            Paso("dias", { f.dias.isNotEmpty() }) {
                PreguntaMichi("¿Qué días?", null, PoseMichi.AGENDA)
                DiasRapidos(f.dias) { d -> viewModel.editar(CampoCuidado.DIAS) { it.copy(dias = d, modo = ModoFrecuencia.DIAS_SEMANA) } }
            },
            Paso("alimento", { true }) {
                PreguntaMichi("¿Qué le das?", "Todo es opcional; sirve para que quien la cuide le dé lo mismo", PoseMichi.LEYENDO)
                DatosAlimento(f.detalle, viewModel::detalle)
            },
            resumen(f, viewModel, nota = "texto"),
        )
        ClaseCuidado.MEDICAMENTO -> listOf(
            Paso("nombre", { f.titulo.isNotBlank() }) {
                PreguntaMichi("¿Qué medicamento es?", "Escríbelo tal como te lo indicó su veterinario", PoseMichi.DOCTOR)
                CampoTexto(f.titulo, { v -> viewModel.editar(CampoCuidado.TITULO) { it.copy(titulo = v.take(80)) } }, "Nombre del medicamento", modifier = Modifier.testTag("titulo_cuidado"))
                Escanear(viewModel, f)
                CampoTexto(f.detalle["dosis"].orEmpty(), { viewModel.detalle("dosis", it) }, "Dosis indicada (opcional)")
                Text("PetCare no valida ni sugiere dosis: solo te recuerda darla.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            },
            Paso("hora", { f.horas.isNotEmpty() }) {
                PreguntaMichi("¿A qué horas se la das?", "Agrega cada toma del día", PoseMichi.DOCTOR)
                HorasRapidas(f.horas) { h -> viewModel.editar(CampoCuidado.HORAS) { it.copy(horas = h) } }
            },
            Paso("frecuencia", { !f.modo.usaN || (f.n.toIntOrNull() ?: 0) > 0 }) {
                PreguntaMichi("¿Qué días?", "Según lo que indicó su veterinario", PoseMichi.AGENDA)
                Frecuencias(f, viewModel, listOf(ModoFrecuencia.DIARIO, ModoFrecuencia.DIAS_SEMANA, ModoFrecuencia.CADA_N_DIAS, ModoFrecuencia.CADA_N_SEMANAS))
            },
            Paso("duracion", { f.fecha != null }) {
                PreguntaMichi("¿Desde cuándo y hasta cuándo?", null, PoseMichi.AGENDA)
                FechaRapida("Empieza", f.fecha, hoy) { d -> viewModel.editar(CampoCuidado.FECHA) { it.copy(fecha = d) } }
                Text("¿Cuántos días dura el tratamiento?", style = MaterialTheme.typography.titleSmall)
                var conFin by remember { mutableStateOf(f.fechaFin != null) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipOpcion("Sin fecha de fin", !conFin, onClick = { conFin = false; viewModel.editar(CampoCuidado.FECHA_FIN) { it.copy(fechaFin = null) } })
                    ChipOpcion("Tiene fin", conFin, onClick = { conFin = true; viewModel.editar(CampoCuidado.FECHA_FIN) { it.copy(fechaFin = (it.fecha ?: hoy).plusDays(6)) } }, modifier = Modifier.testTag("tiene_fin"))
                }
                if (conFin) {
                    val inicio = f.fecha ?: hoy
                    val dias = f.fechaFin?.let { java.time.temporal.ChronoUnit.DAYS.between(inicio, it).toInt() + 1 } ?: 7
                    Stepper(dias.toDouble(), { v -> viewModel.editar(CampoCuidado.FECHA_FIN) { it.copy(fechaFin = inicio.plusDays(v.toLong() - 1)) } }, paso = 1.0, unidad = if (dias == 1) "día" else "días", minimo = 1.0, maximo = 365.0, decimales = 0)
                    f.fechaFin?.let { Text("Último día: ${DescripcionCuidados.fecha(it)}", style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave) }
                }
            },
            resumen(f, viewModel, nota = "indicaciones"),
        )
        ClaseCuidado.VACUNA -> listOf(
            Paso("nombre", { f.titulo.isNotBlank() }) {
                PreguntaMichi("¿Qué vacuna es?", "Como aparece en su carné", PoseMichi.SALUD)
                CampoTexto(f.titulo, { v -> viewModel.editar(CampoCuidado.TITULO) { it.copy(titulo = v.take(80)) } }, "Nombre de la vacuna", modifier = Modifier.testTag("titulo_cuidado"))
                Escanear(viewModel, f)
            },
            Paso("aplicada", saltable = true) {
                PreguntaMichi("¿Cuándo se la aplicaron?", "Si aún no, puedes saltar este paso", PoseMichi.SALUD)
                FechaRapida("Se aplicó", f.fechaAnterior, hoy, permitirNinguna = true, pasado = true) { d -> viewModel.editar { it.copy(fechaAnterior = d) } }
            },
            Paso("proxima", { f.fecha != null }) {
                PreguntaMichi("¿Cuándo es la próxima dosis?", "Te aviso antes para que apartes la cita", PoseMichi.AGENDA)
                SelectorFecha("Próxima dosis", f.fecha, { d -> viewModel.editar(CampoCuidado.FECHA) { it.copy(fecha = d) } }, textoVacio = "Elige la fecha", desde = hoy)
                Text("¿Cuántos días antes te aviso?", style = MaterialTheme.typography.titleSmall)
                val aviso = f.avisoDias.toIntOrNull() ?: 7
                Stepper(aviso.toDouble(), { v -> viewModel.editar { it.copy(avisoDias = v.toInt().toString()) } }, paso = 1.0, unidad = if (aviso == 1) "día" else "días", minimo = 0.0, maximo = 90.0, decimales = 0)
            },
            Paso("certificado", saltable = true) {
                PreguntaMichi("¿Tienes el certificado?", "Una foto del carné o certificado; queda en sus Documentos", PoseMichi.DOCUMENTOS)
                com.udenar.petcare.ui.componentes.ElegirFoto(f.foto, { u -> viewModel.editar { it.copy(foto = u) } }, prefijoTag = "certificado")
                CampoTexto(f.detalle["lote"].orEmpty(), { viewModel.detalle("lote", it.take(40)) }, "Número de lote (opcional)", modifier = Modifier.testTag("lote_vacuna"))
            },
            resumen(f, viewModel, nota = "observaciones"),
        )
        ClaseCuidado.DESPARASITACION -> listOf(
            Paso("tipo") {
                PreguntaMichi("¿Interna o externa?", null, PoseMichi.SALUD)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(Triple(TipoCuidado.DESPARASITACION_INTERNA, "Interna", Icono.MEDICAMENTO), Triple(TipoCuidado.DESPARASITACION_EXTERNA, "Externa", Icono.INSECTO)).forEach { (t, txt, e) ->
                        TarjetaOpcionGrande(txt, f.tipo == t, onClick = { viewModel.editar { it.copy(tipo = t) }; paso++ }, icono = e, modifier = Modifier.weight(1f))
                    }
                }
            },
            Paso("producto", { f.titulo.isNotBlank() }) {
                PreguntaMichi("¿Qué producto usas?", null, PoseMichi.SALUD)
                CampoTexto(f.titulo, { v -> viewModel.editar(CampoCuidado.TITULO) { it.copy(titulo = v.take(80)) } }, "Producto", modifier = Modifier.testTag("titulo_cuidado"))
                Escanear(viewModel, f)
            },
            Paso("ultima", saltable = true) {
                PreguntaMichi("¿Cuándo fue la última vez?", "Desde ese día cuento la próxima", PoseMichi.AGENDA)
                FechaRapida("Última aplicación", f.fechaAnterior, hoy, permitirNinguna = true, pasado = true) { d -> viewModel.editar { it.copy(fechaAnterior = d) } }
            },
            Paso("frecuencia", { (f.n.toIntOrNull() ?: 0) > 0 }) {
                PreguntaMichi("¿Cada cuánto?", "Lo que te indicó su veterinario", PoseMichi.AGENDA)
                Frecuencias(f, viewModel, ModoFrecuencia.INTERVALOS)
            },
            resumen(f, viewModel, nota = "notas"),
        )
        ClaseCuidado.HIGIENE -> listOf(
            Paso("tipo") {
                PreguntaMichi("¿Qué cuidado de higiene?", null, PoseMichi.HIGIENE)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TIPOS_HIGIENE.forEach { t ->
                        TarjetaOpcionGrande(
                            t.etiqueta, f.tipo == t,
                            onClick = { viewModel.editar(CampoCuidado.TITULO) { it.copy(tipo = t, titulo = t.etiqueta) }; paso++ },
                            icono = ICONO_HIGIENE[t] ?: Icono.JABON, modifier = Modifier.testTag("higiene_${t.name}"),
                        )
                    }
                }
            },
            Paso("frecuencia", { (f.n.toIntOrNull() ?: 0) > 0 }) {
                PreguntaMichi("¿Cada cuánto lo hacen?", "Al marcarlo como hecho, cuento desde ese día", PoseMichi.HIGIENE)
                Frecuencias(f, viewModel, ModoFrecuencia.INTERVALOS)
            },
            Paso("proxima", { f.fecha != null }) {
                PreguntaMichi("¿Cuándo toca la próxima vez?", null, PoseMichi.AGENDA)
                FechaRapida("Próxima vez", f.fecha, hoy) { d -> viewModel.editar(CampoCuidado.FECHA) { it.copy(fecha = d) } }
            },
            resumen(f, viewModel, nota = "notas"),
        )
        ClaseCuidado.PERSONALIZADO -> listOf(
            Paso("nombre", { f.titulo.isNotBlank() }) {
                PreguntaMichi("¿Qué actividad quieres recordar?", null, PoseMichi.AGENDA)
                OpcionesGrandes(listOf("Paseo" to Icono.PASEO, "Juego" to Icono.JUEGO, "Entrenamiento" to Icono.HUESO, "Cepillar dientes" to Icono.CEPILLO_DIENTES, "Socializar" to Icono.HUELLAS), f.titulo) { nombre ->
                    // "Paseo" queda como recordatorio de paseo (su propio tipo); lo demás, actividad personalizada
                    viewModel.editar(CampoCuidado.TITULO) { it.copy(titulo = nombre, tipo = if (nombre == "Paseo") com.udenar.petcare.domain.cuidados.TipoCuidado.PASEO else com.udenar.petcare.domain.cuidados.TipoCuidado.PERSONALIZADO) }
                    paso++
                }
                CampoTexto(f.titulo, { v -> viewModel.editar(CampoCuidado.TITULO) { it.copy(titulo = v.take(80)) } }, "U otro nombre", modifier = Modifier.testTag("titulo_cuidado"))
            },
            Paso("frecuencia", { !f.modo.usaN || (f.n.toIntOrNull() ?: 0) > 0 }) {
                PreguntaMichi("¿Cada cuánto?", null, PoseMichi.AGENDA)
                Frecuencias(f, viewModel, ModoFrecuencia.entries)
            },
            Paso("hora", saltable = true) {
                PreguntaMichi("¿A qué hora?", "Si no importa la hora, salta este paso", PoseMichi.AGENDA)
                HorasRapidas(f.horas) { h -> viewModel.editar(CampoCuidado.HORAS) { it.copy(horas = h) } }
            },
            Paso("desde", { f.fecha != null }) {
                PreguntaMichi(if (f.modo == ModoFrecuencia.UNA_VEZ) "¿Qué día?" else "¿Desde cuándo?", null, PoseMichi.AGENDA)
                FechaRapida("Fecha", f.fecha, hoy) { d -> viewModel.editar(CampoCuidado.FECHA) { it.copy(fecha = d) } }
            },
            Paso("detalles", saltable = true) {
                PreguntaMichi("¿Cuánto dura?", "Duración y una foto si quieres (opcional)", PoseMichi.CELEBRA)
                CampoTexto(f.detalle["duracionMin"].orEmpty(), { viewModel.detalle("duracionMin", it.filter(Char::isDigit).take(4)) }, "Duración en minutos", teclado = androidx.compose.ui.text.input.KeyboardType.Number, modifier = Modifier.testTag("duracion_actividad"))
                com.udenar.petcare.ui.componentes.ElegirFoto(f.foto, { u -> viewModel.editar { it.copy(foto = u) } }, prefijoTag = "actividad_foto")
            },
            resumen(f, viewModel, nota = "observaciones"),
        )
    }
    val actual = pasos[paso.coerceIn(0, pasos.lastIndex)]
    val ultimo = paso >= pasos.lastIndex

    AsistentePasos(
        paso = paso.coerceIn(0, pasos.lastIndex),
        total = pasos.size,
        onAtras = { paso-- },
        onCerrar = onListo,
        onSaltar = if (actual.saltable) ({ paso++ }) else null,
        abajo = {
            if (ultimo) {
                BotonPrincipal("Guardar recordatorio", onClick = viewModel::guardar, cargando = f.guardando, modifier = Modifier.testTag("guardar_cuidado"))
            } else {
                BotonPrincipal("Continuar", habilitado = actual.valido(), onClick = { paso++ }, modifier = Modifier.testTag("continuar"))
            }
            f.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        },
    ) { p -> pasos[p.coerceIn(0, pasos.lastIndex)].contenido(this) }
}

private val HORA_COMIDA = mapOf(
    "Desayuno" to LocalTime.of(7, 0), "Almuerzo" to LocalTime.of(12, 0), "Merienda" to LocalTime.of(16, 0), "Cena" to LocalTime.of(19, 0),
)

private val ICONO_HIGIENE = mapOf(
    TipoCuidado.BANO to Icono.BANO, TipoCuidado.CORTE_PELO to Icono.TIJERAS, TipoCuidado.CEPILLADO to Icono.JABON, TipoCuidado.CORTE_UNAS to Icono.UNAS,
    TipoCuidado.LIMPIEZA_DENTAL to Icono.CEPILLO_DIENTES, TipoCuidado.LIMPIEZA_OIDOS to Icono.OIDO, TipoCuidado.LIMPIEZA_OJOS to Icono.OJO,
)

/** Último paso: el cuidado contado en una frase, con una nota opcional. */
private fun resumen(f: FormCuidado, viewModel: FormCuidadoViewModel, nota: String) = Paso("resumen", { viewModel.erroresActuales().isEmpty() }) {
    val e = runCatching { viewModel.vistaPrevia() }.getOrNull()
    PreguntaMichi("¡Así quedará!", "Revisa y guarda. Puedes volver atrás para cambiar algo", PoseMichi.AMIGOS)
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Pc.Blanco).padding(18.dp).testTag("resumen_cuidado"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.udenar.petcare.ui.componentes.BaldosaIcono(f.clase.icono, fondo = Pc.Durazno, tam = 48.dp)
            Text(f.titulo, fontFamily = Kalnia, fontSize = 24.sp, color = Pc.Tinta, modifier = Modifier.padding(start = 12.dp))
        }
        e?.let {
            Text(DescripcionCuidados.frecuencia(it.frecuencia), style = MaterialTheme.typography.titleMedium, color = Pc.Naranja)
            Text("Desde ${DescripcionCuidados.fecha(it.fechaInicio)}" + (it.fechaFin?.let { fin -> " hasta ${DescripcionCuidados.fecha(fin)}" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave)
            it.detalle["dosis"]?.let { d -> Text("Dosis: $d", style = MaterialTheme.typography.bodyMedium) }
        }
        viewModel.erroresActuales().values.firstOrNull()?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    CampoTexto(f.detalle[nota].orEmpty(), { viewModel.detalle(nota, it) }, "Nota para quien lo haga (opcional)", lineas = 2)
}

@Composable
private fun OpcionesGrandes(opciones: List<Pair<String, Icono>>, elegido: String, onElegir: (String) -> Unit) {
    opciones.chunked(2).forEach { fila ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            fila.forEach { (nombre, icono) ->
                TarjetaOpcionGrande(nombre, elegido == nombre, onClick = { onElegir(nombre) }, icono = icono, modifier = Modifier.weight(1f).testTag("opcion_$nombre"))
            }
        }
    }
}

/** Horas frecuentes con un toque, y "otra hora" con el reloj. */
@Composable
private fun HorasRapidas(horas: List<LocalTime>, onCambio: (List<LocalTime>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(6, 7, 8, 12, 14, 18, 19, 20, 22).map { LocalTime.of(it, 0) }.forEach { h ->
            ChipOpcion(DescripcionCuidados.hora(h), h in horas, onClick = { onCambio(if (h in horas) horas - h else (horas + h).sorted()) }, modifier = Modifier.testTag("hora_${h.hour}"))
        }
    }
    Text("¿Otra hora?", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
    EditorHoras(horas, onCambio)
}

/** Todos los días, entre semana, fines de semana o días a elegir. */
@Composable
private fun DiasRapidos(dias: Set<DayOfWeek>, onCambio: (Set<DayOfWeek>) -> Unit) {
    val todos = DayOfWeek.entries.toSet()
    val semana = todos - setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    val finde = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TarjetaOpcionGrande("Todos los días", dias == todos, onClick = { onCambio(todos) }, icono = Icono.CALENDARIO, modifier = Modifier.weight(1f))
        TarjetaOpcionGrande("Entre semana", dias == semana, onClick = { onCambio(semana) }, icono = Icono.MALETIN, modifier = Modifier.weight(1f))
        TarjetaOpcionGrande("Fines de semana", dias == finde, onClick = { onCambio(finde) }, icono = Icono.PLAYA, modifier = Modifier.weight(1f))
    }
    Text("O elige los días", style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave)
    SelectorDias(dias, onCambio)
}

/** Modos de frecuencia como tarjetas; los intervalos piden el número con un stepper. */
@Composable
private fun Frecuencias(f: FormCuidado, viewModel: FormCuidadoViewModel, modos: List<ModoFrecuencia>) {
    LaunchedEffect(f.modo) { if (f.modo.usaN && (f.n.toIntOrNull() ?: 0) <= 0) viewModel.editar(CampoCuidado.N) { it.copy(n = "1") } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        modos.forEach { m ->
            ChipOpcion(m.etiqueta.replace("N ", ""), f.modo == m, onClick = { viewModel.editar(CampoCuidado.N) { it.copy(modo = m, n = if (m.usaN && it.n.isBlank()) "1" else it.n) } }, modifier = Modifier.testTag("modo_${m.name}"))
        }
    }
    if (f.modo.usaN) {
        val n = f.n.toIntOrNull() ?: 1
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Stepper(n.toDouble(), { v -> viewModel.editar(CampoCuidado.N) { it.copy(n = v.toInt().toString()) } }, paso = 1.0, unidad = f.modo.unidad, minimo = 1.0, maximo = 365.0, decimales = 0)
        }
    }
    if (f.modo == ModoFrecuencia.DIAS_SEMANA) SelectorDias(f.dias, { d -> viewModel.editar(CampoCuidado.DIAS) { it.copy(dias = d) } })
}

/** Hoy, mañana (o ayer, para fechas pasadas) u otra fecha del calendario. */
@Composable
private fun FechaRapida(etiqueta: String, fecha: LocalDate?, hoy: LocalDate, permitirNinguna: Boolean = false, pasado: Boolean = false, onCambio: (LocalDate?) -> Unit) {
    val otra = if (pasado) hoy.minusDays(1) else hoy.plusDays(1)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TarjetaOpcionGrande("Hoy", fecha == hoy, onClick = { onCambio(hoy) }, icono = Icono.PIN, modifier = Modifier.weight(1f).testTag("fecha_hoy"))
        TarjetaOpcionGrande(if (pasado) "Ayer" else "Mañana", fecha == otra, onClick = { onCambio(otra) }, icono = if (pasado) Icono.RELOJ else Icono.AMANECER, modifier = Modifier.weight(1f))
    }
    SelectorFecha(
        "O elige otra fecha", fecha, onCambio,
        desde = if (pasado) null else hoy, hasta = if (pasado) hoy else null,
        permitirBorrar = permitirNinguna, textoVacio = if (permitirNinguna) "No sé / no aplica" else "Elegir",
    )
}

/** "Escanear etiqueta": foto → texto leído en el teléfono → la persona elige cada dato con un toque. */
@Composable
private fun Escanear(viewModel: FormCuidadoViewModel, f: FormCuidado) {
    val lectura by viewModel.lectura.collectAsStateWithLifecycle()
    val leyendo by viewModel.leyendo.collectAsStateWithLifecycle()
    var uri by remember { mutableStateOf<android.net.Uri?>(null) }
    val camara = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { ok ->
        if (ok) uri?.let(viewModel::escanear)
    }
    com.udenar.petcare.ui.componentes.BotonSecundario(
        if (leyendo) "Leyendo la etiqueta…" else "Escanear etiqueta o carné",
        onClick = {
            if (!leyendo) {
                val (_, u) = viewModel.archivoCamara()
                uri = u
                camara.launch(u)
            }
        },
        modifier = Modifier.testTag("escanear_etiqueta"),
        icono = Icono.CAMARA,
    )
    lectura?.let { l ->
        Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Pc.Blanco).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (l.nombres.isNotEmpty()) {
                Text("¿Cuál es el nombre?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    l.nombres.forEach { n -> ChipOpcion(n, f.titulo == n, onClick = { viewModel.editar(CampoCuidado.TITULO) { it.copy(titulo = n.take(80)) } }) }
                }
            }
            l.lote?.let { lote ->
                ChipOpcion("Lote: $lote", f.detalle["lote"] == lote, onClick = { viewModel.detalle("lote", lote) })
            }
            if (l.fechas.isNotEmpty()) {
                Text("Fechas encontradas (toca para usarlas)", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    l.fechas.forEach { d ->
                        val pasada = !d.isAfter(viewModel.hoy)
                        ChipOpcion(
                            (if (pasada) "Aplicada: " else "Próxima: ") + DescripcionCuidados.fecha(d) + " ${d.year}",
                            if (pasada) f.fechaAnterior == d else f.fecha == d,
                            onClick = { if (pasada) viewModel.editar { it.copy(fechaAnterior = d) } else viewModel.editar(CampoCuidado.FECHA) { it.copy(fecha = d) } },
                        )
                    }
                }
                Text("Revisa que cada fecha corresponda; la etiqueta también trae la de vencimiento del producto.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            }
        }
    }
}

private val TIPOS_ALIMENTO = listOf("Concentrado", "Húmedo", "Casera", "Otro")

/**
 * Tipo, marca, cantidad y premios de una comida. Solo guarda lo que escribe la persona (o lo
 * que indicó su veterinario): la app no calcula porciones ni sugiere alimentos.
 */
@Composable
internal fun DatosAlimento(detalle: Map<String, String>, onCambio: (String, String) -> Unit) {
    Text("Tipo de alimento", style = MaterialTheme.typography.titleSmall)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TIPOS_ALIMENTO.forEach { t ->
            com.udenar.petcare.ui.componentes.ChipOpcion(t, detalle["tipo_alimento"] == t, onClick = { onCambio("tipo_alimento", if (detalle["tipo_alimento"] == t) "" else t) }, modifier = Modifier.testTag("alimento_$t"))
        }
    }
    CampoTexto(detalle["marca"].orEmpty(), { onCambio("marca", it.take(80)) }, "Marca (opcional)", modifier = Modifier.testTag("marca_alimento"))
    CampoTexto(detalle["cantidad"].orEmpty(), { onCambio("cantidad", it.take(80)) }, "Cantidad (la que tú o su veterinario definieron)", modifier = Modifier.testTag("cantidad_alimento"))
    CampoTexto(detalle["premios"].orEmpty(), { onCambio("premios", it.take(200)) }, "Premios o snacks que le das (opcional)", lineas = 2, modifier = Modifier.testTag("premios"))
    Text("PetCare solo guarda lo que escribes: no calcula porciones ni sugiere alimentos.", style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
}
