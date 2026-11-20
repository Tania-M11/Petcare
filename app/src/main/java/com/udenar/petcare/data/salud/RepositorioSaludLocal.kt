package com.udenar.petcare.data.salud

import com.udenar.petcare.data.mascotas.MascotaDao
import com.udenar.petcare.domain.cuenta.RepositorioCuenta
import com.udenar.petcare.domain.cuidados.Autor
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.Frecuencia
import com.udenar.petcare.domain.cuidados.RepositorioCuidados
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.cuidados.TipoFrecuencia
import com.udenar.petcare.domain.salud.RegistroPeso
import com.udenar.petcare.domain.salud.RepositorioSalud
import com.udenar.petcare.domain.salud.Visita
import com.udenar.petcare.domain.sync.ProgramadorSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RepositorioSaludLocal @Inject constructor(
    private val pesos: PesoDao,
    private val visitas: VisitaDao,
    private val mascotas: MascotaDao,
    private val cuidados: RepositorioCuidados,
    private val cuenta: RepositorioCuenta,
    private val sync: ProgramadorSync,
    private val reloj: Clock,
    private val propiedad: com.udenar.petcare.data.compartir.Propiedad,
) : RepositorioSalud {

    override fun observarPesos(mascotaId: String): Flow<List<RegistroPeso>> = pesos.observarDe(mascotaId).map { l ->
        l.map { RegistroPeso(it.id, it.mascotaId, LocalDate.parse(it.fecha), it.gramos, it.notas, Autor(it.registradoPor, it.registradoPorNombre, it.registradoPorRol), it.pendiente) }
    }

    override fun observarVisitas(mascotaId: String): Flow<List<Visita>> = visitas.observarDe(mascotaId).map { l ->
        l.map {
            Visita(
                it.id, it.mascotaId, LocalDate.parse(it.fecha), it.motivo, it.veterinario, it.clinica, it.tratamiento,
                it.medicamentos, it.notas, it.proximaCitaId, Autor(it.registradoPor, it.registradoPorNombre, it.registradoPorRol),
                Instant.parse(it.registradoEn), it.pendiente,
            )
        }
    }

    override suspend fun registrarPeso(mascotaId: String, fecha: LocalDate, gramos: Int, notas: String?) {
        propiedad.exigir(mascotaId)
        val yo = cuenta.usuario.value
        val ultima = pesos.ultimaFecha(mascotaId)?.let { LocalDate.parse(it) }
        pesos.guardar(
            PesoEntidad(
                UUID.randomUUID().toString(), mascotaId, fecha.toString(), gramos, notas?.trim()?.ifBlank { null },
                yo?.id, yo?.nombre, null, Instant.now(reloj).toString(), false, null, true, reloj.millis(),
            ),
        )
        // RF-10: el peso más reciente es el peso actual de la mascota
        if (ultima == null || !fecha.isBefore(ultima)) actualizarPesoDeFicha(mascotaId, gramos)
        sync.solicitar()
    }

    private suspend fun actualizarPesoDeFicha(mascotaId: String, gramos: Int) {
        val m = mascotas.porId(mascotaId) ?: return
        if (m.pesoGramos == gramos) return
        mascotas.guardar(m.copy(pesoGramos = gramos, pendiente = true, cambioLocal = maxOf(reloj.millis(), m.cambioLocal + 1)))
    }

    override suspend fun eliminarPeso(id: String) {
        val p = pesos.porId(id) ?: return
        propiedad.exigir(p.mascotaId)
        pesos.guardar(p.copy(eliminado = true, pendiente = true, cambioLocal = maxOf(reloj.millis(), p.cambioLocal + 1)))
        sync.solicitar()
    }

    override suspend fun guardarVisita(visita: Visita, proximaCita: LocalDateTime?) {
        propiedad.exigir(visita.mascotaId)
        val anterior = visitas.porId(visita.id)
        // Recordatorio de la próxima cita: un cuidado CITA_VET de una sola vez, con aviso un día antes
        var citaId = anterior?.proximaCitaId
        if (proximaCita != null) {
            citaId = citaId ?: UUID.randomUUID().toString()
            cuidados.guardarEvento(
                EventoCuidado(
                    id = citaId, mascotaId = visita.mascotaId, tipo = TipoCuidado.CITA_VET,
                    titulo = "Cita: ${visita.motivo}".take(80),
                    frecuencia = Frecuencia(TipoFrecuencia.UNICO, horas = listOf(proximaCita.toLocalTime())),
                    fechaInicio = proximaCita.toLocalDate(), avisoAnticipadoDias = 1,
                    detalle = listOfNotNull(visita.veterinario?.let { "veterinario" to it }, visita.clinica?.let { "clinica" to it }).toMap(),
                ),
            )
        } else if (citaId != null) {
            cuidados.eliminarEvento(citaId)
            citaId = null
        }
        val yo = cuenta.usuario.value
        visitas.guardar(
            VisitaEntidad(
                id = visita.id, mascotaId = visita.mascotaId, fecha = visita.fecha.toString(), motivo = visita.motivo.trim(),
                veterinario = visita.veterinario?.trim()?.ifBlank { null }, clinica = visita.clinica?.trim()?.ifBlank { null },
                tratamiento = visita.tratamiento?.trim()?.ifBlank { null }, medicamentos = visita.medicamentos?.trim()?.ifBlank { null },
                notas = visita.notas?.trim()?.ifBlank { null }, proximaCitaId = citaId,
                registradoPor = anterior?.registradoPor ?: yo?.id, registradoPorNombre = anterior?.registradoPorNombre ?: yo?.nombre,
                registradoPorRol = anterior?.registradoPorRol, registradoEn = anterior?.registradoEn ?: Instant.now(reloj).toString(),
                eliminado = false, actualizadoEn = anterior?.actualizadoEn, pendiente = true,
                cambioLocal = maxOf(reloj.millis(), (anterior?.cambioLocal ?: 0) + 1),
            ),
        )
        sync.solicitar()
    }

    override suspend fun eliminarVisita(id: String) {
        val v = visitas.porId(id) ?: return
        propiedad.exigir(v.mascotaId)
        v.proximaCitaId?.let { cuidados.eliminarEvento(it) }
        visitas.guardar(v.copy(eliminado = true, pendiente = true, cambioLocal = maxOf(reloj.millis(), v.cambioLocal + 1)))
        sync.solicitar()
    }
}
