package com.udenar.petcare.di

import com.udenar.petcare.data.cuenta.RepositorioCuentaSupabase
import com.udenar.petcare.domain.cuenta.RepositorioCuenta
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Une cada repositorio del dominio con su implementación (las pruebas lo reemplazan). */
@Module
@InstallIn(SingletonComponent::class)
abstract class ModuloDatos {

    @Binds
    abstract fun cuenta(impl: RepositorioCuentaSupabase): RepositorioCuenta
}
