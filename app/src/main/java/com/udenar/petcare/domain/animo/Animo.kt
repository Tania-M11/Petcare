package com.udenar.petcare.domain.animo

import com.udenar.petcare.domain.modelo.Icono
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * "¿Cómo está hoy?": el dueño anota en un toque cómo vio a su mascota. Es su propia
 * observación (no una evaluación de salud) y sirve para ver la semana de un vistazo.
 */
enum class Animo(val etiqueta: String, val icono: Icono) {
    FELIZ("Feliz", Icono.CORAZON),
    JUGUETON("Juguetón", Icono.JUEGO),
    TRANQUILO("Tranquilo", Icono.HOJA),
    CANSADO("Cansado", Icono.LUNA),
    DECAIDO("Decaído", Icono.NUBE),
}

data class RegistroAnimo(val mascotaId: String, val fecha: LocalDate, val animo: Animo)

interface RepositorioAnimo {
    /** Registros de [mascotaId] desde [desde] (incluido), del más antiguo al más reciente. */
    fun observar(mascotaId: String, desde: LocalDate): Flow<List<RegistroAnimo>>

    /** Lo anotado para todas las mascotas desde [desde] (incluido). */
    fun observarDesde(desde: LocalDate): Flow<List<RegistroAnimo>>

    /** Uno por mascota y día: volver a elegir reemplaza el anterior. */
    suspend fun guardar(r: RegistroAnimo)
}

object LecturaAnimo {
    /**
     * Nota amable cuando lleva varios días seguidos "Decaído" (hasta [hoy]). No diagnostica:
     * solo recuerda que su veterinario es quien puede revisarlo.
     */
    fun nota(nombre: String, registros: List<RegistroAnimo>, hoy: LocalDate): String? {
        val porDia = registros.associateBy { it.fecha }
        var seguidos = 0
        var d = hoy
        while (porDia[d]?.animo == Animo.DECAIDO) {
            seguidos++
            d = d.minusDays(1)
        }
        return if (seguidos >= 2) "$nombre lleva $seguidos días decaído según tus notas. Si sigue así, consulta a su veterinario." else null
    }

    /** Racha de días seguidos con algo anotado, contando hasta [hoy] (o ayer si hoy aún no). */
    fun racha(registros: List<RegistroAnimo>, hoy: LocalDate): Int {
        val dias = registros.map { it.fecha }.toSet()
        var d = if (hoy in dias) hoy else hoy.minusDays(1)
        var n = 0
        while (d in dias) {
            n++
            d = d.minusDays(1)
        }
        return n
    }
}
