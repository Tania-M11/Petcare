package com.udenar.petcare.domain.profesionales

import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.ModoUso
import kotlinx.coroutines.flow.StateFlow
import java.net.URLEncoder
import java.time.DayOfWeek

/** Estado de verificación del veterinario (RF-23). Solo el panel pasa a VERIFICADO o RECHAZADO. */
enum class Verificacion(val mensaje: String) {
    SIN_CERTIFICADO("Sube tu certificado para iniciar tu verificación."),
    EN_REVISION("Recibimos tu certificado. Dentro de unos días recibirás tu verificación."),
    VERIFICADO("Tu perfil está verificado."),
    RECHAZADO("No pudimos verificar tu certificado. Sube uno nuevo.");

    val verificado: Boolean get() = this == VERIFICADO
    val insignia: String get() = if (verificado) "Verificado ✓" else "No verificado"

    companion object {
        fun desdeCodigo(c: String?) = entries.firstOrNull { it.name == c } ?: SIN_CERTIFICADO
    }
}

enum class ServicioVet(val etiqueta: String) {
    CONSULTA("Consulta"), VACUNACION("Vacunación"), DESPARASITACION("Desparasitación"), CIRUGIA("Cirugía"), OTROS("Otros"),
}

/** Perfil público del veterinario (RF-22). [distanciaKm] solo llega en búsquedas con ubicación. */
data class PerfilVeterinario(
    val id: String,
    val nombre: String,
    val especies: Set<Especie>,
    val especialidades: List<String> = emptyList(),
    val servicios: Set<ServicioVet> = emptySet(),
    val clinica: String? = null,
    val telefono: String,
    val whatsapp: String? = null,
    val correo: String? = null,
    val descripcion: String? = null,
    val domicilio: Boolean = false,
    /** Texto libre por día, p. ej. "8:00-12:00, 14:00-18:00"; día ausente = no atiende. */
    val horario: Map<DayOfWeek, String> = emptyMap(),
    val tarifa: String? = null,
    val ciudad: String,
    val direccion: String,
    val lat: Double,
    val lon: Double,
    val verificacion: Verificacion = Verificacion.SIN_CERTIFICADO,
    val activo: Boolean = true,
    val ratingSuma: Int = 0,
    val ratingCantidad: Int = 0,
    val distanciaKm: Double? = null,
) {
    val promedio: Double? get() = if (ratingCantidad == 0) null else ratingSuma.toDouble() / ratingCantidad
}

data class FiltroVeterinarios(
    val ciudad: String = "",
    val especie: Especie? = null,
    val domicilio: Boolean = false,
    val servicio: ServicioVet? = null,
    val soloVerificados: Boolean = false,
)

/** Error con un mensaje ya listo para mostrar. */
class ProfesionalException(mensaje: String, causa: Throwable? = null) : Exception(mensaje, causa)

interface RepositorioVeterinarios {
    /** Mi perfil de veterinario (null = no tengo o aún no se ha cargado). */
    val miPerfil: StateFlow<PerfilVeterinario?>

    /** Lee mi perfil del servidor. Lanza [ProfesionalException] si no hay conexión. */
    suspend fun cargarMiPerfil(): PerfilVeterinario?

    /** Crea o actualiza mi perfil (el id es el del usuario con sesión). */
    suspend fun guardar(perfil: PerfilVeterinario)

    /** Sube o reemplaza el certificado (imagen o PDF, hasta 5 MB) y pasa a EN_REVISION. */
    suspend fun subirCertificado(bytes: ByteArray, tipoMime: String)

    /** Directorio (RF-24): máximo 100, ordenado por distancia si hay ubicación. */
    suspend fun buscar(filtro: FiltroVeterinarios, lat: Double? = null, lon: Double? = null): List<PerfilVeterinario>

    suspend fun obtener(id: String): PerfilVeterinario?
}

/** Modo activo (RF-30): se recuerda en el teléfono; nunca cambia la identidad. */
interface RepositorioModo {
    val modo: StateFlow<ModoUso>
    fun cambiar(modo: ModoUso)
}

object ValidadorVeterinario {
    const val MAX_CERTIFICADO = 5 * 1024 * 1024
    private val TELEFONO = Regex("^\\+?[0-9 ]{7,20}$")

    fun telefonoValido(t: String) = TELEFONO.matches(t.trim())

    /** Errores por campo (vacío = se puede guardar). Las claves son los nombres de los campos. */
    fun errores(p: PerfilVeterinario): Map<String, String> = buildMap {
        if (p.nombre.isBlank()) put("nombre", "Escribe tu nombre completo")
        if (p.especies.isEmpty()) put("especies", "Elige al menos una especie")
        if (!telefonoValido(p.telefono)) put("telefono", "Escribe un teléfono válido (solo números)")
        p.whatsapp?.takeIf { it.isNotBlank() && !telefonoValido(it) }?.let { put("whatsapp", "Número de WhatsApp no válido") }
        p.correo?.takeIf { it.isNotBlank() && !it.contains('@') }?.let { put("correo", "Correo no válido") }
        if (p.ciudad.trim().length < 2) put("ciudad", "Escribe la ciudad")
        if (p.direccion.trim().length < 3) put("direccion", "Escribe la dirección del consultorio")
        if (p.lat == 0.0 && p.lon == 0.0) put("ubicacion", "Ubica tu consultorio en el mapa")
    }
}

/** Enlaces de contacto directo (sin permisos especiales). */
object Contacto {
    private fun digitos(n: String) = n.filter(Char::isDigit)

    fun enlaceLlamar(numero: String) = "tel:" + (if (numero.trim().startsWith("+")) "+" else "") + digitos(numero)

    /** wa.me exige el indicativo: un celular colombiano de 10 dígitos (3xx) se completa con 57. */
    fun enlaceWhatsapp(numero: String, mensaje: String? = null): String {
        val d = digitos(numero).let { if (it.length == 10 && it.startsWith("3")) "57$it" else it }
        return "https://wa.me/$d" + (mensaje?.let { "?text=" + URLEncoder.encode(it, "UTF-8").replace("+", "%20") } ?: "")
    }

    fun enlaceComoLlegar(lat: Double, lon: Double, etiqueta: String) =
        "geo:$lat,$lon?q=$lat,$lon(" + URLEncoder.encode(etiqueta, "UTF-8").replace("+", "%20") + ")"

    fun textoDistancia(km: Double): String =
        if (km < 1) "a ${(km * 1000).toInt()} m" else "a " + String.format(java.util.Locale.forLanguageTag("es-CO"), "%.1f km", km)
}
