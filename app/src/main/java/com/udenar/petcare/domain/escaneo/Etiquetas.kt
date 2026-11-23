package com.udenar.petcare.domain.escaneo

import java.time.LocalDate
import java.time.YearMonth

/**
 * Lo que se pudo leer de la foto de una etiqueta o un carné (Fase 6.3). La app no decide nada:
 * muestra las opciones y la persona toca la que corresponde a cada campo.
 */
data class LecturaEtiqueta(
    /** Líneas que podrían ser el nombre del producto, de la más probable a la menos. */
    val nombres: List<String>,
    val fechas: List<LocalDate>,
    val lote: String?,
)

object AnalizadorEtiqueta {
    private val FECHA_COMPLETA = Regex("""\b(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{2,4})\b""")
    private val MES_ANIO = Regex("""\b(\d{1,2})[/.\-](\d{4})\b""")
    private val LOTE = Regex("""(?i)\b(?:lote|lot|batch|l\.)\s*[:#.]?\s*([A-Z0-9][A-Z0-9\-]{2,15})\b""")
    private val PALABRAS_RUIDO = Regex("""(?i)^(lote|lot|batch|vence|venc|exp|fab|fecha|reg|registro|uso veterinario|via|vía|dosis|ml|mg)\b""")

    fun analizar(texto: String, hoy: LocalDate): LecturaEtiqueta {
        val fechas = mutableListOf<LocalDate>()
        FECHA_COMPLETA.findAll(texto).forEach { m ->
            val (d, mes, a) = m.destructured
            val anio = a.toInt().let { if (it < 100) 2000 + it else it }
            runCatching { LocalDate.of(anio, mes.toInt(), d.toInt()) }.getOrNull()?.let { fechas += it }
        }
        // "09/2027" (vencimiento sin día) → primer día del mes, solo si no era parte de una fecha completa
        val sinCompletas = FECHA_COMPLETA.replace(texto, " ")
        MES_ANIO.findAll(sinCompletas).forEach { m ->
            val (mes, a) = m.destructured
            runCatching { YearMonth.of(a.toInt(), mes.toInt()).atDay(1) }.getOrNull()?.let { fechas += it }
        }
        val lote = LOTE.find(texto)?.groupValues?.get(1)
        val nombres = texto.lines()
            .map { it.trim().trim('*', '-', '•', ':') }
            .filter { l ->
                l.length in 3..40 &&
                    l.count(Char::isLetter) >= 3 &&
                    !PALABRAS_RUIDO.containsMatchIn(l) &&
                    !FECHA_COMPLETA.containsMatchIn(l)
            }
            .distinct()
            // Primero lo que parece un nombre: mayúsculas y pocas palabras
            .sortedWith(compareByDescending<String> { l -> l.count(Char::isUpperCase).toFloat() / l.count(Char::isLetter).coerceAtLeast(1) }.thenBy { it.split(' ').size })
            .take(6)
        return LecturaEtiqueta(nombres, fechas.distinct().filter { it.year in (hoy.year - 30)..(hoy.year + 10) }.sorted(), lote)
    }
}
