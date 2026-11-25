package com.udenar.petcare.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Bathtub
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleanHands
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Female
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Male
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.Soap
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsBaseball
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Toys
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.udenar.petcare.domain.modelo.Icono
import com.udenar.petcare.ui.theme.Pc

/**
 * Íconos de un solo color (Material Symbols redondeados, Apache 2.0). Cada tema tiene su tono de
 * la paleta: salud en cielo, comida y actividad en naranja, organización en lavanda, higiene en
 * menta y celebraciones en sol. Así se reconocen de un vistazo sin parecer emojis.
 */
fun Icono.vector(): ImageVector = when (this) {
    Icono.ADJUNTO -> Icons.Rounded.AttachFile
    Icono.ALARMA -> Icons.Rounded.Alarm
    Icono.AMANECER -> Icons.Rounded.WbTwilight
    Icono.AUTO -> Icons.Rounded.DirectionsCar
    Icono.BAJA -> Icons.AutoMirrored.Rounded.TrendingDown
    Icono.BANO -> Icons.Rounded.Bathtub
    Icono.BRILLOS -> Icons.Rounded.AutoAwesome
    Icono.CALENDARIO -> Icons.Rounded.CalendarMonth
    Icono.CAMARA -> Icons.Rounded.PhotoCamera
    Icono.CAMPANA -> Icons.Rounded.Notifications
    Icono.CANASTA -> Icons.Rounded.ShoppingBasket
    Icono.CANDADO -> Icons.Rounded.Lock
    Icono.CARNE -> Icons.Rounded.Badge
    Icono.CASA -> Icons.Rounded.Home
    Icono.CASA_PERRO -> Icons.Rounded.Cottage
    Icono.CEPILLO -> Icons.Rounded.Brush
    Icono.CEPILLO_DIENTES -> Icons.Rounded.CleanHands
    Icono.CHECK -> Icons.Rounded.CheckCircle
    Icono.COHETE -> Icons.Rounded.RocketLaunch
    Icono.COMIDA -> Icons.Rounded.Restaurant
    Icono.CORAZON -> Icons.Rounded.Favorite
    Icono.DIENTE -> Icons.Rounded.SentimentSatisfied
    Icono.DINERO -> Icons.Rounded.Payments
    Icono.DOCUMENTO -> Icons.Rounded.Description
    Icono.EJERCICIO -> Icons.Rounded.FitnessCenter
    Icono.ENTRENAMIENTO -> Icons.Rounded.School
    Icono.EQUIPO -> Icons.Rounded.Groups
    Icono.ESCUDO -> Icons.Rounded.Shield
    Icono.ESTETOSCOPIO_VET -> Icons.Rounded.MedicalServices
    Icono.ESTRELLA -> Icons.Rounded.Star
    Icono.EXAMEN -> Icons.Rounded.Science
    Icono.FIESTA -> Icons.Rounded.Celebration
    Icono.FLOR -> Icons.Rounded.LocalFlorist
    Icono.FUEGO -> Icons.Rounded.LocalFireDepartment
    Icono.GALERIA -> Icons.Rounded.PhotoLibrary
    Icono.GALLETA -> Icons.Rounded.Cookie
    Icono.GATO, Icono.PERRO, Icono.PERRO_GRANDE, Icono.HUELLAS, Icono.HUESO, Icono.RATON -> Icons.Rounded.Pets
    Icono.GLOBO -> Icons.Rounded.Cake
    Icono.HEMBRA -> Icons.Rounded.Female
    Icono.HOJA -> Icons.Rounded.Eco
    Icono.HOSPITAL -> Icons.Rounded.LocalHospital
    Icono.HOTEL -> Icons.Rounded.Hotel
    Icono.IDEA -> Icons.Rounded.Lightbulb
    Icono.INFO -> Icons.Rounded.Info
    Icono.INSECTO -> Icons.Rounded.BugReport
    Icono.JABON -> Icons.Rounded.Soap
    Icono.JUEGO -> Icons.Rounded.SportsBaseball
    Icono.LAPIZ -> Icons.Rounded.Edit
    Icono.LLUVIA -> Icons.Rounded.Umbrella
    Icono.LUNA -> Icons.Rounded.Bedtime
    Icono.MACHO -> Icons.Rounded.Male
    Icono.MALETIN -> Icons.Rounded.Work
    Icono.MAPA -> Icons.Rounded.Map
    Icono.MAS -> Icons.Rounded.AddCircle
    Icono.MEDICAMENTO -> Icons.Rounded.Medication
    Icono.MENSAJE -> Icons.Rounded.Chat
    Icono.NOTA -> Icons.AutoMirrored.Rounded.StickyNote2
    Icono.NUBE -> Icons.Rounded.Cloud
    Icono.OIDO -> Icons.Rounded.Hearing
    Icono.OJO -> Icons.Rounded.Visibility
    Icono.OSO -> Icons.Rounded.Toys
    Icono.PASEO -> Icons.AutoMirrored.Rounded.DirectionsWalk
    Icono.PASTEL -> Icons.Rounded.Cake
    Icono.PESO -> Icons.Rounded.MonitorWeight
    Icono.PIN -> Icons.Rounded.Place
    Icono.PLAYA -> Icons.Rounded.BeachAccess
    Icono.PREGUNTA -> Icons.Rounded.Quiz
    Icono.PULGAR -> Icons.Rounded.ThumbUp
    Icono.REGALO -> Icons.Rounded.CardGiftcard
    Icono.RELOJ -> Icons.Rounded.Schedule
    Icono.SALIR -> Icons.AutoMirrored.Rounded.Logout
    Icono.SOL -> Icons.Rounded.WbSunny
    Icono.SOL_NUBE -> Icons.Rounded.WbCloudy
    Icono.SUBE -> Icons.AutoMirrored.Rounded.TrendingUp
    Icono.TELEFONO -> Icons.Rounded.Call
    Icono.TERMOMETRO -> Icons.Rounded.Thermostat
    Icono.TIENDA -> Icons.Rounded.Storefront
    Icono.TIJERAS -> Icons.Rounded.ContentCut
    Icono.UNAS -> Icons.Rounded.Spa
    Icono.VACUNA -> Icons.Rounded.Vaccines
    Icono.VETERINARIO -> Icons.Rounded.HealthAndSafety
}

/** Familias de color de los íconos. */
enum class TonoIcono { SALUD, ACTIVIDAD, ORGANIZACION, HIGIENE, CELEBRACION }

fun Icono.familia(): TonoIcono = when (this) {
    Icono.VACUNA, Icono.MEDICAMENTO, Icono.HOSPITAL, Icono.VETERINARIO, Icono.ESTETOSCOPIO_VET, Icono.TERMOMETRO,
    Icono.EXAMEN, Icono.DIENTE, Icono.OIDO, Icono.OJO, Icono.ESCUDO, Icono.INSECTO, Icono.PESO, Icono.BAJA, Icono.SUBE,
    Icono.MACHO, Icono.NUBE, Icono.LLUVIA,
    -> TonoIcono.SALUD
    Icono.COMIDA, Icono.GALLETA, Icono.CANASTA, Icono.FUEGO, Icono.PASEO, Icono.EJERCICIO, Icono.JUEGO, Icono.HUESO,
    Icono.OSO, Icono.MAS, Icono.CASA_PERRO, Icono.GATO, Icono.PERRO, Icono.PERRO_GRANDE, Icono.HUELLAS, Icono.RATON,
    Icono.CORAZON, Icono.ENTRENAMIENTO, Icono.TIJERAS,
    -> TonoIcono.ACTIVIDAD
    Icono.BANO, Icono.JABON, Icono.CEPILLO, Icono.CEPILLO_DIENTES, Icono.UNAS, Icono.HOJA, Icono.FLOR, Icono.CHECK,
    Icono.PULGAR,
    -> TonoIcono.HIGIENE
    Icono.ESTRELLA, Icono.FIESTA, Icono.BRILLOS, Icono.GLOBO, Icono.PASTEL, Icono.REGALO, Icono.COHETE, Icono.IDEA,
    Icono.SOL, Icono.SOL_NUBE, Icono.AMANECER, Icono.PLAYA, Icono.DINERO,
    -> TonoIcono.CELEBRACION
    else -> TonoIcono.ORGANIZACION
}

/** Color fuerte del ícono (se dibuja en un solo color). */
val Icono.tono: Color
    get() = when (familia()) {
        TonoIcono.SALUD -> Pc.CieloOscuro
        TonoIcono.ACTIVIDAD -> Pc.NaranjaOscuro
        TonoIcono.ORGANIZACION -> Pc.LavandaOscura
        TonoIcono.HIGIENE -> Pc.MentaOscura
        TonoIcono.CELEBRACION -> Pc.SolOscuro
    }

/** Fondo suave de la misma familia, para la baldosa. */
val Icono.fondoSuave: Color
    get() = when (familia()) {
        TonoIcono.SALUD -> Pc.CieloClaro
        TonoIcono.ACTIVIDAD -> Pc.Durazno
        TonoIcono.ORGANIZACION -> Pc.LavandaClara
        TonoIcono.HIGIENE -> Pc.MentaClara
        TonoIcono.CELEBRACION -> Pc.SolClaro
    }

/** El ícono solo, en su color (o en [color] si se pide otro, p. ej. blanco sobre un botón). */
@Composable
fun Simbolo(icono: Icono, modifier: Modifier = Modifier, tam: Dp = 24.dp, color: Color = icono.tono) =
    Icon(icono.vector(), contentDescription = null, tint = color, modifier = modifier.size(tam))

/**
 * Ícono dentro de una baldosa redondeada con el fondo suave de su familia.
 * [fondo] permite otra base (p. ej. blanco translúcido sobre una tarjeta de color).
 */
@Composable
fun BaldosaIcono(icono: Icono, modifier: Modifier = Modifier, fondo: Color = icono.fondoSuave, tam: Dp = 44.dp) {
    Box(
        modifier
            .size(tam)
            .clip(RoundedCornerShape(tam * 0.34f))
            .background(fondo),
        contentAlignment = Alignment.Center,
    ) {
        Simbolo(icono, tam = tam * 0.52f)
    }
}

/**
 * Ícono para el título de una sección o tarjeta, según sus palabras (null = sin ícono).
 * Así todas las pantallas comparten el mismo ícono para el mismo tema.
 */
fun iconoDeTitulo(titulo: String): Icono? {
    val t = titulo.lowercase()
    return when {
        "vacuna" in t -> Icono.VACUNA
        "medicamento" in t || "tratamiento" in t -> Icono.MEDICAMENTO
        "desparasit" in t -> Icono.ESCUDO
        "salud" in t -> Icono.TERMOMETRO
        "peso" in t -> Icono.PESO
        "visita" in t || "consulta" in t -> Icono.HOSPITAL
        "veterinari" in t -> Icono.VETERINARIO
        "paseo" in t || "actividad" in t -> Icono.PASEO
        "comida" in t -> Icono.COMIDA
        "higiene" in t -> Icono.BANO
        "document" in t -> Icono.DOCUMENTO
        "historial" in t -> Icono.RELOJ
        "estado de cuidado" in t || t.startsWith("cuidados") -> Icono.CHECK
        "horario" in t || "disponibilidad" in t -> Icono.CALENDARIO
        "contacto" in t -> Icono.TELEFONO
        "dónde" in t -> Icono.MAPA
        "galería" in t -> Icono.GALERIA
        "servicio" in t -> Icono.MALETIN
        "especies" in t || "a quién" in t || t == "mascota" -> Icono.HUELLAS
        "categor" in t -> Icono.ESTRELLA
        "reseña" in t -> Icono.ESTRELLA
        "código" in t -> Icono.CARNE
        "personas" in t || "cuidadores" in t || "con quién" in t -> Icono.EQUIPO
        "verificación" in t -> Icono.CHECK
        "propuesta" in t -> Icono.DINERO
        "edad" in t -> Icono.PASTEL
        "próxima cita" in t -> Icono.CALENDARIO
        "lo que pide" in t || "lo que indicó" in t || "sobre mí" in t || "más detalles" in t -> Icono.NOTA
        else -> null
    }
}

/** Título de sección con su ícono en baldosa a la izquierda (si el tema tiene uno). */
@Composable
fun TituloConIcono(titulo: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        iconoDeTitulo(titulo)?.let { BaldosaIcono(it, tam = 32.dp, modifier = Modifier.padding(end = 10.dp)) }
        Text(titulo, style = MaterialTheme.typography.titleMedium)
    }
}
