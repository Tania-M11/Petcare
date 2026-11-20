package com.udenar.petcare

import com.udenar.petcare.data.remoto.EstadoServidor
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/** Parte 0.3: el teléfono llega a Supabase con las claves de local.properties (requiere internet). */
@HiltAndroidTest
class ServidorTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject
    lateinit var servidor: EstadoServidor

    @Before
    fun inyectar() = hilt.inject()

    @Test
    fun pingResponde() = runBlocking {
        assertTrue("Revisa SUPABASE_URL y SUPABASE_PUBLISHABLE_KEY en local.properties", servidor.conectado())
    }
}
