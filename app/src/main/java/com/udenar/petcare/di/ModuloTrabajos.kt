package com.udenar.petcare.di

import com.udenar.petcare.data.sync.ProgramadorWorkManager
import com.udenar.petcare.domain.sync.ProgramadorSync
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** WorkManager (las pruebas lo reemplazan para sincronizar a mano). */
@Module
@InstallIn(SingletonComponent::class)
abstract class ModuloTrabajos {
    @Binds
    abstract fun programador(p: ProgramadorWorkManager): ProgramadorSync
}
