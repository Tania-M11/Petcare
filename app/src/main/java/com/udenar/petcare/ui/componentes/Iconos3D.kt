package com.udenar.petcare.ui.componentes

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.udenar.petcare.R
import com.udenar.petcare.domain.modelo.Icono
import com.udenar.petcare.ui.theme.Pc

/**
 * Íconos 3D (Fluent Emoji 3D de Microsoft, MIT) sobre fondos pastel de la paleta de PetCare.
 * El crédito está en Perfil → Acerca de.
 */
@DrawableRes
fun Icono.dibujo(): Int = when (this) {
    Icono.ADJUNTO -> R.drawable.ic3d_adjunto
    Icono.ALARMA -> R.drawable.ic3d_alarma
    Icono.AMANECER -> R.drawable.ic3d_amanecer
    Icono.AUTO -> R.drawable.ic3d_auto
    Icono.BAJA -> R.drawable.ic3d_baja
    Icono.BANO -> R.drawable.ic3d_bano
    Icono.BRILLOS -> R.drawable.ic3d_brillos
    Icono.CALENDARIO -> R.drawable.ic3d_calendario
    Icono.CAMARA -> R.drawable.ic3d_camara
    Icono.CAMPANA -> R.drawable.ic3d_campana
    Icono.CANASTA -> R.drawable.ic3d_canasta
    Icono.CANDADO -> R.drawable.ic3d_candado
    Icono.CARNE -> R.drawable.ic3d_carne
    Icono.CASA -> R.drawable.ic3d_casa
    Icono.CASA_PERRO -> R.drawable.ic3d_casa_perro
    Icono.CEPILLO -> R.drawable.ic3d_cepillo
    Icono.CEPILLO_DIENTES -> R.drawable.ic3d_cepillo_dientes
    Icono.CHECK -> R.drawable.ic3d_check
    Icono.COHETE -> R.drawable.ic3d_cohete
    Icono.COMIDA -> R.drawable.ic3d_comida
    Icono.CORAZON -> R.drawable.ic3d_corazon
    Icono.DIENTE -> R.drawable.ic3d_diente
    Icono.DINERO -> R.drawable.ic3d_dinero
    Icono.DOCUMENTO -> R.drawable.ic3d_documento
    Icono.EJERCICIO -> R.drawable.ic3d_ejercicio
    Icono.ENTRENAMIENTO -> R.drawable.ic3d_entrenamiento
    Icono.EQUIPO -> R.drawable.ic3d_equipo
    Icono.ESCUDO -> R.drawable.ic3d_escudo
    Icono.ESTETOSCOPIO_VET -> R.drawable.ic3d_estetoscopio_vet
    Icono.ESTRELLA -> R.drawable.ic3d_estrella
    Icono.EXAMEN -> R.drawable.ic3d_examen
    Icono.FIESTA -> R.drawable.ic3d_fiesta
    Icono.FLOR -> R.drawable.ic3d_flor
    Icono.FUEGO -> R.drawable.ic3d_fuego
    Icono.GALERIA -> R.drawable.ic3d_galeria
    Icono.GALLETA -> R.drawable.ic3d_galleta
    Icono.GATO -> R.drawable.ic3d_gato
    Icono.GLOBO -> R.drawable.ic3d_globo
    Icono.HEMBRA -> R.drawable.ic3d_hembra
    Icono.HOJA -> R.drawable.ic3d_hoja
    Icono.HOSPITAL -> R.drawable.ic3d_hospital
    Icono.HOTEL -> R.drawable.ic3d_hotel
    Icono.HUELLAS -> R.drawable.ic3d_huellas
    Icono.HUESO -> R.drawable.ic3d_hueso
    Icono.IDEA -> R.drawable.ic3d_idea
    Icono.INFO -> R.drawable.ic3d_info
    Icono.INSECTO -> R.drawable.ic3d_insecto
    Icono.JABON -> R.drawable.ic3d_jabon
    Icono.JUEGO -> R.drawable.ic3d_juego
    Icono.LAPIZ -> R.drawable.ic3d_lapiz
    Icono.LLUVIA -> R.drawable.ic3d_lluvia
    Icono.LUNA -> R.drawable.ic3d_luna
    Icono.MACHO -> R.drawable.ic3d_macho
    Icono.MALETIN -> R.drawable.ic3d_maletin
    Icono.MAPA -> R.drawable.ic3d_mapa
    Icono.MAS -> R.drawable.ic3d_mas
    Icono.MEDICAMENTO -> R.drawable.ic3d_medicamento
    Icono.MENSAJE -> R.drawable.ic3d_mensaje
    Icono.NOTA -> R.drawable.ic3d_nota
    Icono.NUBE -> R.drawable.ic3d_nube
    Icono.OIDO -> R.drawable.ic3d_oido
    Icono.OJO -> R.drawable.ic3d_ojo
    Icono.OSO -> R.drawable.ic3d_oso
    Icono.PASEO -> R.drawable.ic3d_paseo
    Icono.PASTEL -> R.drawable.ic3d_pastel
    Icono.PERRO -> R.drawable.ic3d_perro
    Icono.PERRO_GRANDE -> R.drawable.ic3d_perro_grande
    Icono.PESO -> R.drawable.ic3d_peso
    Icono.PIN -> R.drawable.ic3d_pin
    Icono.PLAYA -> R.drawable.ic3d_playa
    Icono.PREGUNTA -> R.drawable.ic3d_pregunta
    Icono.PULGAR -> R.drawable.ic3d_pulgar
    Icono.RATON -> R.drawable.ic3d_raton
    Icono.REGALO -> R.drawable.ic3d_regalo
    Icono.RELOJ -> R.drawable.ic3d_reloj
    Icono.SALIR -> R.drawable.ic3d_salir
    Icono.SOL -> R.drawable.ic3d_sol
    Icono.SOL_NUBE -> R.drawable.ic3d_sol_nube
    Icono.SUBE -> R.drawable.ic3d_sube
    Icono.TELEFONO -> R.drawable.ic3d_telefono
    Icono.TERMOMETRO -> R.drawable.ic3d_termometro
    Icono.TIENDA -> R.drawable.ic3d_tienda
    Icono.TIJERAS -> R.drawable.ic3d_tijeras
    Icono.UNAS -> R.drawable.ic3d_unas
    Icono.VACUNA -> R.drawable.ic3d_vacuna
    Icono.VETERINARIO -> R.drawable.ic3d_veterinario
}

/** El ícono 3D solo, del tamaño pedido. */
@Composable
fun Icono3D(icono: Icono, modifier: Modifier = Modifier, tam: Dp = 24.dp) =
    Image(painterResource(icono.dibujo()), contentDescription = null, modifier = modifier.size(tam))

/** Ícono 3D dentro de una "baldosa" pastel con un brillo suave, como en las tarjetas. */
@Composable
fun BaldosaIcono(icono: Icono, modifier: Modifier = Modifier, fondo: Color = Pc.Crema, tam: Dp = 44.dp) {
    Box(
        modifier
            .size(tam)
            .clip(RoundedCornerShape(tam * 0.32f))
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = if (Pc.oscuro) 0.08f else 0.55f), fondo, fondo))),
        contentAlignment = Alignment.Center,
    ) {
        Icono3D(icono, tam = tam * 0.66f)
    }
}

/**
 * Ícono 3D para el título de una sección o tarjeta, según sus palabras (null = sin ícono).
 * Así todas las pantallas comparten la misma ilustración para el mismo tema.
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

/** Título de sección con su ícono 3D a la izquierda (si el tema tiene uno). */
@androidx.compose.runtime.Composable
fun TituloConIcono(titulo: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        iconoDeTitulo(titulo)?.let { Icono3D(it, tam = 26.dp, modifier = Modifier.padding(end = 10.dp)) }
        androidx.compose.material3.Text(titulo, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
    }
}

