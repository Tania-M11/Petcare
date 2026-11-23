package com.udenar.petcare.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recomendaciones que la persona descartó ("Ahora no"). Vuelven a aparecer después de [DIAS]
 * días si la situación sigue igual; los ids incluyen la fecha o el dato que las originó.
 */
@Singleton
class Descartes @Inject constructor(@ApplicationContext contexto: Context) {
    private val prefs = contexto.getSharedPreferences("petcare_descartes", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(leer())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    private fun leer(): Set<String> {
        val hoy = LocalDate.now()
        return prefs.all.mapNotNull { (id, hasta) -> id.takeIf { runCatching { LocalDate.parse(hasta as String) }.getOrNull()?.isAfter(hoy) == true } }.toSet()
    }

    fun descartar(id: String) {
        prefs.edit().putString(id, LocalDate.now().plusDays(DIAS).toString()).apply()
        _ids.value = _ids.value + id
    }

    fun borrarTodo() {
        prefs.edit().clear().apply()
        _ids.value = emptySet()
    }

    companion object {
        const val DIAS = 7L
    }
}
