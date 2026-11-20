package com.udenar.petcare.domain.cuidados

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Textos legibles de frecuencias y horas (la app describe lo que escribió el usuario, no sugiere nada). */
object DescripcionCuidados {
    private val ES = Locale.forLanguageTag("es-CO")
    private val HORA = DateTimeFormatter.ofPattern("h:mm a", ES)

    /** Java usa espacios finos sin quiebre dentro de "a. m."; se normalizan a espacios comunes. */
    fun hora(h: LocalTime): String = HORA.format(h).replace(' ', ' ').replace(' ', ' ')

    fun horas(lista: List<LocalTime>): String {
        val textos = lista.sorted().map { hora(it) }
        return when (textos.size) {
            0 -> ""
            1 -> textos[0]
            else -> textos.dropLast(1).joinToString(", ") + " y " + textos.last()
        }
    }

    private fun dias(dias: Set<DayOfWeek>): String = when {
        dias.size == 7 -> "Todos los días"
        dias == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "Fines de semana"
        dias.size == 5 && DayOfWeek.SATURDAY !in dias && DayOfWeek.SUNDAY !in dias -> "De lunes a viernes"
        else -> dias.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, ES).trimEnd('.').replaceFirstChar { c -> c.uppercase() } }
    }

    private fun cada(n: Int, uno: String, varios: String) = if (n == 1) "Cada $uno" else "Cada $n $varios"

    /** "Todos los días · 7:00 a. m. y 7:00 p. m.", "Cada 15 días", "Lun, Jue · 6:00 p. m."… */
    fun frecuencia(f: Frecuencia): String {
        val base = when (f.tipo) {
            TipoFrecuencia.UNICO -> "Una vez"
            TipoFrecuencia.DIARIO -> if (f.n == 1) "Todos los días" else cada(f.n, "día", "días")
            TipoFrecuencia.CADA_N_DIAS -> cada(f.n, "día", "días")
            TipoFrecuencia.SEMANAL -> {
                val semanas = if (f.n == 1) "Cada semana" else "Cada ${f.n} semanas"
                if (f.diasSemana.isEmpty()) semanas else "$semanas · ${dias(f.diasSemana)}"
            }
            TipoFrecuencia.PERSONALIZADO -> dias(f.diasSemana)
            TipoFrecuencia.MENSUAL -> cada(f.n, "mes", "meses")
            TipoFrecuencia.ANUAL -> cada(f.n, "año", "años")
        }
        return if (f.horas.isEmpty()) base else "$base · ${horas(f.horas)}"
    }
}

/** Grupos para ordenar los cuidados en la ficha de la mascota. */
enum class GrupoCuidado(val titulo: String) { COMIDAS("Comidas"), SALUD("Salud"), HIGIENE("Higiene"), ACTIVIDADES("Actividades"), CITAS("Citas y servicios") }

val TipoCuidado.grupo: GrupoCuidado
    get() = when (this) {
        TipoCuidado.COMIDA -> GrupoCuidado.COMIDAS
        TipoCuidado.MEDICAMENTO, TipoCuidado.VACUNA, TipoCuidado.DESPARASITACION_INTERNA,
        TipoCuidado.DESPARASITACION_EXTERNA, TipoCuidado.PESAJE -> GrupoCuidado.SALUD
        TipoCuidado.BANO, TipoCuidado.CORTE_PELO, TipoCuidado.CEPILLADO, TipoCuidado.CORTE_UNAS,
        TipoCuidado.LIMPIEZA_DENTAL, TipoCuidado.LIMPIEZA_OIDOS, TipoCuidado.LIMPIEZA_OJOS -> GrupoCuidado.HIGIENE
        TipoCuidado.PERSONALIZADO -> GrupoCuidado.ACTIVIDADES
        TipoCuidado.CITA_VET, TipoCuidado.SERVICIO_CONTRATADO -> GrupoCuidado.CITAS
    }

/** Tipos de higiene (RF-08), en el orden en que se ofrecen. */
val TIPOS_HIGIENE = listOf(
    TipoCuidado.BANO, TipoCuidado.CORTE_PELO, TipoCuidado.CEPILLADO, TipoCuidado.CORTE_UNAS,
    TipoCuidado.LIMPIEZA_DENTAL, TipoCuidado.LIMPIEZA_OIDOS, TipoCuidado.LIMPIEZA_OJOS,
)

/** Nombres sugeridos para comidas (F6): solo nombres, nunca cantidades ni alimentos. */
val NOMBRES_COMIDA = listOf("Desayuno", "Almuerzo", "Cena", "Merienda")
