package com.udenar.petcare.ui.mascotas

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.udenar.petcare.domain.compartir.CompartirException
import com.udenar.petcare.domain.compartir.Miembro
import com.udenar.petcare.domain.compartir.RepositorioCompartir
import com.udenar.petcare.domain.cuidados.AreaCuidado
import com.udenar.petcare.domain.cuidados.DescripcionCuidados
import com.udenar.petcare.domain.cuidados.EstadoCuidado
import com.udenar.petcare.domain.cuidados.EstadoRegistro
import com.udenar.petcare.domain.cuidados.NivelCuidado
import com.udenar.petcare.domain.cuidados.RepositorioCuidados
import com.udenar.petcare.domain.mascotas.FichaMascota
import com.udenar.petcare.domain.salud.RegistroPeso
import com.udenar.petcare.domain.salud.RepositorioSalud
import com.udenar.petcare.domain.salud.Visita
import com.udenar.petcare.ui.cuidados.emoji
import com.udenar.petcare.domain.mascotas.CodigoMascota
import com.udenar.petcare.domain.cuidados.ResumenCuidado
import com.udenar.petcare.domain.cuidados.resumir
import kotlinx.coroutines.flow.combine
import java.time.LocalDateTime
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.ui.navegacion.RutaDetalleMascota
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

sealed interface EstadoCodigo {
    data object Cargando : EstadoCodigo
    data class Listo(val codigo: CodigoMascota) : EstadoCodigo
    /** La mascota aún no llega al servidor (se creó sin internet). */
    data object SinSincronizar : EstadoCodigo
    data object SinConexion : EstadoCodigo
}

/** Ficha de una mascota. [mascota]: null = cargando; Eliminada = ya no existe. */
@HiltViewModel
class DetalleMascotaViewModel @Inject constructor(
    estado: SavedStateHandle,
    private val repo: RepositorioMascotas,
    private val reloj: Clock,
    cuidados: RepositorioCuidados,
    private val salud: RepositorioSalud,
    private val compartir: RepositorioCompartir,
) : ViewModel() {

    /** Personas que cuidan esta mascota (solo la ve el dueño; requiere internet). */
    private val _miembros = MutableStateFlow<List<Miembro>?>(null)
    val miembros: StateFlow<List<Miembro>?> = _miembros.asStateFlow()

    fun cargarMiembros() = viewModelScope.launch {
        _miembros.value = runCatching { compartir.miembros(id) }.getOrNull()
    }

    fun quitar(m: Miembro, avisar: (String) -> Unit) = viewModelScope.launch {
        try {
            compartir.quitar(id, m.usuarioId)
            avisar("${m.nombre.substringBefore(' ')} ya no cuida a esta mascota")
            cargarMiembros()
        } catch (e: CompartirException) {
            avisar(e.message ?: "No se pudo completar")
        }
    }

    fun salir(onListo: () -> Unit, avisar: (String) -> Unit) = viewModelScope.launch {
        try {
            compartir.salir(id)
            onListo()
        } catch (e: CompartirException) {
            avisar(e.message ?: "No se pudo completar")
        }
    }

    fun regenerarCodigo(avisar: (String) -> Unit) = viewModelScope.launch {
        try {
            compartir.regenerarCodigo(id)
            avisar("Código nuevo: el anterior ya no sirve")
            cargarCodigo()
        } catch (e: CompartirException) {
            avisar(e.message ?: "No se pudo completar")
        }
    }

    fun desactivarCodigo(avisar: (String) -> Unit) = viewModelScope.launch {
        try {
            compartir.desactivarCodigo(id)
            avisar("Código desactivado. Nadie podrá pedir cuidarla hasta que generes uno nuevo.")
            cargarCodigo()
        } catch (e: CompartirException) {
            avisar(e.message ?: "No se pudo completar")
        }
    }

    val id = estado.toRoute<RutaDetalleMascota>().id

    val mascota: StateFlow<Mascota?> = repo.observar()
        .map { lista -> lista.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Cuidados de esta mascota con su próxima fecha y estado. */
    val cuidados: StateFlow<List<ResumenCuidado>> = combine(cuidados.observarEventos(), cuidados.observarRegistros()) { e, r ->
        val ahora = LocalDateTime.now(reloj)
        e.filter { it.mascotaId == id }.map { resumir(it, r, ahora, reloj.zone) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pesos: StateFlow<List<RegistroPeso>> = salud.observarPesos(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val visitas: StateFlow<List<Visita>> = salud.observarVisitas(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Estado de cuidado (RF-16) con la agenda de hoy de esta mascota. */
    val estadoCuidado: StateFlow<Map<AreaCuidado, NivelCuidado>> = combine(this.cuidados, cuidados.agenda(LocalDate.now(reloj))) { res, tomas ->
        EstadoCuidado.calcular(res, tomas.filter { it.evento.mascotaId == id }, LocalDateTime.now(reloj))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Línea de tiempo: cuidados hechos u omitidos, pesos y visitas (RF-15). */
    val historial: StateFlow<List<ItemHistorial>> = combine(cuidados.observarEventos(), cuidados.observarRegistros(), pesos, visitas) { ev, reg, ps, vs ->
        val porId = ev.associateBy { it.id }
        val zona = reloj.zone
        buildList {
            reg.filter { it.mascotaId == id && it.estado != EstadoRegistro.POSPUESTA }.forEach { r ->
                val e = porId[r.eventoId]
                val quien = r.registradoPor?.nombre?.substringBefore(' ')
                val verbo = if (r.estado == EstadoRegistro.OMITIDA) "Omitido" else "Hecho"
                add(
                    ItemHistorial(
                        r.registradoEn.atZone(zona).toLocalDateTime(), CategoriaHistorial.CUIDADOS, e?.tipo?.emoji ?: "✔️",
                        e?.titulo ?: "Cuidado eliminado",
                        "$verbo${quien?.let { " por $it" } ?: ""} · ${DescripcionCuidados.hora(r.registradoEn.atZone(zona).toLocalTime())}",
                    ),
                )
            }
            ps.forEach { p ->
                add(ItemHistorial(p.fecha.atTime(12, 0), CategoriaHistorial.PESO, "⚖️", FichaMascota.peso(p.gramos), "Pesaje${p.registradoPor?.nombre?.let { " · ${it.substringBefore(' ')}" } ?: ""}"))
            }
            vs.forEach { v ->
                add(ItemHistorial(v.fecha.atTime(12, 0), CategoriaHistorial.VISITAS, "🩺", v.motivo, listOfNotNull("Visita veterinaria", v.veterinario, v.clinica).joinToString(" · ")))
            }
        }.sortedByDescending { it.instante }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun registrarPeso(fecha: LocalDate, gramos: Int) = viewModelScope.launch { salud.registrarPeso(id, fecha, gramos) }

    fun eliminarPeso(pesoId: String) = viewModelScope.launch { salud.eliminarPeso(pesoId) }

    private val _codigo = MutableStateFlow<EstadoCodigo>(EstadoCodigo.Cargando)
    val codigo: StateFlow<EstadoCodigo> = _codigo.asStateFlow()

    val hoy: LocalDate get() = LocalDate.now(reloj)

    init {
        cargarCodigo()
    }

    fun cargarCodigo() {
        _codigo.value = EstadoCodigo.Cargando
        viewModelScope.launch {
            _codigo.value = try {
                repo.codigo(id)?.let { EstadoCodigo.Listo(it) } ?: EstadoCodigo.SinSincronizar
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                EstadoCodigo.SinConexion
            }
        }
    }

    fun eliminar(onListo: () -> Unit) {
        viewModelScope.launch {
            repo.eliminar(id)
            onListo()
        }
    }
}
