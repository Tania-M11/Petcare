package com.udenar.petcare.data.resumen

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.udenar.petcare.data.alarmas.Notificador
import com.udenar.petcare.domain.cuidados.RepositorioCuidados
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.paseos.RepositorioPaseos
import com.udenar.petcare.domain.recomendaciones.Progreso
import com.udenar.petcare.domain.recomendaciones.ResumenSemanal
import com.udenar.petcare.domain.salud.RepositorioSalud
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Programa el resumen de los domingos a las 7 p. m. (Fase 6.4); se puede apagar en Perfil. */
@Singleton
class ProgramadorResumen @Inject constructor(@ApplicationContext private val contexto: Context, private val reloj: Clock) {
    private val prefs = contexto.getSharedPreferences("resumen_semanal", Context.MODE_PRIVATE)
    private val _activo = MutableStateFlow(prefs.getBoolean(ACTIVO, true))
    val activo: StateFlow<Boolean> = _activo.asStateFlow()

    fun cambiar(activo: Boolean) {
        prefs.edit().putBoolean(ACTIVO, activo).apply()
        _activo.value = activo
        if (activo) programar(reemplazar = true) else runCatching { WorkManager.getInstance(contexto).cancelUniqueWork(TRABAJO) }
    }

    fun programar(reemplazar: Boolean = false) {
        if (!_activo.value) return
        val pedido = PeriodicWorkRequestBuilder<TrabajoResumenSemanal>(7, TimeUnit.DAYS)
            .setInitialDelay(esperaHastaDomingo(LocalDateTime.now(reloj)).toMinutes(), TimeUnit.MINUTES)
            .build()
        val politica = if (reemplazar) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP
        runCatching { WorkManager.getInstance(contexto).enqueueUniquePeriodicWork(TRABAJO, politica, pedido) }
    }

    companion object {
        private const val ACTIVO = "activo"
        private const val TRABAJO = "resumen-semanal"

        /** Tiempo hasta el próximo domingo a las 19:00 (si ya pasó hoy, el de la otra semana). */
        fun esperaHastaDomingo(ahora: LocalDateTime): Duration {
            var objetivo = ahora.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(LocalTime.of(19, 0))
            if (!objetivo.isAfter(ahora)) objetivo = objetivo.plusWeeks(1)
            return Duration.between(ahora, objetivo)
        }
    }
}

@HiltWorker
class TrabajoResumenSemanal @AssistedInject constructor(
    @Assisted contexto: Context,
    @Assisted params: WorkerParameters,
    private val mascotas: RepositorioMascotas,
    private val cuidados: RepositorioCuidados,
    private val paseos: RepositorioPaseos,
    private val salud: RepositorioSalud,
    private val notificador: Notificador,
    private val programador: ProgramadorResumen,
    private val reloj: Clock,
) : CoroutineWorker(contexto, params) {
    override suspend fun doWork(): Result {
        if (!programador.activo.value) return Result.success()
        val hoy = LocalDate.now(reloj)
        val eventos = cuidados.observarEventos().first()
        val registros = cuidados.observarRegistros().first()
        val pesos = salud.observarTodosPesos().first()
        val progresos = mascotas.observar().first().map { m ->
            m.nombre to Progreso.calcular(
                eventos.filter { it.mascotaId == m.id }, registros.filter { it.mascotaId == m.id },
                paseos.observar(m.id).first(), pesos.filter { it.mascotaId == m.id }, hoy, reloj.zone,
            )
        }
        ResumenSemanal.texto(progresos)?.let { (titulo, texto) -> notificador.mostrarResumen(titulo, texto) }
        return Result.success()
    }
}
