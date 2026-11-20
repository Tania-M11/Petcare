package com.udenar.petcare.domain.cuidados

/** Mantiene programadas en el teléfono las alarmas de los cuidados (RF-14). */
interface ProgramadorRecordatorios {
    /** Recalcula y programa todas las alarmas a partir de lo guardado en el teléfono. */
    suspend fun reprogramar()
}
