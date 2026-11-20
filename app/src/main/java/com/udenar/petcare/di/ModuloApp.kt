package com.udenar.petcare.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Dependencias de toda la app. El reloj se inyecta (en vez de llamar a Instant.now())
 * para que el motor de cuidados y las alarmas se puedan probar con fechas fijas.
 */
@Module
@InstallIn(SingletonComponent::class)
object ModuloApp {

    @Provides
    @Singleton
    fun reloj(): Clock = Clock.systemDefaultZone()
}
