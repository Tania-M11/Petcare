package com.udenar.petcare.data.cuidados

import com.udenar.petcare.domain.cuenta.RepositorioCuenta
import com.udenar.petcare.domain.cuidados.Ancla
import com.udenar.petcare.domain.cuidados.Autor
import com.udenar.petcare.domain.cuidados.EstadoRegistro
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.Frecuencia
import com.udenar.petcare.domain.cuidados.MotorCuidados
import com.udenar.petcare.domain.cuidados.Origen
import com.udenar.petcare.domain.cuidados.RegistroCuidado
import com.udenar.petcare.domain.cuidados.RepositorioCuidados
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.cuidados.TipoFrecuencia
import com.udenar.petcare.domain.cuidados.Toma
import com.udenar.petcare.domain.sync.ProgramadorSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RepositorioCuidadosLocal @Inject constructor(
    private val eventos: EventoDao,
    private val registros: RegistroDao,
    private val sync: ProgramadorSync,
    private val cuenta: RepositorioCuenta,
    private val reloj: Clock,
) : RepositorioCuidados {

    override fun observarEventos(): Flow<List<EventoCuidado>> = eventos.observarActivos().map { l -> l.map { it.comoDominio() } }

    override fun observarRegistros(): Flow<List<RegistroCuidado>> = registros.observarActivos().map { l -> l.map { it.comoDominio() } }

    override fun agenda(fecha: LocalDate): Flow<List<Toma>> = combine(observarEventos(), observarRegistros()) { e, r ->
        MotorCuidados.agendaDelDia(fecha, LocalDate.now(reloj), e, r, reloj.zone)
    }

    override suspend fun guardarEvento(evento: EventoCuidado) {
        val anterior = eventos.porId(evento.id)
        eventos.guardar(evento.comoEntidad(anterior, siguienteCambio(anterior?.cambioLocal)))
        sync.solicitar()
    }

    override suspend fun eliminarEvento(id: String) {
        val actual = eventos.porId(id) ?: return
        eventos.guardar(actual.copy(eliminado = true, pendiente = true, cambioLocal = siguienteCambio(actual.cambioLocal)))
        sync.solicitar()
    }

    override suspend fun registrar(toma: Toma, estado: EstadoRegistro, notas: String?) {
        val id = MotorCuidados.idRegistro(toma.evento.id, toma.fechaHora)
        val anterior = registros.porId(id)
        val yo = cuenta.usuario.value
        registros.guardar(
            RegistroEntidad(
                id = id,
                mascotaId = toma.evento.mascotaId,
                eventoId = toma.evento.id,
                fechaProgramada = toma.fechaHora.toString(),
                estado = estado.name,
                // Provisional hasta sincronizar: el servidor pone el autor definitivo
                registradoPor = yo?.id,
                registradoPorNombre = yo?.nombre,
                registradoPorRol = anterior?.registradoPorRol,
                registradoEn = Instant.now(reloj).toString(),
                notas = notas?.trim()?.ifBlank { null },
                eliminado = false,
                actualizadoEn = anterior?.actualizadoEn,
                pendiente = true,
                cambioLocal = siguienteCambio(anterior?.cambioLocal),
            ),
        )
        sync.solicitar()
    }

    override suspend fun deshacer(toma: Toma) {
        val actual = registros.porId(MotorCuidados.idRegistro(toma.evento.id, toma.fechaHora)) ?: return
        registros.guardar(actual.copy(eliminado = true, pendiente = true, cambioLocal = siguienteCambio(actual.cambioLocal)))
        sync.solicitar()
    }

    /** Siempre crece, aunque el reloj del teléfono retroceda. */
    private fun siguienteCambio(anterior: Long?) = maxOf(reloj.millis(), (anterior ?: 0) + 1)
}

private val json = Json { ignoreUnknownKeys = true }

private fun EventoCuidado.comoEntidad(anterior: EventoEntidad?, cambio: Long) = EventoEntidad(
    id = id,
    mascotaId = mascotaId,
    tipo = tipo.name,
    titulo = titulo.trim(),
    frecuenciaTipo = frecuencia.tipo.name,
    frecuenciaN = frecuencia.n,
    diasSemana = frecuencia.diasSemana.map { it.value }.sorted().joinToString(","),
    horas = frecuencia.horas.sorted().joinToString(",") { it.toString() },
    ancla = ancla.name,
    fechaInicio = fechaInicio.toString(),
    fechaFin = fechaFin?.toString(),
    avisoAnticipadoDias = avisoAnticipadoDias,
    detalle = JsonObject(detalle.filterValues { it.isNotBlank() }.mapValues { JsonPrimitive(it.value.trim()) }).toString(),
    origen = origen.name,
    origenRef = origenRef,
    creadoPor = anterior?.creadoPor,
    creadoPorNombre = anterior?.creadoPorNombre,
    creadoPorRol = anterior?.creadoPorRol,
    oculto = oculto,
    eliminado = false,
    actualizadoEn = anterior?.actualizadoEn,
    pendiente = true,
    cambioLocal = cambio,
)

internal fun EventoEntidad.comoDominio() = EventoCuidado(
    id = id,
    mascotaId = mascotaId,
    tipo = TipoCuidado.entries.firstOrNull { it.name == tipo } ?: TipoCuidado.PERSONALIZADO,
    titulo = titulo,
    frecuencia = Frecuencia(
        tipo = TipoFrecuencia.entries.firstOrNull { it.name == frecuenciaTipo } ?: TipoFrecuencia.UNICO,
        n = frecuenciaN,
        diasSemana = diasSemana.split(',').mapNotNull { it.trim().toIntOrNull() }.map { DayOfWeek.of(it) }.toSet(),
        horas = horas.split(',').filter { it.isNotBlank() }.map { LocalTime.parse(it.trim()) },
    ),
    ancla = Ancla.entries.firstOrNull { it.name == ancla } ?: Ancla.CALENDARIO,
    fechaInicio = LocalDate.parse(fechaInicio),
    fechaFin = fechaFin?.let { LocalDate.parse(it) },
    avisoAnticipadoDias = avisoAnticipadoDias,
    detalle = runCatching { json.parseToJsonElement(detalle) as JsonObject }.getOrNull()
        ?.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() }.orEmpty(),
    origen = Origen.entries.firstOrNull { it.name == origen } ?: Origen.MANUAL,
    origenRef = origenRef,
    creadoPor = Autor(creadoPor, creadoPorNombre, creadoPorRol),
    oculto = oculto,
    porSubir = pendiente,
)

internal fun RegistroEntidad.comoDominio() = RegistroCuidado(
    id = id,
    mascotaId = mascotaId,
    eventoId = eventoId,
    fechaProgramada = LocalDateTime.parse(fechaProgramada),
    estado = EstadoRegistro.entries.firstOrNull { it.name == estado } ?: EstadoRegistro.REALIZADA,
    registradoPor = Autor(registradoPor, registradoPorNombre, registradoPorRol),
    registradoEn = Instant.parse(registradoEn),
    notas = notas,
    porSubir = pendiente,
)
