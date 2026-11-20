package com.udenar.petcare.ui.guia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udenar.petcare.domain.cuenta.CuentaException
import com.udenar.petcare.domain.cuenta.RepositorioCuenta
import com.udenar.petcare.domain.guia.Intencion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EstadoIntenciones(
    val elegidas: Set<Intencion> = emptySet(),
    val guardando: Boolean = false,
    val error: String? = null,
    val listo: Boolean = false,
)

@HiltViewModel
class IntencionesViewModel @Inject constructor(private val cuenta: RepositorioCuenta) : ViewModel() {

    // Al volver desde Perfil, arranca con lo que ya había elegido
    private val _estado = MutableStateFlow(EstadoIntenciones(elegidas = cuenta.usuario.value?.intenciones.orEmpty()))
    val estado: StateFlow<EstadoIntenciones> = _estado.asStateFlow()

    fun alternar(i: Intencion) = _estado.update {
        it.copy(elegidas = if (i in it.elegidas) it.elegidas - i else it.elegidas + i, error = null)
    }

    fun continuar() = guardar(_estado.value.elegidas)

    fun ahoraNo() = guardar(emptySet())

    private fun guardar(intenciones: Set<Intencion>) {
        if (_estado.value.guardando) return
        _estado.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            try {
                cuenta.guardarIntenciones(intenciones)
                _estado.update { it.copy(listo = true) }
            } catch (e: CuentaException) {
                _estado.update { it.copy(error = e.message) }
            } finally {
                _estado.update { it.copy(guardando = false) }
            }
        }
    }
}
