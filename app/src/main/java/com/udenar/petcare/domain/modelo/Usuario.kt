package com.udenar.petcare.domain.modelo

/** Identidad base de quien usa la app (spec 2.2). Los roles se acumulan sobre ella. */
data class Usuario(
    val id: String,
    val nombre: String,
    val correo: String,
    val intenciones: List<String> = emptyList(),
) {
    val primerNombre: String get() = nombre.trim().substringBefore(' ')
}

enum class EstadoSesion { CARGANDO, SIN_SESION, CON_SESION }
