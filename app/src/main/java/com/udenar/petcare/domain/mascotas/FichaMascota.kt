package com.udenar.petcare.domain.mascotas

import com.udenar.petcare.domain.modelo.Especie
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Period

enum class UnidadPeso { KG, G }

enum class CampoMascota { NOMBRE, ESPECIE, ESPECIE_OTRO, NACIMIENTO, PESO }

/** Reglas de la ficha de la mascota (RF-02): la app guarda lo que escribe el usuario, sin sugerir nada. */
object FichaMascota {

    /** "2 años", "1 año y 3 meses", "5 meses", "3 semanas"; con "aprox." si la fecha es aproximada. */
    fun edad(nacimiento: LocalDate, aproximada: Boolean, hoy: LocalDate): String {
        if (nacimiento.isAfter(hoy)) return ""
        val p = Period.between(nacimiento, hoy)
        val texto = when {
            p.years >= 2 -> plural(p.years, "año", "años")
            p.years == 1 -> if (p.months > 0) "1 año y ${plural(p.months, "mes", "meses")}" else "1 año"
            p.months >= 1 -> plural(p.months, "mes", "meses")
            else -> {
                val semanas = (hoy.toEpochDay() - nacimiento.toEpochDay()) / 7
                if (semanas >= 1) plural(semanas.toInt(), "semana", "semanas") else "Recién nacido"
            }
        }
        return if (aproximada) "$texto aprox." else texto
    }

    /** Convierte "4,2" kg o "350" g a gramos. null si está vacío; -1 si no es un número válido. */
    fun gramos(texto: String, unidad: UnidadPeso): Int? {
        val limpio = texto.trim().replace(',', '.')
        if (limpio.isEmpty()) return null
        val valor = limpio.toBigDecimalOrNull() ?: return -1
        if (valor <= BigDecimal.ZERO) return -1
        val g = if (unidad == UnidadPeso.KG) valor.multiply(BigDecimal(1000)) else valor
        return g.setScale(0, RoundingMode.HALF_UP).toInt()
    }

    /** Texto para mostrar el peso: "4,2 kg" o "350 g". */
    fun peso(gramos: Int): String =
        if (gramos >= 1000) {
            BigDecimal(gramos).divide(BigDecimal(1000)).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
                .toPlainString().replace('.', ',') + " kg"
        } else "$gramos g"

    /** Para rellenar el campo al editar: en kg si pesa 1 kg o más. */
    fun pesoEditable(gramos: Int?): Pair<String, UnidadPeso> = when {
        gramos == null -> "" to UnidadPeso.KG
        gramos >= 1000 -> peso(gramos).removeSuffix(" kg") to UnidadPeso.KG
        else -> gramos.toString() to UnidadPeso.G
    }

    fun validar(
        nombre: String,
        especie: Especie?,
        especieOtro: String,
        nacimiento: LocalDate?,
        pesoTexto: String,
        unidad: UnidadPeso,
        hoy: LocalDate,
    ): Map<CampoMascota, String> = buildMap {
        when {
            nombre.isBlank() -> put(CampoMascota.NOMBRE, "Escribe su nombre")
            nombre.trim().length > 40 -> put(CampoMascota.NOMBRE, "Máximo 40 caracteres")
        }
        if (especie == null) put(CampoMascota.ESPECIE, "Elige su especie")
        if (especie == Especie.OTRO && especieOtro.isBlank()) put(CampoMascota.ESPECIE_OTRO, "¿Qué especie es?")
        if (nacimiento != null && nacimiento.isAfter(hoy)) put(CampoMascota.NACIMIENTO, "La fecha no puede ser futura")
        val g = gramos(pesoTexto, unidad)
        if (g == -1) put(CampoMascota.PESO, "Escribe un número mayor que 0")
        else if (g != null && g >= 2_000_000) put(CampoMascota.PESO, "Revisa el peso")
    }

    private fun plural(n: Int, uno: String, varios: String) = if (n == 1) "1 $uno" else "$n $varios"
}

/** Emoji y color de cada especie, para el avatar cuando no hay foto. */
val Especie.emoji: String
    get() = when (this) {
        Especie.PERRO -> "🐶"
        Especie.GATO -> "🐱"
        Especie.AVE -> "🐦"
        Especie.CONEJO -> "🐰"
        Especie.ROEDOR -> "🐹"
        Especie.REPTIL -> "🦎"
        Especie.PEZ -> "🐟"
        Especie.EQUINO -> "🐴"
        Especie.OTRO -> "🐾"
    }
