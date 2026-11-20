package com.udenar.petcare.domain.mascotas

import com.udenar.petcare.domain.modelo.Mascota

/** Campos que el propietario elige mostrar en el QR (RF-18). El código va apagado por defecto. */
enum class CampoQr(val etiqueta: String, val descripcion: String) {
    NOMBRE("Nombre", "Nombre de la mascota"),
    ESPECIE("Especie", "Especie"),
    RAZA("Raza", "Raza"),
    ALERGIAS("Alergias", "Alergias"),
    CONDICIONES("Condiciones", "Condiciones conocidas"),
    CONTACTO("Contacto", "Un teléfono o nombre para contactarte"),
    CODIGO("Codigo", "Código para pedir cuidarla (permite solicitarlo, no da acceso)"),
}

data class ConfigQr(
    val campos: Set<CampoQr> = POR_DEFECTO,
    val contacto: String = "",
) {
    companion object {
        val POR_DEFECTO = setOf(CampoQr.NOMBRE, CampoQr.ESPECIE, CampoQr.RAZA, CampoQr.ALERGIAS, CampoQr.CONDICIONES, CampoQr.CONTACTO)
    }
}

/** Lo que se lee de un QR de PetCare. */
data class FichaQr(val valores: Map<String, String>) {
    val nombre get() = valores["Nombre"]
    val codigo get() = valores["Codigo"]
}

/**
 * QR con texto directo (RF-18): legible con cualquier cámara, sin servidor ni internet.
 * Formato: primera línea "PETCARE v1" y luego "Etiqueta: valor" por línea.
 */
object QrMascota {
    const val ENCABEZADO = "PETCARE v1"
    const val MAXIMO = 800
    private const val MAX_CAMPO = 160

    fun texto(m: Mascota, config: ConfigQr, codigo: String?): String {
        val lineas = mutableListOf(ENCABEZADO)
        fun agregar(campo: CampoQr, valor: String?) {
            // Cada campo en una sola línea: los saltos de línea se vuelven "; "
            val v = valor?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString("; ")?.take(MAX_CAMPO)
            if (campo in config.campos && !v.isNullOrBlank()) lineas += "${campo.etiqueta}: $v"
        }
        agregar(CampoQr.NOMBRE, m.nombre)
        agregar(CampoQr.ESPECIE, m.especieOtro ?: m.especie.etiqueta)
        agregar(CampoQr.RAZA, m.raza)
        agregar(CampoQr.ALERGIAS, m.alergias)
        agregar(CampoQr.CONDICIONES, m.condiciones)
        agregar(CampoQr.CONTACTO, config.contacto)
        agregar(CampoQr.CODIGO, codigo)
        // Si se pasa del máximo, se recortan primero los campos largos del final
        var texto = lineas.joinToString("\n")
        while (texto.length > MAXIMO && lineas.size > 2) {
            lineas.removeAt(lineas.size - 1)
            texto = lineas.joinToString("\n")
        }
        return texto.take(MAXIMO)
    }

    /** null si el texto no es un QR de PetCare (se muestra el texto crudo, RF-19). */
    fun leer(texto: String): FichaQr? {
        val lineas = texto.trim().lines()
        if (lineas.firstOrNull()?.trim() != ENCABEZADO) return null
        val valores = lineas.drop(1).mapNotNull { l ->
            val i = l.indexOf(':')
            if (i <= 0) null else l.substring(0, i).trim() to l.substring(i + 1).trim()
        }.toMap()
        return FichaQr(valores)
    }
}
