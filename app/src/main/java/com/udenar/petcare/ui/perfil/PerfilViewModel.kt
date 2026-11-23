package com.udenar.petcare.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udenar.petcare.data.alarmas.PlanificadorAlarmas
import com.udenar.petcare.data.alarmas.RelojAlarmas
import com.udenar.petcare.data.remoto.EstadoServidor
import com.udenar.petcare.domain.cuenta.RepositorioCuenta
import com.udenar.petcare.domain.modelo.Usuario
import com.udenar.petcare.domain.sync.EstadoSync
import com.udenar.petcare.domain.sync.ProgramadorSync
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
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
    private val sync: ProgramadorSync,
    private val relojAlarmas: RelojAlarmas,
    private val planificador: PlanificadorAlarmas,
    private val veterinarios: com.udenar.petcare.domain.profesionales.RepositorioVeterinarios,
    private val modo: com.udenar.petcare.domain.profesionales.RepositorioModo,
    prestadoresRepo: com.udenar.petcare.domain.profesionales.RepositorioPrestadores,
) : ViewModel() {

    val perfilVet: StateFlow<com.udenar.petcare.domain.profesionales.PerfilVeterinario?> = veterinarios.miPerfil

    fun modoVeterinario() = modo.cambiar(com.udenar.petcare.domain.modelo.ModoUso.VETERINARIO)

    val perfilPres: StateFlow<com.udenar.petcare.domain.profesionales.PerfilPrestador?> = prestadoresRepo.miPerfil
    fun modoPrestador() = modo.cambiar(com.udenar.petcare.domain.modelo.ModoUso.PRESTADOR)

    /** Android 12+: permiso de alarmas a la hora exacta (se revisa al volver a la pantalla). */
    private val _horaExacta = MutableStateFlow(relojAlarmas.puedeExactas())
    val horaExacta: StateFlow<Boolean> = _horaExacta.asStateFlow()

    fun revisarHoraExacta() {
        _horaExacta.value = relojAlarmas.puedeExactas()
        viewModelScope.launch { planificador.reprogramar() }
    }

    /** Solo en la versión de desarrollo: notificación de prueba en 1 minuto. */
    fun probarRecordatorio() = planificador.probar()

    val estadoSync: StateFlow<EstadoSync?> = sync.estado
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun sincronizarAhora() = sync.solicitar()

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
