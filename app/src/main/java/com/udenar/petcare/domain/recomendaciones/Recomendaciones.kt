package com.udenar.petcare.domain.recomendaciones

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
    val emoji: String,
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

object MotorRecomendaciones {
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
                        "vencido_${e.id}_${r.proxima}", m.id, if (e.tipo == TipoCuidado.VACUNA) Tono.URGENTE else Tono.ATENCION, "💉",
                        "${e.titulo} de ${m.nombre} está vencida",
                        "Si ya se la aplicaron, márcala como hecha. Si no, agenda la cita con su veterinario.",
                        "Su fecha era el $fecha y no está registrada como aplicada.",
                        AccionSugerida.VER_CUIDADOS, "Ver cuidados",
                    ),
                )
                clinico && r.vencimiento == Vencimiento.PROXIMO -> add(
                    Recomendacion(
                        "proximo_${e.id}_${r.proxima}", m.id, Tono.SUGERENCIA, "📅",
                        "Se acerca: ${e.titulo}",
                        "${m.nombre} la tiene para el $fecha. Es buen momento para apartar la cita.",
                        "Faltan pocos días para la fecha que registraste.",
                        AccionSugerida.BUSCAR_VETERINARIO, "Buscar veterinario",
                    ),
                )
                r.terminaPronto -> add(
                    Recomendacion(
                        "termina_${e.id}_${e.fechaFin}", m.id, Tono.SUGERENCIA, "💊",
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
                        "omitidas_${e.id}_$hoy", m.id, if (medicina) Tono.ATENCION else Tono.SUGERENCIA, if (medicina) "⏰" else "🔔",
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
                    "sin_peso_${m.id}_${ultimo?.fecha}", m.id, Tono.SUGERENCIA, "⚖️",
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
                        "cambio_peso_${m.id}_${ultimo.id}", m.id, Tono.ATENCION, if (cambio > 0) "📈" else "📉",
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
                    "sin_cuidados_${m.id}", m.id, Tono.SUGERENCIA, "✨",
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
                    "ficha_${m.id}_${faltan.joinToString("_")}", m.id, Tono.SUGERENCIA, "📝",
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
                    "paseo_${m.id}_${hoy.minusDays(hoy.dayOfWeek.value - 1L)}", m.id, Tono.SUGERENCIA, "🦮",
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
                    "cumple_${m.id}_${hoy.year}", m.id, Tono.CELEBRACION, "🎂",
                    "¡Hoy ${m.nombre} cumple $anos ${if (anos == 1) "año" else "años"}!",
                    "Un día perfecto para su foto favorita y un paseo largo.",
                    "Hoy es su fecha de nacimiento${if (m.nacimientoAproximado) " (aproximada)" else ""}.",
                    AccionSugerida.VER_FICHA, "Ver su ficha",
                ),
            )
            faltan in 1..7 -> add(
                Recomendacion(
                    "cumple_pronto_${m.id}_${hoy.year}", m.id, Tono.CELEBRACION, "🎈",
                    "${m.nombre} cumple $anos el ${proximo.format(DIA)}",
                    "Faltan $faltan ${if (faltan == 1L) "día" else "días"}.",
                    "Calculado con su fecha de nacimiento.",
                    AccionSugerida.VER_FICHA, "Ver su ficha",
                ),
            )
        }
    }
}

/** Mensaje de Chopper para el inicio: cambia con la hora, el avance del día y el nombre. */
data class MensajeChopper(val texto: String, val pose: PoseChopper)

enum class PoseChopper { SALUDO, COMIDA, AMIGOS, ORGANIZADOR, DOCTOR }

object Chopper {
    /** [hora] 0–23; [pendientes]/[total] del día; [mascotas] = nombres. */
    fun saludo(nombre: String, hora: Int, total: Int, pendientes: Int, mascotas: List<String>, hoy: LocalDate): MensajeChopper {
        val quien = nombre.ifBlank { "" }.let { if (it.isEmpty()) "" else ", $it" }
        val momento = when (hora) {
            in 5..11 -> "¡Buenos días$quien!"
            in 12..18 -> "¡Buenas tardes$quien!"
            else -> "¡Buenas noches$quien!"
        }
        val juntos = when (mascotas.size) {
            0 -> ""
            1 -> mascotas[0]
            else -> mascotas.dropLast(1).joinToString(", ") + " y " + mascotas.last()
        }
        return when {
            mascotas.isEmpty() -> MensajeChopper("$momento Soy Chopper. Agrega a tu primera mascota y yo te ayudo a recordar sus cuidados.", PoseChopper.SALUDO)
            total == 0 -> MensajeChopper("$momento Hoy no hay cuidados programados para $juntos. ${consejo(hoy)}", PoseChopper.ORGANIZADOR)
            pendientes == 0 -> MensajeChopper("¡Todo listo por hoy! $juntos ${if (mascotas.size == 1) "está feliz" else "están felices"}. 🎉", PoseChopper.AMIGOS)
            pendientes == total -> MensajeChopper("$momento Hoy hay $total ${if (total == 1) "cuidado" else "cuidados"} para $juntos. Vamos uno por uno.", PoseChopper.COMIDA)
            pendientes == 1 -> MensajeChopper("¡Ya casi! Solo falta 1 cuidado para terminar el día.", PoseChopper.COMIDA)
            else -> MensajeChopper("¡Vas muy bien! Llevas ${total - pendientes} de $total; quedan $pendientes.", PoseChopper.COMIDA)
        }
    }

    /** Pequeños trucos de la app, uno distinto cada día. */
    fun consejo(hoy: LocalDate): String = CONSEJOS[(hoy.dayOfYear) % CONSEJOS.size]

    private val CONSEJOS = listOf(
        "Truco: desliza un cuidado hacia la derecha para marcarlo hecho.",
        "Truco: toca la foto de una mascota arriba para ver solo sus cuidados.",
        "Truco: en la ficha puedes ver el carné con su código QR.",
        "Truco: comparte el código de tu mascota para que alguien más te ayude a cuidarla.",
        "Truco: registra los paseos con GPS y verás su ruta en el mapa.",
        "Truco: toca \"Ver mes\" para ver cómo vas en todo el mes.",
    )

    /** Mensaje corto al marcar una toma (entre procesos). */
    fun alMarcar(titulo: String, mascota: String, quedan: Int): String = when (quedan) {
        0 -> "¡$titulo de $mascota listo! Terminaste todo lo de hoy 🎉"
        1 -> "¡Bien! $titulo de $mascota ✓ — queda 1"
        else -> "¡Bien! $titulo de $mascota ✓ — quedan $quedan"
    }
}
