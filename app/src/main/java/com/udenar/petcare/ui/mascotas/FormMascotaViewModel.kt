package com.udenar.petcare.ui.mascotas

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.udenar.petcare.data.fotos.ArchivosFotos
import com.udenar.petcare.domain.mascotas.CampoMascota
import com.udenar.petcare.domain.mascotas.FichaMascota
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.mascotas.UnidadPeso
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.domain.modelo.Sexo
import com.udenar.petcare.domain.modelo.Tamano
import com.udenar.petcare.ui.navegacion.RutaFormMascota
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class FormMascota(
    val id: String,
    val esNueva: Boolean,
    val cargando: Boolean = false,
    val nombre: String = "",
    val especie: Especie? = null,
    val especieOtro: String = "",
    val raza: String = "",
    val sexo: Sexo = Sexo.DESCONOCIDO,
    val nacimiento: LocalDate? = null,
    val aproximada: Boolean = false,
    val pesoTexto: String = "",
    val unidad: UnidadPeso = UnidadPeso.KG,
    val tamano: Tamano? = null,
    val color: String = "",
    val esterilizado: Boolean? = null,
    val notas: String = "",
    val alergias: String = "",
    val condiciones: String = "",
    val medicamentos: String = "",
    val observaciones: String = "",
    val vet: com.udenar.petcare.domain.modelo.VeterinarioManual = com.udenar.petcare.domain.modelo.VeterinarioManual(),
    /** Foto que se muestra (la actual o la recién elegida). */
    val foto: String? = null,
    val fotoNueva: String? = null,
    val procesandoFoto: Boolean = false,
    val errores: Map<CampoMascota, String> = emptyMap(),
    val mensaje: String? = null,
    val guardando: Boolean = false,
    val guardada: Boolean = false,
)

@HiltViewModel
class FormMascotaViewModel @Inject constructor(
    estado: SavedStateHandle,
    private val repo: RepositorioMascotas,
    private val archivos: ArchivosFotos,
    private val reloj: Clock,
) : ViewModel() {

    private val idExistente = estado.toRoute<RutaFormMascota>().id
    private var original: Mascota? = null

    private val _form = MutableStateFlow(FormMascota(id = idExistente ?: UUID.randomUUID().toString(), esNueva = idExistente == null, cargando = idExistente != null))
    val form: StateFlow<FormMascota> = _form.asStateFlow()

    val hoy: LocalDate get() = LocalDate.now(reloj)

    init {
        if (idExistente != null) viewModelScope.launch {
            val m = repo.obtener(idExistente)
            original = m
            if (m == null) {
                _form.update { it.copy(cargando = false, mensaje = "No encontramos esta mascota") }
            } else {
                val (peso, unidad) = FichaMascota.pesoEditable(m.pesoGramos)
                _form.update {
                    it.copy(
                        cargando = false, nombre = m.nombre, especie = m.especie, especieOtro = m.especieOtro.orEmpty(),
                        raza = m.raza.orEmpty(), sexo = m.sexo, nacimiento = m.fechaNacimiento, aproximada = m.nacimientoAproximado,
                        pesoTexto = peso, unidad = unidad, tamano = m.tamano, color = m.color.orEmpty(), esterilizado = m.esterilizado,
                        notas = m.notas.orEmpty(), alergias = m.alergias.orEmpty(), condiciones = m.condiciones.orEmpty(),
                        medicamentos = m.medicamentosActuales.orEmpty(), observaciones = m.observaciones.orEmpty(), foto = m.fotoLocal,
                        vet = m.veterinario ?: com.udenar.petcare.domain.modelo.VeterinarioManual(),
                    )
                }
            }
        }
    }

    /** Cambia un campo y limpia el error de ese campo. */
    fun editar(campo: CampoMascota? = null, cambio: (FormMascota) -> FormMascota) =
        _form.update { cambio(it).copy(errores = if (campo == null) it.errores else it.errores - campo, mensaje = null) }

    fun elegirFoto(uri: Uri) {
        _form.update { it.copy(procesandoFoto = true) }
        viewModelScope.launch {
            try {
                val archivo = archivos.comprimir(uri, _form.value.id)
                _form.update { it.copy(foto = archivo.absolutePath, fotoNueva = archivo.absolutePath, procesandoFoto = false) }
            } catch (e: Exception) {
                _form.update { it.copy(procesandoFoto = false, mensaje = "No pudimos usar esa imagen. Prueba con otra.") }
            }
        }
    }

    fun guardar() {
        val f = _form.value
        if (f.guardando || f.procesandoFoto) return
        val errores = FichaMascota.validar(f.nombre, f.especie, f.especieOtro, f.nacimiento, f.pesoTexto, f.unidad, hoy)
        if (errores.isNotEmpty()) {
            _form.update { it.copy(errores = errores, mensaje = "Revisa los campos marcados") }
            return
        }
        _form.update { it.copy(guardando = true) }
        viewModelScope.launch {
            val base = original ?: Mascota(id = f.id, nombre = "", especie = f.especie!!)
            repo.guardar(
                base.copy(
                    nombre = f.nombre, especie = f.especie!!, especieOtro = f.especieOtro.takeIf { f.especie == Especie.OTRO },
                    raza = f.raza, sexo = f.sexo, fechaNacimiento = f.nacimiento, nacimientoAproximado = f.nacimiento != null && f.aproximada,
                    pesoGramos = FichaMascota.gramos(f.pesoTexto, f.unidad), tamano = f.tamano, color = f.color,
                    esterilizado = f.esterilizado, notas = f.notas, alergias = f.alergias, condiciones = f.condiciones,
                    medicamentosActuales = f.medicamentos, observaciones = f.observaciones, veterinario = f.vet,
                ),
                fotoNueva = f.fotoNueva,
            )
            _form.update { it.copy(guardando = false, guardada = true) }
        }
    }
}
