package com.udenar.petcare.data.alarmas

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.udenar.petcare.data.cuidados.EventoDao
import com.udenar.petcare.data.cuidados.RegistroDao
import com.udenar.petcare.data.cuidados.comoDominio
import com.udenar.petcare.data.mascotas.MascotaDao
import com.udenar.petcare.domain.cuidados.EstadoRegistro
import com.udenar.petcare.domain.cuidados.MotorCuidados
import com.udenar.petcare.domain.cuidados.PlanAlarmas
import com.udenar.petcare.domain.cuidados.ProgramadorRecordatorios
import com.udenar.petcare.domain.cuidados.RepositorioCuidados
import com.udenar.petcare.domain.cuidados.Toma
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/** Dependencias de los receptores (se piden al grafo de Hilt de la aplicación). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DependenciasReceptores {
    fun eventos(): EventoDao
    fun registros(): RegistroDao
    fun mascotas(): MascotaDao
    fun notificador(): Notificador
    fun recordatorios(): ProgramadorRecordatorios
    fun planificador(): PlanificadorAlarmas
    fun cuidados(): RepositorioCuidados
    fun reloj(): Clock
}

/**
 * Obtiene las dependencias, o null si el grafo aún no existe (p. ej. un aviso del sistema que
 * llega al proceso de pruebas antes de que empiece una prueba). En la app real siempre existe.
 */
private fun dependencias(contexto: Context): DependenciasReceptores? = try {
    EntryPointAccessors.fromApplication(contexto.applicationContext, DependenciasReceptores::class.java)
} catch (_: IllegalStateException) {
    null
}

/** Ejecuta trabajo suspendido dentro de un receptor sin que Android lo corte (goAsync). */
private fun BroadcastReceiver.enSegundoPlano(bloque: suspend () -> Unit) {
    val pendiente = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            bloque()
        } finally {
            pendiente.finish()
        }
    }
}

/** Suena una alarma: muestra la notificación si la toma sigue pendiente y programa la siguiente. */
class ReceptorAlarma : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dep = dependencias(context) ?: return
        enSegundoPlano { manejar(dep, intent) }
    }

    private suspend fun manejar(dep: DependenciasReceptores, intent: Intent) {
        val eventos = dep.eventos()
        val registros = dep.registros()
        val mascotas = dep.mascotas()
        val notificador = dep.notificador()
        val reloj = dep.reloj()
        val d = RelojAlarmasAndroid.datos(intent)
        if (d.prueba) {
            notificador.mostrarPrueba()
            return
        }
        val evento = d.eventoId?.let { eventos.porId(it) }?.takeIf { !it.eliminado }?.comoDominio()
        val toma = d.toma?.let { LocalDateTime.parse(it) }
        val mascota = evento?.let { mascotas.porId(it.mascotaId) }?.takeIf { !it.eliminado }
        if (evento != null && toma != null && mascota != null && !evento.oculto) {
            val etiqueta = MotorCuidados.idRegistro(evento.id, toma)
            val registro = registros.porId(etiqueta)?.takeIf { !it.eliminado }
            val hoy = LocalDate.now(reloj)
            when {
                d.esAviso -> notificador.mostrarAviso(
                    "aviso:${evento.id}", PlanAlarmas.titulo(evento, mascota.nombre, true, toma.toLocalDate(), hoy), PlanAlarmas.texto(evento),
                )
                // Nadie la ha resuelto (o se pospuso): avisar
                registro == null || registro.estado == EstadoRegistro.POSPUESTA.name -> notificador.mostrarToma(
                    etiqueta = etiqueta,
                    titulo = PlanAlarmas.titulo(evento, mascota.nombre, false, toma.toLocalDate(), hoy),
                    texto = PlanAlarmas.texto(evento),
                    eventoId = evento.id,
                    toma = toma.toString(),
                    textoHecho = if (evento.tipo == com.udenar.petcare.domain.cuidados.TipoCuidado.COMIDA) "Dada" else "Hecho",
                )
            }
        }
        dep.recordatorios().reprogramar()
    }
}

/** Botones de la notificación: Hecho / Posponer / Omitir (RF-11, RF-14). */
class ReceptorAccion : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dep = dependencias(context) ?: return
        enSegundoPlano { manejar(dep, intent) }
    }

    private suspend fun manejar(dep: DependenciasReceptores, intent: Intent) {
        val cuidados = dep.cuidados()
        val etiqueta = intent.getStringExtra(EXTRA_ETIQUETA) ?: return
        dep.notificador().retirar(etiqueta)
        val evento = intent.getStringExtra(EXTRA_EVENTO)?.let { dep.eventos().porId(it) }?.comoDominio() ?: return
        val fecha = intent.getStringExtra(EXTRA_TOMA)?.let { LocalDateTime.parse(it) } ?: return
        val toma = Toma(evento, fecha, evento.frecuencia.horas.isNotEmpty(), null)
        when (intent.action) {
            HECHO -> cuidados.registrar(toma, evento.tipo.estadoHecho)
            OMITIR -> cuidados.registrar(toma, EstadoRegistro.OMITIDA)
            POSPONER -> {
                cuidados.registrar(toma, EstadoRegistro.POSPUESTA)
                dep.planificador().posponer(evento.id, fecha.toString())
            }
        }
    }

    companion object {
        const val HECHO = "com.udenar.petcare.HECHO"
        const val OMITIR = "com.udenar.petcare.OMITIR"
        const val POSPONER = "com.udenar.petcare.POSPONER"
        const val EXTRA_ETIQUETA = "etiqueta"
        const val EXTRA_EVENTO = "evento"
        const val EXTRA_TOMA = "toma"
    }
}

/** Reinicio del teléfono, cambio de hora o zona, actualización de la app o permiso de hora exacta. */
class ReceptorSistema : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dep = dependencias(context) ?: return
        enSegundoPlano { dep.recordatorios().reprogramar() }
    }
}
