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

    override suspend fun subirFoto(bytes: ByteArray) = "https://ejemplo.test/perfil.jpg"

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

/** Prestadores en memoria: catálogo fijo y directorio que llena cada prueba. */
@Singleton
class PrestadoresFalso @Inject constructor() : com.udenar.petcare.domain.profesionales.RepositorioPrestadores {
    override val miPerfil = MutableStateFlow<com.udenar.petcare.domain.profesionales.PerfilPrestador?>(null)
    val directorio = mutableListOf<com.udenar.petcare.domain.profesionales.PerfilPrestador>()
    private val catalogo = listOf(
        com.udenar.petcare.domain.profesionales.Categoria("PASEADOR", "Paseadores de mascotas", "🦮", 1),
        com.udenar.petcare.domain.profesionales.Categoria("BANO_PELUQUERIA", "Baño y peluquería", "✂️", 2),
        com.udenar.petcare.domain.profesionales.Categoria("CUIDADO_TEMPORAL", "Cuidado temporal de mascotas", "🏠", 3),
    )

    override suspend fun cargarMiPerfil() = miPerfil.value
    override suspend fun guardar(perfil: com.udenar.petcare.domain.profesionales.PerfilPrestador) {
        require(com.udenar.petcare.domain.profesionales.ValidadorPrestador.errores(perfil).isEmpty())
        miPerfil.value = perfil
    }
    override suspend fun categorias() = catalogo
    override suspend fun subirFoto(bytes: ByteArray) = "https://ejemplo.test/foto.jpg"
    override suspend fun borrarFoto(url: String) = Unit
    override suspend fun buscar(filtro: com.udenar.petcare.domain.profesionales.FiltroPrestadores, lat: Double?, lon: Double?) = directorio.filter { p ->
        p.activo && (filtro.categoria == null || filtro.categoria in p.categorias) &&
            (filtro.especie == null || filtro.especie in p.especies) && (filtro.modalidad == null || filtro.modalidad in p.modalidades) &&
            (filtro.ciudad.isBlank() || p.ciudad.equals(filtro.ciudad.trim(), ignoreCase = true)) &&
            (filtro.zona.isBlank() || p.zona.orEmpty().contains(filtro.zona.trim(), ignoreCase = true))
    }.sortedWith(compareByDescending<com.udenar.petcare.domain.profesionales.PerfilPrestador> { it.recibiendo }.thenBy { it.nombre })
    override suspend fun obtener(id: String) = directorio.firstOrNull { it.id == id }
}

/** Cotizaciones en memoria; [detalle] imita las reglas del servidor sobre lo compartido (RF-36). */
@Singleton
class CotizacionesFalsas @Inject constructor() : com.udenar.petcare.domain.profesionales.RepositorioCotizaciones {
    val solicitudes = mutableListOf<com.udenar.petcare.domain.profesionales.SolicitudPrestador>()
    val salud = mutableMapOf<String, com.udenar.petcare.domain.profesionales.SaludCompartida>()
    val contacto = mutableMapOf<String, com.udenar.petcare.domain.profesionales.ContactoCompartido>()

    override suspend fun solicitar(
        mascotaId: String, prestadorId: String, categoriaId: String, servicioId: String?, modalidad: com.udenar.petcare.domain.profesionales.ModalidadPrestador,
        inicio: java.time.LocalDateTime, fin: java.time.LocalDateTime?, descripcion: String, mensaje: String?,
        salud: com.udenar.petcare.domain.profesionales.SaludCompartida?, contacto: com.udenar.petcare.domain.profesionales.ContactoCompartido?,
    ): String {
        val id = "c${solicitudes.size + 1}"
        solicitudes += com.udenar.petcare.domain.profesionales.SolicitudPrestador(
            id, mascotaId, "Ana María", prestadorId, "Paseos Felices", categoriaId, "Paseo de 1 hora",
            com.udenar.petcare.domain.profesionales.ModoPrecio.FIJO, 15000, modalidad, inicio, fin, descripcion, mensaje,
            resumen = com.udenar.petcare.domain.profesionales.ResumenMascota("Max", com.udenar.petcare.domain.modelo.Especie.PERRO),
        )
        salud?.let { this.salud[id] = it }
        contacto?.let { this.contacto[id] = it }
        return id
    }

    fun cambiar(id: String, f: (com.udenar.petcare.domain.profesionales.SolicitudPrestador) -> com.udenar.petcare.domain.profesionales.SolicitudPrestador) {
        val i = solicitudes.indexOfFirst { it.id == id }
        solicitudes[i] = f(solicitudes[i])
    }

    private fun estado(id: String, e: com.udenar.petcare.domain.profesionales.EstadoSolicitudPres) = cambiar(id) { it.copy(estado = e) }

    override suspend fun misSolicitudes() = solicitudes.toList()
    override suspend fun confirmar(id: String) = estado(id, com.udenar.petcare.domain.profesionales.EstadoSolicitudPres.ACEPTADA)
    override suspend fun dejarDeCompartir(id: String) {
        salud.remove(id); contacto.remove(id)
    }
    override suspend fun bandeja() = solicitudes.toList()
    override suspend fun cotizar(id: String, propuesta: com.udenar.petcare.domain.profesionales.Propuesta) = cambiar(id) {
        it.copy(estado = com.udenar.petcare.domain.profesionales.EstadoSolicitudPres.COTIZADA, cotizacionPrecio = propuesta.precioCop,
            cotizacionObservaciones = propuesta.observaciones, propuestaInicio = propuesta.inicio)
    }
    override suspend fun aceptar(id: String) = estado(id, com.udenar.petcare.domain.profesionales.EstadoSolicitudPres.ACEPTADA)
    override suspend fun rechazar(id: String, motivo: String?) = estado(id, com.udenar.petcare.domain.profesionales.EstadoSolicitudPres.RECHAZADA)
    override suspend fun completar(id: String) = estado(id, com.udenar.petcare.domain.profesionales.EstadoSolicitudPres.COMPLETADA)
    override suspend fun cancelar(id: String, motivo: String?) = estado(id, com.udenar.petcare.domain.profesionales.EstadoSolicitudPres.CANCELADA)
    override suspend fun detalle(id: String): com.udenar.petcare.domain.profesionales.DetalleSolicitudPres {
        val s = solicitudes.first { it.id == id }
        return com.udenar.petcare.domain.profesionales.DetalleSolicitudPres(
            s, salud[id]?.takeIf { s.estado.abierta },
            contacto[id]?.takeIf { s.estado == com.udenar.petcare.domain.profesionales.EstadoSolicitudPres.ACEPTADA },
        )
    }
}

/** Reseñas y reportes en memoria. */
@Singleton
class ResenasFalsas @Inject constructor() : com.udenar.petcare.domain.profesionales.RepositorioResenas {
    val resenas = mutableListOf<Pair<String, com.udenar.petcare.domain.profesionales.Resena>>() // profesional → reseña
    val reportes = mutableListOf<Pair<com.udenar.petcare.domain.profesionales.TipoReporte, String>>()

    override suspend fun de(profesionalId: String, tipo: com.udenar.petcare.domain.profesionales.TipoResena, pagina: Int, tamano: Int) =
        resenas.filter { it.first == profesionalId }.map { it.second }.drop(pagina * tamano).take(tamano)
    override suspend fun mias() = resenas.map { it.second }.filter { it.mia }.associateBy { it.solicitudId }
    override suspend fun guardar(solicitudId: String, tipo: com.udenar.petcare.domain.profesionales.TipoResena, estrellas: Int, comentario: String?) {
        resenas.removeAll { it.second.solicitudId == solicitudId }
        resenas += "?" to com.udenar.petcare.domain.profesionales.Resena("r$solicitudId", solicitudId, "Ana María", estrellas, comentario, java.time.LocalDate.now(), mia = true)
    }
    override suspend fun eliminar(solicitudId: String) {
        resenas.removeAll { it.second.solicitudId == solicitudId }
    }
    override suspend fun reportar(tipo: com.udenar.petcare.domain.profesionales.TipoReporte, refId: String, motivo: com.udenar.petcare.domain.profesionales.MotivoReporte, detalle: String?) {
        reportes += tipo to refId
    }
}

/** Solicitudes y fichas en memoria para las pruebas de interfaz. */
@Singleton
class AtencionVetFalsa @Inject constructor() : com.udenar.petcare.domain.profesionales.RepositorioAtencionVet {
    val solicitudes = mutableListOf<com.udenar.petcare.domain.profesionales.SolicitudVet>()
    val pacientes = mutableListOf<com.udenar.petcare.domain.modelo.Mascota>()
    val visitas = mutableListOf<String>()

    override suspend fun solicitar(
        mascotaId: String, veterinarioId: String, servicio: com.udenar.petcare.domain.profesionales.ServicioVet,
        modalidad: com.udenar.petcare.domain.profesionales.Modalidad, fecha: java.time.LocalDateTime, mensaje: String?,
    ): String {
        val id = "s${solicitudes.size + 1}"
        solicitudes += com.udenar.petcare.domain.profesionales.SolicitudVet(
            id, mascotaId, "Max", com.udenar.petcare.domain.modelo.Especie.PERRO, "Ana María", null, veterinarioId, "Laura Gómez",
            servicio, modalidad, fecha, mensaje = mensaje,
        )
        return id
    }

    private fun cambiar(id: String, f: (com.udenar.petcare.domain.profesionales.SolicitudVet) -> com.udenar.petcare.domain.profesionales.SolicitudVet) {
        val i = solicitudes.indexOfFirst { it.id == id }
        solicitudes[i] = f(solicitudes[i])
    }

    override suspend fun misSolicitudes() = solicitudes.toList()
    override suspend fun cancelar(id: String) = cambiar(id) { it.copy(estado = com.udenar.petcare.domain.profesionales.EstadoSolicitudVet.CANCELADA) }
    override suspend fun vetsDe(mascotaId: String) = emptyList<com.udenar.petcare.domain.profesionales.VetVinculado>()
    override suspend fun bandeja() = solicitudes.toList()
    override suspend fun resolver(id: String, aceptar: Boolean, fecha: java.time.LocalDateTime?, motivo: String?) = cambiar(id) {
        if (aceptar) it.copy(estado = com.udenar.petcare.domain.profesionales.EstadoSolicitudVet.ACEPTADA, fechaConfirmada = fecha ?: it.fechaPreferida)
        else it.copy(estado = com.udenar.petcare.domain.profesionales.EstadoSolicitudVet.RECHAZADA, motivoRechazo = motivo)
    }
    override suspend fun completar(id: String) = cambiar(id) { it.copy(estado = com.udenar.petcare.domain.profesionales.EstadoSolicitudVet.COMPLETADA) }
    override suspend fun pacientes() = pacientes.map { com.udenar.petcare.domain.profesionales.Paciente(it) }
    override suspend fun ficha(mascotaId: String) = com.udenar.petcare.domain.profesionales.FichaClinica(
        pacientes.first { it.id == mascotaId }, emptyList(),
        visitas.map { com.udenar.petcare.domain.salud.Visita(it, mascotaId, java.time.LocalDate.now(), it) }, emptyList(), emptyList(),
    )
    override suspend fun registrarVisita(mascotaId: String, fecha: java.time.LocalDate, motivo: String, tratamiento: String?, medicamentos: String?, notas: String?) {
        visitas += motivo
    }
    override suspend fun registrarCuidado(mascotaId: String, cuidado: com.udenar.petcare.domain.profesionales.NuevoCuidadoClinico) = Unit
    override suspend fun registrarPeso(mascotaId: String, gramos: Int) = Unit
    override suspend fun subirDocumento(mascotaId: String, nombre: String, tipo: com.udenar.petcare.domain.documentos.TipoDocumento, bytes: ByteArray, mime: String) = Unit
    override suspend fun descargarDocumento(ruta: String) = ByteArray(0)
    override suspend fun desvincular(mascotaId: String, veterinarioId: String) {
        pacientes.removeAll { it.id == mascotaId }
    }
}

/** Fotos de solicitudes en memoria: registra lo adjuntado para las pruebas. */
@Singleton
class FotosSolicitudesFalsas @Inject constructor() : com.udenar.petcare.domain.profesionales.FotosSolicitudes {
    val adjuntadas = mutableMapOf<String, Int>()
    override suspend fun adjuntar(tipo: com.udenar.petcare.domain.profesionales.TipoSolicitud, solicitudId: String, fotos: List<ByteArray>) {
        if (fotos.isNotEmpty()) adjuntadas[solicitudId] = fotos.size
    }
    override suspend fun descargar(ruta: String) = ByteArray(0)
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ModuloProfesionales::class])
abstract class ModuloProfesionalesPrueba {
    @Binds abstract fun veterinarios(f: VeterinariosFalso): RepositorioVeterinarios

    @Binds abstract fun modo(m: ModoMemoria): RepositorioModo

    @Binds abstract fun prestadores(p: PrestadoresFalso): com.udenar.petcare.domain.profesionales.RepositorioPrestadores

    @Binds abstract fun cotizaciones(c: CotizacionesFalsas): com.udenar.petcare.domain.profesionales.RepositorioCotizaciones

    @Binds abstract fun resenas(r: ResenasFalsas): com.udenar.petcare.domain.profesionales.RepositorioResenas

    @Binds abstract fun atencion(a: AtencionVetFalsa): com.udenar.petcare.domain.profesionales.RepositorioAtencionVet

    @Binds abstract fun fotosSolicitudes(f: FotosSolicitudesFalsas): com.udenar.petcare.domain.profesionales.FotosSolicitudes
}
