package com.udenar.petcare

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.udenar.petcare.data.alarmas.Notificador
import com.udenar.petcare.domain.cuidados.ProgramadorRecordatorios
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Punto de entrada: Hilt, WorkManager con inyección, canal de notificaciones y alarmas al día. */
@HiltAndroidApp
class PetcareApp : Application(), Configuration.Provider {

    @Inject lateinit var fabricaTrabajos: HiltWorkerFactory
    @Inject lateinit var notificador: Notificador
    @Inject lateinit var recordatorios: ProgramadorRecordatorios

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(fabricaTrabajos).build()

    override fun onCreate() {
        super.onCreate()
        notificador.crearCanales()
        // Motor de mapas (paseos con GPS)
        org.maplibre.android.MapLibre.getInstance(this)
        // Cada vez que arranca la app (también si Android la cerró), las alarmas quedan al día
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { recordatorios.reprogramar() }
    }
}
