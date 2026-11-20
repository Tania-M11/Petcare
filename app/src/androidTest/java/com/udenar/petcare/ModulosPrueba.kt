package com.udenar.petcare

import android.content.Context
import androidx.room.Room
import com.udenar.petcare.data.alarmas.AlarmaDao
import com.udenar.petcare.data.cuidados.EventoDao
import com.udenar.petcare.data.cuidados.RegistroDao
import com.udenar.petcare.data.local.CursorDao
import com.udenar.petcare.data.local.PetcareDb
import com.udenar.petcare.data.mascotas.MascotaDao
import com.udenar.petcare.di.ModuloLocal
import com.udenar.petcare.di.ModuloSupabase
import com.udenar.petcare.di.ModuloTrabajos
import com.udenar.petcare.domain.sync.EstadoSync
import com.udenar.petcare.domain.sync.ProgramadorSync
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Base de datos en memoria: las pruebas no tocan los datos reales del teléfono. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ModuloLocal::class])
object ModuloLocalPrueba {
    @Provides
    @Singleton
    fun db(@ApplicationContext c: Context): PetcareDb = Room.inMemoryDatabaseBuilder(c, PetcareDb::class.java).build()

    @Provides
    fun mascotas(db: PetcareDb): MascotaDao = db.mascotas()

    @Provides
    fun cursores(db: PetcareDb): CursorDao = db.cursores()

    @Provides
    fun eventos(db: PetcareDb): EventoDao = db.eventos()

    @Provides
    fun registros(db: PetcareDb): RegistroDao = db.registros()

    @Provides
    fun alarmas(db: PetcareDb): AlarmaDao = db.alarmas()

    @Provides
    fun pesos(db: PetcareDb): com.udenar.petcare.data.salud.PesoDao = db.pesos()

    @Provides
    fun visitas(db: PetcareDb): com.udenar.petcare.data.salud.VisitaDao = db.visitas()

    @Provides
    fun preferencias(db: PetcareDb): com.udenar.petcare.data.local.PreferenciaDao = db.preferencias()
}

/** Supabase con la sesión solo en memoria: no reemplaza la sesión real guardada en el teléfono. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ModuloSupabase::class])
object ModuloSupabasePrueba {
    @Provides
    @Singleton
    fun cliente(): SupabaseClient = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY) {
        install(Auth) {
            autoLoadFromStorage = false
            autoSaveToStorage = false
            sessionManager = MemorySessionManager()
        }
        install(Postgrest)
        install(Storage)
        install(io.github.jan.supabase.realtime.Realtime)
    }
}

/** Sin WorkManager: las pruebas llaman al sincronizador directamente. */
@Singleton
class ProgramadorFalso @Inject constructor(mascotas: MascotaDao) : ProgramadorSync {
    var solicitudes = 0
    override fun solicitar() {
        solicitudes++
    }
    override val estado: Flow<EstadoSync> = mascotas.contarPendientes().map { EstadoSync(it, null) }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ModuloTrabajos::class])
abstract class ModuloTrabajosPrueba {
    @Binds
    abstract fun programador(p: ProgramadorFalso): ProgramadorSync
}

/** Reloj de alarmas en memoria: las pruebas no dejan alarmas reales en el teléfono. */
@Singleton
class RelojAlarmasFalso @Inject constructor() : com.udenar.petcare.data.alarmas.RelojAlarmas {
    val programadas = mutableMapOf<String, java.time.Instant>()
    override fun programar(datos: com.udenar.petcare.data.alarmas.DatosAlarma, cuando: java.time.Instant) {
        programadas[datos.clave] = cuando
    }
    override fun cancelar(clave: String) {
        programadas.remove(clave)
    }
    override fun puedeExactas() = true
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [com.udenar.petcare.di.ModuloAlarmas::class])
abstract class ModuloAlarmasPrueba {
    @Binds
    abstract fun reloj(r: RelojAlarmasFalso): com.udenar.petcare.data.alarmas.RelojAlarmas

    @Binds
    abstract fun recordatorios(p: com.udenar.petcare.data.alarmas.PlanificadorAlarmas): com.udenar.petcare.domain.cuidados.ProgramadorRecordatorios
}
