package com.udenar.petcare.ui.cuidados

import com.udenar.petcare.domain.modelo.Icono
import com.udenar.petcare.ui.componentes.Simbolo
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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

val TipoCuidado.icono: Icono
    get() = when (this) {
        TipoCuidado.COMIDA -> Icono.COMIDA
        TipoCuidado.MEDICAMENTO -> Icono.MEDICAMENTO
        TipoCuidado.VACUNA -> Icono.VACUNA
        TipoCuidado.DESPARASITACION_INTERNA, TipoCuidado.DESPARASITACION_EXTERNA -> Icono.ESCUDO
        TipoCuidado.BANO -> Icono.BANO
        TipoCuidado.CORTE_PELO -> Icono.TIJERAS
        TipoCuidado.CEPILLADO -> Icono.JABON
        TipoCuidado.CORTE_UNAS -> Icono.UNAS
        TipoCuidado.LIMPIEZA_DENTAL -> Icono.DIENTE
        TipoCuidado.LIMPIEZA_OIDOS -> Icono.OIDO
        TipoCuidado.LIMPIEZA_OJOS -> Icono.OJO
        TipoCuidado.CITA_VET -> Icono.VETERINARIO
        TipoCuidado.SERVICIO_CONTRATADO -> Icono.CANASTA
        TipoCuidado.PESAJE -> Icono.PESO
        TipoCuidado.PERSONALIZADO -> Icono.ESTRELLA
        TipoCuidado.PASEO -> Icono.PASEO
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
            com.udenar.petcare.ui.componentes.TituloConIcono("Cuidados", Modifier.weight(1f))
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
            com.udenar.petcare.ui.componentes.VacioMichi(com.udenar.petcare.domain.recomendaciones.PoseMichi.ALARMA, "Aún no hay cuidados. Agrega sus comidas, medicinas, vacunas o baños y te los recordaré.", fondo = Pc.Blanco)
        }
        // Los registros del veterinario que el dueño ocultó no suenan ni se listan (RF-27)
        var verOcultos by remember { mutableStateOf(false) }
        val ocultos = resumenes.count { it.evento.oculto }
        GrupoCuidado.entries.forEach { g ->
            val delGrupo = resumenes.filter { it.evento.tipo.grupo == g && (verOcultos || !it.evento.oculto) }
            if (delGrupo.isEmpty()) return@forEach
            Text(g.titulo, style = MaterialTheme.typography.labelLarge, color = Pc.TintaSuave, modifier = Modifier.padding(top = 4.dp))
            delGrupo.forEach { r -> FilaCuidado(r, onClick = { onAbrir(r.evento) }) }
        }
        if (ocultos > 0) {
            Text(
                if (verOcultos) "Esconder los ocultos" else "Ver $ocultos ${if (ocultos == 1) "oculto" else "ocultos"}",
                style = MaterialTheme.typography.labelMedium, color = Pc.TintaSuave,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { verOcultos = !verOcultos }.padding(8.dp).testTag("ver_ocultos"),
            )
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
        ) { Simbolo(r.evento.tipo.icono, tam = 28.dp) }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(r.evento.titulo, style = MaterialTheme.typography.titleSmall)
            val partes = listOfNotNull(
                r.descripcion,
                r.evento.detalle["dosis"],
                listOfNotNull(r.evento.detalle["tipo_alimento"], r.evento.detalle["marca"], r.evento.detalle["cantidad"]).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
                r.proxima?.let { "Próxima: ${it.format(CORTA)}" }?.takeIf { r.evento.ancla == Ancla.ULTIMA_VEZ || r.evento.tipo == com.udenar.petcare.domain.cuidados.TipoCuidado.VACUNA },
            )
            Text(partes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Pc.TintaSuave)
            if (r.evento.oculto) Text("Oculto: no aparece en la agenda", style = MaterialTheme.typography.labelSmall, color = Pc.TintaSuave)
            if (r.evento.creadoPor?.rol == "VETERINARIO") {
                com.udenar.petcare.ui.componentes.Chip("Registrado por veterinario · ${r.evento.creadoPor.nombre.orEmpty()}", fondo = Pc.CieloClaro, color = Pc.CieloOscuro, icono = Icono.VETERINARIO)
            }
        }
        when {
            r.vencimiento == Vencimiento.VENCIDO -> Chip("Vencido", fondo = Pc.Durazno, color = MaterialTheme.colorScheme.error)
            r.vencimiento == Vencimiento.PROXIMO -> Chip("Próximo", fondo = Pc.SolClaro)
            r.vencimiento == Vencimiento.AL_DIA -> Chip("Al día", fondo = Pc.MentaClara)
            r.terminaPronto -> Chip("Termina pronto", fondo = Pc.LavandaClara)
            r.evento.porSubir -> Simbolo(Icono.NUBE, tam = 18.dp)
        }
    }
}
