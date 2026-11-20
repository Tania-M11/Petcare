package com.udenar.petcare

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Punto de entrada: inicia Hilt y le entrega a WorkManager la fábrica que inyecta los trabajos. */
@HiltAndroidApp
class PetcareApp : Application(), Configuration.Provider {

    @Inject
    lateinit var fabricaTrabajos: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(fabricaTrabajos).build()
}
