package com.udenar.petcare.domain.profesionales

import com.udenar.petcare.domain.modelo.Icono
import com.udenar.petcare.domain.modelo.Especie
import kotlinx.coroutines.flow.StateFlow
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

enum class TipoPrestador(val etiqueta: String) { PERSONA("Persona"), NEGOCIO("Negocio") }

enum class ModalidadPrestador(val etiqueta: String) { DOMICILIO("A domicilio"), ESTABLECIMIENTO("En establecimiento") }

/** Categoría del catálogo (RF-32). Vive en la base de datos: se agregan sin cambiar la app. */
data class Categoria(val id: String, val nombre: String, val icono: String, val orden: Int = 0) {
    /** Ícono 3D de la categoría (el emoji guardado en la base queda solo como respaldo). */
    val icono3d: Icono get() = when (id) {
        "PASEADOR" -> Icono.PASEO
        "BANO_PELUQUERIA" -> Icono.TIJERAS
        "CUIDADO_TEMPORAL" -> Icono.CASA
        else -> Icono.HUELLAS
    }
}

enum class ModoPrecio(val etiqueta: String) { FIJO("Precio fijo"), DESDE("Desde"), COTIZACION("A cotizar") }

enum class UnidadPrecio(val etiqueta: String) {
    POR_SERVICIO("por servicio"), POR_HORA("por hora"), POR_PASEO("por paseo"), POR_DIA("por día"), POR_NOCHE("por noche"),
}

/** Servicio dentro de una categoría. Los precios son orientativos; el pago se acuerda fuera de PetCare. */
data class ServicioPrestador(
    val id: String,
    val categoriaId: String,
    val nombre: String,
    val descripcion: String? = null,
    val modo: ModoPrecio = ModoPrecio.COTIZACION,
    val montoCop: Int? = null,
    val unidad: UnidadPrecio? = null,
    val especies: Set<Especie> = emptySet(),
)

data class PerfilPrestador(
    val id: String,
    val nombre: String,
    val tipo: TipoPrestador = TipoPrestador.PERSONA,
    val descripcion: String,
    val telefono: String,
    val whatsapp: String? = null,
    val correo: String? = null,
    val ciudad: String,
    val direccion: String? = null,
    val zona: String? = null,
    val radioKm: Int? = null,
    val lat: Double,
    val lon: Double,
    val modalidades: Set<ModalidadPrestador>,
    val especies: Set<Especie>,
    /** La primera es la principal. */
    val categorias: List<String>,
    val servicios: List<ServicioPrestador> = emptyList(),
    val horario: Map<DayOfWeek, String> = emptyMap(),
    val noDisponible: List<LocalDate> = emptyList(),
    val recibiendo: Boolean = true,
    /** URL públicas de la galería de trabajos (máximo 12). */
    val galeria: List<String> = emptyList(),
    val activo: Boolean = true,
    val ratingSuma: Int = 0,
    val ratingCantidad: Int = 0,
    val distanciaKm: Double? = null,
) {
    val categoriaPrincipal: String? get() = categorias.firstOrNull()
    val promedio: Double? get() = if (ratingCantidad == 0) null else ratingSuma.toDouble() / ratingCantidad

    /** Texto de cobertura: zona, radio y/o dirección. */
    val cobertura: String
        get() = listOfNotNull(zona?.takeIf { it.isNotBlank() }, radioKm?.let { "hasta $it km" }, direccion?.takeIf { it.isNotBlank() }).joinToString(" · ")

    /** El precio orientativo más bajo, para la tarjeta resumida (RF-33). */
    val precioDesde: String?
        get() = servicios.filter { it.modo != ModoPrecio.COTIZACION && it.montoCop != null }.minByOrNull { it.montoCop!! }?.let { Precios.texto(it.copy(modo = ModoPrecio.DESDE)) }
}

data class FiltroPrestadores(
    val ciudad: String = "",
    val zona: String = "",
    val categoria: String? = null,
    val especie: Especie? = null,
    val modalidad: ModalidadPrestador? = null,
)

interface RepositorioPrestadores {
    val miPerfil: StateFlow<PerfilPrestador?>
    suspend fun cargarMiPerfil(): PerfilPrestador?
    suspend fun guardar(perfil: PerfilPrestador)
    suspend fun categorias(): List<Categoria>

    /** Sube una foto (JPG) a la galería pública y devuelve su URL. */
    suspend fun subirFoto(bytes: ByteArray): String
    suspend fun borrarFoto(url: String)
    suspend fun buscar(filtro: FiltroPrestadores, lat: Double? = null, lon: Double? = null): List<PerfilPrestador>
    suspend fun obtener(id: String): PerfilPrestador?
}

object Precios {
    private val COP = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO"))

    fun pesos(monto: Int) = "$" + COP.format(monto).replace(' ', ' ')

    /** "$25.000 por paseo", "Desde $40.000" o "A cotizar". */
    fun texto(s: ServicioPrestador): String = when {
        s.modo == ModoPrecio.COTIZACION || s.montoCop == null -> "A cotizar"
        else -> (if (s.modo == ModoPrecio.DESDE) "Desde " else "") + pesos(s.montoCop) + (s.unidad?.let { " ${it.etiqueta}" } ?: "")
    }
}

object ValidadorPrestador {
    const val MAX_GALERIA = 12

    fun errores(p: PerfilPrestador): Map<String, String> = buildMap {
        if (p.nombre.isBlank()) put("nombre", "Escribe tu nombre o el del negocio")
        if (p.descripcion.trim().length < 10) put("descripcion", "Cuéntales en al menos 10 caracteres qué ofreces")
        if (!ValidadorVeterinario.telefonoValido(p.telefono)) put("telefono", "Escribe un teléfono válido (solo números)")
        p.whatsapp?.takeIf { it.isNotBlank() && !ValidadorVeterinario.telefonoValido(it) }?.let { put("whatsapp", "Número de WhatsApp no válido") }
        p.correo?.takeIf { it.isNotBlank() && !it.contains('@') }?.let { put("correo", "Correo no válido") }
        if (p.categorias.isEmpty()) put("categorias", "Elige al menos una categoría")
        if (p.especies.isEmpty()) put("especies", "Elige al menos una especie")
        if (p.modalidades.isEmpty()) put("modalidades", "Elige domicilio, establecimiento o ambos")
        if (p.ciudad.trim().length < 2) put("ciudad", "Escribe la ciudad")
        if (p.direccion.isNullOrBlank() && p.zona.isNullOrBlank() && p.radioKm == null) put("zona", "Escribe tu zona de cobertura o una dirección")
        p.radioKm?.let { if (it !in 1..100) put("zona", "El radio debe estar entre 1 y 100 km") }
        if (p.lat == 0.0 && p.lon == 0.0) put("ubicacion", "Ubícate en el mapa")
        if (p.galeria.size > MAX_GALERIA) put("galeria", "Máximo $MAX_GALERIA fotos")
        p.servicios.firstOrNull { s -> erroresServicio(s, p.categorias).isNotEmpty() }?.let { put("servicios", "Revisa el servicio \"${it.nombre}\"") }
    }

    fun erroresServicio(s: ServicioPrestador, categorias: List<String>): List<String> = buildList {
        if (s.nombre.isBlank()) add("Escribe el nombre del servicio")
        if (s.categoriaId !in categorias) add("Elige una de tus categorías")
        if (s.modo != ModoPrecio.COTIZACION && (s.montoCop ?: 0) <= 0) add("Escribe el precio o elige \"A cotizar\"")
    }
}
