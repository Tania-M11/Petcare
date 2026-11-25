package com.udenar.petcare.domain.recomendaciones

import com.udenar.petcare.domain.modelo.Icono
import com.udenar.petcare.domain.cuidados.Ancla
import com.udenar.petcare.domain.cuidados.EstadoRegistro
import com.udenar.petcare.domain.cuidados.EventoCuidado
import com.udenar.petcare.domain.cuidados.RegistroCuidado
import com.udenar.petcare.domain.cuidados.TipoCuidado
import com.udenar.petcare.domain.cuidados.Vencimiento
import com.udenar.petcare.domain.cuidados.resumir
import com.udenar.petcare.domain.modelo.Especie
import com.udenar.petcare.domain.modelo.Mascota
import com.udenar.petcare.domain.paseos.Paseo
import com.udenar.petcare.domain.salud.RegistroPeso
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Qué tan pronto conviene atenderla (ordena y colorea la tarjeta). */
enum class Tono { URGENTE, ATENCION, SUGERENCIA, CELEBRACION }

/** Lo que pasa al tocar el botón de la tarjeta. */
enum class AccionSugerida { VER_CUIDADOS, NUEVO_CUIDADO, REGISTRAR_PESO, BUSCAR_VETERINARIO, COMPLETAR_FICHA, PASEAR, VER_FICHA }

/**
 * Recomendación de organización y seguimiento (Fase 6.5). Nunca sugiere dosis, porciones,
 * tratamientos ni diagnósticos: cuando algo cambia en la salud, sugiere hablar con el veterinario.
 */
data class Recomendacion(
    /** Estable: se usa para descartarla ("No ahora") sin que vuelva enseguida. */
    val id: String,
    val mascotaId: String?,
    val tono: Tono,
    val icono: Icono,
    val titulo: String,
    val mensaje: String,
    /** "¿Por qué la veo?": la regla, contada en lenguaje simple. */
    val porque: String,
    val accion: AccionSugerida,
    val textoAccion: String,
)

data class ContextoRecomendaciones(
    val hoy: LocalDate,
    val mascotas: List<Mascota>,
    val eventos: List<EventoCuidado>,
    val registros: List<RegistroCuidado>,
    val pesos: List<RegistroPeso>,
    val paseos: List<Paseo>,
    val zona: ZoneId,
)

/** Clima actual aproximado (Open-Meteo) para sugerir el momento del paseo. */
data class Clima(val temperatura: Double, val lloviendo: Boolean)

object MotorRecomendaciones {
    /**
     * Sugerencia de paseo según el clima (solo si hay perros). No es un consejo de salud:
     * habla de comodidad (calor, lluvia) y de llevar agua.
     */
    fun clima(c: Clima?, mascotas: List<Mascota>, hoy: LocalDate, hora: Int): Recomendacion? {
        val perros = mascotas.filter { it.especie == Especie.PERRO }
        if (c == null || perros.isEmpty() || hora !in 6..20) return null
        val quien = if (perros.size == 1) perros[0].nombre else "tus perros"
        val grados = c.temperatura.roundToInt()
        return when {
            c.lloviendo -> Recomendacion(
                "clima_lluvia_$hoy", null, Tono.SUGERENCIA, Icono.LLUVIA, "Está lloviendo ($grados °C)",
                "Quizá hoy sea mejor un paseo corto o un rato de juego en casa con $quien.",
                "Según el pronóstico de Open-Meteo para tu zona.", AccionSugerida.PASEAR, "Registrar paseo",
            )
            grados >= 28 -> Recomendacion(
                "clima_calor_$hoy", null, Tono.ATENCION, Icono.SOL, "Hace calor: $grados °C",
                "Para pasear a $quien, mejor temprano o al atardecer, por la sombra y con agua.",
                "Según el pronóstico de Open-Meteo para tu zona.", AccionSugerida.PASEAR, "Registrar paseo",
            )
            grados in 14..25 -> Recomendacion(
                "clima_bueno_$hoy", null, Tono.CELEBRACION, Icono.SOL_NUBE, "Buen clima para pasear ($grados °C)",
                "Un buen momento para salir con $quien.",
                "Según el pronóstico de Open-Meteo para tu zona.", AccionSugerida.PASEAR, "Pasear",
            )
            else -> null
        }
    }

    private val LOCALE = Locale.forLanguageTag("es-CO")
    private val FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM", LOCALE)
    private val DIA = DateTimeFormatter.ofPattern("EEEE", LOCALE)

    const val DIAS_SIN_PESO = 90
    const val CAMBIO_PESO = 0.10
    const val DIAS_CAMBIO_PESO = 30L
    const val OMITIDAS_SEGUIDAS = 2

    fun generar(c: ContextoRecomendaciones): List<Recomendacion> {
        val ahora = c.hoy.atTime(12, 0)
        val lista = mutableListOf<Recomendacion>()
        for (m in c.mascotas) {
            val eventos = c.eventos.filter { it.mascotaId == m.id && !it.oculto }
            val registros = c.registros.filter { it.mascotaId == m.id }
            if (m.esPropietario) {
                lista += cuidadosClinicos(m, eventos, registros, ahora, c.zona)
                lista += peso(m, c.pesos.filter { it.mascotaId == m.id }.sortedByDescending { it.fecha }, c.hoy)
                lista += ficha(m, eventos)
            }
            lista += omitidas(m, eventos, registros, c.hoy)
            lista += paseos(m, c.paseos.filter { it.mascotaId == m.id }, c.hoy, c.zona)
            lista += fechasEspeciales(m, c.hoy)
        }
        val orden = listOf("vencido", "cambio", "omitidas", "proximo", "termina", "sin_cuidados", "cumple", "ficha", "sin_peso", "paseo")
        return lista.distinctBy { it.id }.sortedWith(
            compareBy<Recomendacion> { it.tono.ordinal }.thenBy { r -> orden.indexOfFirst { r.id.startsWith(it) }.let { i -> if (i < 0) orden.size else i } },
        )
    }

    private fun cuidadosClinicos(m: Mascota, eventos: List<EventoCuidado>, registros: List<RegistroCuidado>, ahora: LocalDateTime, zona: ZoneId) = buildList {
        for (e in eventos) {
            val r = resumir(e, registros, ahora, zona)
            val fecha = r.proxima?.format(FECHA)
            val clinico = e.tipo == TipoCuidado.VACUNA || e.tipo == TipoCuidado.DESPARASITACION_INTERNA || e.tipo == TipoCuidado.DESPARASITACION_EXTERNA
            when {
                clinico && r.vencimiento == Vencimiento.VENCIDO -> add(
                    Recomendacion(
                        "vencido_${e.id}_${r.proxima}", m.id, if (e.tipo == TipoCuidado.VACUNA) Tono.URGENTE else Tono.ATENCION, Icono.VACUNA,
                        "${e.titulo} de ${m.nombre} está vencida",
                        "Si ya se la aplicaron, márcala como hecha. Si no, agenda la cita con su veterinario.",
                        "Su fecha era el $fecha y no está registrada como aplicada.",
                        AccionSugerida.VER_CUIDADOS, "Ver cuidados",
                    ),
                )
                clinico && r.vencimiento == Vencimiento.PROXIMO -> add(
                    Recomendacion(
                        "proximo_${e.id}_${r.proxima}", m.id, Tono.SUGERENCIA, Icono.CALENDARIO,
                        "Se acerca: ${e.titulo}",
                        "${m.nombre} la tiene para el $fecha. Es buen momento para apartar la cita.",
                        "Faltan pocos días para la fecha que registraste.",
                        AccionSugerida.BUSCAR_VETERINARIO, "Buscar veterinario",
                    ),
                )
                r.terminaPronto -> add(
                    Recomendacion(
                        "termina_${e.id}_${e.fechaFin}", m.id, Tono.SUGERENCIA, Icono.MEDICAMENTO,
                        "${e.titulo} termina pronto",
                        "El tratamiento de ${m.nombre} termina el ${e.fechaFin?.format(FECHA)}. Si tienes dudas sobre continuarlo, consúltalo con su veterinario.",
                        "Registraste esa fecha como el último día del tratamiento.",
                        AccionSugerida.VER_CUIDADOS, "Ver tratamiento",
                    ),
                )
            }
        }
    }

    /** Varias tomas seguidas sin hacer en los últimos 3 días. */
    private fun omitidas(m: Mascota, eventos: List<EventoCuidado>, registros: List<RegistroCuidado>, hoy: LocalDate) = buildList {
        val desde = hoy.minusDays(3)
        for (e in eventos) {
            val recientes = registros.filter { it.eventoId == e.id && !it.fechaProgramada.toLocalDate().isBefore(desde) }
                .sortedByDescending { it.fechaProgramada }
            val seguidas = recientes.takeWhile { it.estado == EstadoRegistro.OMITIDA }.size
            if (seguidas >= OMITIDAS_SEGUIDAS) {
                val medicina = e.tipo == TipoCuidado.MEDICAMENTO
                add(
                    Recomendacion(
                        "omitidas_${e.id}_$hoy", m.id, if (medicina) Tono.ATENCION else Tono.SUGERENCIA, if (medicina) Icono.ALARMA else Icono.CAMPANA,
                        "$seguidas tomas seguidas sin hacer: ${e.titulo}",
                        if (medicina) "Si se está complicando darla a tiempo, coméntalo con el veterinario de ${m.nombre}. También puedes ajustar las horas del recordatorio."
                        else "¿Las horas de este recordatorio le sirven a tu rutina? Puedes cambiarlas desde la ficha de ${m.nombre}.",
                        "Las últimas $seguidas tomas quedaron marcadas como omitidas.",
                        AccionSugerida.VER_CUIDADOS, "Revisar horario",
                    ),
                )
            }
        }
    }

    private fun peso(m: Mascota, pesos: List<RegistroPeso>, hoy: LocalDate) = buildList {
        val ultimo = pesos.firstOrNull()
        if (ultimo == null || ChronoUnit.DAYS.between(ultimo.fecha, hoy) >= DIAS_SIN_PESO) {
            add(
                Recomendacion(
                    "sin_peso_${m.id}_${ultimo?.fecha}", m.id, Tono.SUGERENCIA, Icono.PESO,
                    "¿Cuánto pesa ${m.nombre} hoy?",
                    "Registrar el peso de vez en cuando ayuda a notar cambios a tiempo.",
                    if (ultimo == null) "Aún no hay ningún peso registrado." else "El último peso es del ${ultimo.fecha.format(FECHA)}.",
                    AccionSugerida.REGISTRAR_PESO, "Registrar peso",
                ),
            )
        }
        // Cambio fuerte entre el último peso y uno de hace hasta 30 días
        val anterior = ultimo?.let { u -> pesos.drop(1).firstOrNull { ChronoUnit.DAYS.between(it.fecha, u.fecha) in 1..DIAS_CAMBIO_PESO } }
        if (ultimo != null && anterior != null && anterior.gramos > 0) {
            val cambio = (ultimo.gramos - anterior.gramos).toDouble() / anterior.gramos
            if (abs(cambio) >= CAMBIO_PESO) {
                val porcentaje = (abs(cambio) * 100).roundToInt()
                val dias = ChronoUnit.DAYS.between(anterior.fecha, ultimo.fecha)
                add(
                    Recomendacion(
                        "cambio_peso_${m.id}_${ultimo.id}", m.id, Tono.ATENCION, if (cambio > 0) Icono.SUBE else Icono.BAJA,
                        "${m.nombre} ${if (cambio > 0) "subió" else "bajó"} $porcentaje % de peso",
                        "Un cambio así en $dias días vale la pena comentarlo con su veterinario.",
                        "Comparamos sus dos últimos pesos registrados.",
                        AccionSugerida.BUSCAR_VETERINARIO, "Pedir cita",
                    ),
                )
            }
        }
    }

    private fun ficha(m: Mascota, eventos: List<EventoCuidado>) = buildList {
        if (eventos.isEmpty()) {
            add(
                Recomendacion(
                    "sin_cuidados_${m.id}", m.id, Tono.SUGERENCIA, Icono.BRILLOS,
                    "${m.nombre} aún no tiene recordatorios",
                    "Empieza por lo de todos los días: sus comidas o un paseo.",
                    "No hay ningún cuidado registrado para ${m.nombre}.",
                    AccionSugerida.NUEVO_CUIDADO, "Crear recordatorio",
                ),
            )
        }
        val faltan = listOfNotNull(
            "alergias".takeIf { m.alergias.isNullOrBlank() && m.condiciones.isNullOrBlank() },
            "veterinario de cabecera".takeIf { m.veterinario == null },
        )
        if (faltan.isNotEmpty()) {
            add(
                Recomendacion(
                    "ficha_${m.id}_${faltan.joinToString("_")}", m.id, Tono.SUGERENCIA, Icono.NOTA,
                    "Completa la ficha de ${m.nombre}",
                    "Falta: ${faltan.joinToString(" y ")}. Así quienes la cuidan y su veterinario tienen lo importante a mano.",
                    "Esos datos están vacíos en su ficha.",
                    AccionSugerida.COMPLETAR_FICHA, "Completar",
                ),
            )
        }
    }

    /** Perros sin paseos registrados en la última semana. */
    private fun paseos(m: Mascota, paseos: List<Paseo>, hoy: LocalDate, zona: ZoneId) = buildList {
        if (m.especie != Especie.PERRO) return@buildList
        val ultimo = paseos.maxOfOrNull { it.inicio.atZone(zona).toLocalDate() }
        if (ultimo == null || ChronoUnit.DAYS.between(ultimo, hoy) >= 7) {
            add(
                Recomendacion(
                    "paseo_${m.id}_${hoy.minusDays(hoy.dayOfWeek.value - 1L)}", m.id, Tono.SUGERENCIA, Icono.PASEO,
                    "¿Salieron a pasear esta semana?",
                    "Registra los paseos de ${m.nombre}, con GPS o a mano, y verás cuánto caminan.",
                    if (ultimo == null) "Aún no hay paseos registrados." else "El último paseo registrado fue el ${ultimo.format(FECHA)}.",
                    AccionSugerida.PASEAR, "Pasear",
                ),
            )
        }
    }

    private fun fechasEspeciales(m: Mascota, hoy: LocalDate) = buildList {
        val nacimiento = m.fechaNacimiento ?: return@buildList
        val dia = MonthDay.from(nacimiento)
        val proximo = dia.atYear(hoy.year).let { if (it.isBefore(hoy)) dia.atYear(hoy.year + 1) else it }
        val faltan = ChronoUnit.DAYS.between(hoy, proximo)
        val anos = proximo.year - nacimiento.year
        if (anos <= 0) return@buildList
        when {
            faltan == 0L -> add(
                Recomendacion(
                    "cumple_${m.id}_${hoy.year}", m.id, Tono.CELEBRACION, Icono.PASTEL,
                    "¡Hoy ${m.nombre} cumple $anos ${if (anos == 1) "año" else "años"}!",
                    "Un día perfecto para su foto favorita y un paseo largo.",
                    "Hoy es su fecha de nacimiento${if (m.nacimientoAproximado) " (aproximada)" else ""}.",
                    AccionSugerida.VER_FICHA, "Ver su ficha",
                ),
            )
            faltan in 1..7 -> add(
                Recomendacion(
                    "cumple_pronto_${m.id}_${hoy.year}", m.id, Tono.CELEBRACION, Icono.GLOBO,
                    "${m.nombre} cumple $anos el ${proximo.format(DIA)}",
                    "Faltan $faltan ${if (faltan == 1L) "día" else "días"}.",
                    "Calculado con su fecha de nacimiento.",
                    AccionSugerida.VER_FICHA, "Ver su ficha",
                ),
            )
        }
    }
}

/** Mensaje de Michi para el inicio: cambia con la hora, el avance del día y el nombre. */
data class MensajeMichi(val texto: String, val pose: PoseMichi)

enum class PoseMichi { SALUDO, CELEBRA, DORMIDO, AGENDA, ALARMA, HIGIENE, DOCTOR, SALUD, DOCUMENTOS, LEYENDO, AMIGOS }

data class CabeceraHoy(val momento: String, val estado: String, val pose: PoseMichi, val icono: Icono)

data class Consejo(val icono: Icono, val texto: String)

object Michi {
    /**
     * Cabecera de Hoy en tres piezas cortas (Fase 6): el momento del día, una línea con el
     * estado y la pose de Michi. Nada de párrafos: lo demás lo cuentan el progreso y los trucos.
     */
    fun cabecera(hora: Int, total: Int, pendientes: Int, hayMascotas: Boolean): CabeceraHoy {
        val momento = when (hora) {
            in 5..11 -> "Buenos días"
            in 12..18 -> "Buenas tardes"
            else -> "Buenas noches"
        }
        val hechos = total - pendientes
        return when {
            !hayMascotas -> CabeceraHoy(momento, "¡Empecemos! Soy Michi", PoseMichi.SALUDO, Icono.HUELLAS)
            total == 0 -> CabeceraHoy(momento, "Hoy no hay cuidados programados", PoseMichi.DORMIDO, Icono.HOJA)
            pendientes == 0 -> CabeceraHoy(momento, "¡Todo listo por hoy!", PoseMichi.CELEBRA, Icono.FIESTA)
            pendientes == total -> CabeceraHoy(momento, "$total ${if (total == 1) "cuidado" else "cuidados"} para hoy", PoseMichi.ALARMA, Icono.CALENDARIO)
            pendientes == 1 -> CabeceraHoy(momento, "¡Ya casi! Falta 1", PoseMichi.CELEBRA, Icono.FUEGO)
            else -> CabeceraHoy(momento, "Llevas $hechos de $total", PoseMichi.AGENDA, Icono.ESTRELLA)
        }
    }

    /** Trucos cortos de la app; la lista empieza en uno distinto cada día y rota sola. */
    fun consejos(hoy: LocalDate): List<Consejo> = CONSEJOS.indices.map { CONSEJOS[(hoy.dayOfYear + it) % CONSEJOS.size] }

    /** El truco del día (para textos que no rotan). */
    fun consejo(hoy: LocalDate): String = consejos(hoy).first().texto

    private val CONSEJOS = listOf(
        Consejo(Icono.CHECK, "Desliza un cuidado a la derecha para marcarlo"),
        Consejo(Icono.HUELLAS, "Toca una mascota para ver solo lo suyo"),
        Consejo(Icono.CARNE, "Su carné con código QR está en la ficha"),
        Consejo(Icono.EQUIPO, "Comparte su código y cuiden en equipo"),
        Consejo(Icono.MAPA, "Registra paseos con GPS y mira la ruta"),
        Consejo(Icono.CALENDARIO, "Mira cómo vas en todo el mes"),
        Consejo(Icono.CAMARA, "Escanea la etiqueta de una vacuna"),
    )

    /** Mensaje corto al marcar una toma (entre procesos). */
    fun alMarcar(titulo: String, mascota: String, quedan: Int): String = when (quedan) {
        0 -> "¡$titulo de $mascota listo! Terminaste todo lo de hoy."
        1 -> "¡Bien! $titulo de $mascota hecho. Queda 1."
        else -> "¡Bien! $titulo de $mascota hecho. Quedan $quedan."
    }
}
