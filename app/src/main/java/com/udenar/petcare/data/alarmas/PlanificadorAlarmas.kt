package com.udenar.petcare.data.alarmas

import com.udenar.petcare.data.cuidados.EventoDao
import com.udenar.petcare.data.cuidados.RegistroDao
import com.udenar.petcare.data.cuidados.comoDominio
import com.udenar.petcare.domain.cuidados.MotorCuidados
import com.udenar.petcare.domain.cuidados.PlanAlarmas
import com.udenar.petcare.domain.cuidados.ProgramadorRecordatorios
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compara las alarmas que deberían existir con las programadas y ajusta la diferencia.
 * Lee directamente de Room: funciona sin internet y tras reiniciar el teléfono.
 */
@Singleton
class PlanificadorAlarmas @Inject constructor(
    private val eventos: EventoDao,
    private val registros: RegistroDao,
    private val alarmas: AlarmaDao,
    private val reloj: RelojAlarmas,
    private val notificador: Notificador,
    private val clock: Clock,
    private val preferencias: com.udenar.petcare.data.local.PreferenciaDao,
) : ProgramadorRecordatorios {

    private val candado = Mutex()

    override suspend fun reprogramar() = candado.withLock {
        // RF-14: las mascotas silenciadas en este teléfono no suenan
        val silenciadas = preferencias.silenciadasAhora().toSet()
        val listaEventos = eventos.observarActivos().first().map { it.comoDominio() }.filter { it.mascotaId !in silenciadas }
        val listaRegistros = registros.observarActivos().first().map { it.comoDominio() }
        val deseadas = PlanAlarmas.calcular(listaEventos, listaRegistros, LocalDateTime.now(clock), clock.zone)
            .associateBy { it.clave }
        val actuales = alarmas.todas().associateBy { it.clave }

        // Las que sobran se cancelan
        for ((clave, _) in actuales - deseadas.keys) {
            reloj.cancelar(clave)
            alarmas.borrar(clave)
        }
        // Las nuevas o cambiadas se (re)programan; tras reiniciar el teléfono se programan todas otra vez
        for ((clave, a) in deseadas) {
            val cuando = a.disparaEn.atZone(clock.zone).toInstant()
            reloj.programar(DatosAlarma(clave, a.eventoId, a.toma.toString(), a.esAviso), cuando)
            val actual = actuales[clave]
            if (actual == null || actual.disparaEn != cuando.toEpochMilli() || actual.toma != a.toma.toString()) {
                alarmas.guardar(AlarmaEntidad(clave, a.eventoId, a.toma.toString(), cuando.toEpochMilli(), a.esAviso))
            }
        }

        // T6: si otra persona (u otro teléfono) ya resolvió una toma, su notificación sobra
        val resueltas = listaRegistros.filter { it.estado != com.udenar.petcare.domain.cuidados.EstadoRegistro.POSPUESTA }.map { it.id }.toSet()
        notificador.retirarResueltas(resueltas)
    }

    /** "Posponer": vuelve a sonar en 15 minutos (alarma suelta, fuera del plan). */
    fun posponer(eventoId: String, toma: String) {
        val clave = "posponer:" + MotorCuidados.idRegistro(eventoId, LocalDateTime.parse(toma))
        reloj.programar(DatosAlarma(clave, eventoId, toma, esAviso = false), clock.instant().plusSeconds(MINUTOS_POSPONER * 60))
    }

    /** Solo en desarrollo: una notificación de prueba en 1 minuto. */
    fun probar() = reloj.programar(DatosAlarma("prueba", null, null, esAviso = false, prueba = true), clock.instant().plusSeconds(60))

    companion object {
        const val MINUTOS_POSPONER = 15L
    }
}
