package com.udenar.petcare

import com.udenar.petcare.di.ModuloDatos
import com.udenar.petcare.domain.cuenta.CuentaException
import com.udenar.petcare.domain.cuenta.RepositorioCuenta
import com.udenar.petcare.domain.guia.Intencion
import com.udenar.petcare.domain.modelo.EstadoSesion
import com.udenar.petcare.domain.modelo.Usuario
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cuenta en memoria para las pruebas: no crea usuarios reales en Supabase.
 * Empieza con sesión (Ana); cada prueba puede cambiar el estado.
 */
@Singleton
class CuentaFalsa @Inject constructor() : RepositorioCuenta {
    val sesion = MutableStateFlow(EstadoSesion.CON_SESION)
    override val estadoSesion = sesion
    override val usuario: MutableStateFlow<Usuario?> = MutableStateFlow(ANA)

    /** Correos que ya "existen" en el servidor falso. */
    val existentes = mutableSetOf("ana@petcare.test")

    /** true = el servidor pide confirmar el correo antes de entrar. */
    var exigeConfirmacion = false
    val correosRecuperacion = mutableListOf<String>()
    var claveCambiada: String? = null
    var cuentaEliminada = false

    override suspend fun registrar(nombre: String, correo: String, clave: String): Boolean {
        if (correo.trim().lowercase() in existentes) throw CuentaException("Ya existe una cuenta con ese correo.")
        if (exigeConfirmacion) return false
        usuario.value = Usuario("2", nombre.trim(), correo.trim().lowercase(), guiaRespondida = false)
        sesion.value = EstadoSesion.CON_SESION
        return true
    }

    override suspend fun recuperarClave(correo: String) {
        correosRecuperacion += correo.trim().lowercase()
    }

    override suspend fun cambiarClave(nueva: String) {
        claveCambiada = nueva
    }

    override suspend fun eliminarCuenta() {
        cuentaEliminada = true
        usuario.value = null
        sesion.value = EstadoSesion.SIN_SESION
    }

    override suspend fun iniciarSesion(correo: String, clave: String) {
        if (correo.trim().lowercase() != ANA.correo || clave != "clave1234") throw CuentaException("Correo o contraseña incorrectos.")
        usuario.value = ANA
        sesion.value = EstadoSesion.CON_SESION
    }

    override suspend fun cerrarSesion() {
        usuario.value = null
        sesion.value = EstadoSesion.SIN_SESION
    }

    override suspend fun cargarUsuario() = Unit

    override suspend fun guardarIntenciones(intenciones: Set<Intencion>) {
        usuario.value = usuario.value?.copy(intenciones = intenciones, guiaRespondida = true)
    }

    override suspend fun omitirPaso(id: String) {
        usuario.value = usuario.value?.copy(pasosOmitidos = usuario.value!!.pasosOmitidos + id)
    }

    /** Ana con sesión y la guía de dueña de mascotas por responder o respondida. */
    fun conAna(intenciones: Set<Intencion> = emptySet(), respondida: Boolean = true) {
        usuario.value = ANA.copy(intenciones = intenciones, guiaRespondida = respondida)
        sesion.value = EstadoSesion.CON_SESION
    }

    fun sinSesion() {
        usuario.value = null
        sesion.value = EstadoSesion.SIN_SESION
    }

    companion object {
        val ANA = Usuario("1", "Ana María", "ana@petcare.test", guiaRespondida = true)
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ModuloDatos::class])
abstract class ModuloDatosPrueba {
    @Binds
    abstract fun cuenta(falsa: CuentaFalsa): RepositorioCuenta
}
