package com.udenar.petcare.ui.cuidados

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.udenar.petcare.domain.cuidados.Ancla
import com.udenar.petcare.domain.cuidados.DescripcionCuidados
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.GrupoCuidado
import com.udenar.petcare.domain.cuidados.MotorCuidados
import com.udenar.petcare.domain.cuidados.RegistroCuidado
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.cuidados.ResumenCuidado
import com.udenar.petcare.domain.cuidados.Vencimiento
import com.udenar.petcare.domain.cuidados.grupo
import com.udenar.petcare.ui.componentes.Chip
import com.udenar.petcare.ui.theme.Pc
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CORTA = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-CO"))

val TipoCuidado.emoji: String
    get() = when (this) {
        TipoCuidado.COMIDA -> "🍽️"
        TipoCuidado.MEDICAMENTO -> "💊"
        TipoCuidado.VACUNA -> "💉"
        TipoCuidado.DESPARASITACION_INTERNA, TipoCuidado.DESPARASITACION_EXTERNA -> "🛡️"
        TipoCuidado.BANO -> "🛁"
        TipoCuidado.CORTE_PELO -> "✂️"
        TipoCuidado.CEPILLADO -> "🧼"
        TipoCuidado.CORTE_UNAS -> "💅"
        TipoCuidado.LIMPIEZA_DENTAL -> "🦷"
        TipoCuidado.LIMPIEZA_OIDOS -> "👂"
        TipoCuidado.LIMPIEZA_OJOS -> "👁️"
        TipoCuidado.CITA_VET -> "🩺"
        TipoCuidado.SERVICIO_CONTRATADO -> "🧺"
        TipoCuidado.PESAJE -> "⚖️"
        TipoCuidado.PERSONALIZADO -> "⭐"
    }

fun GrupoCuidado.color(): Pair<Color, Color> = when (this) {
    GrupoCuidado.COMIDAS -> Pc.Durazno to Pc.Naranja
    GrupoCuidado.SALUD -> Pc.LavandaClara to Pc.LavandaOscura
    GrupoCuidado.HIGIENE -> Pc.CieloClaro to Pc.CieloOscuro
    GrupoCuidado.ACTIVIDADES -> Pc.SolClaro to Pc.Naranja
    GrupoCuidado.CITAS -> Pc.MentaClara to Pc.Tinta
}

/** Sección "Cuidados" de la ficha: agrupados, con su próxima fecha y botón para agregar. */
@Composable
fun SeccionCuidados(
    resumenes: List<ResumenCuidado>,
    onAgregar: (() -> Unit)?,
    onAbrir: (EventoCuidado) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Pc.Blanco)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cuidados", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (onAgregar != null) Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Pc.Durazno)
                    .clickable(onClick = onAgregar)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("agregar_cuidado"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = Pc.Naranja, modifier = Modifier.size(18.dp))
                Text("Agregar", style = MaterialTheme.typography.labelLarge, color = Pc.Naranja)
            }
        }
        if (resumenes.isEmpty()) {
            Text(
                "Aún no hay cuidados. Agrega sus comidas, medicinas, vacunas o baños y PetCare te los recordará.",
                style = MaterialTheme.typography.bodyMedium, color = Pc.TintaSuave,
            )
        }
        GrupoCuidado.entries.forEach { g ->
            val delGrupo = resumenes.filter { it.evento.tipo.grupo == g }
            if (delGrupo.isEmpty()) return@forEach
            Text(g.titulo, style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave, modifier = Modifier.padding(top = 4.dp))
            delGrupo.forEach { r -> FilaCuidado(r, onClick = { onAbrir(r.evento) }) }
        }
    }
}

@Composable
private fun FilaCuidado(r: ResumenCuidado, onClick: () -> Unit) {
    val (fondo, _) = r.evento.tipo.grupo.color()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .testTag("cuidado_${r.evento.titulo}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(fondo),
            contentAlignment = Alignment.Center,
        ) { Text(r.evento.tipo.emoji, fontSize = 20.sp) }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(r.evento.titulo, style = MaterialTheme.typography.titleSmall)
            val partes = listOfNotNull(
                r.descripcion,
                r.evento.detalle["dosis"],
                r.proxima?.let { "Próxima: ${it.format(CORTA)}" }?.takeIf { r.evento.ancla == Ancla.ULTIMA_VEZ || r.evento.tipo == com.udenar.petcare.domain.cuidados.TipoCuidado.VACUNA },
            )
            Text(partes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
        }
        when {
            r.vencimiento == Vencimiento.VENCIDO -> Chip("Vencido", fondo = Pc.Durazno, color = MaterialTheme.colorScheme.error)
            r.vencimiento == Vencimiento.PROXIMO -> Chip("Próximo", fondo = Pc.SolClaro)
            r.vencimiento == Vencimiento.AL_DIA -> Chip("Al día", fondo = Pc.MentaClara)
            r.terminaPronto -> Chip("Termina pronto", fondo = Pc.LavandaClara)
            r.evento.porSubir -> Text("☁", color = Pc.TintaSuave)
        }
    }
}
