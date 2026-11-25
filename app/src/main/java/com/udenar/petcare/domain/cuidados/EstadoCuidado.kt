package com.udenar.petcare.domain.cuidados

import com.udenar.petcare.domain.modelo.Icono
import java.time.LocalDateTime

/** Categorías del estado de cuidado (RF-16). */
enum class AreaCuidado(val titulo: String, val icono: Icono) {
    VACUNAS("Vacunas", Icono.VACUNA),
    MEDICAMENTOS("Medicamentos", Icono.MEDICAMENTO),
    HIGIENE("Higiene", Icono.BANO),
    COMIDAS("Comidas", Icono.COMIDA),
    CITAS("Citas", Icono.VETERINARIO),
    ACTIVIDAD("Actividad física", Icono.PASEO),
}

enum class NivelCuidado(val etiqueta: String) { AL_DIA("Al día"), PROXIMO("Próximo"), PENDIENTE("Pendiente") }

/**
 * Resumen por área calculado SOLO con fechas y registros de cumplimiento (RF-16).
 * No es un diagnóstico: la interfaz siempre lo acompaña del aviso correspondiente.
 */
object EstadoCuidado {

    fun area(tipo: TipoCuidado): AreaCuidado? = when (tipo) {
        TipoCuidado.VACUNA -> AreaCuidado.VACUNAS
        TipoCuidado.MEDICAMENTO, TipoCuidado.DESPARASITACION_INTERNA, TipoCuidado.DESPARASITACION_EXTERNA -> AreaCuidado.MEDICAMENTOS
        TipoCuidado.BANO, TipoCuidado.CORTE_PELO, TipoCuidado.CEPILLADO, TipoCuidado.CORTE_UNAS,
        TipoCuidado.LIMPIEZA_DENTAL, TipoCuidado.LIMPIEZA_OIDOS, TipoCuidado.LIMPIEZA_OJOS -> AreaCuidado.HIGIENE
        TipoCuidado.COMIDA -> AreaCuidado.COMIDAS
        TipoCuidado.CITA_VET, TipoCuidado.SERVICIO_CONTRATADO -> AreaCuidado.CITAS
        TipoCuidado.PASEO, TipoCuidado.PERSONALIZADO -> AreaCuidado.ACTIVIDAD
        TipoCuidado.PESAJE -> null
    }

    /**
     * @param resumenes cuidados de la mascota con su próxima fecha y vencimiento.
     * @param tomasHoy agenda de hoy de la mascota.
     * @return solo las áreas que tienen algún cuidado registrado.
     */
    fun calcular(resumenes: List<ResumenCuidado>, tomasHoy: List<Toma>, ahora: LocalDateTime): Map<AreaCuidado, NivelCuidado> {
        val resultado = sortedMapOf<AreaCuidado, NivelCuidado>()
        for (a in AreaCuidado.entries) {
            val delArea = resumenes.filter { area(it.evento.tipo) == a }
            if (delArea.isEmpty()) continue
            val tomas = tomasHoy.filter { area(it.evento.tipo) == a }
            // Una toma con hora que ya pasó y nadie marcó, o un cuidado vencido: pendiente
            val atrasada = tomas.any { it.pendiente && (if (it.conHora) it.fechaHora.isBefore(ahora) else it.fechaHora.toLocalDate().isBefore(ahora.toLocalDate()) || it.fechaHora.toLocalDate() == ahora.toLocalDate()) }
            val vencida = delArea.any { it.vencimiento == Vencimiento.VENCIDO }
            val proxima = delArea.any { it.vencimiento == Vencimiento.PROXIMO || it.terminaPronto } ||
                tomas.any { it.pendiente } ||
                (a == AreaCuidado.CITAS && delArea.any { r -> r.proxima?.let { !it.isAfter(ahora.toLocalDate().plusDays(MotorCuidados.AVISO_POR_DEFECTO.toLong())) } == true })
            resultado[a] = when {
                atrasada || vencida -> NivelCuidado.PENDIENTE
                proxima -> NivelCuidado.PROXIMO
                else -> NivelCuidado.AL_DIA
            }
        }
        return resultado
    }
}
