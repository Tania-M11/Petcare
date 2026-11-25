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
class AperturaViewModel @Inject constructor(
    private val apertura: AperturaAvisos,
    private val formularios: com.udenar.petcare.domain.guia.FormulariosPendientes,
) : ViewModel() {
    val pendiente: StateFlow<Apertura?> = apertura.pendiente

    /** Formularios profesionales por abrir tras "¿Qué te trae a PetCare?". */
    val formulariosPendientes: StateFlow<List<com.udenar.petcare.domain.guia.Intencion>> = formularios.cola

    fun tomarFormularios(): List<com.udenar.petcare.domain.guia.Intencion> = formularios.tomar()

    /** Devuelve la apertura si es de [modo] y la marca como atendida. */
    fun tomar(modo: ModoUso): Apertura? = apertura.pendiente.value?.takeIf { it.modo == modo }?.also { apertura.consumir() }
}
