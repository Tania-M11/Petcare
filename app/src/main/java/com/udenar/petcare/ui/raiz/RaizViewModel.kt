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
    private val veterinarios: com.udenar.petcare.domain.profesionales.RepositorioVeterinarios,
    private val modoRepo: com.udenar.petcare.domain.profesionales.RepositorioModo,
    private val prestadores: com.udenar.petcare.domain.profesionales.RepositorioPrestadores,
    private val avisos: com.udenar.petcare.data.avisos.CentroAvisos,
    private val push: com.udenar.petcare.data.avisos.PushFcm,
    private val apertura: com.udenar.petcare.data.avisos.AperturaAvisos,
    recuperacion: com.udenar.petcare.data.cuenta.RecuperacionClave,
) : ViewModel() {

    /** El enlace de recuperación abrió la app: pedir la contraseña nueva. */
    val recuperandoClave: StateFlow<Boolean> = recuperacion.pendiente

    init {
        // Un aviso tocado cambia primero al modo que corresponde (RF-29); cada estructura abre su pestaña
        viewModelScope.launch {
            apertura.pendiente.collect { a -> if (a != null && a.modo != modoRepo.modo.value) modoRepo.cambiar(a.modo) }
        }
    }

    /** Modo activo (RF-30); se recuerda en el teléfono y funciona sin conexión. */
    val modo: StateFlow<com.udenar.petcare.domain.modelo.ModoUso> = modoRepo.modo

    val sesion: StateFlow<EstadoSesion> = cuenta.estadoSesion
        .onEach {
            if (it == EstadoSesion.CON_SESION) {
                viewModelScope.launch { cuenta.cargarUsuario() }
                viewModelScope.launch {
                    // Si el perfil ya no está activo (lo ocultó en otro teléfono), vuelve al modo personal
                    runCatching { veterinarios.cargarMiPerfil() }.onSuccess { p ->
                        if (p?.activo != true && modoRepo.modo.value == com.udenar.petcare.domain.modelo.ModoUso.VETERINARIO) {
                            modoRepo.cambiar(com.udenar.petcare.domain.modelo.ModoUso.PERSONAL)
                        }
                    }
                    runCatching { prestadores.cargarMiPerfil() }.onSuccess { p ->
                        if (p?.activo != true && modoRepo.modo.value == com.udenar.petcare.domain.modelo.ModoUso.PRESTADOR) {
                            modoRepo.cambiar(com.udenar.petcare.domain.modelo.ModoUso.PERSONAL)
                        }
                    }
                }
                // Avisos de solicitudes: push (si hay Firebase), revisión periódica y los nuevos ya
                avisos.programar()
                viewModelScope.launch {
                    avisos.revisar()
                    push.registrar()
                }
                // Al abrir la app (o en un teléfono nuevo) se sube lo pendiente y se baja lo nuevo
                sync.solicitar()
                // Mientras la app esté abierta, los cambios de otros miembros llegan en vivo
                tiempoReal.iniciar(viewModelScope)
            } else if (it == EstadoSesion.SIN_SESION) {
                viewModelScope.launch { tiempoReal.detener() }
                modoRepo.cambiar(com.udenar.petcare.domain.modelo.ModoUso.PERSONAL)
                avisos.reiniciar()
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EstadoSesion.CARGANDO)

    /** Para saber si hay que preguntar "¿Qué te trae a PetCare?" (guiaRespondida == false). */
    val usuario: StateFlow<Usuario?> = cuenta.usuario
}
