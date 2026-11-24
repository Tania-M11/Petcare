package com.udenar.petcare.domain.documentos

import com.udenar.petcare.domain.modelo.Icono
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

enum class TipoDocumento(val etiqueta: String, val icono: Icono) {
    CARNE("Carné", Icono.CARNE),
    FORMULA("Fórmula", Icono.NOTA),
    EXAMEN("Examen", Icono.EXAMEN),
    INFORME("Informe", Icono.DOCUMENTO),
    OTRO("Otro", Icono.ADJUNTO),
}

/** Documento de una mascota (RF-17): imagen o PDF que sube el propietario. */
data class Documento(
    val id: String,
    val mascotaId: String,
    val nombre: String,
    val tipo: TipoDocumento,
    val fecha: LocalDate,
    val mime: String,
    val tamanoBytes: Int,
    val visitaId: String? = null,
    /** Copia en el teléfono (null si aún no se descarga). */
    val archivoLocal: String? = null,
    val subidoPor: String? = null,
    val porSubir: Boolean = false,
) {
    val esPdf: Boolean get() = mime == "application/pdf"
}

class DocumentoException(mensaje: String) : Exception(mensaje)

interface RepositorioDocumentos {
    fun observar(mascotaId: String): Flow<List<Documento>>

    /**
     * Guarda en el teléfono (imagen comprimida o PDF de hasta 5 MB) y lo sube al sincronizar.
     * [origen] es la Uri elegida por la persona.
     */
    suspend fun agregar(mascotaId: String, origen: String, nombre: String, tipo: TipoDocumento, fecha: LocalDate, visitaId: String?)
    suspend fun eliminar(id: String)

    /** Descarga el archivo si este teléfono no lo tiene; devuelve la ruta local o null sin internet. */
    suspend fun archivo(id: String): String?

    companion object {
        const val MAXIMO_BYTES = 5 * 1024 * 1024
    }
}
