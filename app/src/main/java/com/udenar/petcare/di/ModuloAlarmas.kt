package com.udenar.petcare.di

import com.udenar.petcare.data.alarmas.PlanificadorAlarmas
import com.udenar.petcare.data.alarmas.RelojAlarmas
import com.udenar.petcare.data.alarmas.RelojAlarmasAndroid
import com.udenar.petcare.domain.cuidados.ProgramadorRecordatorios
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Alarmas del sistema (las pruebas reemplazan el reloj para no programar alarmas reales). */
@Module
@InstallIn(SingletonComponent::class)
abstract class ModuloAlarmas {
    @Binds
    abstract fun reloj(r: RelojAlarmasAndroid): RelojAlarmas

    @Binds
    abstract fun recordatorios(p: PlanificadorAlarmas): ProgramadorRecordatorios
}
