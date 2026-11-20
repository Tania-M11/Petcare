package com.udenar.petcare.data.cuenta

import com.udenar.petcare.domain.cuenta.CuentaException
import com.udenar.petcare.domain.cuenta.RepositorioCuenta
import com.udenar.petcare.domain.modelo.EstadoSesion
import com.udenar.petcare.domain.modelo.Usuario
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class FilaUsuario(
    val id: String,
    val nombre: String,
    val intenciones: List<String> = emptyList(),
)

/** Cuenta con Supabase Auth; los datos personales viven en la tabla `usuarios` (supabase/01_base.sql). */
@Singleton
class RepositorioCuentaSupabase @Inject constructor(
    private val supabase: SupabaseClient,
) : RepositorioCuenta {

    private val _usuario = MutableStateFlow<Usuario?>(null)
    override val usuario: StateFlow<Usuario?> = _usuario.asStateFlow()

    override val estadoSesion: Flow<EstadoSesion> = supabase.auth.sessionStatus
        .map { estado ->
            when (estado) {
                is SessionStatus.Initializing -> EstadoSesion.CARGANDO
                is SessionStatus.NotAuthenticated -> EstadoSesion.SIN_SESION
                // Autenticado, o sin poder renovar el token por falta de red: la sesión sigue en el teléfono
                else -> EstadoSesion.CON_SESION
            }
        }
        .distinctUntilChanged()

    override suspend fun registrar(nombre: String, correo: String, clave: String) = traducir {
        supabase.auth.signUpWith(Email) {
            email = correo.trim().lowercase()
            password = clave
            data = buildJsonObject {
                put("nombre", nombre.trim())
                // La base de datos rechaza el registro sin esta marca (al_crear_cuenta)
                put("acepto_privacidad", true)
            }
        }
        cargarUsuario()
    }

    override suspend fun iniciarSesion(correo: String, clave: String) = traducir {
        supabase.auth.signInWith(Email) {
            email = correo.trim().lowercase()
            password = clave
        }
        cargarUsuario()
    }

    override suspend fun cerrarSesion() {
        try {
            supabase.auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sin red no se puede avisar al servidor, pero la sesión se borra del teléfono igual
            supabase.auth.clearSession()
        }
        _usuario.value = null
    }

    override suspend fun cargarUsuario() {
        val sesion = supabase.auth.currentUserOrNull() ?: return
        // Primero lo que ya trae la sesión (funciona sin internet)...
        if (_usuario.value?.id != sesion.id) _usuario.value = sesion.comoUsuario()
        // ...luego los datos actualizados de la tabla
        try {
            val fila = supabase.from("usuarios")
                .select { filter { eq("id", sesion.id) } }
                .decodeSingleOrNull<FilaUsuario>()
            if (fila != null) {
                _usuario.value = Usuario(fila.id, fila.nombre, sesion.email.orEmpty(), fila.intenciones)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sin conexión: se queda con los datos de la sesión
        }
    }

    private fun UserInfo.comoUsuario() = Usuario(
        id = id,
        nombre = userMetadata?.get("nombre")?.jsonPrimitive?.contentOrNull ?: "",
        correo = email.orEmpty(),
    )

    /** Convierte los errores de Supabase y de red en mensajes en español. */
    private suspend fun traducir(bloque: suspend () -> Unit) {
        try {
            bloque()
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthWeakPasswordException) {
            throw CuentaException("La contraseña es muy débil. Usa al menos 8 caracteres.", e)
        } catch (e: AuthRestException) {
            throw CuentaException(
                when (e.errorCode) {
                    AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists -> "Ya existe una cuenta con ese correo."
                    AuthErrorCode.InvalidCredentials -> "Correo o contraseña incorrectos."
                    AuthErrorCode.WeakPassword -> "La contraseña es muy débil. Usa al menos 8 caracteres."
                    AuthErrorCode.ValidationFailed -> "Revisa el correo: no parece válido."
                    AuthErrorCode.EmailNotConfirmed -> "Confirma tu correo antes de entrar."
                    AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit ->
                        "Demasiados intentos. Espera un momento y vuelve a intentarlo."
                    AuthErrorCode.SignupDisabled -> "El registro está desactivado por ahora."
                    else -> "No pudimos completar la operación. Intenta de nuevo."
                },
                e,
            )
        } catch (e: HttpRequestException) {
            throw CuentaException("Sin conexión. Revisa tu internet e intenta de nuevo.", e)
        } catch (e: IOException) {
            throw CuentaException("Sin conexión. Revisa tu internet e intenta de nuevo.", e)
        } catch (e: Exception) {
            throw CuentaException("Algo salió mal. Intenta de nuevo.", e)
        }
    }
}
