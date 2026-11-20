package com.udenar.petcare.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udenar.petcare.data.remoto.EstadoServidor
import com.udenar.petcare.domain.cuenta.RepositorioCuenta
import com.udenar.petcare.domain.modelo.Usuario
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Conexion { COMPROBANDO, CONECTADO, SIN_CONEXION }

@HiltViewModel
class PerfilViewModel @Inject constructor(
    private val servidor: EstadoServidor,
    private val cuenta: RepositorioCuenta,
) : ViewModel() {

    val usuario: StateFlow<Usuario?> = cuenta.usuario

    fun cerrarSesion() {
        viewModelScope.launch { cuenta.cerrarSesion() }
    }

    private val _conexion = MutableStateFlow(Conexion.COMPROBANDO)
    val conexion: StateFlow<Conexion> = _conexion.asStateFlow()

    /** Se llama al abrir "Acerca de". */
    fun comprobarServidor() {
        _conexion.value = Conexion.COMPROBANDO
        viewModelScope.launch {
            _conexion.value = if (servidor.conectado()) Conexion.CONECTADO else Conexion.SIN_CONEXION
        }
    }
}
