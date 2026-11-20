package com.udenar.petcare

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Punto de entrada de la aplicación: inicia el grafo de dependencias de Hilt. */
@HiltAndroidApp
class PetcareApp : Application()
