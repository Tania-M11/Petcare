package com.udenar.petcare.ui.hoy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udenar.petcare.domain.cuenta.CuentaException
import com.udenar.petcare.domain.cuenta.RepositorioCuenta
import com.udenar.petcare.domain.guia.Guia
import com.udenar.petcare.domain.guia.ProgresoGuia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class HoyEstado(
    val hoy: LocalDate,
    val semana: List<LocalDate>,
    val seleccionado: LocalDate,
)

/** Estado de la pestaña Hoy: saludo, semana y Primeros pasos. */
@HiltViewModel
class HoyViewModel @Inject constructor(reloj: Clock, private val cuenta: RepositorioCuenta) : ViewModel() {

    private val hoy = LocalDate.now(reloj)
    private val lunes = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private val _estado = MutableStateFlow(
        HoyEstado(hoy = hoy, semana = List(7) { lunes.plusDays(it.toLong()) }, seleccionado = hoy),
    )
    val estado: StateFlow<HoyEstado> = _estado.asStateFlow()

    /** Primer nombre para el saludo ("Hola, Tania"). */
    val nombre: StateFlow<String> = cuenta.usuario
        .map { it?.primerNombre.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), cuenta.usuario.value?.primerNombre.orEmpty())

    private val notificacionesActivas = MutableStateFlow(false)

    /** Guía de Primeros pasos; null si no aplica (sin intenciones o ya cerrada). */
    val guia: StateFlow<ProgresoGuia?> = combine(cuenta.usuario, notificacionesActivas) { u, notif ->
        if (u == null || u.guiaRespondida != true || u.intenciones.isEmpty() || Guia.CERRADA in u.pasosOmitidos) {
            null
        } else {
            // Por ahora solo las notificaciones se detectan solas; los demás pasos se marcarán al existir su función
            val hechos = buildSet { if (notif) add(Guia.NOTIFICACIONES) }
            Guia.progreso(u.intenciones, hechos, u.pasosOmitidos)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun elegirDia(dia: LocalDate) = _estado.update { it.copy(seleccionado = dia) }

    fun notificaciones(activas: Boolean) {
        notificacionesActivas.value = activas
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
