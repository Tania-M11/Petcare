package com.udenar.petcare.di

import com.udenar.petcare.data.profesionales.ModoLocal
import com.udenar.petcare.data.profesionales.VeterinariosSupabase
import com.udenar.petcare.domain.profesionales.RepositorioModo
import com.udenar.petcare.domain.profesionales.RepositorioVeterinarios
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Perfiles profesionales y modo de uso (las pruebas de interfaz lo reemplazan por versiones en memoria). */
@Module
@InstallIn(SingletonComponent::class)
abstract class ModuloProfesionales {
    @Binds abstract fun veterinarios(impl: VeterinariosSupabase): RepositorioVeterinarios

    @Binds abstract fun modo(impl: ModoLocal): RepositorioModo

    @Binds abstract fun prestadores(impl: com.udenar.petcare.data.profesionales.PrestadoresSupabase): com.udenar.petcare.domain.profesionales.RepositorioPrestadores

    @Binds abstract fun cotizaciones(impl: com.udenar.petcare.data.profesionales.CotizacionesSupabase): com.udenar.petcare.domain.profesionales.RepositorioCotizaciones

    @Binds abstract fun resenas(impl: com.udenar.petcare.data.profesionales.ResenasSupabase): com.udenar.petcare.domain.profesionales.RepositorioResenas

    @Binds abstract fun atencion(impl: com.udenar.petcare.data.profesionales.AtencionVetSupabase): com.udenar.petcare.domain.profesionales.RepositorioAtencionVet
}
