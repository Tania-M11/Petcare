package com.udenar.petcare.ui.acceso

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udenar.petcare.domain.cuenta.Campo
import com.udenar.petcare.domain.cuenta.CuentaException
import com.udenar.petcare.domain.cuenta.RepositorioCuenta
import com.udenar.petcare.domain.cuenta.ValidadorCuenta
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FormularioAcceso(
    val nombre: String = "",
    val correo: String = "",
    val clave: String = "",
    val aceptoPrivacidad: Boolean = false,
    val errores: Map<Campo, String> = emptyMap(),
    val errorGeneral: String? = null,
    val cargando: Boolean = false,
)

/** Lógica común de los dos formularios: editar campos, validar y llamar al repositorio. */
abstract class FormularioViewModel : ViewModel() {
    protected val _form = MutableStateFlow(FormularioAcceso())
    val form: StateFlow<FormularioAcceso> = _form.asStateFlow()

    fun nombre(v: String) = _form.update { it.copy(nombre = v, errores = it.errores - Campo.NOMBRE, errorGeneral = null) }
    fun correo(v: String) = _form.update { it.copy(correo = v, errores = it.errores - Campo.CORREO, errorGeneral = null) }
    fun clave(v: String) = _form.update { it.copy(clave = v, errores = it.errores - Campo.CLAVE, errorGeneral = null) }
    fun privacidad(v: Boolean) = _form.update { it.copy(aceptoPrivacidad = v, errores = it.errores - Campo.PRIVACIDAD) }

    protected fun enviar(errores: Map<Campo, String>, accion: suspend () -> Unit) {
        if (_form.value.cargando) return
        if (errores.isNotEmpty()) {
            _form.update { it.copy(errores = errores) }
            return
        }
        _form.update { it.copy(cargando = true, errorGeneral = null) }
        viewModelScope.launch {
            try {
                accion()
                // Al iniciar sesión, la raíz cambia sola a las pestañas
            } catch (e: CuentaException) {
                _form.update { it.copy(errorGeneral = e.message) }
            } finally {
                _form.update { it.copy(cargando = false) }
            }
        }
    }
}

@HiltViewModel
class RegistroViewModel @Inject constructor(private val cuenta: RepositorioCuenta) : FormularioViewModel() {
    fun registrar() {
        val f = _form.value
        enviar(ValidadorCuenta.registro(f.nombre, f.correo, f.clave, f.aceptoPrivacidad)) {
            cuenta.registrar(f.nombre, f.correo, f.clave)
        }
    }
}

@HiltViewModel
class InicioSesionViewModel @Inject constructor(private val cuenta: RepositorioCuenta) : FormularioViewModel() {
    fun entrar() {
        val f = _form.value
        enviar(ValidadorCuenta.inicioSesion(f.correo, f.clave)) {
            cuenta.iniciarSesion(f.correo, f.clave)
        }
    }
}
