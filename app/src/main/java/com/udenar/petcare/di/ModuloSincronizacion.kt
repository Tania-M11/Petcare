package com.udenar.petcare.di

import com.udenar.petcare.data.cuidados.RepositorioCuidadosLocal
import com.udenar.petcare.data.cuidados.TablaEventos
import com.udenar.petcare.data.cuidados.TablaRegistros
import com.udenar.petcare.data.fotos.AlmacenFotos
import com.udenar.petcare.data.fotos.FotosSupabase
import com.udenar.petcare.data.mascotas.RepositorioMascotasLocal
import com.udenar.petcare.data.mascotas.TablaMascotas
import com.udenar.petcare.data.sync.AlmacenCursores
import com.udenar.petcare.data.sync.AlmacenLocalRoom
import com.udenar.petcare.data.sync.CursoresRoom
import com.udenar.petcare.data.sync.FuenteRemota
import com.udenar.petcare.data.sync.RemotaSupabase
import com.udenar.petcare.data.sync.Sincronizador
import com.udenar.petcare.data.sync.TablaSincronizable
import com.udenar.petcare.domain.cuidados.RepositorioCuidados
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.sync.AlmacenLocal
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.Clock
import javax.inject.Singleton

/** Tablas que se sincronizan (cada parte agrega la suya con @IntoSet) y el motor. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ModuloSincronizacion {

    @Binds @IntoSet
    abstract fun tablaMascotas(t: TablaMascotas): TablaSincronizable

    @Binds @IntoSet
    abstract fun tablaEventos(t: TablaEventos): TablaSincronizable

    @Binds @IntoSet
    abstract fun tablaRegistros(t: TablaRegistros): TablaSincronizable

    @Binds @IntoSet
    abstract fun tablaPesos(t: com.udenar.petcare.data.salud.TablaPesos): TablaSincronizable

    @Binds @IntoSet
    abstract fun tablaVisitas(t: com.udenar.petcare.data.salud.TablaVisitas): TablaSincronizable

    @Binds
    abstract fun salud(r: com.udenar.petcare.data.salud.RepositorioSaludLocal): com.udenar.petcare.domain.salud.RepositorioSalud

    @Binds
    abstract fun cuidados(r: RepositorioCuidadosLocal): RepositorioCuidados

    @Binds
    abstract fun remota(r: RemotaSupabase): FuenteRemota

    @Binds
    abstract fun fotos(f: FotosSupabase): AlmacenFotos

    @Binds
    abstract fun cursores(c: CursoresRoom): AlmacenCursores

    @Binds
    abstract fun almacenLocal(a: AlmacenLocalRoom): AlmacenLocal

    @Binds
    abstract fun mascotas(r: RepositorioMascotasLocal): RepositorioMascotas

    companion object {
        @Provides
        @Singleton
        fun sincronizador(
            tablas: Set<@JvmSuppressWildcards TablaSincronizable>,
            remota: FuenteRemota,
            cursores: AlmacenCursores,
            reloj: Clock,
        ) = Sincronizador(tablas, remota, cursores, reloj)
    }
}
