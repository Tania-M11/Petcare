package com.udenar.petcare.ui.mascotas

import com.udenar.petcare.domain.cuidados.DescripcionCuidados
import com.udenar.petcare.domain.cuidados.EstadoRegistro
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.RegistroCuidado
import com.udenar.petcare.domain.mascotas.FichaMascota
import com.udenar.petcare.domain.modelo.Icono
import com.udenar.petcare.domain.paseos.Paseo
import com.udenar.petcare.domain.salud.RegistroPeso
import com.udenar.petcare.domain.salud.Sintoma
import com.udenar.petcare.domain.salud.Visita
import com.udenar.petcare.ui.cuidados.icono
import java.time.ZoneId

/**
 * Línea de tiempo de cuidados hechos u omitidos, pesos, paseos, síntomas y visitas, de la más
 * reciente a la más antigua. [nombreDe] agrega el nombre de la mascota (historial general).
 */
fun construirHistorial(
    eventos: List<EventoCuidado>,
    registros: List<RegistroCuidado>,
    pesos: List<RegistroPeso>,
    visitas: List<Visita>,
    paseos: List<Paseo>,
    sintomas: List<Sintoma>,
    zona: ZoneId,
    nombreDe: ((String) -> String?)? = null,
): List<ItemHistorial> {
    val porId = eventos.associateBy { it.id }
    fun de(mascotaId: String, texto: String) = nombreDe?.invoke(mascotaId)?.let { "$it · $texto" } ?: texto
    fun quien(n: String?) = n?.substringBefore(' ')
    return buildList {
        registros.filter { it.estado != EstadoRegistro.POSPUESTA }.forEach { r ->
            val e = porId[r.eventoId]
            val verbo = if (r.estado == EstadoRegistro.OMITIDA) "Omitido" else "Hecho"
            add(
                ItemHistorial(
                    r.registradoEn.atZone(zona).toLocalDateTime(), CategoriaHistorial.CUIDADOS, e?.tipo?.icono ?: Icono.CHECK,
                    e?.titulo ?: "Cuidado eliminado",
                    de(r.mascotaId, "$verbo${quien(r.registradoPor?.nombre)?.let { " por $it" } ?: ""} · ${DescripcionCuidados.hora(r.registradoEn.atZone(zona).toLocalTime())}"),
                ),
            )
        }
        pesos.forEach { p ->
            add(ItemHistorial(p.fecha.atTime(12, 0), CategoriaHistorial.PESO, Icono.PESO, FichaMascota.peso(p.gramos), de(p.mascotaId, "Pesaje${quien(p.registradoPor?.nombre)?.let { " · $it" } ?: ""}")))
        }
        paseos.forEach { p ->
            add(
                ItemHistorial(
                    p.inicio.atZone(zona).toLocalDateTime(), CategoriaHistorial.PASEOS, p.tipo.icono,
                    listOfNotNull(p.distanciaM?.let { com.udenar.petcare.domain.paseos.textoDistancia(it) }, com.udenar.petcare.domain.paseos.textoDuracion(p.duracionS)).joinToString(" · "),
                    de(p.mascotaId, "${p.tipo.etiqueta}${quien(p.registradoPor?.nombre)?.let { " · $it" } ?: ""}"),
                ),
            )
        }
        sintomas.forEach { s ->
            add(ItemHistorial(s.fechaHora, CategoriaHistorial.SINTOMAS, s.tipo.icono, s.tipo.etiqueta, de(s.mascotaId, listOfNotNull(s.descripcion, quien(s.registradoPor?.nombre)).joinToString(" · ").ifEmpty { "Síntoma anotado" })))
        }
        visitas.forEach { v ->
            add(ItemHistorial(v.fecha.atTime(12, 0), CategoriaHistorial.VISITAS, Icono.VETERINARIO, v.motivo, de(v.mascotaId, listOfNotNull("Visita veterinaria", v.veterinario, v.clinica).joinToString(" · "))))
        }
    }.sortedByDescending { it.instante }
}
