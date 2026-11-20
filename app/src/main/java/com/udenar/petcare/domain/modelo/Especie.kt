package com.udenar.petcare.domain.modelo

/** Catálogo cerrado de especies (spec, sección 4). OTRO se acompaña de un texto libre. */
enum class Especie(val etiqueta: String) {
    PERRO("Perro"),
    GATO("Gato"),
    AVE("Ave"),
    CONEJO("Conejo"),
    ROEDOR("Roedor"),
    REPTIL("Reptil"),
    PEZ("Pez"),
    EQUINO("Equino"),
    OTRO("Otro");

    companion object {
        /** Convierte el valor guardado en la base de datos; lo desconocido se trata como OTRO. */
        fun desdeCodigo(codigo: String?): Especie =
            entries.firstOrNull { it.name == codigo } ?: OTRO
    }
}
