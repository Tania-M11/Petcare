package com.udenar.petcare.domain.modelo

import com.udenar.petcare.domain.guia.Intencion

/** Identidad base de quien usa la app (spec 2.2). Los roles se acumulan sobre ella. */
data class Usuario(
    val id: String,
    val nombre: String,
    val correo: String,
    val intenciones: Set<Intencion> = emptySet(),
    /** null = aún no se sabe (sin conexión al entrar); false = hay que preguntarle. */
    val guiaRespondida: Boolean? = null,
    val pasosOmitidos: Set<String> = emptySet(),
) {
    val primerNombre: String get() = nombre.trim().substringBefore(' ')
}

enum class EstadoSesion { CARGANDO, SIN_SESION, CON_SESION }
