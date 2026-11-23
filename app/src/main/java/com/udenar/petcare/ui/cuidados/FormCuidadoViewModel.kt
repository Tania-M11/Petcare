package com.udenar.petcare.ui.cuidados

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.udenar.petcare.domain.cuidados.Ancla
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.ModoFrecuencia
import com.udenar.petcare.domain.cuidados.MotorCuidados
import com.udenar.petcare.domain.cuidados.NOMBRES_COMIDA
import com.udenar.petcare.domain.cuidados.RepositorioCuidados
import com.udenar.petcare.domain.cuidados.TIPOS_HIGIENE
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.ui.navegacion.RutaFormCuidado
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

/** Familias de formulario: cada una muestra solo los campos que le corresponden. */
enum class ClaseCuidado(val titulo: String, val emoji: String) {
    COMIDA("Comida", "🍽️"),
    MEDICAMENTO("Medicamento", "💊"),
    VACUNA("Vacuna", "💉"),
    DESPARASITACION("Desparasitación", "🛡️"),
    HIGIENE("Higiene", "🛁"),
    PERSONALIZADO("Actividad personalizada", "⭐");

    companion object {
        fun de(tipo: TipoCuidado): ClaseCuidado = when (tipo) {
            TipoCuidado.COMIDA -> COMIDA
            TipoCuidado.MEDICAMENTO -> MEDICAMENTO
            TipoCuidado.VACUNA -> VACUNA
            TipoCuidado.DESPARASITACION_INTERNA, TipoCuidado.DESPARASITACION_EXTERNA -> DESPARASITACION
            in TIPOS_HIGIENE -> HIGIENE
            else -> PERSONALIZADO
        }
    }
}

enum class CampoCuidado { TITULO, HORAS, DIAS, N, FECHA, FECHA_FIN }

data class FormCuidado(
    val id: String,
    val mascotaId: String,
    val clase: ClaseCuidado,
    val esNuevo: Boolean,
    val cargando: Boolean = false,
    val tipo: TipoCuidado,
    val titulo: String = "",
    val modo: ModoFrecuencia = ModoFrecuencia.DIARIO,
    val n: String = "1",
    val dias: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val horas: List<LocalTime> = emptyList(),
    /** Inicio, próxima fecha o fecha de la vacuna, según la clase. */
    val fecha: LocalDate? = null,
    val fechaFin: LocalDate? = null,
    /** Vacuna: cuándo se aplicó. Desparasitación: última aplicación. */
    val fechaAnterior: LocalDate? = null,
    val avisoDias: String = MotorCuidados.AVISO_POR_DEFECTO.toString(),
    val detalle: Map<String, String> = emptyMap(),
    val errores: Map<CampoCuidado, String> = emptyMap(),
    val mensaje: String? = null,
    val guardando: Boolean = false,
    val listo: Boolean = false,
)

@HiltViewModel
class FormCuidadoViewModel @Inject constructor(
    estado: SavedStateHandle,
    private val repo: RepositorioCuidados,
    private val reloj: Clock,
) : ViewModel() {

    private val ruta = estado.toRoute<RutaFormCuidado>()
    val hoy: LocalDate get() = LocalDate.now(reloj)
    private var original: EventoCuidado? = null

    private val _form = MutableStateFlow(inicial())
    val form: StateFlow<FormCuidado> = _form.asStateFlow()

    private fun inicial(): FormCuidado {
        val clase = runCatching { ClaseCuidado.valueOf(ruta.clase) }.getOrDefault(ClaseCuidado.PERSONALIZADO)
        val base = FormCuidado(
            id = ruta.id ?: UUID.randomUUID().toString(), mascotaId = ruta.mascotaId, clase = clase,
            esNuevo = ruta.id == null, cargando = ruta.id != null, tipo = TipoCuidado.PERSONALIZADO,
        )
        return when (clase) {
            ClaseCuidado.COMIDA -> base.copy(tipo = TipoCuidado.COMIDA, modo = ModoFrecuencia.DIAS_SEMANA, horas = listOf(LocalTime.of(7, 0)))
            ClaseCuidado.MEDICAMENTO -> base.copy(tipo = TipoCuidado.MEDICAMENTO, fecha = hoy)
            ClaseCuidado.VACUNA -> base.copy(tipo = TipoCuidado.VACUNA, modo = ModoFrecuencia.UNA_VEZ)
            ClaseCuidado.DESPARASITACION -> base.copy(tipo = TipoCuidado.DESPARASITACION_INTERNA, modo = ModoFrecuencia.CADA_N_MESES, n = "")
            ClaseCuidado.HIGIENE -> base.copy(tipo = TipoCuidado.BANO, titulo = TipoCuidado.BANO.etiqueta, modo = ModoFrecuencia.CADA_N_DIAS, n = "", fecha = hoy)
            ClaseCuidado.PERSONALIZADO -> base.copy(fecha = hoy)
        }
    }

    init {
        ruta.id?.let { id ->
            viewModelScope.launch {
                val e = repo.observarEventos().first().firstOrNull { it.id == id }
                original = e
                if (e == null) {
                    _form.update { it.copy(cargando = false, mensaje = "No encontramos este cuidado") }
                    return@launch
                }
                _form.update {
                    it.copy(
                        cargando = false, tipo = e.tipo, titulo = e.titulo, modo = ModoFrecuencia.desde(e.frecuencia),
                        n = e.frecuencia.n.toString(), dias = e.frecuencia.diasSemana.ifEmpty { DayOfWeek.entries.toSet() },
                        horas = e.frecuencia.horas, fecha = e.fechaInicio, fechaFin = e.fechaFin,
                        fechaAnterior = (e.detalle["aplicadaEl"] ?: e.detalle["ultimaAplicacion"])?.let { f -> runCatching { LocalDate.parse(f) }.getOrNull() },
                        avisoDias = (e.avisoAnticipadoDias ?: MotorCuidados.AVISO_POR_DEFECTO).toString(), detalle = e.detalle,
                    )
                }
            }
        }
    }

    fun editar(campo: CampoCuidado? = null, cambio: (FormCuidado) -> FormCuidado) =
        _form.update { cambio(it).copy(errores = if (campo == null) it.errores else it.errores - campo, mensaje = null) }

    fun detalle(clave: String, valor: String) = editar { it.copy(detalle = it.detalle + (clave to valor)) }

    /** Nombres sugeridos según la clase (solo nombres; nunca cantidades). */
    val sugerencias: List<String> get() = if (_form.value.clase == ClaseCuidado.COMIDA) NOMBRES_COMIDA else emptyList()

    fun guardar() {
        val f = _form.value
        if (f.guardando) return
        val errores = validar(f)
        if (errores.isNotEmpty()) {
            _form.update { it.copy(errores = errores, mensaje = "Revisa los campos marcados") }
            return
        }
        val evento = construir(f)
        MotorCuidados.validar(evento.frecuencia, evento.fechaInicio, evento.fechaFin)?.let { msg ->
            _form.update { it.copy(mensaje = msg) }
            return
        }
        _form.update { it.copy(guardando = true) }
        viewModelScope.launch {
            repo.guardarEvento(evento)
            _form.update { it.copy(guardando = false, listo = true) }
        }
    }

    fun eliminar() {
        viewModelScope.launch {
            repo.eliminarEvento(_form.value.id)
            _form.update { it.copy(listo = true) }
        }
    }

    private fun n(f: FormCuidado) = f.n.trim().toIntOrNull()

    private fun validar(f: FormCuidado): Map<CampoCuidado, String> = buildMap {
        if (f.titulo.isBlank()) put(CampoCuidado.TITULO, if (f.clase == ClaseCuidado.DESPARASITACION) "Escribe el producto" else "Escribe un nombre")
        if (f.titulo.trim().length > 80) put(CampoCuidado.TITULO, "Máximo 80 caracteres")
        if (f.modo.usaN && (n(f) == null || n(f)!! !in 1..365)) put(CampoCuidado.N, "Entre 1 y 365")
        if (f.modo == ModoFrecuencia.DIAS_SEMANA && f.dias.isEmpty()) put(CampoCuidado.DIAS, "Elige al menos un día")
        when (f.clase) {
            ClaseCuidado.COMIDA, ClaseCuidado.MEDICAMENTO -> if (f.horas.isEmpty()) put(CampoCuidado.HORAS, "Agrega al menos una hora")
            ClaseCuidado.VACUNA -> if (f.fecha == null) put(CampoCuidado.FECHA, "Elige la próxima fecha")
            else -> Unit
        }
        if (f.fechaFin != null && f.fecha != null && f.fechaFin.isBefore(f.fecha)) put(CampoCuidado.FECHA_FIN, "No puede ser antes del inicio")
    }

    /** Arma el cuidado con lo que escribió el usuario (la app no completa ni sugiere valores). */
    /** Cómo quedará el cuidado (para el resumen del recorrido guiado). */
    fun vistaPrevia(): EventoCuidado = construir(_form.value)

    /** Errores del formulario sin marcarlos (para habilitar "Guardar" en el recorrido). */
    fun erroresActuales(): Map<CampoCuidado, String> = validar(_form.value)

    private fun construir(f: FormCuidado): EventoCuidado {
        val n = n(f) ?: 1
        val detalle = f.detalle.toMutableMap()
        var fechaInicio = f.fecha ?: hoy
        var ancla = Ancla.CALENDARIO
        var horas = f.horas
        var modo = f.modo
        var aviso: Int? = null
        when (f.clase) {
            ClaseCuidado.VACUNA -> {
                modo = ModoFrecuencia.UNA_VEZ
                horas = emptyList()
                aviso = f.avisoDias.trim().toIntOrNull()?.coerceIn(0, 90)
                f.fechaAnterior?.let { detalle["aplicadaEl"] = it.toString() } ?: detalle.remove("aplicadaEl")
            }
            ClaseCuidado.DESPARASITACION -> {
                ancla = Ancla.ULTIMA_VEZ
                horas = emptyList()
                // Próxima = última aplicación + intervalo (RF-06); sin última aplicación, hoy
                fechaInicio = f.fechaAnterior?.let { ultima ->
                    when (modo) {
                        ModoFrecuencia.CADA_N_DIAS -> ultima.plusDays(n.toLong())
                        ModoFrecuencia.CADA_N_SEMANAS -> ultima.plusWeeks(n.toLong())
                        else -> ultima.plusMonths(n.toLong())
                    }
                } ?: hoy
                f.fechaAnterior?.let { detalle["ultimaAplicacion"] = it.toString() } ?: detalle.remove("ultimaAplicacion")
                detalle["producto"] = f.titulo.trim()
            }
            ClaseCuidado.HIGIENE -> {
                ancla = Ancla.ULTIMA_VEZ
                horas = emptyList()
            }
            else -> Unit
        }
        val base = original
        return EventoCuidado(
            id = f.id,
            mascotaId = f.mascotaId,
            tipo = f.tipo,
            titulo = f.titulo.trim(),
            frecuencia = modo.aFrecuencia(n, f.dias, horas),
            ancla = ancla,
            fechaInicio = fechaInicio,
            fechaFin = f.fechaFin.takeIf { f.clase == ClaseCuidado.MEDICAMENTO || f.clase == ClaseCuidado.PERSONALIZADO },
            avisoAnticipadoDias = aviso,
            detalle = detalle.filterValues { it.isNotBlank() },
            origen = base?.origen ?: com.udenar.petcare.domain.cuidados.Origen.MANUAL,
            origenRef = base?.origenRef,
            creadoPor = base?.creadoPor,
            oculto = base?.oculto ?: false,
        )
    }
}
