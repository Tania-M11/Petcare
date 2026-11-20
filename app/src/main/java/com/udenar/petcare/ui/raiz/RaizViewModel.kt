package com.udenar.petcare.ui.raiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udenar.petcare.domain.cuenta.RepositorioCuenta
import com.udenar.petcare.domain.modelo.EstadoSesion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Decide qué ve la app: carga, acceso (sin sesión) o pestañas (con sesión). */
@HiltViewModel
class RaizViewModel @Inject constructor(private val cuenta: RepositorioCuenta) : ViewModel() {

    val sesion: StateFlow<EstadoSesion> = cuenta.estadoSesion
        .onEach { if (it == EstadoSesion.CON_SESION) viewModelScope.launch { cuenta.cargarUsuario() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EstadoSesion.CARGANDO)
}
