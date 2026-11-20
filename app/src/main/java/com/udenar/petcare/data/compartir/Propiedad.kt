package com.udenar.petcare.data.compartir

import com.udenar.petcare.data.mascotas.MascotaDao
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import javax.inject.Inject

/** Error cuando un cuidador intenta algo que solo puede hacer el propietario (sección 5). */
class SoloPropietarioException : IllegalStateException("Solo el dueño de la mascota puede hacer esto.")

/**
 * ¿La mascota es del usuario actual? Una mascota sin dueño conocido (creada en este teléfono
 * y aún sin subir) es del usuario. Las reglas del servidor lo vuelven a verificar.
 */
class Propiedad @Inject constructor(
    private val mascotas: MascotaDao,
    private val supabase: SupabaseClient,
    private val cuenta: com.udenar.petcare.domain.cuenta.RepositorioCuenta,
) {
    suspend fun esMia(mascotaId: String): Boolean {
        val m = mascotas.porId(mascotaId) ?: return true
        val yo = supabase.auth.currentUserOrNull()?.id ?: cuenta.usuario.value?.id ?: return true
        return m.propietarioId == null || m.propietarioId == yo
    }

    suspend fun exigir(mascotaId: String) {
        if (!esMia(mascotaId)) throw SoloPropietarioException()
    }
}
