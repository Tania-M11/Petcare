package com.udenar.petcare.ui.mascotas

import com.udenar.petcare.domain.modelo.Icono
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.udenar.petcare.domain.compartir.CompartirException
import com.udenar.petcare.domain.compartir.Miembro
import com.udenar.petcare.domain.compartir.RepositorioCompartir
import com.udenar.petcare.domain.cuidados.AreaCuidado
import com.udenar.petcare.domain.cuidados.DescripcionCuidados
import com.udenar.petcare.domain.cuidados.EstadoCuidado
import com.udenar.petcare.domain.cuidados.EstadoRegistro
import com.udenar.petcare.domain.cuidados.NivelCuidado
import com.udenar.petcare.domain.cuidados.RepositorioCuidados
import com.udenar.petcare.domain.mascotas.FichaMascota
import com.udenar.petcare.domain.salud.RegistroPeso
import com.udenar.petcare.domain.salud.RepositorioSalud
import com.udenar.petcare.domain.salud.Visita
import com.udenar.petcare.ui.cuidados.icono
import com.udenar.petcare.domain.mascotas.CodigoMascota
import com.udenar.petcare.domain.cuidados.ResumenCuidado
import com.udenar.petcare.domain.cuidados.resumir
import kotlinx.coroutines.flow.combine
import java.time.LocalDateTime
import com.udenar.petcare.domain.mascotas.RepositorioMascotas
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.ui.navegacion.RutaDetalleMascota
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

sealed interface EstadoCodigo {
    data object Cargando : EstadoCodigo
    data class Listo(val codigo: CodigoMascota) : EstadoCodigo
    /** La mascota aún no llega al servidor (se creó sin internet). */
    data object SinSincronizar : EstadoCodigo
    data object SinConexion : EstadoCodigo
}

/** Ficha de una mascota. [mascota]: null = cargando; Eliminada = ya no existe. */
@HiltViewModel
class DetalleMascotaViewModel @Inject constructor(
    estado: SavedStateHandle,
    private val repo: RepositorioMascotas,
    private val reloj: Clock,
    cuidados: RepositorioCuidados,
    private val salud: RepositorioSalud,
    private val compartir: RepositorioCompartir,
    private val preferencias: com.udenar.petcare.data.local.PreferenciaDao,
    private val recordatorios: com.udenar.petcare.domain.cuidados.ProgramadorRecordatorios,
    private val documentosRepo: com.udenar.petcare.domain.documentos.RepositorioDocumentos,
    private val paseosRepo: com.udenar.petcare.domain.paseos.RepositorioPaseos,
    private val atencion: com.udenar.petcare.domain.profesionales.RepositorioAtencionVet,
    private val descartes: com.udenar.petcare.data.local.Descartes,
    private val sintomasRepo: com.udenar.petcare.domain.salud.RepositorioSintomas,
) : ViewModel() {

    val sintomas: StateFlow<List<com.udenar.petcare.domain.salud.Sintoma>> by lazy {
        sintomasRepo.observar(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    fun eliminarSintoma(sintomaId: String) = viewModelScope.launch { sintomasRepo.eliminar(sintomaId) }

    val paseos: StateFlow<List<com.udenar.petcare.domain.paseos.Paseo>> by lazy {
        paseosRepo.observar(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    val documentos: StateFlow<List<com.udenar.petcare.domain.documentos.Documento>> by lazy {
        documentosRepo.observar(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    fun agregarDocumento(origen: String, nombre: String, tipo: com.udenar.petcare.domain.documentos.TipoDocumento, fecha: LocalDate, visitaId: String?, avisar: (String) -> Unit) =
        viewModelScope.launch {
            try {
                documentosRepo.agregar(id, origen, nombre, tipo, fecha, visitaId)
            } catch (e: com.udenar.petcare.domain.documentos.DocumentoException) {
                avisar(e.message ?: "No se pudo guardar")
            } catch (e: Exception) {
                avisar("No se pudo leer el archivo")
            }
        }

    fun eliminarDocumento(d: com.udenar.petcare.domain.documentos.Documento) = viewModelScope.launch { documentosRepo.eliminar(d.id) }

    fun abrirDocumento(d: com.udenar.petcare.domain.documentos.Documento, abrir: (String) -> Unit, avisar: (String) -> Unit) = viewModelScope.launch {
        val ruta = documentosRepo.archivo(d.id)
        if (ruta != null) abrir(ruta) else avisar("Conéctate a internet para descargarlo")
    }

    /** RF-14: si este teléfono recibe los recordatorios de esta mascota (cada miembro decide). */
    val recordatoriosActivos: StateFlow<Boolean> = preferencias.silenciadas()
        .map { id !in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun cambiarRecordatorios(activos: Boolean) = viewModelScope.launch {
        preferencias.guardar(com.udenar.petcare.data.local.PreferenciaMascota(id, activos))
        recordatorios.reprogramar()
    }

    /** Personas que cuidan esta mascota (solo la ve el dueño; requiere internet). */
    private val _miembros = MutableStateFlow<List<Miembro>?>(null)
    val miembros: StateFlow<List<Miembro>?> = _miembros.asStateFlow()

    private val _vets = kotlinx.coroutines.flow.MutableStateFlow<List<com.udenar.petcare.domain.profesionales.VetVinculado>?>(null)
    /** Veterinarios vinculados (RF-26); null = sin conexión. */
    val vets: StateFlow<List<com.udenar.petcare.domain.profesionales.VetVinculado>?> = _vets.asStateFlow()

    fun cargarVets() = viewModelScope.launch { _vets.value = runCatching { atencion.vetsDe(id) }.getOrNull() }

    fun desvincularVet(v: com.udenar.petcare.domain.profesionales.VetVinculado, avisar: (String) -> Unit) = viewModelScope.launch {
        try {
            atencion.desvincular(id, v.id)
            avisar("${v.nombre} ya no ve la ficha")
        } catch (e: com.udenar.petcare.domain.profesionales.ProfesionalException) {
            avisar(e.message ?: "No se pudo desvincular")
        }
        cargarVets()
    }

    fun cargarMiembros() = viewModelScope.launch {
        _miembros.value = runCatching { compartir.miembros(id) }.getOrNull()
    }

    fun quitar(m: Miembro, avisar: (String) -> Unit) = viewModelScope.launch {
        try {
            compartir.quitar(id, m.usuarioId)
            avisar("${m.nombre.substringBefore(' ')} ya no cuida a esta mascota")
            cargarMiembros()
        } catch (e: CompartirException) {
            avisar(e.message ?: "No se pudo completar")
        }
    }

    fun salir(onListo: () -> Unit, avisar: (String) -> Unit) = viewModelScope.launch {
        try {
            compartir.salir(id)
            onListo()
        } catch (e: CompartirException) {
            avisar(e.message ?: "No se pudo completar")
        }
    }

    fun regenerarCodigo(avisar: (String) -> Unit) = viewModelScope.launch {
        try {
            compartir.regenerarCodigo(id)
            avisar("Código nuevo: el anterior ya no sirve")
            cargarCodigo()
        } catch (e: CompartirException) {
            avisar(e.message ?: "No se pudo completar")
        }
    }

    fun desactivarCodigo(avisar: (String) -> Unit) = viewModelScope.launch {
        try {
            compartir.desactivarCodigo(id)
            avisar("Código desactivado. Nadie podrá pedir cuidarla hasta que generes uno nuevo.")
            cargarCodigo()
        } catch (e: CompartirException) {
            avisar(e.message ?: "No se pudo completar")
        }
    }

    val id = estado.toRoute<RutaDetalleMascota>().id

    val mascota: StateFlow<Mascota?> = repo.observar()
        .map { lista -> lista.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Cuidados de esta mascota con su próxima fecha y estado. */
    val cuidados: StateFlow<List<ResumenCuidado>> = combine(cuidados.observarEventos(), cuidados.observarRegistros()) { e, r ->
        val ahora = LocalDateTime.now(reloj)
        e.filter { it.mascotaId == id }.map { resumir(it, r, ahora, reloj.zone) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pesos: StateFlow<List<RegistroPeso>> = salud.observarPesos(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val visitas: StateFlow<List<Visita>> = salud.observarVisitas(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Estado de cuidado (RF-16) con la agenda de hoy de esta mascota. */
    val estadoCuidado: StateFlow<Map<AreaCuidado, NivelCuidado>> = combine(this.cuidados, cuidados.agenda(LocalDate.now(reloj))) { res, tomas ->
        EstadoCuidado.calcular(res, tomas.filter { it.evento.mascotaId == id }, LocalDateTime.now(reloj))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Línea de tiempo: cuidados hechos u omitidos, pesos y visitas (RF-15). */
    /** Progreso (Fase 6.4): cumplimiento semanal, racha, paseos, equipo y logros. */
    val progreso: StateFlow<com.udenar.petcare.domain.recomendaciones.ProgresoMascota?> = combine(
        cuidados.observarEventos(), cuidados.observarRegistros(), paseosRepo.observar(id), pesos,
    ) { ev, reg, pas, ps ->
        com.udenar.petcare.domain.recomendaciones.Progreso.calcular(
            ev.filter { it.mascotaId == id }, reg.filter { it.mascotaId == id }, pas, ps, LocalDate.now(reloj), reloj.zone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Recomendaciones solo de esta mascota (las mismas reglas que Hoy). */
    val recomendaciones: StateFlow<List<com.udenar.petcare.domain.recomendaciones.Recomendacion>> = combine(
        mascota, cuidados.observarEventos(), cuidados.observarRegistros(), pesos, combine(paseosRepo.observar(id), descartes.ids, ::Pair),
    ) { m, ev, reg, ps, (pas, descartadas) ->
        if (m == null) emptyList()
        else com.udenar.petcare.domain.recomendaciones.MotorRecomendaciones.generar(
            com.udenar.petcare.domain.recomendaciones.ContextoRecomendaciones(LocalDate.now(reloj), listOf(m), ev, reg, ps, pas, reloj.zone),
        ).filter { it.id !in descartadas }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun descartar(r: String) = descartes.descartar(r)

    val historial: StateFlow<List<ItemHistorial>> = combine(
        cuidados.observarEventos(), cuidados.observarRegistros(), pesos, visitas,
        combine(paseosRepo.observar(id), sintomasRepo.observar(id), ::Pair),
    ) { ev, reg, ps, vs, (pas, sin) ->
        construirHistorial(ev, reg.filter { it.mascotaId == id }, ps, vs, pas, sin, reloj.zone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun registrarPeso(fecha: LocalDate, gramos: Int) = viewModelScope.launch { salud.registrarPeso(id, fecha, gramos) }

    fun eliminarPeso(pesoId: String) = viewModelScope.launch { salud.eliminarPeso(pesoId) }

    private val _codigo = MutableStateFlow<EstadoCodigo>(EstadoCodigo.Cargando)
    val codigo: StateFlow<EstadoCodigo> = _codigo.asStateFlow()

    val hoy: LocalDate get() = LocalDate.now(reloj)

    init {
        cargarCodigo()
    }

    fun cargarCodigo() {
        _codigo.value = EstadoCodigo.Cargando
        viewModelScope.launch {
            _codigo.value = try {
                repo.codigo(id)?.let { EstadoCodigo.Listo(it) } ?: EstadoCodigo.SinSincronizar
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                EstadoCodigo.SinConexion
            }
        }
    }

    fun eliminar(onListo: () -> Unit) {
        viewModelScope.launch {
            repo.eliminar(id)
            onListo()
        }
    }
}
