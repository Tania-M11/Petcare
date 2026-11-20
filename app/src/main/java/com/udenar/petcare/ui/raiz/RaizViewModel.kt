package com.udenar.petcare.ui.raiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udenar.petcare.domain.cuenta.RepositorioCuenta
import com.udenar.petcare.domain.modelo.EstadoSesion
import com.udenar.petcare.domain.modelo.Usuario
import com.udenar.petcare.domain.sync.ProgramadorSync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Decide qué ve la app: carga, acceso (sin sesión) o pestañas (con sesión). */
@HiltViewModel
class RaizViewModel @Inject constructor(
    private val cuenta: RepositorioCuenta,
    private val sync: ProgramadorSync,
    private val tiempoReal: com.udenar.petcare.data.sync.TiempoReal,
) : ViewModel() {

    val sesion: StateFlow<EstadoSesion> = cuenta.estadoSesion
        .onEach {
            if (it == EstadoSesion.CON_SESION) {
                viewModelScope.launch { cuenta.cargarUsuario() }
                // Al abrir la app (o en un teléfono nuevo) se sube lo pendiente y se baja lo nuevo
                sync.solicitar()
                // Mientras la app esté abierta, los cambios de otros miembros llegan en vivo
                tiempoReal.iniciar(viewModelScope)
            } else if (it == EstadoSesion.SIN_SESION) {
                viewModelScope.launch { tiempoReal.detener() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EstadoSesion.CARGANDO)

    /** Para saber si hay que preguntar "¿Qué te trae a PetCare?" (guiaRespondida == false). */
    val usuario: StateFlow<Usuario?> = cuenta.usuario
}
