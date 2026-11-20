package com.udenar.petcare.domain.modelo

import java.time.LocalDate

enum class Sexo(val etiqueta: String) { MACHO("Macho"), HEMBRA("Hembra"), DESCONOCIDO("No sé") }

/** Veterinario de la mascota en modo manual (RF-04). */
data class VeterinarioManual(
    val nombre: String = "",
    val telefono: String = "",
    val clinica: String = "",
    val direccion: String = "",
    val horario: String = "",
    val notas: String = "",
) {
    val vacio: Boolean get() = listOf(nombre, telefono, clinica, direccion, horario, notas).all { it.isBlank() }
}

enum class Tamano(val etiqueta: String) { PEQUENO("Pequeño"), MEDIANO("Mediano"), GRANDE("Grande") }

/** Ficha de una mascota (RF-02) con su salud básica (RF-03). Todo lo escribe el usuario. */
data class Mascota(
    val id: String,
    val nombre: String,
    val especie: Especie,
    val especieOtro: String? = null,
    val raza: String? = null,
    val sexo: Sexo = Sexo.DESCONOCIDO,
    val fechaNacimiento: LocalDate? = null,
    val nacimientoAproximado: Boolean = false,
    val color: String? = null,
    val tamano: Tamano? = null,
    /** Peso en gramos (la interfaz permite kg o g). */
    val pesoGramos: Int? = null,
    val esterilizado: Boolean? = null,
    val notas: String? = null,
    val alergias: String? = null,
    val condiciones: String? = null,
    val medicamentosActuales: String? = null,
    val observaciones: String? = null,
    val fotoUrl: String? = null,
    /** Archivo de la foto en el teléfono (para mostrarla sin internet). */
    val fotoLocal: String? = null,
    val veterinario: VeterinarioManual? = null,
    val propietarioId: String? = null,
    /** true mientras el cambio no ha llegado al servidor. */
    val porSubir: Boolean = false,
    /** Si el usuario actual es el dueño o la cuida (sección 5 de la spec). */
    val esPropietario: Boolean = true,
)
