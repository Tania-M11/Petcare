package com.udenar.petcare.ui.hoy

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

/** Estado de la pestaña Hoy. Por ahora: la semana actual y el día elegido en la tira. */
@HiltViewModel
class HoyViewModel @Inject constructor(reloj: Clock) : ViewModel() {

    private val hoy = LocalDate.now(reloj)
    private val lunes = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private val _estado = MutableStateFlow(
        HoyEstado(hoy = hoy, semana = List(7) { lunes.plusDays(it.toLong()) }, seleccionado = hoy),
    )
    val estado: StateFlow<HoyEstado> = _estado.asStateFlow()

    fun elegirDia(dia: LocalDate) = _estado.update { it.copy(seleccionado = dia) }
}
