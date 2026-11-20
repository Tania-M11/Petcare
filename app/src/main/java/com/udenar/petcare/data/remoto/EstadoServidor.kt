package com.udenar.petcare.data.remoto

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import javax.inject.Singleton

/** Comprueba que la app llega a Supabase llamando a la función pública `ping()`. */
@Singleton
class EstadoServidor @Inject constructor(private val supabase: SupabaseClient) {

    suspend fun conectado(): Boolean = runCatching {
        supabase.postgrest.rpc("ping").data.trim('"') == "petcare"
    }.getOrDefault(false)
}
