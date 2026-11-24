package com.udenar.petcare.domain.profesionales

enum class TipoSolicitud { VET, PRESTADOR }

/**
 * Fotos opcionales de una solicitud (RF-34, Fase 6.7): hasta 3, en un bucket privado que solo
 * ven el dueño y el profesional al que se envió.
 */
interface FotosSolicitudes {
    /** Sube las fotos (JPEG ya comprimidas) y las adjunta a la solicitud recién creada. */
    suspend fun adjuntar(tipo: TipoSolicitud, solicitudId: String, fotos: List<ByteArray>)

    suspend fun descargar(ruta: String): ByteArray

    companion object {
        const val MAXIMO = 3
    }
}
