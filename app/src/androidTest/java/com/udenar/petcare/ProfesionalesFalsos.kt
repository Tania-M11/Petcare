package com.udenar.petcare

import com.udenar.petcare.di.ModuloProfesionales
import com.udenar.petcare.domain.modelo.ModoUso
import com.udenar.petcare.domain.profesionales.FiltroVeterinarios
import com.udenar.petcare.domain.profesionales.PerfilVeterinario
import com.udenar.petcare.domain.profesionales.RepositorioModo
import com.udenar.petcare.domain.profesionales.RepositorioVeterinarios
import com.udenar.petcare.domain.profesionales.ValidadorVeterinario
import com.udenar.petcare.domain.profesionales.Verificacion
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/** Veterinarios en memoria: el directorio se llena desde cada prueba. */
@Singleton
class VeterinariosFalso @Inject constructor() : RepositorioVeterinarios {
    override val miPerfil = MutableStateFlow<PerfilVeterinario?>(null)
    val directorio = mutableListOf<PerfilVeterinario>()
    var certificados = 0

    override suspend fun cargarMiPerfil() = miPerfil.value

    override suspend fun guardar(perfil: PerfilVeterinario) {
        require(ValidadorVeterinario.errores(perfil).isEmpty())
        miPerfil.value = perfil.copy(verificacion = miPerfil.value?.verificacion ?: Verificacion.SIN_CERTIFICADO)
    }

    override suspend fun subirCertificado(bytes: ByteArray, tipoMime: String) {
        certificados++
        miPerfil.value = miPerfil.value?.copy(verificacion = Verificacion.EN_REVISION)
    }

    private fun normal(t: String) = Normalizer.normalize(t.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    override suspend fun buscar(filtro: FiltroVeterinarios, lat: Double?, lon: Double?) = directorio.filter { v ->
        v.activo &&
            (filtro.ciudad.isBlank() || normal(v.ciudad) == normal(filtro.ciudad)) &&
            (filtro.especie == null || filtro.especie in v.especies) &&
            (!filtro.domicilio || v.domicilio) &&
            (filtro.servicio == null || filtro.servicio in v.servicios) &&
            (!filtro.soloVerificados || v.verificacion.verificado)
    }.sortedBy { it.nombre }

    override suspend fun obtener(id: String) = directorio.firstOrNull { it.id == id }
}

@Singleton
class ModoMemoria @Inject constructor() : RepositorioModo {
    override val modo = MutableStateFlow(ModoUso.PERSONAL)
    override fun cambiar(modo: ModoUso) {
        this.modo.value = modo
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ModuloProfesionales::class])
abstract class ModuloProfesionalesPrueba {
    @Binds abstract fun veterinarios(f: VeterinariosFalso): RepositorioVeterinarios

    @Binds abstract fun modo(m: ModoMemoria): RepositorioModo
}
