package com.udenar.petcare.domain.guia

/**
 * Respuestas a "¿Qué te trae a PetCare?". Solo personalizan la guía de Primeros pasos:
 * no dan permisos (los perfiles profesionales se activan al completar su formulario).
 */
enum class Intencion(val titulo: String, val descripcion: String) {
    MASCOTAS("Tengo mascotas", "Organiza sus cuidados"),
    CUIDAR("Ayudo a cuidar", "La mascota de alguien más"),
    VETERINARIO("Soy veterinario/a", "Recibe pacientes"),
    SERVICIOS("Ofrezco servicios", "Paseo, baño o cuidado");

    companion object {
        fun desdeCodigos(codigos: Collection<String>): Set<Intencion> =
            codigos.mapNotNull { c -> entries.firstOrNull { it.name == c } }.toSet()
    }
}

/** Dónde se resuelve un paso dentro de la app. */
enum class DestinoPaso { MASCOTAS, SERVICIOS, PERFIL, NOTIFICACIONES }

data class PasoGuia(
    val id: String,
    val intencion: Intencion,
    val titulo: String,
    val descripcion: String,
    val destino: DestinoPaso,
    /** Parte del plan donde se habilita (null = ya funciona). */
    val disponibleEn: String?,
)

enum class EstadoPaso { PENDIENTE, HECHO, OMITIDO }

data class ProgresoGuia(
    val pasos: List<Pair<PasoGuia, EstadoPaso>>,
) {
    val resueltos: Int get() = pasos.count { it.second != EstadoPaso.PENDIENTE }
    val total: Int get() = pasos.size
    val completa: Boolean get() = pasos.isNotEmpty() && resueltos == total
}

object Guia {
    const val CERRADA = "GUIA_CERRADA"
    const val NOTIFICACIONES = "notificaciones"
    const val CREAR_MASCOTA = "crear_mascota"
    const val PRIMER_RECORDATORIO = "primer_recordatorio"
    const val CODIGO_CUIDAR = "codigo_cuidar"
    const val APROBACION = "aprobacion"
    const val PERFIL_VET = "perfil_vet"
    const val CONSULTORIO = "consultorio"
    const val CERTIFICADO = "certificado"
    const val CATEGORIAS = "categorias"
    const val PRECIOS = "precios"
    const val HORARIO = "horario"

    private val PASOS = listOf(
        PasoGuia(CREAR_MASCOTA, Intencion.MASCOTAS, "Agrega a tu primera mascota", "Su ficha, foto y especie", DestinoPaso.MASCOTAS, null),
        PasoGuia(PRIMER_RECORDATORIO, Intencion.MASCOTAS, "Crea su primer recordatorio", "Comida, medicina o baño (desde su ficha)", DestinoPaso.MASCOTAS, null),
        PasoGuia(NOTIFICACIONES, Intencion.MASCOTAS, "Activa las notificaciones", "Para que los recordatorios suenen", DestinoPaso.NOTIFICACIONES, null),
        PasoGuia(CODIGO_CUIDAR, Intencion.CUIDAR, "Escribe el código de la mascota", "Mascotas → + → Cuidar con código", DestinoPaso.MASCOTAS, null),
        PasoGuia(APROBACION, Intencion.CUIDAR, "Espera la aprobación", "El dueño confirma que la cuidas", DestinoPaso.MASCOTAS, null),
        PasoGuia("perfil_vet", Intencion.VETERINARIO, "Completa tu perfil profesional", "Especies, servicios y horario", DestinoPaso.PERFIL, null),
        PasoGuia("consultorio", Intencion.VETERINARIO, "Ubica tu consultorio en el mapa", "Para que te encuentren cerca", DestinoPaso.PERFIL, null),
        PasoGuia("certificado", Intencion.VETERINARIO, "Sube tu certificado", "Así obtienes la insignia Verificado", DestinoPaso.PERFIL, null),
        PasoGuia("categorias", Intencion.SERVICIOS, "Elige qué servicios ofreces", "Paseo, baño y peluquería o cuidado", DestinoPaso.PERFIL, null),
        PasoGuia("precios", Intencion.SERVICIOS, "Agrega tus precios", "Fijos, desde o a cotizar", DestinoPaso.PERFIL, null),
        PasoGuia("horario", Intencion.SERVICIOS, "Define tu horario", "Cuándo recibes solicitudes", DestinoPaso.PERFIL, null),
    )

    /** Pasos de las intenciones elegidas, en el orden de la lista de intenciones. */
    fun pasos(intenciones: Set<Intencion>): List<PasoGuia> =
        Intencion.entries.filter { it in intenciones }.flatMap { i -> PASOS.filter { it.intencion == i } }

    fun progreso(intenciones: Set<Intencion>, hechos: Set<String>, omitidos: Set<String>) = ProgresoGuia(
        pasos(intenciones).map { paso ->
            paso to when (paso.id) {
                in hechos -> EstadoPaso.HECHO
                in omitidos -> EstadoPaso.OMITIDO
                else -> EstadoPaso.PENDIENTE
            }
        },
    )
}

/**
 * Perfiles profesionales por completar justo después de "¿Qué te trae a PetCare?": quien elige
 * "Soy veterinario/a" u "Ofrezco servicios" pasa directo a su formulario (luego sigue en Perfil).
 */
@javax.inject.Singleton
class FormulariosPendientes @javax.inject.Inject constructor() {
    private val _cola = kotlinx.coroutines.flow.MutableStateFlow<List<Intencion>>(emptyList())
    val cola: kotlinx.coroutines.flow.StateFlow<List<Intencion>> = _cola

    fun pedir(intenciones: List<Intencion>) {
        _cola.value = intenciones.filter { it == Intencion.VETERINARIO || it == Intencion.SERVICIOS }
    }

    /** Devuelve la cola y la vacía (cada formulario se abre una sola vez). */
    fun tomar(): List<Intencion> = _cola.value.also { _cola.value = emptyList() }
}
