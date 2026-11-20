package com.udenar.petcare.di

import android.content.Context
import androidx.room.Room
import com.udenar.petcare.data.alarmas.AlarmaDao
import com.udenar.petcare.data.cuidados.EventoDao
import com.udenar.petcare.data.cuidados.RegistroDao
import com.udenar.petcare.data.local.CursorDao
import com.udenar.petcare.data.local.PetcareDb
import com.udenar.petcare.data.mascotas.MascotaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Base de datos del teléfono (las pruebas la reemplazan por una en memoria). */
@Module
@InstallIn(SingletonComponent::class)
object ModuloLocal {

    @Provides
    @Singleton
    fun db(@ApplicationContext contexto: Context): PetcareDb =
        Room.databaseBuilder(contexto, PetcareDb::class.java, "petcare.db").build()

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

    @Provides
    fun configQr(db: PetcareDb): com.udenar.petcare.data.local.ConfigQrDao = db.configQr()

    @Provides
    fun documentos(db: PetcareDb): com.udenar.petcare.data.documentos.DocumentoDao = db.documentos()

    @Provides
    fun paseos(db: PetcareDb): com.udenar.petcare.data.paseos.PaseoDao = db.paseos()
}
