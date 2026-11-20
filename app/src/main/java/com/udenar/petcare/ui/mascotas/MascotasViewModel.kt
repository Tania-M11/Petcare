package com.udenar.petcare.ui.mascotas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udenar.petcare.domain.mascotas.FichaMascota
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Mascota
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** null = cargando; lista vacía = estado vacío (RNF-10). */
@HiltViewModel
class MascotasViewModel @Inject constructor(
    private val repo: RepositorioMascotas,
    private val reloj: Clock,
) : ViewModel() {

    val mascotas: StateFlow<List<Mascota>?> = repo.observar()
        .map { it as List<Mascota>? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun descripcion(m: Mascota): String = describir(m, LocalDate.now(reloj))

    /** Descarga fotos que este teléfono aún no tiene (otro dispositivo las subió). */
    fun asegurarFoto(m: Mascota) {
        if (m.fotoLocal == null && m.fotoUrl != null) viewModelScope.launch { repo.asegurarFoto(m.id) }
    }
}

/** "Gato · 2 años · 4,2 kg" */
fun describir(m: Mascota, hoy: LocalDate): String = listOfNotNull(
    m.especieOtro?.takeIf { m.especie == com.udenar.petcare.domain.modelo.Especie.OTRO } ?: m.especie.etiqueta,
    m.fechaNacimiento?.let { FichaMascota.edad(it, m.nacimientoAproximado, hoy) }?.takeIf { it.isNotEmpty() },
    m.pesoGramos?.let { FichaMascota.peso(it) },
).joinToString(" · ")
