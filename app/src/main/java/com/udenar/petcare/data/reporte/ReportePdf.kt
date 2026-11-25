package com.udenar.petcare.data.reporte

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.udenar.petcare.domain.cuidados.ResumenCuidado
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.cuidados.Vencimiento
import com.udenar.petcare.domain.mascotas.FichaMascota
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.domain.paseos.Paseo
import com.udenar.petcare.domain.paseos.textoDistancia
import com.udenar.petcare.domain.paseos.textoDuracion
import com.udenar.petcare.domain.salud.RegistroPeso
import com.udenar.petcare.domain.salud.Sintoma
import com.udenar.petcare.domain.salud.Visita
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Todo lo que va en el reporte para el veterinario. */
data class DatosReporte(
    val mascota: Mascota,
    val cuidados: List<ResumenCuidado>,
    val pesos: List<RegistroPeso>,
    val visitas: List<Visita>,
    val paseos: List<Paseo>,
    val sintomas: List<Sintoma>,
    val hoy: LocalDate,
    val zona: ZoneId,
)

/**
 * Reporte en PDF (A4) para llevar al veterinario: ficha, salud básica, vacunas, medicamentos,
 * desparasitación, visitas, peso, síntomas y actividad. Solo copia lo registrado; no interpreta.
 */
object ReportePdf {
    private val ES = Locale.forLanguageTag("es-CO")
    private val FECHA = DateTimeFormatter.ofPattern("d MMM yyyy", ES)
    private val FECHA_HORA = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", ES)
    private const val ANCHO = 595
    private const val ALTO = 842
    private const val MARGEN = 40f

    fun generar(contexto: Context, d: DatosReporte): File {
        val doc = PdfDocument()
        val lienzo = Lienzo(doc)
        val m = d.mascota

        // Encabezado con foto
        lienzo.encabezado(m, d.hoy)
        lienzo.seccion("Información general")
        lienzo.datos(
            listOfNotNull(
                "Especie" to (m.especieOtro ?: m.especie.etiqueta),
                m.raza?.let { "Raza" to it },
                "Sexo" to m.sexo.etiqueta,
                m.fechaNacimiento?.let { "Edad" to FichaMascota.edad(it, m.nacimientoAproximado, d.hoy) + " (nació el ${it.format(FECHA)})" },
                m.pesoGramos?.let { "Peso actual" to FichaMascota.peso(it) },
                m.color?.let { "Color" to it },
                m.tamano?.let { "Tamaño" to it.etiqueta },
                m.esterilizado?.let { "Esterilizado/a" to if (it) "Sí" else "No" },
                m.microchip?.let { "Microchip" to it },
            ),
        )
        lienzo.seccion("Alergias y salud básica")
        lienzo.datos(
            listOf(
                "Alergias" to (m.alergias ?: "Ninguna registrada"),
                "Condiciones conocidas" to (m.condiciones ?: "Ninguna registrada"),
                "Medicamentos actuales" to (m.medicamentosActuales ?: "Ninguno registrado"),
                "Alimentación especial" to (m.alimentacionEspecial ?: "Ninguna registrada"),
            ) + listOfNotNull(m.observaciones?.let { "Observaciones" to it }),
        )

        fun lista(titulo: String, tipos: Set<TipoCuidado>, vacio: String) {
            lienzo.seccion(titulo)
            val de = d.cuidados.filter { it.evento.tipo in tipos }
            if (de.isEmpty()) lienzo.parrafo(vacio, gris = true)
            de.forEach { r ->
                val e = r.evento
                val estado = when (r.vencimiento) {
                    Vencimiento.VENCIDO -> "Vencida"
                    Vencimiento.PROXIMO -> "Próxima"
                    Vencimiento.AL_DIA -> "Al día"
                    null -> null
                }
                lienzo.item(
                    e.titulo,
                    listOfNotNull(
                        e.detalle["dosis"]?.let { "Dosis: $it" },
                        r.descripcion.ifBlank { null },
                        e.detalle["aplicadaEl"]?.let { "Aplicada: ${runCatching { LocalDate.parse(it).format(FECHA) }.getOrDefault(it)}" },
                        r.proxima?.let { "Próxima: ${it.format(FECHA)}" },
                        estado,
                        e.fechaFin?.let { "Hasta: ${it.format(FECHA)}" },
                        e.detalle["producto"]?.let { "Producto: $it" },
                        e.detalle["lote"]?.let { "Lote: $it" },
                        listOfNotNull(e.detalle["veterinario"], e.detalle["clinica"]).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
                        e.detalle["indicaciones"]?.let { "Indicaciones: $it" },
                    ).joinToString(" · "),
                )
            }
        }
        lista("Vacunas", setOf(TipoCuidado.VACUNA), "Sin vacunas registradas.")
        lista("Medicamentos", setOf(TipoCuidado.MEDICAMENTO), "Sin medicamentos registrados.")
        lista("Desparasitación", setOf(TipoCuidado.DESPARASITACION_INTERNA, TipoCuidado.DESPARASITACION_EXTERNA), "Sin desparasitaciones registradas.")

        lienzo.seccion("Historial de visitas veterinarias")
        if (d.visitas.isEmpty()) lienzo.parrafo("Sin visitas registradas.", gris = true)
        d.visitas.sortedByDescending { it.fecha }.take(15).forEach { v ->
            lienzo.item(
                "${v.fecha.format(FECHA)} · ${v.motivo}",
                listOfNotNull(
                    listOfNotNull(v.veterinario, v.clinica).joinToString(", ").ifBlank { null },
                    v.tratamiento?.let { "Tratamiento: $it" },
                    v.medicamentos?.let { "Medicamentos: $it" },
                    v.notas,
                ).joinToString(" · "),
            )
        }

        lienzo.seccion("Peso")
        if (d.pesos.isEmpty()) lienzo.parrafo("Sin pesajes registrados.", gris = true)
        else {
            lienzo.graficaPeso(d.pesos.sortedBy { it.fecha }.takeLast(12))
            lienzo.parrafo(d.pesos.sortedByDescending { it.fecha }.take(8).joinToString("   ") { "${it.fecha.format(FECHA)}: ${FichaMascota.peso(it.gramos)}" })
        }

        lienzo.seccion("Síntomas y comportamiento (últimos 90 días)")
        val sintomas = d.sintomas.filter { !it.fechaHora.toLocalDate().isBefore(d.hoy.minusDays(90)) }
        if (sintomas.isEmpty()) lienzo.parrafo("Sin síntomas anotados.", gris = true)
        sintomas.take(20).forEach { s -> lienzo.item("${s.fechaHora.format(FECHA_HORA)} · ${s.tipo.etiqueta}", s.descripcion.orEmpty()) }

        lienzo.seccion("Actividad (últimos 30 días)")
        val paseos = d.paseos.filter { !it.inicio.atZone(d.zona).toLocalDate().isBefore(d.hoy.minusDays(30)) }
        if (paseos.isEmpty()) lienzo.parrafo("Sin paseos ni actividades registradas.", gris = true)
        else {
            val minutos = paseos.sumOf { it.duracionS } / 60
            val metros = paseos.sumOf { it.distanciaM ?: 0 }
            lienzo.parrafo("${paseos.size} paseos o actividades · ${textoDuracion(minutos * 60)} en total · ${textoDistancia(metros)} · promedio ${minutos / paseos.size} min")
            paseos.groupBy { it.tipo }.forEach { (t, l) -> lienzo.parrafo("${t.etiqueta}: ${l.size}", gris = true) }
        }
        val otrasActividades = d.cuidados.filter { it.evento.tipo in setOf(TipoCuidado.PERSONALIZADO, TipoCuidado.PASEO, TipoCuidado.COMIDA) }
        if (otrasActividades.isNotEmpty()) {
            lienzo.parrafo("Rutina registrada: " + otrasActividades.joinToString("; ") { r ->
                listOfNotNull(r.evento.titulo, r.descripcion.ifBlank { null }, r.evento.detalle["cantidad"]?.ifBlank { null }).joinToString(", ")
            })
        }

        lienzo.parrafo("")
        lienzo.parrafo("Generado con PetCare con la información que registró la familia. No constituye un diagnóstico médico.", gris = true)
        lienzo.terminar()

        val carpeta = File(contexto.cacheDir, "compartidos").apply { mkdirs() }
        val archivo = File(carpeta, "Reporte_${m.nombre.replace(Regex("[^A-Za-z0-9áéíóúÁÉÍÓÚñÑ]"), "_")}_${d.hoy}.pdf")
        archivo.outputStream().use { doc.writeTo(it) }
        doc.close()
        return archivo
    }

    /** Escribe de arriba abajo y abre página nueva cuando no cabe. */
    private class Lienzo(private val doc: PdfDocument) {
        private var numero = 0
        private var pagina: PdfDocument.Page? = null
        private lateinit var c: Canvas
        private var y = 0f
        private val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = 0xFF2D2433.toInt() }
        private val gris = Paint(texto).apply { color = 0xFF8C8394.toInt() }
        private val negrita = Paint(texto).apply { typeface = Typeface.DEFAULT_BOLD }
        private val titulo = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; color = 0xFFD96A28.toInt() }
        private val linea = Paint().apply { color = 0xFFEFE6DE.toInt(); strokeWidth = 1f }

        init { nueva() }

        private fun nueva() {
            pagina?.let { doc.finishPage(it) }
            numero++
            pagina = doc.startPage(PdfDocument.PageInfo.Builder(ANCHO, ALTO, numero).create())
            c = pagina!!.canvas
            y = MARGEN
            c.drawText("PetCare · Reporte para el veterinario · pág. $numero", MARGEN, ALTO - 20f, gris)
        }

        private fun espacio(alto: Float) { if (y + alto > ALTO - 50f) nueva() }

        private fun lineas(t: String, p: Paint, ancho: Float): List<String> {
            val res = mutableListOf<String>()
            t.split("\n").forEach { parrafo ->
                var actual = ""
                parrafo.split(" ").forEach { palabra ->
                    val prueba = if (actual.isEmpty()) palabra else "$actual $palabra"
                    if (p.measureText(prueba) > ancho && actual.isNotEmpty()) { res += actual; actual = palabra } else actual = prueba
                }
                res += actual
            }
            return res
        }

        fun encabezado(m: Mascota, hoy: LocalDate) {
            val foto = m.fotoLocal?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
            val grande = Paint(titulo).apply { textSize = 24f; color = 0xFF2D2433.toInt() }
            var x = MARGEN
            if (foto != null) {
                val lado = 80f
                val esc = maxOf(lado / foto.width, lado / foto.height)
                val w = foto.width * esc
                val h = foto.height * esc
                c.save()
                c.clipRect(RectF(MARGEN, y, MARGEN + lado, y + lado))
                c.drawBitmap(foto, null, RectF(MARGEN + (lado - w) / 2, y + (lado - h) / 2, MARGEN + (lado + w) / 2, y + (lado + h) / 2), null)
                c.restore()
                x += lado + 14f
            }
            c.drawText(m.nombre, x, y + 28f, grande)
            c.drawText("Reporte para el veterinario", x, y + 48f, negrita)
            c.drawText("Generado el ${hoy.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO")))}", x, y + 64f, gris)
            y += if (foto != null) 92f else 76f
        }

        fun seccion(t: String) {
            espacio(40f)
            y += 10f
            c.drawText(t, MARGEN, y + 12f, titulo)
            y += 18f
            c.drawLine(MARGEN, y, ANCHO - MARGEN, y, linea)
            y += 10f
        }

        fun datos(pares: List<Pair<String, String>>) = pares.forEach { (k, v) ->
            val ls = lineas(v, texto, ANCHO - MARGEN * 2 - 140f)
            espacio(14f * ls.size)
            c.drawText(k, MARGEN, y + 10f, gris)
            ls.forEachIndexed { i, l -> c.drawText(l, MARGEN + 140f, y + 10f + i * 14f, texto) }
            y += 14f * ls.size + 2f
        }

        fun item(t: String, detalle: String) {
            val ls = if (detalle.isBlank()) emptyList() else lineas(detalle, gris, ANCHO - MARGEN * 2 - 10f)
            espacio(16f + 13f * ls.size)
            c.drawText("• $t", MARGEN, y + 11f, negrita)
            y += 15f
            ls.forEach { l -> c.drawText(l, MARGEN + 10f, y + 10f, gris); y += 13f }
            y += 3f
        }

        fun parrafo(t: String, gris: Boolean = false) {
            val p = if (gris) this.gris else texto
            val ls = lineas(t, p, ANCHO - MARGEN * 2)
            espacio(14f * ls.size)
            ls.forEach { l -> c.drawText(l, MARGEN, y + 10f, p); y += 14f }
        }

        /** Línea simple de peso con sus puntos. */
        fun graficaPeso(pesos: List<RegistroPeso>) {
            if (pesos.size < 2) return
            val alto = 90f
            espacio(alto + 10f)
            val izq = MARGEN + 30f
            val der = ANCHO - MARGEN
            val min = pesos.minOf { it.gramos }
            val max = pesos.maxOf { it.gramos }.coerceAtLeast(min + 1)
            val trazo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF08A4B.toInt(); strokeWidth = 2f; style = Paint.Style.STROKE }
            val punto = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF08A4B.toInt() }
            fun px(i: Int) = izq + (der - izq) * i / (pesos.size - 1)
            fun py(g: Int) = y + alto - (alto - 10f) * (g - min) / (max - min)
            c.drawLine(izq, y + alto, der, y + alto, linea)
            for (i in 1 until pesos.size) c.drawLine(px(i - 1), py(pesos[i - 1].gramos), px(i), py(pesos[i].gramos), trazo)
            pesos.forEachIndexed { i, p -> c.drawCircle(px(i), py(p.gramos), 3f, punto) }
            c.drawText(FichaMascota.peso(max), MARGEN - 4f, y + 12f, gris)
            c.drawText(FichaMascota.peso(min), MARGEN - 4f, y + alto, gris)
            y += alto + 12f
        }

        fun terminar() { pagina?.let { doc.finishPage(it) }; pagina = null }
    }
}
