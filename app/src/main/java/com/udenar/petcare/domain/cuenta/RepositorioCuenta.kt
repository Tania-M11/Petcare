package com.udenar.petcare.domain.cuenta

import com.udenar.petcare.domain.guia.Intencion
import com.udenar.petcare.domain.modelo.EstadoSesion
import com.udenar.petcare.domain.modelo.Usuario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Error de cuenta con un mensaje ya listo para mostrar al usuario. */
class CuentaException(mensaje: String, causa: Throwable? = null) : Exception(mensaje, causa)

/** Cuenta y sesión. La implementación real usa Supabase Auth; las pruebas usan una falsa. */
interface RepositorioCuenta {
    val estadoSesion: Flow<EstadoSesion>
    val usuario: StateFlow<Usuario?>

    /** Crea la cuenta e inicia sesión. Solo se llama si el usuario aceptó la política de privacidad. */
    suspend fun registrar(nombre: String, correo: String, clave: String)
    suspend fun iniciarSesion(correo: String, clave: String)
    suspend fun cerrarSesion()

    /** Guarda la respuesta a "¿Qué te trae a PetCare?" (vacía = "Ahora no"). */
    suspend fun guardarIntenciones(intenciones: Set<Intencion>)

    /** Agrega un paso de la guía a los omitidos (o Guia.CERRADA para ocultarla). */
    suspend fun omitirPaso(id: String)

    /** Refresca los datos del usuario desde el servidor (si no hay red, conserva los de la sesión). */
    suspend fun cargarUsuario()
}
