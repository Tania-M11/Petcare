package com.udenar.petcare.di

import com.udenar.petcare.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton

/** Un solo cliente de Supabase para toda la app (URL y clave publishable desde local.properties). */
@Module
@InstallIn(SingletonComponent::class)
object ModuloSupabase {

    @Provides
    @Singleton
    fun cliente(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
    ) {
        install(Auth) {
            // Los enlaces de los correos vuelven a la app (MainActivity)
            scheme = "petcare"
            host = "auth"
        }
        install(Postgrest)
        install(Storage)
        install(io.github.jan.supabase.realtime.Realtime)
    }
}
