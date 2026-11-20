package com.udenar.petcare.ui.mascotas

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.udenar.petcare.domain.mascotas.CodigoMascota
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
) : ViewModel() {

    val id = estado.toRoute<RutaDetalleMascota>().id

    val mascota: StateFlow<Mascota?> = repo.observar()
        .map { lista -> lista.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
