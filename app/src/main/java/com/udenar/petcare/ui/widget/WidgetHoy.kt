package com.udenar.petcare.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.udenar.petcare.MainActivity
import com.udenar.petcare.data.alarmas.DependenciasReceptores
import com.udenar.petcare.data.cuidados.comoDominio
import com.udenar.petcare.domain.cuidados.MotorCuidados
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.cuidados.Toma
import com.udenar.petcare.ui.theme.Pc
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private data class FilaWidget(val eventoId: String, val toma: String, val hora: String, val texto: String, val boton: String)

/** Widget de Hoy (Fase 6.8): próximas tomas pendientes con botón Hecho, sin abrir la app. */
class WidgetHoy : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val filas = runCatching { cargar(context) }.getOrDefault(emptyList())
        provideContent { Contenido(filas) }
    }

    private suspend fun cargar(context: Context): List<FilaWidget> {
        val dep = EntryPointAccessors.fromApplication(context.applicationContext, DependenciasReceptores::class.java)
        val reloj = dep.reloj()
        val hoy = LocalDate.now(reloj)
        val cuidados = dep.cuidados()
        val tomas = MotorCuidados.agendaDelDia(hoy, hoy, cuidados.observarEventos().first(), cuidados.observarRegistros().first(), reloj.zone)
        val hora = DateTimeFormatter.ofPattern("H:mm")
        return tomas.filter(Toma::pendiente).sortedBy { it.fechaHora }.take(4).map { t ->
            val nombre = dep.mascotas().porId(t.evento.mascotaId)?.nombre.orEmpty()
            FilaWidget(
                t.evento.id, t.fechaHora.toString(),
                if (t.conHora) t.fechaHora.format(hora) else "Hoy",
                "$nombre · ${t.evento.titulo}",
                if (t.evento.tipo == TipoCuidado.COMIDA) "Dada" else "Hecho",
            )
        }
    }

    @Composable
    private fun Contenido(filas: List<FilaWidget>) {
        val tinta = ColorProvider(Pc.Tinta)
        val suave = ColorProvider(Pc.TintaSuave)
        Column(
            GlanceModifier.fillMaxSize().background(Pc.Crema).cornerRadius(20.dp).padding(12.dp).clickable(actionStartActivity<MainActivity>()),
        ) {
            Text(
                if (filas.isEmpty()) "Hoy · todo al día 🐾" else "Hoy · ${filas.size} pendiente${if (filas.size == 1) "" else "s"}",
                style = TextStyle(color = tinta, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(6.dp))
            filas.forEach { f ->
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        Text(f.hora, style = TextStyle(color = suave, fontSize = 12.sp))
                        Text(f.texto, style = TextStyle(color = tinta, fontSize = 14.sp), maxLines = 1)
                    }
                    Button(f.boton, onClick = actionRunCallback<MarcarHechoWidget>(actionParametersOf(EVENTO to f.eventoId, TOMA to f.toma)))
                }
            }
        }
    }

    companion object {
        val EVENTO = ActionParameters.Key<String>("evento")
        val TOMA = ActionParameters.Key<String>("toma")
    }
}

/** Botón Hecho del widget: registra igual que el botón de la notificación. */
class MarcarHechoWidget : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val dep = EntryPointAccessors.fromApplication(context.applicationContext, DependenciasReceptores::class.java)
        val evento = parameters[WidgetHoy.EVENTO]?.let { dep.eventos().porId(it) }
            ?.comoDominio() ?: return
        val fecha = parameters[WidgetHoy.TOMA]?.let(LocalDateTime::parse) ?: return
        dep.cuidados().registrar(Toma(evento, fecha, evento.frecuencia.horas.isNotEmpty(), null), evento.tipo.estadoHecho)
        dep.notificador().retirar(MotorCuidados.idRegistro(evento.id, fecha))
        WidgetHoy().updateAll(context)
    }
}

class ReceptorWidgetHoy : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidgetHoy()
}
