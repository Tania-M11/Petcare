package com.udenar.petcare.ui.componentes

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), fondo, fondo))),
        contentAlignment = Alignment.Center,
    ) {
        Icono3D(icono, tam = tam * 0.66f)
    }
}
