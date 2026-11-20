package com.udenar.petcare.ui.hoy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udenar.petcare.domain.cuenta.CuentaException
import com.udenar.petcare.domain.cuenta.RepositorioCuenta
import com.udenar.petcare.domain.cuidados.EstadoRegistro
import com.udenar.petcare.domain.cuidados.MotorCuidados
import com.udenar.petcare.domain.cuidados.RepositorioCuidados
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.cuidados.Toma
import com.udenar.petcare.domain.cuidados.Vencimiento
import com.udenar.petcare.domain.guia.Guia
import com.udenar.petcare.domain.guia.ProgresoGuia
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.domain.cuidados.resumir
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class HoyEstado(
    val hoy: LocalDate,
    val semana: List<LocalDate>,
    val seleccionado: LocalDate,
)

/** Una toma con su mascota, lista para mostrarse en Hoy. */
data class ItemAgenda(val toma: Toma, val mascota: Mascota)

enum class TipoAlerta { VACUNA_VENCIDA, VACUNA_PROXIMA, TRATAMIENTO_TERMINA }

data class Alerta(val tipo: TipoAlerta, val mascota: Mascota, val titulo: String, val fecha: LocalDate?)

/** Estado de la pestaña Hoy (RF-15): saludo, semana, guía, alertas y pendientes del día. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HoyViewModel @Inject constructor(
    private val reloj: Clock,
    private val cuenta: RepositorioCuenta,
    mascotasRepo: RepositorioMascotas,
    private val cuidados: RepositorioCuidados,
) : ViewModel() {

    private val hoy get() = LocalDate.now(reloj)

    private val _estado = MutableStateFlow(estadoPara(hoy))
    val estado: StateFlow<HoyEstado> = _estado.asStateFlow()

    private fun estadoPara(dia: LocalDate): HoyEstado {
        val lunes = dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return HoyEstado(hoy = hoy, semana = List(7) { lunes.plusDays(it.toLong()) }, seleccionado = dia)
    }

    val mascotas: StateFlow<List<Mascota>> = mascotasRepo.observar()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Mascota elegida en la fila de avatares (null = todas). */
    private val _filtro = MutableStateFlow<String?>(null)
    val filtro: StateFlow<String?> = _filtro.asStateFlow()

    /** Primer nombre para el saludo ("Hola, Tania"). */
    val nombre: StateFlow<String> = cuenta.usuario
        .map { it?.primerNombre.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), cuenta.usuario.value?.primerNombre.orEmpty())

    /** Tomas del día elegido (null = cargando). */
    val agenda: StateFlow<List<ItemAgenda>?> = combine(
        _estado.flatMapLatest { cuidados.agenda(it.seleccionado) },
        mascotas,
        _filtro,
    ) { tomas, lista, filtro ->
        val porId = lista.associateBy { it.id }
        tomas.mapNotNull { t -> porId[t.evento.mascotaId]?.let { ItemAgenda(t, it) } }
            .filter { filtro == null || it.mascota.id == filtro }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Vacunas vencidas o próximas y tratamientos por terminar (RF-05, RF-07, RF-15). */
    val alertas: StateFlow<List<Alerta>> = combine(cuidados.observarEventos(), cuidados.observarRegistros(), mascotas) { e, r, lista ->
        val porId = lista.associateBy { it.id }
        val ahora = LocalDateTime.now(reloj)
        e.mapNotNull { ev ->
            val m = porId[ev.mascotaId] ?: return@mapNotNull null
            val res = resumir(ev, r, ahora, reloj.zone)
            when {
                ev.tipo == TipoCuidado.VACUNA && res.vencimiento == Vencimiento.VENCIDO -> Alerta(TipoAlerta.VACUNA_VENCIDA, m, ev.titulo, res.proxima)
                ev.tipo == TipoCuidado.VACUNA && res.vencimiento == Vencimiento.PROXIMO -> Alerta(TipoAlerta.VACUNA_PROXIMA, m, ev.titulo, res.proxima)
                res.terminaPronto -> Alerta(TipoAlerta.TRATAMIENTO_TERMINA, m, ev.titulo, ev.fechaFin)
                else -> null
            }
        }.sortedBy { it.tipo }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Mes visible en el calendario. */
    private val _mes = MutableStateFlow(YearMonth.now(reloj))
    val mes: StateFlow<YearMonth> = _mes.asStateFlow()

    /** Para cada día del mes: cuántas tomas hay y si quedó alguna sin hacer (días pasados). */
    val resumenMes: StateFlow<Map<LocalDate, ResumenDia>> = combine(_mes, cuidados.observarEventos(), cuidados.observarRegistros(), _filtro) { mes, e, r, filtro ->
        val eventos = e.filter { filtro == null || it.mascotaId == filtro }
        (1..mes.lengthOfMonth()).associate { d ->
            val dia = mes.atDay(d)
            val tomas = MotorCuidados.agendaDelDia(dia, hoy, eventos, r, reloj.zone)
            dia to ResumenDia(total = tomas.size, pendientes = tomas.count { it.pendiente })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val notificacionesActivas = MutableStateFlow(false)

    /** Guía de Primeros pasos; null si no aplica (sin intenciones o ya cerrada). */
    val guia: StateFlow<ProgresoGuia?> = combine(cuenta.usuario, notificacionesActivas, mascotas, cuidados.observarEventos()) { u, notif, lista, eventos ->
        if (u == null || u.guiaRespondida != true || u.intenciones.isEmpty() || Guia.CERRADA in u.pasosOmitidos) {
            null
        } else {
            // Se marcan solos los pasos cuya función ya existe
            val hechos = buildSet {
                if (notif) add(Guia.NOTIFICACIONES)
                if (lista.isNotEmpty()) add(Guia.CREAR_MASCOTA)
                if (eventos.isNotEmpty()) add(Guia.PRIMER_RECORDATORIO)
            }
            Guia.progreso(u.intenciones, hechos, u.pasosOmitidos)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun elegirDia(dia: LocalDate) {
        _estado.value = estadoPara(dia)
        _mes.value = YearMonth.from(dia)
    }

    fun cambiarMes(delta: Long) = _mes.update { it.plusMonths(delta) }

    fun filtrar(mascotaId: String?) {
        _filtro.value = if (_filtro.value == mascotaId) null else mascotaId
    }

    fun notificaciones(activas: Boolean) {
        notificacionesActivas.value = activas
    }

    /** Solo se puede marcar lo de hoy o días anteriores (no tomas futuras). */
    fun sePuedeMarcar(toma: Toma) = !toma.fechaHora.toLocalDate().isAfter(hoy)

    fun marcar(toma: Toma, estado: EstadoRegistro) {
        viewModelScope.launch { cuidados.registrar(toma, estado) }
    }

    fun deshacer(toma: Toma) {
        viewModelScope.launch { cuidados.deshacer(toma) }
    }

    fun omitir(id: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                cuenta.omitirPaso(id)
            } catch (e: CuentaException) {
                onError(e.message ?: "No se pudo guardar")
            }
        }
    }
}

data class ResumenDia(val total: Int, val pendientes: Int)
