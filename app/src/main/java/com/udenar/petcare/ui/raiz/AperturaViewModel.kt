package com.udenar.petcare.ui.raiz

import androidx.lifecycle.ViewModel
import com.udenar.petcare.data.avisos.Apertura
import com.udenar.petcare.data.avisos.AperturaAvisos
import com.udenar.petcare.domain.modelo.ModoUso
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Aviso tocado pendiente de abrir; cada estructura (personal, veterinario, prestador) toma el suyo. */
@HiltViewModel
class AperturaViewModel @Inject constructor(private val apertura: AperturaAvisos) : ViewModel() {
    val pendiente: StateFlow<Apertura?> = apertura.pendiente

    /** Devuelve la apertura si es de [modo] y la marca como atendida. */
    fun tomar(modo: ModoUso): Apertura? = apertura.pendiente.value?.takeIf { it.modo == modo }?.also { apertura.consumir() }
}
