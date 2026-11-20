package com.udenar.petcare

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.Configuration
import dagger.hilt.android.testing.CustomTestApplication

/**
 * Base de la aplicación de pruebas: configura WorkManager para que, si Android arranca un
 * trabajo pendiente de la app real dentro del proceso de pruebas, no cierre el proceso.
 */
open class BasePruebas : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}

@CustomTestApplication(BasePruebas::class)
interface AplicacionPruebas

/** Reemplaza PetcareApp por la aplicación de pruebas de Hilt (permite sustituir módulos). */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, AplicacionPruebas_Application::class.java.name, context)
}
