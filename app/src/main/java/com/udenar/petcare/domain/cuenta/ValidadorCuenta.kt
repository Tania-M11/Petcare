package com.udenar.petcare.domain.cuenta

enum class Campo { NOMBRE, CORREO, CLAVE, PRIVACIDAD }

/** Reglas de los formularios de cuenta. Devuelve los errores por campo (vacío = todo bien). */
object ValidadorCuenta {

    const val CLAVE_MINIMA = 8
    private val CORREO = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

    fun registro(nombre: String, correo: String, clave: String, aceptoPrivacidad: Boolean): Map<Campo, String> = buildMap {
        when {
            nombre.isBlank() -> put(Campo.NOMBRE, "Escribe tu nombre")
            nombre.trim().length > 60 -> put(Campo.NOMBRE, "Máximo 60 caracteres")
        }
        correo(correo)?.let { put(Campo.CORREO, it) }
        if (clave.length < CLAVE_MINIMA) put(Campo.CLAVE, "Usa al menos $CLAVE_MINIMA caracteres")
        if (!aceptoPrivacidad) put(Campo.PRIVACIDAD, "Debes aceptar la política de privacidad")
    }

    fun inicioSesion(correo: String, clave: String): Map<Campo, String> = buildMap {
        correo(correo)?.let { put(Campo.CORREO, it) }
        if (clave.isEmpty()) put(Campo.CLAVE, "Escribe tu contraseña")
    }

    fun recuperar(correo: String): Map<Campo, String> = buildMap { correo(correo)?.let { put(Campo.CORREO, it) } }

    fun nuevaClave(clave: String, repetida: String): Map<Campo, String> = buildMap {
        when {
            clave.length < CLAVE_MINIMA -> put(Campo.CLAVE, "Usa al menos $CLAVE_MINIMA caracteres")
            clave != repetida -> put(Campo.CLAVE, "Las contraseñas no coinciden")
        }
    }

    private fun correo(correo: String): String? = when {
        correo.isBlank() -> "Escribe tu correo"
        !CORREO.matches(correo.trim()) -> "Este correo no parece válido"
        else -> null
    }
}
